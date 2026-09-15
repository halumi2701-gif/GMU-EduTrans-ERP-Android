-- GMU EduTrans v21 Company Autopilot RPC hardening
create or replace function private.gmu_v21_autopilot_snapshot() returns jsonb language sql security definer set search_path=public,private,pg_temp as $$
 select jsonb_build_object('version','v21-company-autopilot','automation',jsonb_build_object(
  'active_followup_sequences',(select count(*) from public.sales_followup_sequences where state='ACTIVE'),
  'followup_due',(select count(*) from public.sales_followup_steps where status='PENDING' and due_at<=now()),
  'resource_requests_open',(select count(*) from public.autopilot_resource_requests where status in ('NEEDED','REQUESTED','REPLACEMENT_NEEDED')),
  'retention_active',(select count(*) from public.customer_retention_cycles where status='ACTIVE'),
  'notifications_queued',(select count(*) from public.customer_notification_deliveries where status in ('PENDING','RETRY')),
  'payment_orders_pending',(select count(*) from public.payment_gateway_orders where status not in ('PAID','FAILED','EXPIRED','CANCELLED'))),
 'manager_score',jsonb_build_object('open_recovery',(select count(*) from public.automation_tasks where task_type='RECOVERY_ACTION' and status not in ('DONE','CANCELLED')),'overdue_tasks',(select count(*) from public.automation_tasks where status='OVERDUE'),'open_exceptions',(select count(*) from public.enterprise_exception_queue where status in ('OPEN','IN_PROGRESS','WAITING'))),
 'director',jsonb_build_object('critical_exceptions',(select count(*) from public.enterprise_exception_queue where status in ('OPEN','IN_PROGRESS','WAITING') and severity='CRITICAL' and assigned_role in ('Owner','Director','Direktur')),'action_required',exists(select 1 from public.enterprise_exception_queue where status in ('OPEN','IN_PROGRESS','WAITING') and severity='CRITICAL' and assigned_role in ('Owner','Director','Direktur'))));
$$;
revoke all on function private.gmu_v21_autopilot_snapshot() from public,anon;
grant execute on function private.gmu_v21_autopilot_snapshot() to authenticated;

create or replace function public.internal_company_autopilot_v21() returns jsonb language plpgsql security invoker set search_path=public,private,pg_temp as $$
begin
 if not exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans')) then
  raise exception 'Akses Company Autopilot hanya untuk Owner/Director/Manager';
 end if;
 return private.gmu_v21_autopilot_snapshot();
end;$$;
revoke all on function public.internal_company_autopilot_v21() from public,anon;
grant execute on function public.internal_company_autopilot_v21() to authenticated;