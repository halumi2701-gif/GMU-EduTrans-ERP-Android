
-- GMU EduTrans v22.9 — Internal payable automation without APK update.
-- Package pricing/HPP is untouched. This layer only records internal obligations in payroll_entries.

create unique index if not exists payroll_internal_booking_component_uq
on public.payroll_entries(booking_id,staff_id,component_type)
where booking_id is not null and source_accrual_id is null;

insert into public.company_control_settings(setting_key,numeric_value,text_value,description)
values
  ('INTERNAL_PAYABLE_AUTOMATION_ENABLED',1,'BOOLEAN_NUMERIC','Otomatisasi kewajiban internal aktif; tidak mengeksekusi transfer otomatis'),
  ('SALES_COMMISSION_PER_PAX',5000,'IDR_PER_PAX','Komisi Sales final Rp5.000 per paid/closed pax')
on conflict(setting_key) do update
set numeric_value=excluded.numeric_value,text_value=excluded.text_value,description=excluded.description,updated_at=now();

create or replace function private.gmu_v229_refresh_trip_internal_payables(p_booking_id text)
returns void
language plpgsql
security definer
set search_path=public,private,pg_temp
as $$
declare
  b public.bookings%rowtype;
  t public.trips%rowtype;
  v_manager uuid;
  v_manager_count integer:=0;
  v_manager_fee numeric:=130000;
  v_tl_fee numeric:=110000;
  v_ops_fee numeric:=55000;
  v_meal numeric:=12000;
  v_sales_per_pax numeric:=5000;
  v_enabled numeric:=1;
  v_actor uuid;
  r record;
begin
  if p_booking_id is null or btrim(p_booking_id)='' then return; end if;

  select coalesce(numeric_value,1) into v_enabled
  from public.company_control_settings where setting_key='INTERNAL_PAYABLE_AUTOMATION_ENABLED';
  if coalesce(v_enabled,1)<=0 then return; end if;

  select * into b from public.bookings where id=p_booking_id;
  if not found then return; end if;

  -- Only a closed trip earns operational fees.
  if not exists(select 1 from public.trip_closings tc where tc.booking_id=p_booking_id) then return; end if;

  select * into t from public.trips where booking_id=p_booking_id order by updated_at desc limit 1;

  select coalesce(numeric_value,130000) into v_manager_fee from public.company_control_settings where setting_key='INTERNAL_MANAGER_FEE_PER_TRIP';
  select coalesce(numeric_value,110000) into v_tl_fee from public.company_control_settings where setting_key='INTERNAL_TL_MC_FEE_PER_TRIP';
  select coalesce(numeric_value,55000) into v_ops_fee from public.company_control_settings where setting_key='INTERNAL_OPS_DOC_FEE_PER_TRIP';
  select coalesce(numeric_value,12000) into v_meal from public.company_control_settings where setting_key='FIELD_CREW_MEAL_PER_PERSON';
  select coalesce(numeric_value,5000) into v_sales_per_pax from public.company_control_settings where setting_key='SALES_COMMISSION_PER_PAX';

  -- Prefer explicit Manager assignment for this booking.
  select sa.staff_id into v_manager
  from public.staff_assignments sa
  join public.profiles p on p.id=sa.staff_id and p.is_active=true and p.role::text in ('Manager','Manager EduTrans')
  where sa.booking_id=p_booking_id and upper(coalesce(sa.status,'')) not in ('CANCELLED','VOID')
  order by sa.created_at desc
  limit 1;

  -- Safe fallback only when exactly one active Manager exists.
  if v_manager is null then
    select count(*),min(p.id) into v_manager_count,v_manager
    from public.profiles p
    where p.is_active=true and p.role::text in ('Manager','Manager EduTrans');
    if v_manager_count<>1 then v_manager:=null; end if;
  end if;

  v_actor:=coalesce(v_manager,b.created_by);

  if v_manager is not null and v_manager_fee>0 then
    insert into public.payroll_entries(staff_id,booking_id,component_type,amount,state,earned_at,notes,created_by,created_at,updated_at)
    values(v_manager,p_booking_id,'MANAGER_TRIP_FEE',v_manager_fee,'EARNED',now(),
      '[v22.9 AUTO] Fee Manager per trip closing. Payment tetap approval-only.',v_actor,now(),now())
    on conflict(booking_id,staff_id,component_type) where booking_id is not null and source_accrual_id is null
    do nothing;
  end if;

  if t.tl_id is not null and v_tl_fee>0 then
    insert into public.payroll_entries(staff_id,booking_id,component_type,amount,state,earned_at,notes,created_by,created_at,updated_at)
    values(t.tl_id,p_booking_id,'TL_MC_TRIP_FEE',v_tl_fee,'EARNED',now(),
      '[v22.9 AUTO] Fee TL/MC per trip closing. Payment tetap approval-only.',v_actor,now(),now())
    on conflict(booking_id,staff_id,component_type) where booking_id is not null and source_accrual_id is null
    do nothing;
  end if;

  if t.operation_pic_id is not null and v_ops_fee>0 then
    insert into public.payroll_entries(staff_id,booking_id,component_type,amount,state,earned_at,notes,created_by,created_at,updated_at)
    values(t.operation_pic_id,p_booking_id,'OPS_DOC_TRIP_FEE',v_ops_fee,'EARNED',now(),
      '[v22.9 AUTO] Fee Ops + Dokumentasi per trip closing. Payment tetap approval-only.',v_actor,now(),now())
    on conflict(booking_id,staff_id,component_type) where booking_id is not null and source_accrual_id is null
    do nothing;
  end if;

  if b.sales_id is not null and coalesce(b.pax,0)>0 and v_sales_per_pax>0 then
    insert into public.payroll_entries(staff_id,booking_id,component_type,amount,state,earned_at,notes,created_by,created_at,updated_at)
    values(b.sales_id,p_booking_id,'SALES_COMMISSION',coalesce(b.pax,0)*v_sales_per_pax,'EARNED',now(),
      format('[v22.9 AUTO] Komisi Sales Rp%s x %s pax. Payment tetap approval-only.',v_sales_per_pax,b.pax),
      v_actor,now(),now())
    on conflict(booking_id,staff_id,component_type) where booking_id is not null and source_accrual_id is null
    do nothing;
  end if;

  -- Meal money is for distinct assigned field crew only: Manager, TL/MC, Ops/Documentation.
  for r in
    select distinct staff_id from (
      select v_manager as staff_id
      union all select t.tl_id
      union all select t.operation_pic_id
    ) x
    where staff_id is not null
  loop
    if v_meal>0 then
      insert into public.payroll_entries(staff_id,booking_id,component_type,amount,state,earned_at,notes,created_by,created_at,updated_at)
      values(r.staff_id,p_booking_id,'FIELD_CREW_MEAL',v_meal,'EARNED',now(),
        '[v22.9 AUTO] Uang makan crew lapangan per orang per kegiatan. Payment tetap approval-only.',
        v_actor,now(),now())
      on conflict(booking_id,staff_id,component_type) where booking_id is not null and source_accrual_id is null
      do nothing;
    end if;
  end loop;
end;
$$;
revoke all on function private.gmu_v229_refresh_trip_internal_payables(text) from public,anon,authenticated;

create or replace function private.gmu_v229_after_trip_closing()
returns trigger
language plpgsql
security definer
set search_path=public,private,pg_temp
as $$
begin
  perform private.gmu_v229_refresh_trip_internal_payables(new.booking_id);
  return new;
end;
$$;
revoke all on function private.gmu_v229_after_trip_closing() from public,anon,authenticated;

drop trigger if exists trg_v229_internal_payables_after_closing on public.trip_closings;
create trigger trg_v229_internal_payables_after_closing
after insert or update on public.trip_closings
for each row execute function private.gmu_v229_after_trip_closing();

-- Fixed monthly payroll only follows an explicitly active compensation profile.
-- No role-based guessing, so duplicate/accidental salary liabilities are avoided.
create or replace function private.gmu_v229_refresh_monthly_fixed_payables(p_month date default date_trunc('month',current_date)::date)
returns void
language plpgsql
security definer
set search_path=public,private,pg_temp
as $$
declare
  m date:=date_trunc('month',coalesce(p_month,current_date))::date;
  r record;
begin
  for r in
    select scp.staff_id,scp.base_monthly
    from public.staff_compensation_profiles scp
    join public.profiles p on p.id=scp.staff_id and p.is_active=true
    where scp.status='ACTIVE'
      and coalesce(scp.base_monthly,0)>0
      and scp.effective_from<=m
      and (scp.effective_to is null or scp.effective_to>=m)
  loop
    insert into public.payroll_entries(staff_id,booking_id,component_type,amount,state,earned_at,notes,created_at,updated_at)
    values(r.staff_id,'MONTHLY:'||to_char(m,'YYYY-MM'),'MONTHLY_FIXED',r.base_monthly,'EARNED',m::timestamptz,
      '[v22.9 AUTO] Fixed bulanan dari staff_compensation_profiles ACTIVE. Payment tetap approval-only.',now(),now())
    on conflict(booking_id,staff_id,component_type) where booking_id is not null and source_accrual_id is null
    do nothing;
  end loop;
end;
$$;
revoke all on function private.gmu_v229_refresh_monthly_fixed_payables(date) from public,anon,authenticated;

select private.gmu_v229_refresh_monthly_fixed_payables(date_trunc('month',current_date)::date);

do $$
declare jid bigint;
begin
  select jobid into jid from cron.job where jobname='gmu_v229_monthly_fixed_payables';
  if jid is not null then perform cron.unschedule(jid); end if;
  perform cron.schedule(
    'gmu_v229_monthly_fixed_payables',
    '15 18 * * *',
    $cmd$select private.gmu_v229_refresh_monthly_fixed_payables(date_trunc('month',current_date)::date);$cmd$
  );
end $$;
