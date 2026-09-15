-- GMU EduTrans v21.1 Production Readiness
-- WhatsApp is intentionally deferred. Payment remains required.
-- Active automation tasks must always have a concrete assignee; Manager is the fallback.

create table if not exists public.erp_production_readiness_settings (
  singleton boolean primary key default true check (singleton),
  require_whatsapp boolean not null default false,
  require_payment boolean not null default true,
  updated_at timestamptz not null default now()
);

insert into public.erp_production_readiness_settings(singleton,require_whatsapp,require_payment)
values(true,false,true)
on conflict(singleton) do update
set require_whatsapp=false, updated_at=now();

alter table public.erp_production_readiness_settings enable row level security;
revoke all on public.erp_production_readiness_settings from public,anon,authenticated;

create or replace function private.gmu_v211_pick_active_user(p_role text)
returns uuid
language sql stable security definer
set search_path=public,pg_temp
as $$
  select p.id
  from public.profiles p
  where p.is_active=true
    and case
      when p_role in ('Owner','Director','Direktur') then p.role::text in ('Owner','Director','Direktur')
      when p_role in ('Manager','Manager EduTrans','Management') then p.role::text in ('Manager','Manager EduTrans')
      else p.role::text=p_role
    end
  order by p.created_at
  limit 1;
$$;
revoke all on function private.gmu_v211_pick_active_user(text) from public,anon,authenticated;

create or replace function private.gmu_v211_task_route()
returns trigger
language plpgsql security definer
set search_path=public,private,pg_temp
as $$
declare target uuid; fallback uuid;
begin
  if new.status in ('DONE','CANCELLED') or new.assigned_to is not null then return new; end if;
  target := private.gmu_v211_pick_active_user(coalesce(new.assigned_role,''));
  if target is not null then new.assigned_to := target; return new; end if;
  fallback := private.gmu_v211_pick_active_user('Manager');
  if fallback is not null then new.assigned_to := fallback; end if;
  return new;
end;
$$;
revoke all on function private.gmu_v211_task_route() from public,anon,authenticated;

drop trigger if exists trg_v211_task_route on public.automation_tasks;
create trigger trg_v211_task_route
before insert or update of assigned_role,assigned_to,status on public.automation_tasks
for each row execute function private.gmu_v211_task_route();

update public.automation_tasks t
set assigned_to=coalesce(private.gmu_v211_pick_active_user(t.assigned_role),private.gmu_v211_pick_active_user('Manager')),
    updated_at=now()
where t.status in ('OPEN','IN_PROGRESS','OVERDUE','WAITING_APPROVAL') and t.assigned_to is null;

-- Production readiness now treats WhatsApp as optional while keeping payment required.
create or replace function private.gmu_v211_refresh_readiness()
returns void
language plpgsql security definer
set search_path=public,private,cron,pg_temp
as $$
declare
  required_roles text[] := array['Owner','Manager','Admin','Sales','Finance','Operation','TL'];
  r text; cnt integer; roles jsonb := '{}'::jsonb; missing text[] := '{}'::text[];
  overdue_count integer:=0; unassigned_count integer:=0; customer_failed integer:=0; internal_failed integer:=0;
  payment_count integer:=0; email_ok boolean:=false; whatsapp_ok boolean:=false;
  require_whatsapp boolean:=false; require_payment boolean:=true; cron_bad integer:=0;
  internal_issue boolean:=false; external_issue boolean:=false; result_status text;
begin
  select s.require_whatsapp,s.require_payment into require_whatsapp,require_payment
  from public.erp_production_readiness_settings s where s.singleton=true;

  foreach r in array required_roles loop
    cnt:=private.gmu_v211_role_count(r); roles:=roles||jsonb_build_object(r,cnt);
    if cnt=0 then missing:=array_append(missing,r); end if;
  end loop;

  select count(*)::integer into overdue_count from public.automation_tasks where status='OVERDUE';
  select count(*)::integer into unassigned_count from public.automation_tasks
   where status in ('OPEN','IN_PROGRESS','OVERDUE','WAITING_APPROVAL') and assigned_to is null;
  select count(*)::integer into customer_failed from public.customer_notification_deliveries
   where upper(coalesce(status,'')) in ('FAILED','DEAD','DEAD_LETTER','ERROR');
  select count(*)::integer into internal_failed from public.internal_notification_deliveries
   where upper(coalesce(status,'')) in ('FAILED','DEAD','DEAD_LETTER','ERROR');
  select count(*)::integer into payment_count from public.payment_gateway_channels where is_enabled=true;
  select coalesce(bool_or(is_enabled),false) into email_ok from public.customer_notification_channels where upper(channel)='EMAIL';
  select coalesce(bool_or(is_enabled),false) into whatsapp_ok from public.customer_notification_channels where upper(channel)='WHATSAPP';

  with critical_jobs(jobname) as (values
    ('gmu_v21_company_autopilot_tick'),('gmu-notification-delivery-worker'),('gmu-internal-notification-worker'),
    ('gmu-critical-notification-escalation'),('gmu-payment-gateway-v25-maintenance'))
  select count(*)::integer into cron_bad from critical_jobs x
  where not exists (
    select 1 from cron.job j where j.jobname=x.jobname and j.active=true
      and exists(select 1 from cron.job_run_details d where d.jobid=j.jobid and d.status='succeeded' and d.end_time>=now()-interval '35 minutes'));

  internal_issue:=cardinality(missing)>0 or overdue_count>0 or unassigned_count>0 or customer_failed>0 or internal_failed>0 or cron_bad>0 or not email_ok;
  external_issue:=(require_payment and payment_count=0) or (require_whatsapp and not whatsapp_ok);
  result_status:=case when internal_issue then 'ACTION_REQUIRED' when external_issue then 'READY_WITH_EXTERNAL_BLOCKERS' else 'READY' end;

  insert into public.erp_production_readiness_snapshots(
    overall_status,active_roles,missing_roles,automation_overdue_count,automation_unassigned_count,
    customer_delivery_failed_count,internal_delivery_failed_count,payment_enabled_count,email_enabled,whatsapp_enabled,cron_unhealthy_count,blockers)
  values(result_status,roles,missing,overdue_count,unassigned_count,customer_failed,internal_failed,payment_count,email_ok,whatsapp_ok,cron_bad,
    jsonb_build_object('missing_roles',to_jsonb(missing),'overdue_tasks',overdue_count,'unassigned_tasks',unassigned_count,
      'customer_delivery_failures',customer_failed,'internal_delivery_failures',internal_failed,'cron_unhealthy',cron_bad,
      'email_disabled',not email_ok,'whatsapp_required',require_whatsapp,'whatsapp_disabled',not whatsapp_ok,
      'payment_required',require_payment,'payment_channels_disabled',payment_count=0));

  foreach r in array required_roles loop
    perform private.gmu_v211_sync_blocker_task('v211:role-readiness:'||lower(replace(r,' ','-')),private.gmu_v211_role_count(r)=0,
      case when r in ('Owner','Manager') then 'Owner' else 'Manager' end,'Aktifkan akun role '||r,
      'Role '||r||' belum memiliki akun aktif. Tambahkan SDM/akun agar workflow otomatis dapat diterima oleh penanggung jawab yang benar.',
      case when r in ('Owner','Manager','Sales','Finance','Operation') then 'CRITICAL' else 'HIGH' end);
  end loop;

  perform private.gmu_v211_sync_blocker_task('v211:integration:payment',require_payment and payment_count=0,'Manager',
    'Aktifkan kanal pembayaran produksi','Belum ada payment gateway channel yang aktif. Booking dapat berjalan, tetapi pembayaran otomatis belum production-ready.','CRITICAL');
  perform private.gmu_v211_sync_blocker_task('v211:integration:whatsapp',require_whatsapp and not whatsapp_ok,'Manager',
    'Sambungkan WhatsApp Business produksi','Kanal WhatsApp masih nonaktif.','HIGH');
  perform private.gmu_v211_sync_blocker_task('v211:integration:email',not email_ok,'Manager','Pulihkan kanal email produksi',
    'Kanal email nonaktif sehingga notifikasi customer tidak lengkap.','CRITICAL');
  perform private.gmu_v211_sync_blocker_task('v211:system:cron',cron_bad>0,'Manager','Pulihkan automation scheduler',
    'Satu atau lebih cron kritis tidak memiliki eksekusi sukses dalam 35 menit terakhir.','CRITICAL');
end;
$$;
revoke all on function private.gmu_v211_refresh_readiness() from public,anon,authenticated;

select private.gmu_v211_refresh_readiness();
