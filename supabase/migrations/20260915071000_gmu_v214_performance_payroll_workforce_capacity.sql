-- GMU EduTrans v21.4 — Performance-Based Payroll & Workforce Capacity
-- Director target -> monthly profit -> revenue need -> SDM target -> automatic tasks -> execution -> closing -> actual profit
-- -> recovery gap -> target recalculation -> salary/fee capacity recalculation.
-- WhatsApp is intentionally NOT part of this automation layer.

create extension if not exists pgcrypto;

insert into public.company_control_settings(setting_key,numeric_value,description)
values
  ('VARIABLE_PAY_KPI_FLOOR',70,'Minimum KPI score for variable fee/commission eligibility'),
  ('WORKFORCE_OVERDUE_ALERT_PCT',20,'Overdue task ratio that signals capacity pressure'),
  ('WORKFORCE_MIN_STRONG_KPI_PCT',80,'Minimum KPI showing team is performing before additional headcount is considered')
on conflict(setting_key) do update
set numeric_value=excluded.numeric_value,description=excluded.description,updated_at=now();

create table if not exists public.performance_payroll_snapshots (
  id uuid primary key default gen_random_uuid(),
  period_month date not null,
  target_id uuid references public.executive_profit_targets(id) on delete set null,
  staff_id uuid not null references public.profiles(id) on delete cascade,
  role_name text not null,
  tasks_total integer not null default 0,
  tasks_done integer not null default 0,
  tasks_overdue integer not null default 0,
  task_completion_pct numeric(6,2) not null default 0,
  task_ontime_pct numeric(6,2) not null default 0,
  target_value numeric(18,2) not null default 0,
  sales_won_value numeric(18,2) not null default 0,
  sales_pipeline_value numeric(18,2) not null default 0,
  sales_followups integer not null default 0,
  sales_overdue_followups integer not null default 0,
  kpi_score numeric(6,2) not null default 0 check(kpi_score between 0 and 100),
  accrued_variable_pay numeric(18,2) not null default 0,
  payable_variable_pay numeric(18,2) not null default 0,
  held_variable_pay numeric(18,2) not null default 0,
  fixed_payroll_headroom numeric(18,2) not null default 0,
  company_forecast_gap numeric(18,2) not null default 0,
  recovery_gap numeric(18,2) not null default 0,
  detail jsonb not null default '{}'::jsonb,
  calculated_at timestamptz not null default now(),
  unique(period_month,staff_id)
);

create table if not exists public.workforce_capacity_reviews (
  id uuid primary key default gen_random_uuid(),
  period_month date not null,
  role_name text not null,
  active_staff integer not null default 0,
  tasks_total integer not null default 0,
  tasks_done integer not null default 0,
  tasks_overdue integer not null default 0,
  completion_pct numeric(6,2) not null default 0,
  overdue_pct numeric(6,2) not null default 0,
  company_forecast_gap numeric(18,2) not null default 0,
  weighted_pipeline numeric(18,2) not null default 0,
  fixed_payroll_headroom numeric(18,2) not null default 0,
  recommendation text not null check(recommendation in ('HEALTHY','OPTIMIZE_EXISTING','FREELANCE_POOL','RECRUIT_REVIEW','HOLD')),
  rationale text not null,
  recruitment_case_id uuid references public.recruitment_cases(id) on delete set null,
  calculated_at timestamptz not null default now(),
  unique(period_month,role_name)
);

create index if not exists performance_payroll_staff_idx on public.performance_payroll_snapshots(staff_id,period_month);
create index if not exists workforce_capacity_review_idx on public.workforce_capacity_reviews(period_month,recommendation,role_name);

alter table public.performance_payroll_snapshots enable row level security;
alter table public.workforce_capacity_reviews enable row level security;

drop policy if exists v214_performance_payroll_read on public.performance_payroll_snapshots;
create policy v214_performance_payroll_read on public.performance_payroll_snapshots for select to authenticated using (
  staff_id=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Finance'))
);

drop policy if exists v214_workforce_capacity_read on public.workforce_capacity_reviews;
create policy v214_workforce_capacity_read on public.workforce_capacity_reviews for select to authenticated using (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Finance'))
);

grant select on public.performance_payroll_snapshots,public.workforce_capacity_reviews to authenticated;

-- Trace variable-pay staging into the existing payroll ledger without replacing payroll_entries.
alter table public.payroll_entries add column if not exists source_accrual_id uuid references public.compensation_accruals(id) on delete set null;
create unique index if not exists payroll_entries_source_accrual_uq on public.payroll_entries(source_accrual_id) where source_accrual_id is not null;

create or replace function private.gmu_v214_role_match(p_profile_role text,p_role text)
returns boolean language sql immutable set search_path=pg_catalog as $$
  select case
    when p_role='Manager' then p_profile_role in ('Manager','Manager EduTrans')
    when p_role='Director' then p_profile_role in ('Owner','Director','Direktur')
    else p_profile_role=p_role
  end;
$$;
revoke all on function private.gmu_v214_role_match(text,text) from public,anon,authenticated;

create or replace function private.gmu_v214_refresh_performance_payroll()
returns void language plpgsql security definer set search_path=public,private,cron,pg_temp as $$
declare
  v_month date:=date_trunc('month',current_date)::date;
  t public.executive_profit_targets%rowtype;
  s public.target_cascade_snapshots%rowtype;
  c public.compensation_capacity_snapshots%rowtype;
  p record;
  a record;
  v_tasks integer; v_done integer; v_overdue integer; v_completion numeric; v_ontime numeric;
  v_target numeric; v_won numeric; v_pipeline numeric; v_followups integer; v_overdue_followups integer;
  v_kpi numeric; v_target_score numeric; v_followup_score numeric; v_company_profit_score numeric;
  v_accrued numeric; v_payable numeric; v_held numeric; v_kpi_floor numeric:=70;
  v_amount numeric; v_bonus_gate boolean; v_reason text;
  v_role text; v_active integer; v_role_tasks integer; v_role_done integer; v_role_overdue integer;
  v_role_completion numeric; v_role_overdue_pct numeric; v_rec text; v_rationale text; v_case uuid;
  v_overdue_alert numeric:=20; v_strong_kpi numeric:=80; v_avg_kpi numeric;
begin
  -- Always recalculate target and compensation capacity first, so closing data immediately cascades through the company.
  perform private.gmu_v212_refresh_target_cascade();
  perform private.gmu_v213_refresh_compensation();

  select * into t from public.executive_profit_targets where status='ACTIVE' order by created_at desc limit 1;
  if not found then return; end if;
  select * into s from public.target_cascade_snapshots where target_id=t.id order by created_at desc limit 1;
  if not found then return; end if;
  select * into c from public.compensation_capacity_snapshots where target_id=t.id order by created_at desc limit 1;

  select coalesce(numeric_value,70) into v_kpi_floor from public.company_control_settings where setting_key='VARIABLE_PAY_KPI_FLOOR';
  select coalesce(numeric_value,20) into v_overdue_alert from public.company_control_settings where setting_key='WORKFORCE_OVERDUE_ALERT_PCT';
  select coalesce(numeric_value,80) into v_strong_kpi from public.company_control_settings where setting_key='WORKFORCE_MIN_STRONG_KPI_PCT';

  -- Per-person KPI: actual work evidence first; Sales also receives commercial-result weighting.
  for p in select id,full_name,role::text as role_name from public.profiles where is_active=true loop
    select count(*)::integer,
           count(*) filter(where at.status='DONE')::integer,
           count(*) filter(where at.status='OVERDUE' or (at.due_at<now() and at.status not in ('DONE','CANCELLED')))::integer
      into v_tasks,v_done,v_overdue
      from public.automation_tasks at
     where at.created_at>=v_month and at.created_at<(v_month+interval '1 month')
       and (at.assigned_to=p.id or (at.assigned_to is null and private.gmu_v214_role_match(p.role_name,at.assigned_role)));

    v_completion:=case when v_tasks>0 then round(least(100,greatest(0,v_done*100.0/v_tasks)),2) else 0 end;
    v_ontime:=case when v_tasks>0 then round(least(100,greatest(0,100-(v_overdue*100.0/v_tasks))),2) else 0 end;

    select coalesce(max(a.target_value),0) into v_target
      from public.target_cascade_assignments a
     where a.period_month=v_month and a.status='ACTIVE' and a.assigned_to=p.id;

    v_won:=0; v_pipeline:=0; v_followups:=0; v_overdue_followups:=0;
    if p.role_name='Sales' then
      select coalesce(sum(estimated_value),0) into v_won from public.crm_lead_controls
       where owner_id=p.id and stage='WON' and won_at>=v_month and won_at<(v_month+interval '1 month');
      select coalesce(sum(estimated_value*probability_pct/100),0) into v_pipeline from public.crm_lead_controls
       where owner_id=p.id and stage not in ('WON','LOST');
      select count(*)::integer into v_followups from public.crm_activities
       where sales_id=p.id and created_at>=v_month and created_at<(v_month+interval '1 month');
      select count(*)::integer into v_overdue_followups from public.crm_lead_controls
       where owner_id=p.id and stage not in ('WON','LOST') and next_follow_up_at is not null and next_follow_up_at<now();

      v_target_score:=case when v_target>0 then least(100,v_won*100.0/v_target) else 0 end;
      v_followup_score:=case when v_followups+v_overdue_followups>0 then greatest(0,100-(v_overdue_followups*100.0/(v_followups+v_overdue_followups))) else 0 end;
      v_kpi:=round(least(100,greatest(0,(v_target_score*0.50)+(v_completion*0.30)+(v_followup_score*0.20))),2);
    elsif p.role_name in ('Owner','Director','Direktur','Manager','Manager EduTrans') then
      v_company_profit_score:=case when s.target_profit_month>0 then least(100,greatest(0,s.actual_profit*100.0/s.target_profit_month)) else 0 end;
      v_kpi:=round(least(100,greatest(0,(v_completion*0.50)+(v_ontime*0.20)+(v_company_profit_score*0.30))),2);
    else
      v_kpi:=round(least(100,greatest(0,(v_completion*0.70)+(v_ontime*0.30))),2);
    end if;

    select coalesce(sum(calculated_amount),0),
           coalesce(sum(calculated_amount*kpi_multiplier*collection_ratio) filter(where state in ('PAYABLE','PAID')),0),
           coalesce(sum(calculated_amount) filter(where state='ON_HOLD'),0)
      into v_accrued,v_payable,v_held
      from public.compensation_accruals ca
     where ca.staff_id=p.id and ca.created_at>=v_month and ca.created_at<(v_month+interval '1 month') and ca.state<>'VOID';

    insert into public.performance_payroll_snapshots(
      period_month,target_id,staff_id,role_name,tasks_total,tasks_done,tasks_overdue,task_completion_pct,task_ontime_pct,
      target_value,sales_won_value,sales_pipeline_value,sales_followups,sales_overdue_followups,kpi_score,
      accrued_variable_pay,payable_variable_pay,held_variable_pay,fixed_payroll_headroom,company_forecast_gap,recovery_gap,detail,calculated_at
    ) values(
      v_month,t.id,p.id,p.role_name,v_tasks,v_done,v_overdue,v_completion,v_ontime,
      v_target,v_won,v_pipeline,v_followups,v_overdue_followups,v_kpi,
      v_accrued,v_payable,v_held,coalesce(c.safe_additional_fixed_payroll_pool,0),s.forecast_gap,s.recovery_gap,
      jsonb_build_object('source','v21.4 performance-payroll','kpi_floor',v_kpi_floor,'profit_target',s.target_profit_month,'actual_profit',s.actual_profit,'compensation_health',c.health_status),now()
    )
    on conflict(period_month,staff_id) do update set
      target_id=excluded.target_id,role_name=excluded.role_name,tasks_total=excluded.tasks_total,tasks_done=excluded.tasks_done,tasks_overdue=excluded.tasks_overdue,
      task_completion_pct=excluded.task_completion_pct,task_ontime_pct=excluded.task_ontime_pct,target_value=excluded.target_value,
      sales_won_value=excluded.sales_won_value,sales_pipeline_value=excluded.sales_pipeline_value,sales_followups=excluded.sales_followups,
      sales_overdue_followups=excluded.sales_overdue_followups,kpi_score=excluded.kpi_score,accrued_variable_pay=excluded.accrued_variable_pay,
      payable_variable_pay=excluded.payable_variable_pay,held_variable_pay=excluded.held_variable_pay,fixed_payroll_headroom=excluded.fixed_payroll_headroom,
      company_forecast_gap=excluded.company_forecast_gap,recovery_gap=excluded.recovery_gap,detail=excluded.detail,calculated_at=now();
  end loop;

  -- Variable-pay lifecycle: performance + collection + margin + company safety gate.
  for a in
    select ca.*,cp.component_type,coalesce(pp.kpi_score,0) as staff_kpi
      from public.compensation_accruals ca
      left join public.compensation_policies cp on cp.policy_code=ca.policy_code
      left join public.performance_payroll_snapshots pp on pp.staff_id=ca.staff_id and pp.period_month=v_month
     where ca.state in ('ACCRUED','ELIGIBLE','PAYABLE','ON_HOLD')
  loop
    v_reason:=null;
    v_bonus_gate:=upper(coalesce(a.component_type,'')) like '%BONUS%';
    if a.staff_kpi<v_kpi_floor then v_reason:=format('KPI %s di bawah floor %s',round(a.staff_kpi,2),v_kpi_floor);
    elsif coalesce(a.collection_ratio,0)<=0 then v_reason:='Cash customer belum masuk';
    elsif a.margin_pct is not null and a.margin_pct<coalesce((select numeric_value from public.company_control_settings where setting_key='HEALTHY_MARGIN_FLOOR_PCT'),25) then v_reason:='Margin transaksi di bawah batas sehat';
    elsif v_bonus_gate and (coalesce(c.health_status,'HOLD') in ('LEAN','HOLD') or s.actual_profit<s.target_profit_month or s.recovery_gap>0) then v_reason:='Bonus ditahan sampai target laba/recovery dan kapasitas perusahaan sehat';
    end if;

    if v_reason is not null then
      update public.compensation_accruals set state='ON_HOLD',hold_reason=v_reason,kpi_multiplier=least(1,greatest(0,a.staff_kpi/100.0)),updated_at=now() where id=a.id;
    else
      update public.compensation_accruals set state='PAYABLE',hold_reason=null,kpi_multiplier=least(1,greatest(0,a.staff_kpi/100.0)),updated_at=now() where id=a.id;
      v_amount:=round(a.calculated_amount*least(1,greatest(0,a.staff_kpi/100.0))*least(1,greatest(0,a.collection_ratio)),0);
      if v_amount>0 then
        insert into public.payroll_entries(staff_id,booking_id,component_type,amount,state,earned_at,notes,source_accrual_id,created_at,updated_at)
        values(a.staff_id,a.booking_id,coalesce(a.component_type,'VARIABLE_FEE'),v_amount,'EARNED',now(),'[v21.4 AUTO] Earned from approved performance/cash/margin gates',a.id,now(),now())
        on conflict(source_accrual_id) where source_accrual_id is not null do update set amount=excluded.amount,component_type=excluded.component_type,notes=excluded.notes,updated_at=now();
      end if;
    end if;
  end loop;

  -- Approved payroll becomes a controlled Payment Request; actual transfer remains manual/approval-only.
  insert into public.payment_requests(request_no,category,booking_id,payee_name,payee_reference,amount,purpose,requested_by,approval_state,payment_state,notes)
  select 'PAYROLL-'||replace(pe.id::text,'-',''),
         case when upper(pe.component_type) like '%COMMISSION%' then 'COMMISSION' when upper(pe.component_type) like '%BONUS%' then 'BONUS' else 'PAYROLL' end,
         pe.booking_id,p.full_name,'PAYROLL_ENTRY:'||pe.id::text,pe.amount,'Payroll/fee earned — menunggu proses pembayaran',pe.approved_by,'SUBMITTED','UNPAID','[v21.4 AUTO] Payment Request only; no transfer executed.'
    from public.payroll_entries pe join public.profiles p on p.id=pe.staff_id
   where pe.state='APPROVED' and pe.amount>0
     and not exists(select 1 from public.payment_requests pr where pr.payee_reference='PAYROLL_ENTRY:'||pe.id::text)
  on conflict(request_no) do nothing;

  -- Role capacity engine: hire only when workload/target pressure is real AND existing team is performing.
  foreach v_role in array array['Sales','Admin','Operation','Finance'] loop
    select count(*)::integer into v_active from public.profiles p where p.is_active=true and private.gmu_v214_role_match(p.role::text,v_role);
    select count(*)::integer,
           count(*) filter(where at.status='DONE')::integer,
           count(*) filter(where at.status='OVERDUE' or (at.due_at<now() and at.status not in ('DONE','CANCELLED')))::integer
      into v_role_tasks,v_role_done,v_role_overdue
      from public.automation_tasks at
     where at.created_at>=v_month and at.created_at<(v_month+interval '1 month')
       and (at.assigned_role=v_role or at.evidence->>'desired_role'=v_role);
    v_role_completion:=case when v_role_tasks>0 then round(v_role_done*100.0/v_role_tasks,2) else 0 end;
    v_role_overdue_pct:=case when v_role_tasks>0 then round(v_role_overdue*100.0/v_role_tasks,2) else 0 end;
    select coalesce(avg(pp.kpi_score),0) into v_avg_kpi from public.performance_payroll_snapshots pp where pp.period_month=v_month and private.gmu_v214_role_match(pp.role_name,v_role);

    if s.forecast_gap<=0 then
      v_rec:='HOLD'; v_rationale:='Target forecast sudah tertutup; tidak ada alasan otomatis menambah headcount.';
    elsif v_active=0 then
      if coalesce(c.safe_additional_fixed_payroll_pool,0)>0 then v_rec:='RECRUIT_REVIEW'; v_rationale:='Role belum terisi, target masih gap, dan terdapat fixed-payroll headroom. Ajukan rekrutmen untuk review Owner.';
      else v_rec:='FREELANCE_POOL'; v_rationale:='Role belum terisi tetapi fixed-payroll headroom belum tersedia. Gunakan kapasitas variabel/freelance dan fallback Manager sementara.'; end if;
    elsif v_avg_kpi<v_strong_kpi then
      v_rec:='OPTIMIZE_EXISTING'; v_rationale:=format('KPI rata-rata %s%% belum cukup kuat. Optimalkan SDM existing sebelum menambah orang.',round(v_avg_kpi,1));
    elsif v_role_overdue_pct>=v_overdue_alert and coalesce(c.safe_additional_fixed_payroll_pool,0)>0 then
      v_rec:='RECRUIT_REVIEW'; v_rationale:=format('Tim berkinerja kuat tetapi overdue %s%% menunjukkan tekanan kapasitas. Fixed-payroll headroom tersedia; review tambahan SDM.',round(v_role_overdue_pct,1));
    elsif v_role_overdue_pct>=v_overdue_alert then
      v_rec:='FREELANCE_POOL'; v_rationale:='Tekanan kapasitas nyata tetapi fixed-payroll headroom belum aman. Gunakan freelance/variable capacity.';
    else
      v_rec:='HEALTHY'; v_rationale:='Kapasitas existing masih cukup terhadap workload dan SLA saat ini.';
    end if;

    v_case:=null;
    if v_rec='RECRUIT_REVIEW' then
      select rc.id into v_case from public.recruitment_cases rc
       where rc.position_title=v_role and rc.status in ('NEED_REVIEW','APPROVED','SOURCING','INTERVIEW','OFFER','ONBOARDING')
       order by rc.created_at desc limit 1;
      if v_case is null then
        insert into public.recruitment_cases(position_title,employment_type,reason,need_by,status,owner_id,notes,created_at,updated_at)
        values(v_role,'CONTRACT',v_rationale,current_date+14,'NEED_REVIEW',private.gmu_v212_pick_role('Manager'),'[v21.4 AUTO] Recruitment proposal only. Owner/Manager must review; no automatic hiring.',now(),now()) returning id into v_case;
      end if;
    end if;

    insert into public.workforce_capacity_reviews(period_month,role_name,active_staff,tasks_total,tasks_done,tasks_overdue,completion_pct,overdue_pct,company_forecast_gap,weighted_pipeline,fixed_payroll_headroom,recommendation,rationale,recruitment_case_id,calculated_at)
    values(v_month,v_role,v_active,v_role_tasks,v_role_done,v_role_overdue,v_role_completion,v_role_overdue_pct,s.forecast_gap,s.weighted_pipeline,coalesce(c.safe_additional_fixed_payroll_pool,0),v_rec,v_rationale,v_case,now())
    on conflict(period_month,role_name) do update set active_staff=excluded.active_staff,tasks_total=excluded.tasks_total,tasks_done=excluded.tasks_done,tasks_overdue=excluded.tasks_overdue,completion_pct=excluded.completion_pct,overdue_pct=excluded.overdue_pct,company_forecast_gap=excluded.company_forecast_gap,weighted_pipeline=excluded.weighted_pipeline,fixed_payroll_headroom=excluded.fixed_payroll_headroom,recommendation=excluded.recommendation,rationale=excluded.rationale,recruitment_case_id=coalesce(excluded.recruitment_case_id,public.workforce_capacity_reviews.recruitment_case_id),calculated_at=now();
  end loop;

  -- Recalculate compensation one more time after earned payroll has changed people-cost reality.
  perform private.gmu_v213_refresh_compensation();
end;$$;
revoke all on function private.gmu_v214_refresh_performance_payroll() from public,anon,authenticated;

create or replace function public.internal_performance_payroll_v214_status()
returns jsonb language plpgsql stable security invoker set search_path=public,private,pg_temp as $$
declare v_month date:=date_trunc('month',current_date)::date; v jsonb;
begin
  if not private.gmu_v212_is_management() and not exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text='Finance') then raise exception 'forbidden'; end if;
  select jsonb_build_object(
    'generated_at',now(),
    'cascade',(select to_jsonb(x) from public.target_cascade_snapshots x where x.period_month=v_month order by x.created_at desc limit 1),
    'compensation',(select to_jsonb(x) from public.compensation_capacity_snapshots x where x.period_month=v_month order by x.created_at desc limit 1),
    'staff',coalesce((select jsonb_agg(to_jsonb(x) order by x.role_name,x.kpi_score desc) from public.performance_payroll_snapshots x where x.period_month=v_month),'[]'::jsonb),
    'workforce',coalesce((select jsonb_agg(to_jsonb(w) order by w.role_name) from public.workforce_capacity_reviews w where w.period_month=v_month),'[]'::jsonb),
    'payroll_waiting_approval',(select count(*) from public.payroll_entries where state='EARNED'),
    'payroll_approved_waiting_payment',(select count(*) from public.payroll_entries where state='APPROVED'),
    'payment_requests_waiting',(select count(*) from public.payment_requests where approval_state='SUBMITTED' and payment_state='UNPAID'),
    'whatsapp_automation',false
  ) into v;
  return v;
end;$$;
grant execute on function public.internal_performance_payroll_v214_status() to authenticated;

-- Closing is the authoritative moment for profit recalculation.
create or replace function private.gmu_v214_after_trip_closing()
returns trigger language plpgsql security definer set search_path=public,private,cron,pg_temp as $$
begin
  perform private.gmu_v214_refresh_performance_payroll();
  return new;
end;$$;
revoke all on function private.gmu_v214_after_trip_closing() from public,anon,authenticated;
drop trigger if exists trg_v214_after_trip_closing on public.trip_closings;
create trigger trg_v214_after_trip_closing after insert or update on public.trip_closings for each statement execute function private.gmu_v214_after_trip_closing();

-- Run immediately and keep company target/payroll/workforce capacity synchronized.
select private.gmu_v214_refresh_performance_payroll();

DO $$
DECLARE jid bigint;
BEGIN
  select jobid into jid from cron.job where jobname='gmu_v214_performance_payroll_workforce';
  if jid is not null then perform cron.unschedule(jid); end if;
  perform cron.schedule('gmu_v214_performance_payroll_workforce','8,18,28,38,48,58 * * * *',$cmd$select private.gmu_v214_refresh_performance_payroll();$cmd$);
END$$;
