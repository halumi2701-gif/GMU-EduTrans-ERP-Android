-- GMU EduTrans ERP — Recipient & Notification Policy v1
-- STATUS: STAGED SOURCE. Deploy only after Supabase connector/backend access is healthy.
-- Policy core table and helper functions may already exist in production; statements are idempotent where practical.

create table if not exists public.notification_recipient_policies (
  id uuid primary key default gen_random_uuid(),
  event_key text not null,
  label text not null,
  target_role text not null,
  recipient_scope text not null default 'ROLE' check (recipient_scope in ('ROLE','REQUESTER','ASSIGNED_USER')),
  min_severity text not null default 'INFO' check (min_severity in ('INFO','WARNING','CRITICAL')),
  is_active boolean not null default true,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(event_key,target_role,recipient_scope)
);

alter table public.notification_recipient_policies enable row level security;
revoke all on public.notification_recipient_policies from anon, authenticated;

insert into public.notification_recipient_policies(event_key,label,target_role,recipient_scope,min_severity,is_active,notes) values
('APPROVAL_ESCALATED','Approval / Director Handoff','Owner','ROLE','CRITICAL',true,'Owner menerima eskalasi kritis.'),
('APPROVAL_ESCALATED','Approval / Director Handoff','Director','ROLE','CRITICAL',true,'Direktur menerima eskalasi kritis.'),
('APPROVAL_RESOLVED','Hasil keputusan approval','ANY','REQUESTER','INFO',true,'Hasil kembali ke pengaju.'),
('OPERATION_ALERT','Alert operasional EduTrans','Manager EduTrans','ROLE','WARNING',true,'Manager menerima alert operasional.'),
('OPERATION_ALERT','Alert operasional EduTrans','Operation','ROLE','WARNING',true,'Operation menerima alert operasional.'),
('PAYMENT_RECORDED','Pembayaran tercatat','Finance','ROLE','INFO',true,'Finance menerima event pembayaran.'),
('TRIP_ASSIGNMENT','Penugasan trip','TL','ASSIGNED_USER','INFO',true,'Hanya TL yang ditugaskan menerima event ini.'),
('SYSTEM_CRITICAL','Critical system alert','Owner','ROLE','CRITICAL',true,'Owner menerima critical system alert.'),
('SYSTEM_CRITICAL','Critical system alert','Director','ROLE','CRITICAL',true,'Direktur menerima critical system alert.'),
('DELIVERY_FAILURE_ESCALATION','Kegagalan kanal notifikasi kritis','Owner','ROLE','CRITICAL',true,'Owner menerima kegagalan delivery kritis.'),
('DELIVERY_FAILURE_ESCALATION','Kegagalan kanal notifikasi kritis','Director','ROLE','CRITICAL',true,'Direktur menerima kegagalan delivery kritis.')
on conflict(event_key,target_role,recipient_scope) do nothing;

insert into public.internal_notification_rules(event_type,label,delivery_mode,email_enabled,push_enabled,escalate_on_failure,escalation_after_minutes,is_active)
values
('PAYMENT_RECORDED','Pembayaran tercatat','ERP_ONLY',false,false,false,15,true),
('TRIP_ASSIGNMENT','Penugasan trip','ERP_ONLY',false,false,false,15,true),
('OPERATION_ALERT','Alert operasional EduTrans','ERP_ONLY',false,false,false,15,true),
('SYSTEM_CRITICAL','Critical system alert','CRITICAL_ESCALATION',true,true,true,10,true)
on conflict(event_type) do nothing;

create or replace function private.notification_role_match(p_profile_role text,p_target_role text)
returns boolean language sql immutable set search_path='' as $$
  select case upper(coalesce(p_target_role,''))
    when 'ANY' then true
    when 'DIRECTOR' then p_profile_role in ('Director','Direktur')
    when 'MANAGER EDUTRANS' then p_profile_role in ('Manager EduTrans','Manager')
    else lower(coalesce(p_profile_role,''))=lower(coalesce(p_target_role,''))
  end;
$$;

create or replace function private.notification_severity_rank(p text)
returns integer language sql immutable set search_path='' as $$
  select case upper(coalesce(p,'')) when 'CRITICAL' then 3 when 'WARNING' then 2 else 1 end;
$$;

-- Finance routing: payment event -> existing ERP staff inbox.
create or replace function private.emit_payment_recipient_notification()
returns trigger language plpgsql security definer
set search_path='public','gmu_erp','private','pg_temp'
as $$
declare pol record;
begin
  for pol in
    select * from public.notification_recipient_policies
    where event_key='PAYMENT_RECORDED' and is_active=true and recipient_scope='ROLE'
      and private.notification_severity_rank('INFO') >= private.notification_severity_rank(min_severity)
  loop
    if not exists(
      select 1 from gmu_erp.user_notification
      where source_key='PAYMENT_RECORDED:'||new.id and target_role=pol.target_role
    ) then
      insert into gmu_erp.user_notification(target_role,title,message,severity,link,source_key,created_at)
      values(
        pol.target_role,
        'Pembayaran tercatat',
        'Pembayaran '||coalesce(new.payment_type::text,'')||' sebesar Rp'||to_char(coalesce(new.amount,0),'FM999G999G999G990')||' tercatat untuk booking '||coalesce(new.booking_id,'-')||'.',
        'INFO','DASHBOARD','PAYMENT_RECORDED:'||new.id,now()
      );
    end if;
  end loop;
  return new;
end;
$$;

drop trigger if exists trg_payment_recipient_notification on public.payments;
create trigger trg_payment_recipient_notification after insert on public.payments
for each row execute function private.emit_payment_recipient_notification();

-- Assigned-only TL routing. The assigned auth UUID is embedded in source_key.
-- Server Staff Inbox MUST validate this suffix against auth.uid(); target_role alone is not sufficient.
create or replace function private.emit_trip_assignment_recipient_notification()
returns trigger language plpgsql security definer
set search_path='public','gmu_erp','private','pg_temp'
as $$
declare pol record; v_key text;
begin
  if new.tl_id is not null and (tg_op='INSERT' or old.tl_id is distinct from new.tl_id) then
    v_key := 'TRIP_ASSIGNMENT:'||new.id||':'||new.tl_id::text;
    for pol in
      select * from public.notification_recipient_policies
      where event_key='TRIP_ASSIGNMENT' and is_active=true and recipient_scope='ASSIGNED_USER'
        and private.notification_severity_rank('INFO') >= private.notification_severity_rank(min_severity)
    loop
      if not exists(select 1 from gmu_erp.user_notification where source_key=v_key and target_role=pol.target_role) then
        insert into gmu_erp.user_notification(target_role,title,message,severity,link,source_key,created_at)
        values(
          pol.target_role,
          'Anda ditugaskan sebagai TL',
          'Penugasan trip untuk booking '||coalesce(new.booking_id,'-')||'. Buka Operations untuk melihat kesiapan trip.',
          'INFO','OPERATIONS',v_key,now()
        );
      end if;
    end loop;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_trip_assignment_recipient_notification on public.trips;
create trigger trg_trip_assignment_recipient_notification after insert or update of tl_id on public.trips
for each row execute function private.emit_trip_assignment_recipient_notification();

-- Required server API behavior for gmu-notification-delivery-admin:
-- recipient_policies: Owner/Director read matrix.
-- set_recipient_policy: Owner only mutation.
-- staff_inbox: authenticated staff read; filter target_role against profile.role.
--   For TRIP_ASSIGNMENT rows, parse source_key and require assigned UUID = auth user UUID.
-- mark_staff_notification_read / mark_all_staff_notifications_read: same filtered ownership rules.
-- Never expose gmu_erp.user_notification directly to the Android client.
