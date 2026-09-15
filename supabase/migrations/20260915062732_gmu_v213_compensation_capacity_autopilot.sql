-- GMU EduTrans v21.3 Compensation Capacity Autopilot
-- Production migration: 20260915062732 on gtgnwasijweewmaubvyg
-- Target profit first; compensation second. Recommendations never auto-change payroll.

create table if not exists public.compensation_capacity_snapshots (
  id uuid primary key default gen_random_uuid(),
  target_id uuid references public.executive_profit_targets(id) on delete set null,
  period_month date not null,
  recovery_gap numeric not null default 0,
  monthly_profit_target numeric not null default 0,
  actual_profit numeric not null default 0,
  forecast_profit numeric not null default 0,
  forecast_gap numeric not null default 0,
  target_revenue numeric not null default 0,
  weighted_pipeline numeric not null default 0,
  current_fixed_payroll numeric not null default 0,
  current_variable_people_cost numeric not null default 0,
  safe_additional_fixed_payroll_pool numeric not null default 0,
  health_status text not null check(health_status in ('LEAN','HOLD','REVIEW','HEALTHY')),
  detail jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists compensation_capacity_snapshots_month_idx on public.compensation_capacity_snapshots(period_month,created_at desc);

create table if not exists public.compensation_recommendations (
  id uuid primary key default gen_random_uuid(),
  recommendation_key text not null unique,
  target_id uuid references public.executive_profit_targets(id) on delete set null,
  period_month date not null,
  role_name text not null,
  staff_id uuid references public.profiles(id) on delete set null,
  current_base_monthly numeric not null default 0,
  current_transport_per_day numeric not null default 0,
  healthy_trip_fee_median numeric,
  healthy_trip_fee_sample_count integer not null default 0,
  company_fixed_payroll_headroom numeric not null default 0,
  recommendation_mode text not null check(recommendation_mode in ('VARIABLE_ONLY','HOLD_CURRENT','REVIEW_FIXED','REVIEW_VARIABLE','HEALTHY')),
  recommended_fee_low numeric,
  recommended_fee_high numeric,
  confidence text not null default 'LOW' check(confidence in ('LOW','MEDIUM','HIGH')),
  rationale text not null,
  approval_required boolean not null default true,
  status text not null default 'ACTIVE' check(status in ('ACTIVE','APPROVED','REJECTED','SUPERSEDED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists compensation_recommendations_role_idx on public.compensation_recommendations(role_name,status,period_month);
create index if not exists compensation_recommendations_staff_idx on public.compensation_recommendations(staff_id,status,period_month);

alter table public.compensation_capacity_snapshots enable row level security;
alter table public.compensation_recommendations enable row level security;
create policy v213_management_read_capacity on public.compensation_capacity_snapshots for select to authenticated using (private.gmu_v212_is_management());
create policy v213_management_read_recommendations on public.compensation_recommendations for select to authenticated using (private.gmu_v212_is_management());
grant select on public.compensation_capacity_snapshots,public.compensation_recommendations to authenticated;

create or replace function private.gmu_v213_refresh_compensation()
returns void language plpgsql security definer set search_path=public,private,pg_temp as $$
declare
  t public.executive_profit_targets%rowtype;
  s public.target_cascade_snapshots%rowtype;
  v_month date:=date_trunc('month',current_date)::date;
  v_variable numeric:=0; v_health text; v_role text; v_staff uuid; v_base numeric; v_transport numeric;
  v_median numeric; v_samples integer; v_mode text; v_conf text; v_low numeric; v_high numeric; v_rationale text;
  v_headroom numeric:=0; v_actual numeric:=0; v_forecast_gap numeric:=0; v_recovery_gap numeric:=0;
  v_month_target numeric:=0; v_target_revenue numeric:=0; v_weighted numeric:=0; v_fixed numeric:=0;
begin
  select * into t from public.executive_profit_targets where status='ACTIVE' order by created_at desc limit 1;
  if not found then return; end if;
  select * into s from public.target_cascade_snapshots where target_id=t.id order by created_at desc limit 1;
  if not found then perform private.gmu_v212_refresh_target_cascade(); select * into s from public.target_cascade_snapshots where target_id=t.id order by created_at desc limit 1; end if;
  if not found then return; end if;

  v_headroom:=greatest(coalesce(s.additional_fixed_payroll_headroom,0),0);
  v_actual:=coalesce(s.actual_profit,0); v_forecast_gap:=coalesce(s.forecast_gap,0); v_recovery_gap:=coalesce(s.recovery_gap,0);
  v_month_target:=coalesce(s.target_profit_month,0); v_target_revenue:=coalesce(s.target_revenue_month,0);
  v_weighted:=coalesce(s.weighted_pipeline,0); v_fixed:=coalesce(s.fixed_payroll,0);

  select coalesce(sum(amount),0) into v_variable from public.payroll_entries
  where earned_at>=v_month and earned_at<(v_month+interval '1 month') and state not in ('CANCELLED','VOID');

  v_health:=case when v_recovery_gap>0 and v_headroom<=0 then 'LEAN' when v_forecast_gap>0 then 'HOLD'
    when v_headroom>0 and (v_actual+coalesce(s.forecast_profit_30d,0))>=v_month_target then 'REVIEW' else 'HEALTHY' end;

  insert into public.compensation_capacity_snapshots(target_id,period_month,recovery_gap,monthly_profit_target,actual_profit,forecast_profit,forecast_gap,target_revenue,weighted_pipeline,current_fixed_payroll,current_variable_people_cost,safe_additional_fixed_payroll_pool,health_status,detail)
  values(t.id,v_month,v_recovery_gap,v_month_target,v_actual,coalesce(s.forecast_profit_30d,0),v_forecast_gap,v_target_revenue,v_weighted,v_fixed,v_variable,v_headroom,v_health,
    jsonb_build_object('principle','TARGET_PROFIT_FIRST_COMPENSATION_SECOND','fixed_payroll_rule','New fixed salary/retainer must fit inside safe_additional_fixed_payroll_pool and still require Owner approval.','variable_fee_rule','Use real healthy-trip payroll history; no fabricated fee if sample is insufficient.','recovery_mode',t.target_mode='RECOVERY_CUMULATIVE' and v_recovery_gap>0,'legal_note','Budget capacity is not a substitute for applicable wage/employment law.'));

  update public.compensation_recommendations set status='SUPERSEDED',updated_at=now() where period_month=v_month and status='ACTIVE';

  foreach v_role in array array['Manager','Sales','Admin','Finance','Operation','TL'] loop
    v_staff:=private.gmu_v212_pick_role(v_role);
    select coalesce(avg(cp.base_monthly),0),coalesce(avg(cp.transport_per_attendance_day),0) into v_base,v_transport
    from public.staff_compensation_profiles cp join public.profiles p on p.id=cp.staff_id
    where cp.status='ACTIVE' and cp.effective_from<=current_date and (cp.effective_to is null or cp.effective_to>=current_date)
      and case when v_role='Manager' then p.role::text in ('Manager','Manager EduTrans') else p.role::text=v_role end;

    select count(*)::integer,percentile_cont(0.5) within group(order by pe.amount)::numeric into v_samples,v_median
    from public.payroll_entries pe join public.profiles p on p.id=pe.staff_id join public.trip_closings tc on tc.booking_id=pe.booking_id
    where pe.earned_at>=now()-interval '180 days' and pe.amount>0
      and upper(coalesce(pe.component_type,'')) in ('TRIP_FEE','FEE','COMMISSION','SALES_COMMISSION','TASK_FEE')
      and tc.margin_pct>=25 and tc.net_profit>0 and pe.state in ('EARNED','APPROVED','PAID')
      and case when v_role='Manager' then p.role::text in ('Manager','Manager EduTrans') else p.role::text=v_role end;

    v_conf:=case when v_samples>=5 then 'HIGH' when v_samples>=3 then 'MEDIUM' else 'LOW' end;
    v_low:=case when v_samples>=3 then round(v_median*0.90,0) else null end;
    v_high:=case when v_samples>=3 then round(v_median*1.10,0) else null end;

    if v_recovery_gap>0 and v_headroom<=0 then
      v_mode:=case when v_base>0 then 'HOLD_CURRENT' else 'VARIABLE_ONLY' end;
      v_rationale:=case when v_base>0 then 'Recovery Rp50 juta masih berjalan dan belum ada fixed-payroll headroom. Pertahankan komitmen tetap yang sudah disetujui; jangan tambah gaji tetap.' else 'Recovery Rp50 juta masih berjalan dan fixed-payroll headroom = 0. Gunakan fee berbasis pekerjaan/hasil sampai pipeline dan laba mendukung biaya tetap.' end;
    elsif v_forecast_gap>0 then v_mode:='HOLD_CURRENT'; v_rationale:='Forecast laba masih di bawah target. Tahan kenaikan fixed payroll dan fokuskan insentif pada hasil yang benar-benar menghasilkan laba.';
    elsif v_headroom>0 and v_staff is null then v_mode:='REVIEW_FIXED'; v_rationale:='Ada kapasitas fixed payroll perusahaan, tetapi role belum aktif. Manager dapat mengajukan kebutuhan SDM; Owner tetap menyetujui nominal dan status hubungan kerja.';
    elsif v_samples>=3 then v_mode:='REVIEW_VARIABLE'; v_rationale:='Data trip sehat sudah cukup untuk review fee variabel. Rentang rekomendasi memakai ±10% dari median fee aktual pada trip margin ≥25%.';
    else v_mode:='HOLD_CURRENT'; v_rationale:='Belum cukup histori payroll pada trip sehat untuk menghitung nominal fee yang dapat dipertanggungjawabkan. Sistem tidak mengarang angka.'; end if;

    insert into public.compensation_recommendations(recommendation_key,target_id,period_month,role_name,staff_id,current_base_monthly,current_transport_per_day,healthy_trip_fee_median,healthy_trip_fee_sample_count,company_fixed_payroll_headroom,recommendation_mode,recommended_fee_low,recommended_fee_high,confidence,rationale,approval_required,status)
    values('v213:'||to_char(v_month,'YYYY-MM')||':'||lower(v_role),t.id,v_month,v_role,v_staff,v_base,v_transport,case when v_samples>=3 then v_median else null end,v_samples,v_headroom,v_mode,v_low,v_high,v_conf,v_rationale,true,'ACTIVE')
    on conflict(recommendation_key) do update set target_id=excluded.target_id,staff_id=excluded.staff_id,current_base_monthly=excluded.current_base_monthly,current_transport_per_day=excluded.current_transport_per_day,healthy_trip_fee_median=excluded.healthy_trip_fee_median,healthy_trip_fee_sample_count=excluded.healthy_trip_fee_sample_count,company_fixed_payroll_headroom=excluded.company_fixed_payroll_headroom,recommendation_mode=excluded.recommendation_mode,recommended_fee_low=excluded.recommended_fee_low,recommended_fee_high=excluded.recommended_fee_high,confidence=excluded.confidence,rationale=excluded.rationale,approval_required=true,status='ACTIVE',updated_at=now();
  end loop;

  perform private.gmu_v212_sync_task('v213:compensation-capacity:'||to_char(v_month,'YYYY-MM'),v_fixed>v_headroom and v_forecast_gap>0 and v_fixed>0,'Manager','Jaga biaya SDM terhadap target laba',format('Fixed payroll aktif Rp%s, safe additional fixed payroll pool Rp%s, forecast gap Rp%s. Jangan menambah komitmen gaji tetap sebelum target/pipeline mendukung. Semua perubahan kompensasi tetap approval Owner.',v_fixed,v_headroom,v_forecast_gap),'HIGH',24);
  perform private.gmu_v212_sync_task('v213:bonus-review:'||to_char(v_month,'YYYY-MM'),v_recovery_gap<=0 and v_actual>v_month_target,'Director','Review bonus setelah target recovery tercapai','Target recovery sudah tercapai dan laba bulan berjalan melampaui floor. Review bonus berbasis laba/cash hanya setelah Finance memastikan closing dan likuiditas sehat.','NORMAL',72);
  delete from public.compensation_capacity_snapshots where created_at<now()-interval '180 days';
end;$$;
revoke all on function private.gmu_v213_refresh_compensation() from public,anon,authenticated;

create or replace function public.internal_compensation_autopilot_status()
returns jsonb language plpgsql stable security invoker set search_path=public,private,pg_temp as $$
declare v jsonb; v_month date:=date_trunc('month',current_date)::date;
begin
  if not private.gmu_v212_is_management() then raise exception 'forbidden'; end if;
  select jsonb_build_object('capacity',to_jsonb(c),'recommendations',coalesce((select jsonb_agg(to_jsonb(r) order by r.role_name) from public.compensation_recommendations r where r.period_month=v_month and r.status='ACTIVE'),'[]'::jsonb)) into v
  from public.compensation_capacity_snapshots c where c.period_month=v_month order by c.created_at desc limit 1;
  return coalesce(v,'{}'::jsonb);
end;$$;
grant execute on function public.internal_compensation_autopilot_status() to authenticated;

create or replace function public.internal_set_executive_profit_target(
  p_target_amount numeric,p_target_mode text default 'RECOVERY_CUMULATIVE',p_monthly_profit_floor numeric default 15000000,p_target_margin_pct numeric default 25,p_recovery_start_date date default current_date,p_notes text default null
) returns jsonb language plpgsql security invoker set search_path=public,private,pg_temp as $$
declare v_mode text:=upper(trim(coalesce(p_target_mode,''))); v_id uuid; v_key text;
begin
  if not private.gmu_v212_is_director() then raise exception 'forbidden'; end if;
  if p_target_amount is null or p_target_amount<=0 then raise exception 'Target profit must be positive'; end if;
  if v_mode not in ('MONTHLY_NET_PROFIT','RECOVERY_CUMULATIVE') then raise exception 'Invalid target mode'; end if;
  if p_target_margin_pct<=0 or p_target_margin_pct>100 then raise exception 'Invalid target margin'; end if;
  update public.executive_profit_targets set status='SUPERSEDED',updated_at=now() where target_mode=v_mode and status='ACTIVE';
  v_key:='v212:'||lower(v_mode)||':'||to_char(now(),'YYYYMMDDHH24MISSMS');
  insert into public.executive_profit_targets(target_key,target_mode,target_amount,monthly_profit_floor,target_margin_pct,period_month,recovery_start_date,status,set_by,notes)
  values(v_key,v_mode,p_target_amount,greatest(coalesce(p_monthly_profit_floor,0),0),p_target_margin_pct,case when v_mode='MONTHLY_NET_PROFIT' then date_trunc('month',current_date)::date else null end,case when v_mode='RECOVERY_CUMULATIVE' then coalesce(p_recovery_start_date,current_date) else null end,'ACTIVE',(select auth.uid()),p_notes) returning id into v_id;
  perform private.gmu_v212_refresh_target_cascade();
  perform private.gmu_v213_refresh_compensation();
  return jsonb_build_object('ok',true,'target_id',v_id,'target_mode',v_mode,'target_amount',p_target_amount,'compensation_recalculated',true);
end;$$;
grant execute on function public.internal_set_executive_profit_target(numeric,text,numeric,numeric,date,text) to authenticated;

select private.gmu_v213_refresh_compensation();

DO $$
DECLARE jid bigint;
BEGIN
  select jobid into jid from cron.job where jobname='gmu_v213_compensation_autopilot';
  if jid is not null then perform cron.unschedule(jid); end if;
  perform cron.schedule('gmu_v213_compensation_autopilot','5,15,25,35,45,55 * * * *',$cmd$select private.gmu_v213_refresh_compensation();$cmd$);
END$$;