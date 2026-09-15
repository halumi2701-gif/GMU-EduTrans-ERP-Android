create extension if not exists pgcrypto;

create table if not exists public.crm_lead_controls (
  booking_request_id uuid primary key references public.booking_requests(id) on delete cascade,
  stage text not null default 'NEW' check (stage in ('NEW','CONTACTED','QUALIFIED','QUOTATION','NEGOTIATION','WAITING_DP','WON','LOST','NURTURE')),
  owner_id uuid references public.profiles(id) on delete set null,
  lead_source text,
  qualification_score smallint check (qualification_score between 0 and 100),
  probability_pct numeric(5,2) not null default 10 check (probability_pct between 0 and 100),
  estimated_value numeric(16,2) not null default 0 check (estimated_value >= 0),
  last_contact_at timestamptz,
  next_follow_up_at timestamptz,
  lost_reason text,
  lost_at timestamptz,
  won_at timestamptz,
  recovery_status text not null default 'NONE' check (recovery_status in ('NONE','NEEDED','ACTIVE','CLOSED')),
  recovery_plan text,
  recovery_due_at timestamptz,
  created_by uuid references public.profiles(id) on delete set null,
  updated_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.crm_activities add column if not exists booking_request_id uuid references public.booking_requests(id) on delete set null;

create index if not exists crm_lead_controls_owner_stage_idx on public.crm_lead_controls(owner_id, stage, next_follow_up_at);
create index if not exists crm_lead_controls_followup_idx on public.crm_lead_controls(next_follow_up_at) where next_follow_up_at is not null;
create index if not exists crm_lead_controls_recovery_idx on public.crm_lead_controls(recovery_status, recovery_due_at);
create index if not exists crm_activities_booking_request_idx on public.crm_activities(booking_request_id, created_at desc);

alter table public.crm_lead_controls enable row level security;

drop policy if exists crm_lead_controls_read on public.crm_lead_controls;
create policy crm_lead_controls_read on public.crm_lead_controls for select to authenticated using (
  owner_id=(select auth.uid()) or exists (
    select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Admin','Sales')
  )
);

drop policy if exists crm_lead_controls_insert on public.crm_lead_controls;
create policy crm_lead_controls_insert on public.crm_lead_controls for insert to authenticated with check (
  owner_id=(select auth.uid()) or exists (
    select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Admin','Sales')
  )
);

drop policy if exists crm_lead_controls_update on public.crm_lead_controls;
create policy crm_lead_controls_update on public.crm_lead_controls for update to authenticated using (
  owner_id=(select auth.uid()) or exists (
    select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Admin')
  )
) with check (
  owner_id=(select auth.uid()) or exists (
    select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Admin')
  )
);

grant select,insert,update on public.crm_lead_controls to authenticated;
grant select,insert,update on public.crm_activities to authenticated;

create or replace function private.gmu_v112_probability_for_stage(p_stage text)
returns numeric language sql immutable set search_path=pg_catalog as $$
  select case upper(coalesce(p_stage,''))
    when 'NEW' then 10 when 'CONTACTED' then 20 when 'QUALIFIED' then 40 when 'QUOTATION' then 55
    when 'NEGOTIATION' then 70 when 'WAITING_DP' then 85 when 'WON' then 100 when 'LOST' then 0 when 'NURTURE' then 15 else 10 end::numeric;
$$;
revoke all on function private.gmu_v112_probability_for_stage(text) from public, anon, authenticated;

create or replace function private.gmu_v112_sync_lead_control()
returns trigger language plpgsql security definer set search_path=public,private,pg_temp as $$
declare
  v_stage text := 'NEW';
  v_status text := upper(coalesce(new.status,''));
  v_estimated numeric := greatest(coalesce(new.budget_per_pax,0) * coalesce(new.pax,0),0);
begin
  if new.converted_booking_id is not null then v_stage := 'WON';
  elsif v_status like '%LOST%' or v_status like '%REJECT%' then v_stage := 'LOST';
  elsif v_status like '%WAIT%DP%' or v_status like '%PAYMENT%' then v_stage := 'WAITING_DP';
  elsif v_status like '%NEGOT%' then v_stage := 'NEGOTIATION';
  elsif v_status like '%QUOT%' then v_stage := 'QUOTATION';
  elsif v_status like '%QUAL%' then v_stage := 'QUALIFIED';
  elsif v_status like '%CONTACT%' then v_stage := 'CONTACTED';
  else v_stage := 'NEW'; end if;

  insert into public.crm_lead_controls(
    booking_request_id,stage,owner_id,lead_source,probability_pct,estimated_value,won_at,lost_at,created_by,updated_by
  ) values(
    new.id,v_stage,new.assigned_sales,new.source,private.gmu_v112_probability_for_stage(v_stage),v_estimated,
    case when v_stage='WON' then now() end,case when v_stage='LOST' then now() end,(select auth.uid()),(select auth.uid())
  )
  on conflict (booking_request_id) do update set
    owner_id=coalesce(excluded.owner_id,public.crm_lead_controls.owner_id),
    lead_source=coalesce(excluded.lead_source,public.crm_lead_controls.lead_source),
    estimated_value=excluded.estimated_value,
    stage=case when public.crm_lead_controls.stage in ('LOST','NURTURE') and excluded.stage not in ('WON','LOST') then public.crm_lead_controls.stage else excluded.stage end,
    probability_pct=case when public.crm_lead_controls.stage in ('LOST','NURTURE') and excluded.stage not in ('WON','LOST') then public.crm_lead_controls.probability_pct else excluded.probability_pct end,
    won_at=coalesce(excluded.won_at,public.crm_lead_controls.won_at),
    lost_at=coalesce(excluded.lost_at,public.crm_lead_controls.lost_at),
    updated_by=(select auth.uid()),updated_at=now();
  return new;
end;
$$;
revoke all on function private.gmu_v112_sync_lead_control() from public, anon, authenticated;

create or replace function private.gmu_v112_crm_activity_rollup()
returns trigger language plpgsql security definer set search_path=public,private,pg_temp as $$
declare
  v_request_id uuid := new.booking_request_id;
begin
  if v_request_id is null and new.booking_id is not null then
    select br.id into v_request_id from public.booking_requests br where br.converted_booking_id=new.booking_id order by br.updated_at desc limit 1;
  end if;
  if v_request_id is null and new.customer_id is not null then
    select br.id into v_request_id from public.booking_requests br where br.converted_customer_id=new.customer_id or br.erp_lead_customer_id=new.customer_id order by br.updated_at desc limit 1;
  end if;
  if v_request_id is null then return new; end if;

  insert into public.crm_lead_controls(booking_request_id,stage,owner_id,lead_source,last_contact_at,next_follow_up_at,lost_reason,lost_at,probability_pct,created_by,updated_by)
  select v_request_id,
         case when nullif(new.lost_reason,'') is not null then 'LOST' else 'CONTACTED' end,
         coalesce(new.sales_id,br.assigned_sales),coalesce(new.lead_source,br.source),new.created_at,new.next_follow_up_at,nullif(new.lost_reason,''),
         case when nullif(new.lost_reason,'') is not null then new.created_at end,
         case when nullif(new.lost_reason,'') is not null then 0 else 20 end,
         coalesce(new.created_by,new.sales_id),(select auth.uid())
  from public.booking_requests br where br.id=v_request_id
  on conflict (booking_request_id) do update set
    owner_id=coalesce(excluded.owner_id,public.crm_lead_controls.owner_id),
    lead_source=coalesce(excluded.lead_source,public.crm_lead_controls.lead_source),
    last_contact_at=greatest(coalesce(public.crm_lead_controls.last_contact_at,'epoch'::timestamptz),excluded.last_contact_at),
    next_follow_up_at=coalesce(excluded.next_follow_up_at,public.crm_lead_controls.next_follow_up_at),
    lost_reason=coalesce(excluded.lost_reason,public.crm_lead_controls.lost_reason),
    lost_at=coalesce(excluded.lost_at,public.crm_lead_controls.lost_at),
    stage=case when excluded.lost_reason is not null then 'LOST' when public.crm_lead_controls.stage='NEW' then 'CONTACTED' else public.crm_lead_controls.stage end,
    probability_pct=case when excluded.lost_reason is not null then 0 when public.crm_lead_controls.stage='NEW' then 20 else public.crm_lead_controls.probability_pct end,
    updated_by=(select auth.uid()),updated_at=now();
  return new;
end;
$$;
revoke all on function private.gmu_v112_crm_activity_rollup() from public, anon, authenticated;

create or replace function private.gmu_v112_crm_time_maintenance()
returns void language plpgsql security definer set search_path=public,private,pg_temp as $$
begin
  update public.automation_tasks
     set status='OVERDUE',updated_at=now()
   where due_at < now() and status in ('OPEN','IN_PROGRESS','WAITING_APPROVAL');

  update public.crm_lead_controls
     set recovery_status='NEEDED',recovery_due_at=coalesce(recovery_due_at,now()+interval '1 day'),updated_at=now()
   where stage not in ('WON','LOST') and next_follow_up_at < now()-interval '24 hours' and recovery_status='NONE';

  insert into public.automation_tasks(task_key,booking_id,assigned_to,assigned_role,task_type,title,description,due_at,priority,status,evidence_required,approval_required)
  select 'crm-followup:'||c.booking_request_id::text||':'||extract(epoch from c.next_follow_up_at)::bigint,
         br.converted_booking_id,c.owner_id,'Sales','SALES','Tindak lanjut CRM terlambat',
         'Follow-up lead '||coalesce(br.institution_name,br.pic_name,br.booking_code,'')||' terlambat. Catat hasil kontak dan next follow-up.',
         c.next_follow_up_at,case when c.next_follow_up_at < now()-interval '24 hours' then 'HIGH' else 'NORMAL' end,'OVERDUE',false,false
    from public.crm_lead_controls c join public.booking_requests br on br.id=c.booking_request_id
   where c.stage not in ('WON','LOST') and c.next_follow_up_at is not null and c.next_follow_up_at < now()
  on conflict (task_key) do nothing;
end;
$$;
revoke all on function private.gmu_v112_crm_time_maintenance() from public, anon, authenticated;

create or replace function private.gmu_v112_touch_crm_lead_control()
returns trigger language plpgsql set search_path=public,pg_temp as $$
begin new.updated_at=now(); return new; end; $$;
revoke all on function private.gmu_v112_touch_crm_lead_control() from public, anon, authenticated;

drop trigger if exists trg_v112_sync_lead_control on public.booking_requests;
create trigger trg_v112_sync_lead_control after insert or update of status,assigned_sales,source,converted_booking_id,budget_per_pax,pax on public.booking_requests for each row execute function private.gmu_v112_sync_lead_control();

drop trigger if exists trg_v112_crm_activity_rollup on public.crm_activities;
create trigger trg_v112_crm_activity_rollup after insert or update of booking_request_id,booking_id,customer_id,sales_id,next_follow_up_at,lost_reason,lead_source on public.crm_activities for each row execute function private.gmu_v112_crm_activity_rollup();

drop trigger if exists trg_v112_crm_lead_touch on public.crm_lead_controls;
create trigger trg_v112_crm_lead_touch before update on public.crm_lead_controls for each row execute function private.gmu_v112_touch_crm_lead_control();

insert into public.crm_lead_controls(booking_request_id,stage,owner_id,lead_source,probability_pct,estimated_value,won_at,created_at,updated_at)
select br.id,
       case when br.converted_booking_id is not null then 'WON' when upper(coalesce(br.status,'')) like '%QUOT%' then 'QUOTATION' else 'NEW' end,
       br.assigned_sales,br.source,
       case when br.converted_booking_id is not null then 100 when upper(coalesce(br.status,'')) like '%QUOT%' then 55 else 10 end,
       greatest(coalesce(br.budget_per_pax,0)*coalesce(br.pax,0),0),
       case when br.converted_booking_id is not null then coalesce(br.updated_at,now()) end,
       coalesce(br.created_at,now()),now()
from public.booking_requests br
on conflict (booking_request_id) do nothing;

do $$
begin
  if not exists (select 1 from cron.job where jobname='gmu_v112_crm_maintenance') then
    perform cron.schedule('gmu_v112_crm_maintenance','*/15 * * * *','select private.gmu_v112_crm_time_maintenance();');
  end if;
end $$;
