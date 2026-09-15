create index if not exists staff_probation_contract_idx on public.staff_probation_checkpoints(contract_id);
create index if not exists staff_probation_reviewed_by_idx on public.staff_probation_checkpoints(reviewed_by);
create index if not exists staff_leave_bal_updated_by_idx on public.staff_leave_balances(updated_by);
create index if not exists staff_comp_approved_by_idx on public.staff_compensation_profiles(approved_by);
create index if not exists payroll_periods_approved_by_idx on public.payroll_periods(approved_by);
create index if not exists payroll_periods_locked_by_idx on public.payroll_periods(locked_by);
create index if not exists staff_asset_access_verified_by_idx on public.staff_asset_access_handover(verified_by);
create index if not exists enterprise_automation_updated_by_idx on public.enterprise_automation_policies(updated_by);
create index if not exists enterprise_exception_resolved_by_idx on public.enterprise_exception_queue(resolved_by);

do $$
declare t text;
begin
  foreach t in array array['staff_probation_checkpoints','staff_leave_balances','staff_asset_access_handover','staff_compensation_profiles','payroll_periods','enterprise_automation_policies','enterprise_exception_queue'] loop
    execute format('drop policy if exists %I_write on public.%I',t,t);
    execute format('drop policy if exists %I_insert on public.%I',t,t);
    execute format('drop policy if exists %I_update on public.%I',t,t);
    execute format('create policy %I_insert on public.%I for insert to authenticated with check (private.gmu_v200_is_management())',t,t);
    execute format('create policy %I_update on public.%I for update to authenticated using (private.gmu_v200_is_management()) with check (private.gmu_v200_is_management())',t,t);
  end loop;
end $$;
