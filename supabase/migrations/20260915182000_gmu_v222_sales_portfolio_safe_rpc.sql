-- GMU EduTrans v22.2 — Safe Sales Portfolio RPC
-- Sales sees own paid-pax performance and compensation only.
-- Owner/Director/Manager can see team aggregate plus booked value/cash-in.

create or replace function public.gmu_sales_portfolio_summary(
  p_period_month date default date_trunc('month', current_date)::date
)
returns table(
  period_month date,
  target_key text,
  target_name text,
  paid_bookings integer,
  paid_pax integer,
  booked_value numeric,
  cash_in numeric,
  bep_paid_pax integer,
  productive_paid_pax integer,
  target_paid_pax integer,
  stretch_paid_pax integer,
  outstanding_paid_pax integer,
  achievement_pct numeric,
  pax_above_target integer,
  sales_retainer numeric,
  sales_fee_per_paid_pax numeric,
  variable_sales_fee numeric,
  target_bonus_earned numeric,
  modeled_sales_income numeric,
  achievement_level text,
  can_view_finance boolean
)
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
declare
  v_uid uuid := (select auth.uid());
  v_role text;
  v_is_sales boolean := false;
begin
  select p.role::text into v_role
  from public.profiles p
  where p.id=v_uid and p.is_active=true;

  if v_role is null or v_role not in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales') then
    raise exception 'ERP role is not allowed to read Sales portfolio target';
  end if;

  v_is_sales := v_role='Sales';

  return query
  with paid_activity as (
    select
      count(*)::integer as paid_bookings,
      coalesce(sum(a.paid_pax),0)::integer as paid_pax,
      coalesce(sum(a.booking_value),0)::numeric as booked_value
    from public.v_sales_paid_booking_attribution a
    where a.period_month=p_period_month
      and (not v_is_sales or a.sales_id=v_uid)
  ), cash_activity as (
    select case when v_is_sales then 0::numeric else coalesce(sum(p.amount),0)::numeric end as cash_in
    from public.payments p
    where not v_is_sales
      and coalesce(p.amount,0)>0
      and (p.verified_at is not null or p.verified_by is not null)
      and date_trunc('month',coalesce(p.verified_at,p.created_at,p.payment_date::timestamptz))::date=p_period_month
  )
  select
    t.period_month,
    t.target_key,
    t.target_name,
    a.paid_bookings,
    a.paid_pax,
    case when v_is_sales then 0::numeric else a.booked_value end as booked_value,
    c.cash_in,
    t.bep_paid_pax,
    t.productive_paid_pax,
    t.target_paid_pax,
    t.stretch_paid_pax,
    t.outstanding_paid_pax,
    round((a.paid_pax::numeric/nullif(t.target_paid_pax,0))*100,2) as achievement_pct,
    greatest(a.paid_pax-t.target_paid_pax,0)::integer as pax_above_target,
    t.sales_retainer,
    t.sales_fee_per_paid_pax,
    (a.paid_pax*t.sales_fee_per_paid_pax)::numeric as variable_sales_fee,
    case when a.paid_pax>=t.target_paid_pax then t.target_bonus else 0 end::numeric as target_bonus_earned,
    (t.sales_retainer + a.paid_pax*t.sales_fee_per_paid_pax + case when a.paid_pax>=t.target_paid_pax then t.target_bonus else 0 end)::numeric as modeled_sales_income,
    case
      when a.paid_pax>=t.outstanding_paid_pax then 'OUTSTANDING'
      when a.paid_pax>=t.stretch_paid_pax then 'STRETCH'
      when a.paid_pax>=t.target_paid_pax then 'TARGET_TERCAPAI'
      when a.paid_pax>=t.productive_paid_pax then 'PRODUKTIF'
      when a.paid_pax>=t.bep_paid_pax then 'BEP'
      else 'DI_BAWAH_BEP'
    end::text as achievement_level,
    (not v_is_sales) as can_view_finance
  from public.sales_portfolio_targets t
  cross join paid_activity a
  cross join cash_activity c
  where t.period_month=p_period_month and t.target_key='ALL_PROGRAMS' and t.status='ACTIVE'
  limit 1;
end;
$$;

revoke all on function public.gmu_sales_portfolio_summary(date) from public, anon;
grant execute on function public.gmu_sales_portfolio_summary(date) to authenticated;

create or replace function public.gmu_sales_portfolio_program_breakdown(
  p_period_month date default date_trunc('month', current_date)::date
)
returns table(
  program_name text,
  paid_bookings integer,
  paid_pax integer,
  booked_value numeric,
  can_view_finance boolean
)
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
declare
  v_uid uuid := (select auth.uid());
  v_role text;
  v_is_sales boolean := false;
begin
  select p.role::text into v_role
  from public.profiles p
  where p.id=v_uid and p.is_active=true;

  if v_role is null or v_role not in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales') then
    raise exception 'ERP role is not allowed to read Sales portfolio breakdown';
  end if;

  v_is_sales := v_role='Sales';

  return query
  select
    coalesce(nullif(a.program_name,''),'Program Lain')::text as program_name,
    count(*)::integer as paid_bookings,
    coalesce(sum(a.paid_pax),0)::integer as paid_pax,
    case when v_is_sales then 0::numeric else coalesce(sum(a.booking_value),0)::numeric end as booked_value,
    (not v_is_sales) as can_view_finance
  from public.v_sales_paid_booking_attribution a
  where a.period_month=p_period_month
    and (not v_is_sales or a.sales_id=v_uid)
  group by coalesce(nullif(a.program_name,''),'Program Lain')
  order by coalesce(sum(a.paid_pax),0) desc;
end;
$$;

revoke all on function public.gmu_sales_portfolio_program_breakdown(date) from public, anon;
grant execute on function public.gmu_sales_portfolio_program_breakdown(date) to authenticated;

comment on function public.gmu_sales_portfolio_summary(date) is 'Safe target summary: Sales gets own paid pax/fee/bonus without company booked value or cash-in; management gets team financial aggregate.';
comment on function public.gmu_sales_portfolio_program_breakdown(date) is 'Safe program breakdown; Sales sees own pax only, management can also see booked value.';
