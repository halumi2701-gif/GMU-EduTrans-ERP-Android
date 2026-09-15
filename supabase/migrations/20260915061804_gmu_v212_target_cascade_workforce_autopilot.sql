-- GMU EduTrans v21.2 Target Cascade & Workforce Autopilot
-- Production migration: 20260915061804 on project gtgnwasijweewmaubvyg
-- Director Target -> Profit Gap -> Revenue Need -> Workforce Execution -> Actual Profit -> Reforecast

create table if not exists public.executive_profit_targets (
  id uuid primary key default gen_random_uuid(),
  target_key text not null unique,
  target_mode text not null check (target_mode in ('MONTHLY_NET_PROFIT','RECOVERY_CUMULATIVE')),
  target_amount numeric not null check (target_amount > 0),
  monthly_profit_floor numeric not null default 15000000 check (monthly_profit_floor >= 0),
  target_margin_pct numeric not null default 25 check (target_margin_pct > 0 and target_margin_pct <= 100),
  period_month date,
  recovery_start_date date,
  status text not null default 'ACTIVE' check (status in ('ACTIVE','ACHIEVED','SUPERSEDED','CANCELLED')),
  set_by uuid references public.profiles(id) on delete set null,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index if not exists executive_profit_targets_active_mode_uq
  on public.executive_profit_targets(target_mode) where status='ACTIVE';

create table if not exists public.target_cascade_snapshots (
  id uuid primary key default gen_random_uuid(),
  target_id uuid not null references public.executive_profit_targets(id) on delete cascade,
  period_month date not null,
  actual_profit numeric not null default 0,
  target_profit_month numeric not null default 0,
  recovery_actual numeric not null default 0,
  recovery_gap numeric not null default 0,
  target_margin_pct numeric not null default 25,
  required_revenue_month numeric not null default 0,
  target_revenue_month numeric not null default 0,
  required_revenue_recovery numeric not null default 0,
  avg_profit_per_trip numeric,
  required_bookings_month integer,
  weighted_pipeline numeric not null default 0,
  forecast_profit_30d numeric not null default 0,
  forecast_gap numeric not null default 0,
  active_sales_count integer not null default 0,
  fixed_payroll numeric not null default 0,
  planned_overhead numeric not null default 0,
  additional_fixed_payroll_headroom numeric not null default 0,
  data_confidence text not null default 'LOW' check (data_confidence in ('LOW','MEDIUM','HIGH')),
  workforce_status text not null default 'GAP' check (workforce_status in ('GAP','READY','OVER_CAPACITY')),
  detail jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists target_cascade_snapshots_target_idx on public.target_cascade_snapshots(target_id,created_at desc);

create table if not exists public.target_cascade_assignments (
  id uuid primary key default gen_random_uuid(),
  assignment_key text not null unique,
  target_id uuid not null references public.executive_profit_targets(id) on delete cascade,
  period_month date not null,
  assigned_role text not null,
  assigned_to uuid references public.profiles(id) on delete set null,
  objective text not null,
  target_value numeric,
  target_unit text,
  cadence text not null default 'MONTHLY',
  status text not null default 'ACTIVE' check(status in ('ACTIVE','ACHIEVED','PAUSED','SUPERSEDED')),
  payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists target_cascade_assignments_role_idx on public.target_cascade_assignments(assigned_role,status,period_month);
create index if not exists target_cascade_assignments_assignee_idx on public.target_cascade_assignments(assigned_to,status,period_month);

alter table public.executive_profit_targets enable row level security;
alter table public.target_cascade_snapshots enable row level security;
alter table public.target_cascade_assignments enable row level security;

create or replace function private.gmu_v212_is_director()
returns boolean language sql stable set search_path=public,pg_temp as $$
  select exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Direktur'));
$$;
revoke all on function private.gmu_v212_is_director() from public,anon,authenticated;

create or replace function private.gmu_v212_is_management()
returns boolean language sql stable set search_path=public,pg_temp as $$
  select exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans'));
$$;
revoke all on function private.gmu_v212_is_management() from public,anon,authenticated;

create policy v212_management_read_profit_targets on public.executive_profit_targets
for select to authenticated using (private.gmu_v212_is_management());
create policy v212_management_read_cascade_snapshots on public.target_cascade_snapshots
for select to authenticated using (private.gmu_v212_is_management());
create policy v212_staff_read_own_cascade_assignment on public.target_cascade_assignments
for select to authenticated using (
  private.gmu_v212_is_management() or assigned_to=(select auth.uid()) or exists(
    select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and (
      p.role::text=target_cascade_assignments.assigned_role
      or (target_cascade_assignments.assigned_role='Manager' and p.role::text='Manager EduTrans')
      or (target_cascade_assignments.assigned_role='Director' and p.role::text in ('Owner','Direktur'))
    )
  )
);
grant select on public.executive_profit_targets,public.target_cascade_snapshots,public.target_cascade_assignments to authenticated;

create or replace function private.gmu_v212_role_count(p_role text)
returns integer language sql stable security definer set search_path=public,pg_temp as $$
  select count(*)::integer from public.profiles p where p.is_active=true and case
    when p_role='Manager' then p.role::text in ('Manager','Manager EduTrans')
    when p_role='Director' then p.role::text in ('Owner','Director','Direktur')
    else p.role::text=p_role end;
$$;
revoke all on function private.gmu_v212_role_count(text) from public,anon,authenticated;

create or replace function private.gmu_v212_pick_role(p_role text)
returns uuid language sql stable security definer set search_path=public,pg_temp as $$
  select p.id from public.profiles p where p.is_active=true and case
    when p_role='Manager' then p.role::text in ('Manager','Manager EduTrans')
    when p_role='Director' then p.role::text in ('Owner','Director','Direktur')
    else p.role::text=p_role end
  order by p.created_at,p.id limit 1;
$$;
revoke all on function private.gmu_v212_pick_role(text) from public,anon,authenticated;

create or replace function private.gmu_v212_fallback_role(p_desired text)
returns text language plpgsql stable security definer set search_path=public,private,pg_temp as $$
begin
  if private.gmu_v212_role_count(p_desired)>0 then return p_desired; end if;
  if private.gmu_v212_role_count('Manager')>0 then return 'Manager'; end if;
  return 'Owner';
end;$$;
revoke all on function private.gmu_v212_fallback_role(text) from public,anon,authenticated;

create or replace function private.gmu_v212_upsert_assignment(
  p_key text,p_target uuid,p_month date,p_role text,p_objective text,p_value numeric,p_unit text,p_cadence text,p_payload jsonb
) returns void language plpgsql security definer set search_path=public,private,pg_temp as $$
declare v_role text:=private.gmu_v212_fallback_role(p_role); v_to uuid;
begin
  v_to:=private.gmu_v212_pick_role(v_role);
  insert into public.target_cascade_assignments(assignment_key,target_id,period_month,assigned_role,assigned_to,objective,target_value,target_unit,cadence,status,payload)
  values(p_key,p_target,p_month,v_role,v_to,p_objective,p_value,p_unit,coalesce(p_cadence,'MONTHLY'),'ACTIVE',coalesce(p_payload,'{}'::jsonb)||jsonb_build_object('desired_role',p_role,'fallback_used',v_role<>p_role))
  on conflict(assignment_key) do update set assigned_role=excluded.assigned_role,assigned_to=excluded.assigned_to,objective=excluded.objective,target_value=excluded.target_value,target_unit=excluded.target_unit,cadence=excluded.cadence,status='ACTIVE',payload=excluded.payload,updated_at=now();
end;$$;
revoke all on function private.gmu_v212_upsert_assignment(text,uuid,date,text,text,numeric,text,text,jsonb) from public,anon,authenticated;

create or replace function private.gmu_v212_sync_task(
  p_key text,p_active boolean,p_desired_role text,p_title text,p_description text,p_priority text default 'HIGH',p_due_hours integer default 24
) returns void language plpgsql security definer set search_path=public,private,pg_temp as $$
declare v_role text:=private.gmu_v212_fallback_role(p_desired_role); v_to uuid:=private.gmu_v212_pick_role(v_role);
begin
  if p_active then
    insert into public.automation_tasks(task_key,assigned_to,assigned_role,task_type,title,description,due_at,priority,status,evidence_required,evidence,approval_required,created_at,updated_at)
    values(p_key,v_to,v_role,'TARGET_CASCADE',p_title,p_description,now()+make_interval(hours=>greatest(p_due_hours,1)),p_priority,'OPEN',true,jsonb_build_object('desired_role',p_desired_role,'source','v21.2 target cascade'),false,now(),now())
    on conflict(task_key) do update set assigned_to=excluded.assigned_to,assigned_role=excluded.assigned_role,title=excluded.title,description=excluded.description,priority=excluded.priority,due_at=case when public.automation_tasks.status in ('DONE','CANCELLED') then excluded.due_at else public.automation_tasks.due_at end,status=case when public.automation_tasks.status in ('DONE','CANCELLED') then 'OPEN' else public.automation_tasks.status end,evidence=excluded.evidence,completed_at=case when public.automation_tasks.status in ('DONE','CANCELLED') then null else public.automation_tasks.completed_at end,updated_at=now();
  else
    update public.automation_tasks set status='DONE',completed_at=coalesce(completed_at,now()),updated_at=now() where task_key=p_key and status not in ('DONE','CANCELLED');
  end if;
end;$$;
revoke all on function private.gmu_v212_sync_task(text,boolean,text,text,text,text,integer) from public,anon,authenticated;

create or replace function private.gmu_v212_refresh_target_cascade()
returns void language plpgsql security definer set search_path=public,private,cron,pg_temp as $$
declare
  t public.executive_profit_targets%rowtype;
  v_month date:=date_trunc('month',current_date)::date;
  v_month_target numeric; v_month_actual numeric:=0; v_recovery_actual numeric:=0; v_recovery_gap numeric:=0;
  v_margin numeric; v_target_contribution numeric; v_target_revenue numeric; v_month_gap numeric;
  v_required_revenue_month numeric; v_required_revenue_recovery numeric; v_avg_profit numeric:=0; v_closed_count integer:=0;
  v_required_bookings integer; v_weighted numeric:=0; v_forecast jsonb; v_forecast_profit numeric:=0; v_forecast_gap numeric:=0;
  v_sales_count integer:=0; v_fixed_payroll numeric:=0; v_overhead numeric:=0; v_headroom numeric:=0;
  v_confidence text; v_workforce text; v_missing_roles text[]:='{}'::text[]; r text; s record;
  v_per_sales_revenue numeric; v_per_sales_profit numeric; v_per_sales_bookings integer; v_snapshot_detail jsonb;
begin
  for t in select * from public.executive_profit_targets where status='ACTIVE' order by created_at loop
    v_margin:=greatest(coalesce(t.target_margin_pct,25),1);
    v_month_target:=case when t.target_mode='MONTHLY_NET_PROFIT' then t.target_amount else greatest(t.monthly_profit_floor,0) end;
    begin v_month_actual:=coalesce((private.gl_profit_loss(v_month,current_date)->>'net_profit')::numeric,0); exception when others then v_month_actual:=0; end;
    if t.target_mode='RECOVERY_CUMULATIVE' then
      begin v_recovery_actual:=coalesce((private.gl_profit_loss(coalesce(t.recovery_start_date,current_date),current_date)->>'net_profit')::numeric,0); exception when others then v_recovery_actual:=0; end;
      v_recovery_gap:=greatest(t.target_amount-greatest(v_recovery_actual,0),0);
    else v_recovery_actual:=v_month_actual; v_recovery_gap:=greatest(t.target_amount-v_month_actual,0); end if;

    select count(*)::integer,coalesce(avg(nullif(net_profit,0)),0) into v_closed_count,v_avg_profit from public.trip_closings where closed_at>=now()-interval '90 days';
    v_confidence:=case when v_closed_count>=5 and v_avg_profit>0 then 'HIGH' when v_closed_count>=1 and v_avg_profit>0 then 'MEDIUM' else 'LOW' end;
    select coalesce(sum(base_monthly),0) into v_fixed_payroll from public.staff_compensation_profiles where status='ACTIVE' and effective_from<=current_date and (effective_to is null or effective_to>=current_date);
    select coalesce(sum(amount),0) into v_overhead from public.planning_budgets where period_month=v_month and upper(coalesce(budget_type,'')) in ('OVERHEAD','FIXED','OPERATING','OPEX');

    v_target_contribution:=v_month_target+v_fixed_payroll+v_overhead;
    v_target_revenue:=round(v_target_contribution/(v_margin/100),0);
    v_month_gap:=greatest(v_month_target-v_month_actual,0);
    v_required_revenue_month:=round((v_month_gap+v_fixed_payroll+v_overhead)/(v_margin/100),0);
    v_required_revenue_recovery:=round(v_recovery_gap/(v_margin/100),0);
    v_required_bookings:=case when v_avg_profit>0 then ceil(v_month_gap/v_avg_profit)::integer else null end;
    select coalesce(sum(estimated_value*probability_pct/100),0) into v_weighted from public.crm_lead_controls where stage not in ('WON','LOST');
    begin v_forecast:=private.planning_profit_forecast(current_date); v_forecast_profit:=coalesce((v_forecast->'days_30'->>'projected_profit')::numeric,0); exception when others then v_forecast_profit:=0; end;
    v_forecast_gap:=greatest(v_month_target-(v_month_actual+v_forecast_profit),0);
    select count(*)::integer into v_sales_count from public.profiles where is_active=true and role::text='Sales';
    v_missing_roles:='{}'::text[];
    foreach r in array array['Sales','Admin','Finance','Operation'] loop if private.gmu_v212_role_count(r)=0 then v_missing_roles:=array_append(v_missing_roles,r); end if; end loop;
    v_workforce:=case when cardinality(v_missing_roles)>0 then 'GAP' else 'READY' end;
    v_headroom:=greatest(round((v_weighted*(v_margin/100))-v_month_target-v_overhead-v_fixed_payroll,0),0);

    insert into public.planning_targets(period_month,scope_type,target_revenue,target_bookings,target_pax,target_profit,target_margin_pct,target_cash_in,notes,created_by,updated_by)
    values(v_month,'COMPANY',v_target_revenue,coalesce(v_required_bookings,0),0,v_month_target,v_margin,v_target_revenue,'[v21.2 AUTO] Target Cascade dari keputusan Direktur',t.set_by,t.set_by)
    on conflict(period_month) where scope_type='COMPANY' do update set target_revenue=excluded.target_revenue,target_bookings=excluded.target_bookings,target_profit=excluded.target_profit,target_margin_pct=excluded.target_margin_pct,target_cash_in=excluded.target_cash_in,notes=excluded.notes,updated_by=excluded.updated_by,updated_at=now();

    delete from public.planning_targets pt where pt.period_month=v_month and pt.scope_type='SALES' and coalesce(pt.notes,'') like '[v21.2 AUTO]%' and not exists(select 1 from public.profiles p where p.id=pt.sales_id and p.is_active=true and p.role::text='Sales');

    if v_sales_count>0 then
      v_per_sales_revenue:=round(v_target_revenue/v_sales_count,0); v_per_sales_profit:=round(v_month_target/v_sales_count,0);
      v_per_sales_bookings:=case when v_required_bookings is null then 0 else ceil(v_required_bookings::numeric/v_sales_count)::integer end;
      for s in select id,full_name from public.profiles where is_active=true and role::text='Sales' order by created_at,id loop
        insert into public.planning_targets(period_month,scope_type,sales_id,target_revenue,target_bookings,target_pax,target_profit,target_margin_pct,target_cash_in,notes,created_by,updated_by)
        values(v_month,'SALES',s.id,v_per_sales_revenue,v_per_sales_bookings,0,v_per_sales_profit,v_margin,v_per_sales_revenue,'[v21.2 AUTO] Target individu hasil cascade perusahaan',t.set_by,t.set_by)
        on conflict(period_month,sales_id) where scope_type='SALES' do update set target_revenue=excluded.target_revenue,target_bookings=excluded.target_bookings,target_profit=excluded.target_profit,target_margin_pct=excluded.target_margin_pct,target_cash_in=excluded.target_cash_in,notes=excluded.notes,updated_by=excluded.updated_by,updated_at=now();
        perform private.gmu_v212_upsert_assignment('v212:sales:'||to_char(v_month,'YYYY-MM')||':'||s.id::text,t.id,v_month,'Sales','Kejar pipeline dan booking yang menghasilkan laba, bukan omzet kosong',v_per_sales_revenue,'Rp omzet target','MONTHLY',jsonb_build_object('sales_name',s.full_name,'target_profit',v_per_sales_profit,'target_bookings',v_per_sales_bookings,'margin_floor_pct',v_margin,'prospect_floor_month',ceil(200.0/v_sales_count)));
        perform private.gmu_v212_sync_task('v212:sales-recovery:'||to_char(v_month,'YYYY-MM')||':'||s.id::text,v_forecast_gap>0,'Sales','Kejar gap target laba bulan ini',format('Target omzet individu Rp%s; target laba Rp%s; margin minimum %s%%. Fokus prospek, follow-up, quotation dan closing. Forecast gap perusahaan saat ini Rp%s.',v_per_sales_revenue,v_per_sales_profit,v_margin,v_forecast_gap),'HIGH',24);
      end loop;
    else
      perform private.gmu_v212_upsert_assignment('v212:sales-fallback:'||to_char(v_month,'YYYY-MM'),t.id,v_month,'Sales','Jalankan fungsi Sales sementara dan aktifkan SDM Sales',v_target_revenue,'Rp omzet target','MONTHLY',jsonb_build_object('fallback_to_manager',true,'prospect_floor_month',200,'qualified_floor_month',20,'quotation_floor_month',12));
      perform private.gmu_v212_sync_task('v212:activate-sales:'||to_char(v_month,'YYYY-MM'),true,'Manager','Aktifkan fungsi Sales untuk target perusahaan',format('Belum ada Sales aktif. Manager memegang fungsi Sales sementara. Target omzet perusahaan Rp%s dengan laba minimum Rp%s dan margin %s%%.',v_target_revenue,v_month_target,v_margin),'CRITICAL',4);
    end if;

    perform private.gmu_v212_upsert_assignment('v212:manager:'||to_char(v_month,'YYYY-MM'),t.id,v_month,'Manager','Pastikan target laba tercapai melalui Sales, margin, operasi dan closing',v_month_target,'Rp laba bersih','MONTHLY',jsonb_build_object('forecast_gap',v_forecast_gap,'recovery_gap',v_recovery_gap,'required_revenue_remaining',v_required_revenue_month,'weighted_pipeline',v_weighted,'data_confidence',v_confidence));
    perform private.gmu_v212_sync_task('v212:manager-recovery:'||to_char(v_month,'YYYY-MM'),v_forecast_gap>0,'Manager','Recovery Plan Target Laba',format('Forecast masih kurang Rp%s dari target laba Rp%s. Atur Sales → pricing/margin → kesiapan operasi → collection → closing. Sisa recovery kumulatif Rp%s.',v_forecast_gap,v_month_target,v_recovery_gap),'CRITICAL',4);
    perform private.gmu_v212_upsert_assignment('v212:admin:'||to_char(v_month,'YYYY-MM'),t.id,v_month,'Admin','Jaga seluruh lead/booking siap diproses tanpa administrasi tertahan',100,'persen kelengkapan','ONGOING',jsonb_build_object('sla','lead/booking/quotation/documents tidak boleh tertahan','revenue_context',v_target_revenue));
    perform private.gmu_v212_sync_task('v212:admin-flow:'||to_char(v_month,'YYYY-MM'),v_forecast_gap>0,'Admin','Jaga SLA administrasi untuk target laba','Pastikan lead, data customer, quotation, booking, manifest dan dokumen tidak menjadi bottleneck. Bukti penyelesaian wajib pada task terkait.','HIGH',24);
    perform private.gmu_v212_upsert_assignment('v212:operation:'||to_char(v_month,'YYYY-MM'),t.id,v_month,'Operation','Pastikan booking yang didapat Sales dapat dieksekusi dengan margin sehat',100,'persen trip ready','ONGOING',jsonb_build_object('margin_floor_pct',v_margin,'readiness_required',100,'h7_h3_h1',true));
    perform private.gmu_v212_sync_task('v212:operation-capacity:'||to_char(v_month,'YYYY-MM'),v_forecast_gap>0,'Operation','Siapkan kapasitas operasi untuk target laba',format('Jaga H-7/H-3/H-1, crew/vendor, rundown, operation sheet dan readiness. Jangan menerima struktur biaya yang menjatuhkan margin di bawah %s%% tanpa eskalasi.',v_margin),'HIGH',24);
    perform private.gmu_v212_upsert_assignment('v212:finance:'||to_char(v_month,'YYYY-MM'),t.id,v_month,'Finance','Pastikan laba benar-benar menjadi cash dan tercatat melalui closing',100,'persen closing H+3','ONGOING',jsonb_build_object('target_profit',v_month_target,'collection_required',true,'closing_h3',true));
    perform private.gmu_v212_sync_task('v212:finance-control:'||to_char(v_month,'YYYY-MM'),v_forecast_gap>0,'Finance','Kontrol cash, biaya dan closing untuk target laba','Verifikasi pembayaran, kendalikan AR/AP, finalisasi actual cost, payroll/fee dan Trip Closing H+3. Laba tidak dianggap tercapai sebelum data keuangan tervalidasi.','CRITICAL',24);
    perform private.gmu_v212_upsert_assignment('v212:tl:'||to_char(v_month,'YYYY-MM'),t.id,v_month,'TL','Eksekusi trip sesuai operation sheet dan bukti lapangan',100,'persen tugas trip','PER_TRIP',jsonb_build_object('financial_visibility',false,'evidence_required',true));

    v_snapshot_detail:=jsonb_build_object('target_mode',t.target_mode,'target_amount',t.target_amount,'monthly_profit_floor',t.monthly_profit_floor,'missing_roles',to_jsonb(v_missing_roles),'target_contribution_month',v_target_contribution,'compensation_guardrail',jsonb_build_object('current_fixed_payroll',v_fixed_payroll,'planned_overhead',v_overhead,'max_additional_fixed_payroll_from_current_weighted_pipeline',v_headroom,'recommendation',case when v_headroom<=0 then 'KEEP_FIXED_PAYROLL_LEAN_USE_VARIABLE_FEE' else 'REVIEW_WITH_OWNER_BEFORE_ADDING_FIXED_PAYROLL' end),'booking_calculation',jsonb_build_object('required_bookings',v_required_bookings,'basis',case when v_avg_profit>0 then '90D_REAL_CLOSING_AVERAGE' else 'INSUFFICIENT_CLOSING_HISTORY' end),'director_action',case when v_forecast_gap>0 then 'NO_DAILY_ACTION_MANAGER_OWNS_RECOVERY' else 'NO_ACTION' end);

    insert into public.target_cascade_snapshots(target_id,period_month,actual_profit,target_profit_month,recovery_actual,recovery_gap,target_margin_pct,required_revenue_month,target_revenue_month,required_revenue_recovery,avg_profit_per_trip,required_bookings_month,weighted_pipeline,forecast_profit_30d,forecast_gap,active_sales_count,fixed_payroll,planned_overhead,additional_fixed_payroll_headroom,data_confidence,workforce_status,detail)
    values(t.id,v_month,v_month_actual,v_month_target,v_recovery_actual,v_recovery_gap,v_margin,v_required_revenue_month,v_target_revenue,v_required_revenue_recovery,nullif(v_avg_profit,0),v_required_bookings,v_weighted,v_forecast_profit,v_forecast_gap,v_sales_count,v_fixed_payroll,v_overhead,v_headroom,v_confidence,v_workforce,v_snapshot_detail);

    if t.target_mode='RECOVERY_CUMULATIVE' and v_recovery_gap<=0 then update public.executive_profit_targets set status='ACHIEVED',updated_at=now() where id=t.id;
    elsif t.target_mode='MONTHLY_NET_PROFIT' and v_month_actual>=t.target_amount then update public.executive_profit_targets set status='ACHIEVED',updated_at=now() where id=t.id; end if;
  end loop;
  delete from public.target_cascade_snapshots where created_at<now()-interval '180 days';
end;$$;
revoke all on function private.gmu_v212_refresh_target_cascade() from public,anon,authenticated;

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
  return jsonb_build_object('ok',true,'target_id',v_id,'target_mode',v_mode,'target_amount',p_target_amount);
end;$$;
grant execute on function public.internal_set_executive_profit_target(numeric,text,numeric,numeric,date,text) to authenticated;

create or replace function public.internal_target_cascade_status()
returns jsonb language plpgsql stable security invoker set search_path=public,private,pg_temp as $$
declare v jsonb;
begin
  if not private.gmu_v212_is_management() then raise exception 'forbidden'; end if;
  select jsonb_build_object('target',to_jsonb(t),'snapshot',to_jsonb(s),'assignments',coalesce((select jsonb_agg(to_jsonb(a) order by a.assigned_role,a.assignment_key) from public.target_cascade_assignments a where a.target_id=t.id and a.status='ACTIVE'),'[]'::jsonb)) into v
  from public.executive_profit_targets t
  left join lateral (select x.* from public.target_cascade_snapshots x where x.target_id=t.id order by x.created_at desc limit 1) s on true
  where t.status in ('ACTIVE','ACHIEVED') order by case when t.status='ACTIVE' then 0 else 1 end,t.created_at desc limit 1;
  return coalesce(v,'{}'::jsonb);
end;$$;
grant execute on function public.internal_target_cascade_status() to authenticated;

-- Owner directive active at v21.2 rollout.
insert into public.executive_profit_targets(target_key,target_mode,target_amount,monthly_profit_floor,target_margin_pct,recovery_start_date,status,notes)
select 'v212:recovery:50000000:20260915','RECOVERY_CUMULATIVE',50000000,15000000,25,date '2026-09-15','ACTIVE','Owner directive: kejar pemulihan laba bersih Rp50 juta sebelum growth.'
where not exists(select 1 from public.executive_profit_targets where target_mode='RECOVERY_CUMULATIVE' and status='ACTIVE');

select private.gmu_v212_refresh_target_cascade();

DO $$
DECLARE jid bigint;
BEGIN
  select jobid into jid from cron.job where jobname='gmu_v212_target_cascade';
  if jid is not null then perform cron.unschedule(jid); end if;
  perform cron.schedule('gmu_v212_target_cascade','*/10 * * * *',$cmd$select private.gmu_v212_refresh_target_cascade();$cmd$);
END$$;