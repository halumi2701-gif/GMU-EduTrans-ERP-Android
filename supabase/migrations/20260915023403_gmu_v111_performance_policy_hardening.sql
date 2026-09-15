-- GMU EduTrans ERP v11.1 — Performance & RLS hardening
-- Applied to production project gtgnwasijweewmaubvyg as migration 20260915023403.

create index if not exists automation_events_actor_idx on public.automation_events(actor_id);
create index if not exists automation_tasks_source_event_idx on public.automation_tasks(source_event_id);
create index if not exists automation_tasks_approval_idx on public.automation_tasks(approval_id);
create index if not exists automation_tasks_completed_by_idx on public.automation_tasks(completed_by);
create index if not exists approval_details_created_by_idx on public.approval_details(created_by);
create index if not exists payroll_entries_approved_by_idx on public.payroll_entries(approved_by);
create index if not exists payroll_entries_created_by_idx on public.payroll_entries(created_by);
create index if not exists recruitment_cases_owner_idx on public.recruitment_cases(owner_id);
create index if not exists recruitment_cases_approved_by_idx on public.recruitment_cases(approved_by);
create index if not exists recruitment_cases_created_by_idx on public.recruitment_cases(created_by);
create index if not exists capa_cases_owner_idx on public.capa_cases(owner_id);
create index if not exists capa_cases_created_by_idx on public.capa_cases(created_by);
create index if not exists workforce_plans_owner_idx on public.workforce_plans(owner_id);
create index if not exists workforce_plans_created_by_idx on public.workforce_plans(created_by);
create index if not exists risk_register_owner_idx on public.risk_register(owner_id);
create index if not exists risk_register_created_by_idx on public.risk_register(created_by);
create index if not exists crm_activities_created_by_idx on public.crm_activities(created_by);
create index if not exists ai_action_drafts_approved_by_idx on public.ai_action_drafts(approved_by);

drop policy if exists approval_details_write on public.approval_details;
create policy approval_details_insert on public.approval_details for insert to authenticated with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans','Finance')));
create policy approval_details_update on public.approval_details for update to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans','Finance'))) with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans','Finance')));
create policy approval_details_delete on public.approval_details for delete to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans','Finance')));

drop policy if exists payroll_entries_write on public.payroll_entries;
create policy payroll_entries_insert on public.payroll_entries for insert to authenticated with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans','Finance')));
create policy payroll_entries_update on public.payroll_entries for update to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans','Finance'))) with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans','Finance')));
create policy payroll_entries_delete on public.payroll_entries for delete to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans','Finance')));

drop policy if exists capa_cases_write on public.capa_cases;
create policy capa_cases_insert on public.capa_cases for insert to authenticated with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans','Admin','Operation')));
create policy capa_cases_update on public.capa_cases for update to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans','Admin','Operation'))) with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans','Admin','Operation')));
create policy capa_cases_delete on public.capa_cases for delete to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans')));

drop policy if exists risk_register_write on public.risk_register;
create policy risk_register_insert on public.risk_register for insert to authenticated with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans')));
create policy risk_register_update on public.risk_register for update to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans'))) with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans')));
create policy risk_register_delete on public.risk_register for delete to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director')));

drop policy if exists ai_action_drafts_write on public.ai_action_drafts;
create policy ai_action_drafts_insert on public.ai_action_drafts for insert to authenticated with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans')));
create policy ai_action_drafts_update on public.ai_action_drafts for update to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans'))) with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans')));
create policy ai_action_drafts_delete on public.ai_action_drafts for delete to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director')));

drop policy if exists recruitment_cases_manage on public.recruitment_cases;
create policy recruitment_cases_read on public.recruitment_cases for select to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans')));
create policy recruitment_cases_insert on public.recruitment_cases for insert to authenticated with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans')));
create policy recruitment_cases_update on public.recruitment_cases for update to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans'))) with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans')));
create policy recruitment_cases_delete on public.recruitment_cases for delete to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director')));

drop policy if exists workforce_plans_manage on public.workforce_plans;
create policy workforce_plans_read on public.workforce_plans for select to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans')));
create policy workforce_plans_insert on public.workforce_plans for insert to authenticated with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans')));
create policy workforce_plans_update on public.workforce_plans for update to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans'))) with check (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director','Manager','Manager EduTrans')));
create policy workforce_plans_delete on public.workforce_plans for delete to authenticated using (exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in('Owner','Director')));
