create table if not exists public.staff_probation_checkpoints (
  id uuid primary key default gen_random_uuid(),
  staff_id uuid not null references public.profiles(id) on delete cascade,
  contract_id uuid references public.staff_contracts(id) on delete set null,
  checkpoint_day smallint not null check (checkpoint_day in (30,60,90)),
  due_date date not null,
  status text not null default 'PENDING' check (status in ('PENDING','DUE','COMPLETED','WAIVED')),
  score numeric(5,2),
  decision text,
  notes text,
  reviewed_by uuid references public.profiles(id) on delete set null,
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(staff_id, contract_id, checkpoint_day)
);

create table if not exists public.staff_leave_balances (
  id uuid primary key default gen_random_uuid(),
  staff_id uuid not null references public.profiles(id) on delete cascade,
  year integer not null check (year between 2020 and 2100),
  leave_type text not null,
  opening_balance numeric(8,2) not null default 0,
  accrued numeric(8,2) not null default 0,
  used numeric(8,2) not null default 0,
  adjustment numeric(8,2) not null default 0,
  notes text,
  updated_by uuid references public.profiles(id) on delete set null,
  updated_at timestamptz not null default now(),
  unique(staff_id, year, leave_type)
);

create table if not exists public.staff_compensation_profiles (
  staff_id uuid primary key references public.profiles(id) on delete cascade,
  compensation_type text not null default 'VARIABLE' check (compensation_type in ('SALARY','RETAINER','VARIABLE','MIXED')),
  base_monthly numeric(16,2) not null default 0 check (base_monthly >= 0),
  transport_per_attendance_day numeric(16,2) not null default 0 check (transport_per_attendance_day >= 0),
  effective_from date not null default current_date,
  effective_to date,
  status text not null default 'ACTIVE' check (status in ('ACTIVE','INACTIVE')),
  notes text,
  approved_by uuid references public.profiles(id) on delete set null,
  approved_at timestamptz,
  updated_at timestamptz not null default now()
);

create table if not exists public.payroll_periods (
  id uuid primary key default gen_random_uuid(),
  period_start date not null,
  period_end date not null,
  status text not null default 'OPEN' check (status in ('OPEN','CALCULATED','APPROVAL','LOCKED','PAID','REOPENED')),
  calculated_at timestamptz,
  approved_by uuid references public.profiles(id) on delete set null,
  approved_at timestamptz,
  locked_by uuid references public.profiles(id) on delete set null,
  locked_at timestamptz,
  paid_at timestamptz,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(period_start, period_end),
  check (period_end >= period_start)
);

create table if not exists public.staff_asset_access_handover (
  id uuid primary key default gen_random_uuid(),
  staff_id uuid not null references public.profiles(id) on delete cascade,
  item_type text not null,
  item_name text not null,
  reference text,
  lifecycle_stage text not null default 'ONBOARDING' check (lifecycle_stage in ('ONBOARDING','ACTIVE','OFFBOARDING')),
  status text not null default 'PENDING' check (status in ('PENDING','ASSIGNED','RETURNED','REVOKED','CLOSED')),
  assigned_at timestamptz,
  completed_at timestamptz,
  verified_by uuid references public.profiles(id) on delete set null,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.enterprise_automation_policies (
  policy_code text primary key,
  domain text not null,
  trigger_event text not null,
  action_type text not null,
  assigned_role text,
  sla_hours integer not null default 24 check (sla_hours >= 0),
  requires_approval boolean not null default false,
  active boolean not null default true,
  severity text not null default 'NORMAL' check (severity in ('LOW','NORMAL','HIGH','CRITICAL')),
  description text,
  updated_by uuid references public.profiles(id) on delete set null,
  updated_at timestamptz not null default now()
);

create table if not exists public.enterprise_exception_queue (
  id uuid primary key default gen_random_uuid(),
  exception_key text not null unique,
  domain text not null,
  exception_type text not null,
  severity text not null default 'NORMAL' check (severity in ('LOW','NORMAL','HIGH','CRITICAL')),
  title text not null,
  description text,
  entity_type text,
  entity_id text,
  assigned_role text,
  assigned_to uuid references public.profiles(id) on delete set null,
  due_at timestamptz,
  status text not null default 'OPEN' check (status in ('OPEN','IN_PROGRESS','WAITING','RESOLVED','DISMISSED')),
  resolution text,
  resolved_by uuid references public.profiles(id) on delete set null,
  resolved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists staff_probation_due_idx on public.staff_probation_checkpoints(status,due_date);
create index if not exists staff_leave_bal_staff_idx on public.staff_leave_balances(staff_id,year);
create index if not exists staff_asset_access_staff_idx on public.staff_asset_access_handover(staff_id,status);
create index if not exists enterprise_exception_status_idx on public.enterprise_exception_queue(status,severity,due_at);
create index if not exists enterprise_exception_assignee_idx on public.enterprise_exception_queue(assigned_to,status);

alter table public.staff_probation_checkpoints enable row level security;
alter table public.staff_leave_balances enable row level security;
alter table public.staff_compensation_profiles enable row level security;
alter table public.payroll_periods enable row level security;
alter table public.staff_asset_access_handover enable row level security;
alter table public.enterprise_automation_policies enable row level security;
alter table public.enterprise_exception_queue enable row level security;

do $$
declare t text;
begin
  foreach t in array array['staff_probation_checkpoints','staff_leave_balances','staff_asset_access_handover'] loop
    execute format('drop policy if exists %I_read on public.%I',t,t);
    execute format($p$create policy %I_read on public.%I for select to authenticated using (
      staff_id=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Admin'))
    )$p$,t,t);
  end loop;
end $$;

drop policy if exists staff_compensation_profiles_read on public.staff_compensation_profiles;
create policy staff_compensation_profiles_read on public.staff_compensation_profiles for select to authenticated using (
  staff_id=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
);

drop policy if exists payroll_periods_read on public.payroll_periods;
create policy payroll_periods_read on public.payroll_periods for select to authenticated using (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
);

drop policy if exists enterprise_automation_policies_read on public.enterprise_automation_policies;
create policy enterprise_automation_policies_read on public.enterprise_automation_policies for select to authenticated using (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
);

drop policy if exists enterprise_exception_queue_read on public.enterprise_exception_queue;
create policy enterprise_exception_queue_read on public.enterprise_exception_queue for select to authenticated using (
  assigned_to=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance','Admin'))
);

create or replace function private.gmu_v200_is_management()
returns boolean language sql stable security definer set search_path=public,pg_temp as $$
  select exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'));
$$;
revoke all on function private.gmu_v200_is_management() from public,anon;
grant execute on function private.gmu_v200_is_management() to authenticated;

do $$
declare t text;
begin
  foreach t in array array['staff_probation_checkpoints','staff_leave_balances','staff_asset_access_handover','staff_compensation_profiles','payroll_periods','enterprise_automation_policies','enterprise_exception_queue'] loop
    execute format('drop policy if exists %I_write on public.%I',t,t);
    execute format('create policy %I_write on public.%I for all to authenticated using (private.gmu_v200_is_management()) with check (private.gmu_v200_is_management())',t,t);
  end loop;
end $$;

grant select,insert,update on public.staff_probation_checkpoints,public.staff_leave_balances,public.staff_asset_access_handover,public.staff_compensation_profiles,public.payroll_periods,public.enterprise_automation_policies,public.enterprise_exception_queue to authenticated;

create or replace function private.gmu_v200_seed_probation()
returns trigger language plpgsql security definer set search_path=public,private,pg_temp as $$
begin
  insert into public.staff_probation_checkpoints(staff_id,contract_id,checkpoint_day,due_date)
  values
    (new.staff_id,new.id,30,new.start_date+30),
    (new.staff_id,new.id,60,new.start_date+60),
    (new.staff_id,new.id,90,new.start_date+90)
  on conflict (staff_id,contract_id,checkpoint_day) do nothing;
  return new;
end $$;
revoke all on function private.gmu_v200_seed_probation() from public,anon,authenticated;
drop trigger if exists trg_v200_seed_probation on public.staff_contracts;
create trigger trg_v200_seed_probation after insert on public.staff_contracts for each row execute function private.gmu_v200_seed_probation();

insert into public.enterprise_automation_policies(policy_code,domain,trigger_event,action_type,assigned_role,sla_hours,requires_approval,severity,description)
values
('LEAD_UNASSIGNED','SALES','LEAD_CREATED','CREATE_TASK','Manager',4,false,'HIGH','Lead baru wajib punya PIC Sales maksimal 4 jam.'),
('FOLLOWUP_OVERDUE','SALES','FOLLOWUP_DUE','CREATE_TASK','Sales',0,false,'HIGH','Follow-up lewat jatuh tempo menjadi tugas overdue.'),
('BOOKING_CONFIRMED','OPERATIONS','BOOKING_CONFIRMED','CREATE_TASK','Operation',24,false,'NORMAL','Booking terkonfirmasi memicu persiapan operasional.'),
('DP_RECEIVED','FINANCE','DP_RECEIVED','CREATE_TASK','Finance',4,false,'NORMAL','DP masuk memicu verifikasi dan handoff booking.'),
('TRIP_H7','OPERATIONS','TRIP_H7','CREATE_TASK','Operation',24,false,'HIGH','H-7 readiness, vendor, crew dan dokumen.'),
('TRIP_H1','OPERATIONS','TRIP_H1','CREATE_TASK','Manager',4,false,'CRITICAL','H-1 final readiness dan exception check.'),
('TRIP_CLOSED','FINANCE','TRIP_CLOSED','CREATE_TASK','Finance',24,false,'HIGH','Trip selesai memicu closing biaya, payroll dan profit.'),
('COMPLAINT_HIGH','QUALITY','COMPLAINT_HIGH','CREATE_CAPA','Manager',4,true,'CRITICAL','Keluhan berat/kritis wajib CAPA dan recovery.'),
('PAYROLL_READY','HR','PAYROLL_READY','CREATE_APPROVAL','Finance',24,true,'HIGH','Payroll siap harus diverifikasi dan disetujui.'),
('RISK_CRITICAL','GOVERNANCE','RISK_CRITICAL','ESCALATE','Director',1,true,'CRITICAL','Risiko kritis langsung eskalasi Direktur.')
on conflict (policy_code) do update set domain=excluded.domain,trigger_event=excluded.trigger_event,action_type=excluded.action_type,assigned_role=excluded.assigned_role,sla_hours=excluded.sla_hours,requires_approval=excluded.requires_approval,severity=excluded.severity,description=excluded.description,updated_at=now();

create or replace function public.internal_enterprise_control_tower()
returns jsonb language plpgsql stable security invoker set search_path=public,pg_temp as $$
declare v jsonb;
begin
  if not private.gmu_v200_is_management() then raise exception 'forbidden'; end if;
  select jsonb_build_object(
    'generated_at',now(),
    'sales',jsonb_build_object(
      'open_leads',(select count(*) from public.crm_lead_controls where stage not in ('WON','LOST')),
      'unassigned_leads',(select count(*) from public.crm_lead_controls where owner_id is null and stage not in ('WON','LOST')),
      'overdue_followups',(select count(*) from public.crm_lead_controls where stage not in ('WON','LOST') and next_follow_up_at<now()),
      'weighted_pipeline',(select coalesce(sum(estimated_value*probability_pct/100),0) from public.crm_lead_controls where stage not in ('WON','LOST'))
    ),
    'operations',jsonb_build_object(
      'open_tasks',(select count(*) from public.automation_tasks where status in ('OPEN','IN_PROGRESS','WAITING_APPROVAL','OVERDUE')),
      'overdue_tasks',(select count(*) from public.automation_tasks where status='OVERDUE' or (due_at<now() and status not in ('DONE','CANCELLED'))),
      'unresolved_incidents',(select count(*) from public.trip_incidents where resolved=false),
      'open_vendor_bills',(select count(*) from public.vendor_bills where upper(coalesce(status,'')) not in ('PAID','CANCELLED','CANCELED'))
    ),
    'people',jsonb_build_object(
      'active_staff',(select count(*) from public.profiles where is_active=true),
      'contracts_expiring_30d',(select count(*) from public.staff_contracts where end_date between current_date and current_date+30),
      'probation_due',(select count(*) from public.staff_probation_checkpoints where status in ('PENDING','DUE') and due_date<=current_date+7),
      'pending_leave',(select count(*) from public.staff_leave where upper(coalesce(status,''))='PENDING'),
      'open_warnings',(select count(*) from public.staff_warnings where upper(coalesce(status,'')) not in ('CLOSED','RESOLVED')),
      'training_expiring_30d',(select count(*) from public.staff_training where expiry_date between current_date and current_date+30)
    ),
    'quality',jsonb_build_object(
      'open_tickets',(select count(*) from public.customer_support_tickets where upper(coalesce(status,'')) not in ('CLOSED','RESOLVED')),
      'open_capa',(select count(*) from public.capa_cases where upper(coalesce(status,'')) not in ('CLOSED','RESOLVED')),
      'critical_risks',(select count(*) from public.risk_register where status<>'CLOSED' and likelihood*impact>=16)
    ),
    'finance',jsonb_build_object(
      'open_payroll_entries',(select count(*) from public.payroll_entries where state not in ('PAID','CANCELLED')),
      'open_payroll_periods',(select count(*) from public.payroll_periods where status not in ('PAID','LOCKED')),
      'unclosed_finance_periods',(select count(*) from public.finance_periods where upper(coalesce(status,'')) not in ('CLOSED','LOCKED'))
    ),
    'governance',jsonb_build_object(
      'open_exceptions',(select count(*) from public.enterprise_exception_queue where status in ('OPEN','IN_PROGRESS','WAITING')),
      'critical_exceptions',(select count(*) from public.enterprise_exception_queue where status in ('OPEN','IN_PROGRESS','WAITING') and severity='CRITICAL'),
      'pending_approvals',(select count(*) from public.approvals where upper(coalesce(status,''))='PENDING')
    )
  ) into v;
  return v;
end $$;
grant execute on function public.internal_enterprise_control_tower() to authenticated;

create or replace function private.gmu_v200_refresh_exceptions()
returns void language plpgsql security definer set search_path=public,private,pg_temp as $$
begin
  update public.staff_probation_checkpoints set status='DUE',updated_at=now() where status='PENDING' and due_date<=current_date;

  insert into public.enterprise_exception_queue(exception_key,domain,exception_type,severity,title,description,entity_type,entity_id,assigned_role,due_at)
  select 'lead-unassigned:'||c.booking_request_id::text,'SALES','LEAD_UNASSIGNED','HIGH','Lead belum memiliki PIC Sales',coalesce(br.institution_name,br.pic_name,br.booking_code,'Lead')||' belum ditugaskan.','booking_request',c.booking_request_id::text,'Manager',coalesce(br.created_at,now())+interval '4 hours'
  from public.crm_lead_controls c join public.booking_requests br on br.id=c.booking_request_id
  where c.owner_id is null and c.stage not in ('WON','LOST')
  on conflict (exception_key) do update set status=case when enterprise_exception_queue.status='RESOLVED' then 'OPEN' else enterprise_exception_queue.status end,updated_at=now();

  insert into public.enterprise_exception_queue(exception_key,domain,exception_type,severity,title,description,entity_type,entity_id,assigned_role,due_at)
  select 'task-overdue:'||t.id::text,'OPERATIONS','TASK_OVERDUE',case when t.priority='CRITICAL' then 'CRITICAL' else 'HIGH' end,'Tugas melewati SLA',t.title,'automation_task',t.id::text,coalesce(t.assigned_role,'Manager'),t.due_at
  from public.automation_tasks t where t.due_at<now() and t.status not in ('DONE','CANCELLED')
  on conflict (exception_key) do update set severity=excluded.severity,due_at=excluded.due_at,updated_at=now();

  update public.enterprise_exception_queue e set status='RESOLVED',resolved_at=coalesce(resolved_at,now()),resolution=coalesce(resolution,'Resolved automatically by source state'),updated_at=now()
  where e.exception_type='TASK_OVERDUE' and not exists(select 1 from public.automation_tasks t where t.id::text=e.entity_id and t.due_at<now() and t.status not in ('DONE','CANCELLED'));
end $$;
revoke all on function private.gmu_v200_refresh_exceptions() from public,anon,authenticated;

do $$ begin
  if exists(select 1 from pg_namespace where nspname='cron') and not exists(select 1 from cron.job where jobname='gmu_v200_enterprise_exception_refresh') then
    perform cron.schedule('gmu_v200_enterprise_exception_refresh','*/10 * * * *','select private.gmu_v200_refresh_exceptions();');
  end if;
end $$;
