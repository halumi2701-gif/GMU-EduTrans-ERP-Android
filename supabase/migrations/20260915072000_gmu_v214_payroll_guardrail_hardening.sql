-- GMU EduTrans v21.4 — payroll guardrail hardening
-- Enforce variable incentive cap against verified cash before payroll entry can be created.

create or replace function private.gmu_v214_guard_variable_payroll()
returns trigger language plpgsql security definer set search_path=public,private,pg_temp as $$
declare
  a public.compensation_accruals%rowtype;
  v_component text;
  v_cash numeric:=0;
  v_cap_pct numeric:=6.25;
  v_cap_amount numeric:=0;
  v_existing numeric:=0;
  v_month date:=date_trunc('month',current_date)::date;
begin
  if new.source_accrual_id is null then return new; end if;
  select * into a from public.compensation_accruals where id=new.source_accrual_id;
  if not found or a.state<>'PAYABLE' then
    return case when tg_op='INSERT' then null else old end;
  end if;

  select coalesce(cp.component_type,new.component_type) into v_component from public.compensation_policies cp where cp.policy_code=a.policy_code;
  v_component:=upper(coalesce(v_component,new.component_type,''));

  if v_component not like '%COMMISSION%' and v_component not like '%BONUS%' and v_component not in ('TRIP_FEE','FEE','TASK_FEE','VARIABLE_FEE','SALES_COMMISSION','TEAM_BONUS','TARGET_BONUS') then
    return new;
  end if;

  select coalesce(sum(p.amount),0) into v_cash from public.payments p
   where p.verified_at is not null and p.verified_at>=v_month and p.verified_at<(v_month+interval '1 month');
  select coalesce(numeric_value,6.25) into v_cap_pct from public.company_control_settings where setting_key='VARIABLE_INCENTIVE_CAP_PCT';
  v_cap_amount:=round(v_cash*(v_cap_pct/100.0),0);

  select coalesce(sum(pe.amount),0) into v_existing
    from public.payroll_entries pe
   where pe.earned_at>=v_month and pe.earned_at<(v_month+interval '1 month')
     and pe.state not in ('VOID')
     and pe.id is distinct from new.id
     and upper(coalesce(pe.component_type,'')) in ('TRIP_FEE','FEE','TASK_FEE','VARIABLE_FEE','SALES_COMMISSION','TEAM_BONUS','TARGET_BONUS','COMMISSION','BONUS');

  if v_cash<=0 or v_existing+new.amount>v_cap_amount then
    update public.compensation_accruals
       set state='ON_HOLD',
           hold_reason=format('Variable incentive cap %.2f%% dari verified cash terlampaui. Cash Rp%s; cap Rp%s; variable payroll existing Rp%s; proposed Rp%s.',v_cap_pct,v_cash,v_cap_amount,v_existing,new.amount),
           updated_at=now()
     where id=new.source_accrual_id;
    return case when tg_op='INSERT' then null else old end;
  end if;

  return new;
end;$$;
revoke all on function private.gmu_v214_guard_variable_payroll() from public,anon,authenticated;

drop trigger if exists trg_v214_guard_variable_payroll on public.payroll_entries;
create trigger trg_v214_guard_variable_payroll
before insert or update of amount,state,source_accrual_id on public.payroll_entries
for each row execute function private.gmu_v214_guard_variable_payroll();

-- Statement-level closing trigger must return NULL; closing data remains untouched.
create or replace function private.gmu_v214_after_trip_closing()
returns trigger language plpgsql security definer set search_path=public,private,cron,pg_temp as $$
begin
  perform private.gmu_v214_refresh_performance_payroll();
  return null;
end;$$;
revoke all on function private.gmu_v214_after_trip_closing() from public,anon,authenticated;
