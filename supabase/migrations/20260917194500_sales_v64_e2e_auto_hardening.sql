-- GMU EduTrans Sales v6.4 E2E Auto hardening
-- Mirrors production fixes for lifecycle synchronization, block commission, and integration health.

create or replace function private.sync_erp_status_to_request_trigger()
returns trigger
language plpgsql
set search_path to 'public', 'private', 'pg_temp'
as $function$
declare
  br public.booking_requests;
  v_target text;
  v_allowed boolean := false;
begin
  if old.status is not distinct from new.status then
    return new;
  end if;

  select * into br
  from public.booking_requests r
  where r.converted_booking_id=new.id or r.erp_lead_booking_id=new.id
  order by r.converted_booking_id is not null desc,r.created_at desc
  limit 1;

  if not found or br.status='REJECTED' then
    return new;
  end if;

  case new.status::text
    when 'Quotation' then
      v_target := 'QUOTATION';
      v_allowed := br.status in ('NEW_REQUEST','VERIFICATION','QUOTATION');
    when 'DP' then
      v_target := 'WAITING_DP';
      v_allowed := br.status in ('QUOTATION','WAITING_DP');
    when 'Confirmed' then
      v_target := 'CONFIRMED';
      v_allowed := br.status='CONFIRMED'
        or (
          br.status='WAITING_DP'
          and exists(
            select 1 from public.invoices i
            where i.booking_request_id=br.id
              and i.invoice_type='DP'
              and i.status='PAID'
          )
        );
    when 'Preparation' then
      v_target := 'PREPARATION';
      v_allowed := br.status in ('CONFIRMED','PAID','PREPARATION','READY');
    when 'Trip' then
      v_target := 'ON_TRIP';
      v_allowed := br.status in ('PREPARATION','READY','ON_TRIP');
    when 'Completed' then
      v_target := 'COMPLETED';
      v_allowed := br.status in ('ON_TRIP','COMPLETED');
    when 'Closed' then
      v_target := 'CLOSED';
      v_allowed := br.status in ('COMPLETED','CLOSED')
        and exists(select 1 from public.trip_closings tc where tc.booking_id=new.id);
    else
      return new;
  end case;

  if v_allowed and br.status is distinct from v_target then
    update public.booking_requests
      set status=v_target,updated_at=now()
    where id=br.id;
  end if;

  return new;
end;
$function$;

create or replace function public.gmu_sales_portfolio_summary(
  p_period_month date default (date_trunc('month', current_date))::date
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
set search_path to 'public', 'pg_temp'
as $function$
declare
  v_uid uuid := auth.uid();
  v_role text;
  v_is_sales boolean := false;
begin
  select p.role::text into v_role
  from public.profiles p
  where p.id=v_uid and p.is_active=true;

  if v_role is null or v_role not in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales') then
    raise exception 'Role is not allowed to read Sales portfolio target';
  end if;
  v_is_sales := v_role='Sales';

  return query
  with first_paid as (
    select p.booking_id,
           min(coalesce(p.verified_at,p.payment_date::timestamptz,p.created_at)) as first_paid_at
    from public.payments p
    where coalesce(p.amount,0)>0
      and (p.verified_at is not null or p.verified_by is not null)
      and coalesce(p.verified_at,p.payment_date::timestamptz,p.created_at) is not null
    group by p.booking_id
  ), paid_activity as (
    select count(*)::integer as paid_bookings,
           coalesce(sum(greatest(coalesce(b.pax,0),0)),0)::integer as paid_pax,
           coalesce(sum(greatest(coalesce(b.pax,0),0)*coalesce(b.price_per_pax,0)),0)::numeric as booked_value
    from public.bookings b
    join first_paid fp on fp.booking_id=b.id
    where date_trunc('month',fp.first_paid_at)::date=p_period_month
      and (not v_is_sales or b.sales_id=v_uid)
  ), cash_activity as (
    select case when v_is_sales then 0::numeric else coalesce(sum(p.amount),0)::numeric end as cash_in
    from public.payments p
    where not v_is_sales
      and coalesce(p.amount,0)>0
      and (p.verified_at is not null or p.verified_by is not null)
      and date_trunc('month',coalesce(p.verified_at,p.payment_date::timestamptz,p.created_at))::date=p_period_month
  )
  select t.period_month,t.target_key,t.target_name,
         a.paid_bookings,a.paid_pax,
         case when v_is_sales then 0::numeric else a.booked_value end,
         c.cash_in,
         t.bep_paid_pax,t.productive_paid_pax,t.target_paid_pax,t.stretch_paid_pax,t.outstanding_paid_pax,
         round((a.paid_pax::numeric/nullif(t.target_paid_pax,0))*100,2),
         greatest(a.paid_pax-t.target_paid_pax,0)::integer,
         t.sales_retainer,t.sales_fee_per_paid_pax,
         (floor(a.paid_pax::numeric/20)*50000)::numeric,
         case when a.paid_pax>=t.target_paid_pax then t.target_bonus else 0 end::numeric,
         (t.sales_retainer + floor(a.paid_pax::numeric/20)*50000 + case when a.paid_pax>=t.target_paid_pax then t.target_bonus else 0 end)::numeric,
         case
           when a.paid_pax>=t.outstanding_paid_pax then 'OUTSTANDING'
           when a.paid_pax>=t.stretch_paid_pax then 'STRETCH'
           when a.paid_pax>=t.target_paid_pax then 'TARGET_TERCAPAI'
           when a.paid_pax>=t.productive_paid_pax then 'PRODUKTIF'
           when a.paid_pax>=t.bep_paid_pax then 'BEP'
           else 'DI_BAWAH_BEP'
         end::text,
         (not v_is_sales)
  from public.sales_portfolio_targets t
  cross join paid_activity a
  cross join cash_activity c
  where t.period_month=p_period_month and t.target_key='ALL_PROGRAMS' and t.status='ACTIVE'
  limit 1;
end;
$function$;

create or replace function public.gmu_sales_earnings_summary(
  p_period_month date default (date_trunc('month', current_date))::date
)
returns table(
  period_month date,
  paid_pax integer,
  complete_blocks integer,
  pending_pax integer,
  commission_per_block numeric,
  commission_amount numeric,
  retainer numeric,
  target_bonus_earned numeric,
  total_modeled numeric,
  rule_text text
)
language plpgsql
security definer
set search_path to 'public', 'auth', 'pg_temp'
as $function$
declare
  v_uid uuid := auth.uid();
  v_role text;
  v_paid_pax integer := 0;
  v_retainer numeric := 600000;
  v_target_bonus numeric := 0;
  v_target integer := 400;
  v_blocks integer := 0;
  v_pending integer := 0;
  v_commission numeric := 0;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
  if v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;

  select coalesce(t.sales_retainer,600000), coalesce(t.target_bonus,0), coalesce(t.target_paid_pax,400)
    into v_retainer,v_target_bonus,v_target
  from public.sales_portfolio_targets t
  where t.period_month=p_period_month and t.target_key='ALL_PROGRAMS' and t.status='ACTIVE'
  limit 1;

  with first_verified as (
    select p.booking_id,
           min(coalesce(p.verified_at,p.payment_date::timestamptz,p.created_at)) as first_verified_at
    from public.payments p
    where coalesce(p.amount,0)>0
      and (p.verified_at is not null or p.verified_by is not null)
    group by p.booking_id
  )
  select coalesce(sum(greatest(coalesce(b.pax,0),0)),0)::integer
    into v_paid_pax
  from public.bookings b
  join first_verified fv on fv.booking_id=b.id
  where b.sales_id=v_uid
    and date_trunc('month',fv.first_verified_at)::date=p_period_month;

  v_blocks := floor(v_paid_pax::numeric/20)::integer;
  v_pending := mod(v_paid_pax,20);
  v_commission := v_blocks * 50000;

  return query select
    p_period_month,
    v_paid_pax,
    v_blocks,
    v_pending,
    50000::numeric,
    v_commission,
    v_retainer,
    case when v_paid_pax>=v_target then v_target_bonus else 0 end,
    v_retainer + v_commission + case when v_paid_pax>=v_target then v_target_bonus else 0 end,
    'Rp50.000 per complete 20 paid pax; sisa pax menunggu melengkapi blok berikutnya.'::text;
end;
$function$;

create or replace function public.gmu_sales_e2e_health()
returns table(
  quotation_decision_ready boolean,
  payment_to_won_ready boolean,
  invoice_to_handover_ready boolean,
  request_handover_progress_ready boolean,
  booking_status_sync_ready boolean,
  block_commission_ready boolean,
  onboarding_ready boolean,
  field_ops_ready boolean,
  healthy boolean
)
language plpgsql
security definer
set search_path to 'public', 'pg_catalog', 'auth', 'pg_temp'
as $function$
declare
  v_uid uuid := auth.uid();
  v_role text;
  v_q boolean;
  v_pay boolean;
  v_inv boolean;
  v_req boolean;
  v_book boolean;
  v_comm boolean;
  v_onboarding boolean;
  v_field boolean;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
  if v_role is null or v_role not in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales') then
    raise exception 'Role is not allowed to read Sales integration health';
  end if;

  select exists(
    select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public' and p.proname='gmu_process_customer_quotation_decision'
  ) into v_q;

  select exists(
    select 1 from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace
    where not t.tgisinternal and n.nspname='public' and c.relname='payments' and t.tgname='trg_sales_handover_after_verified_payment'
  ) into v_pay;

  select exists(
    select 1 from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace
    where not t.tgisinternal and n.nspname='public' and c.relname='invoices' and t.tgname='trg_invoices_sales_dp_handover'
  ) into v_inv;

  select exists(
    select 1 from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace
    where not t.tgisinternal and n.nspname='public' and c.relname='booking_requests' and t.tgname='trg_sales_handover_request_status'
  ) into v_req;

  select exists(
    select 1 from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace
    where not t.tgisinternal and n.nspname='public' and c.relname='bookings' and t.tgname='trg_erp_booking_status_to_request'
  ) into v_book;

  select exists(
    select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public' and p.proname='gmu_sales_portfolio_summary'
      and position('floor(a.paid_pax::numeric/20)*50000' in pg_get_functiondef(p.oid)) > 0
  ) and exists(
    select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public' and p.proname='gmu_sales_earnings_summary'
  ) into v_comm;

  v_onboarding := to_regclass('public.sales_applications') is not null
    and exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='gmu_sales_complete_onboarding');

  v_field := to_regclass('public.staff_attendance') is not null
    and to_regclass('public.sales_visits') is not null
    and to_regclass('public.sales_daily_reports') is not null
    and to_regclass('public.sales_price_requests') is not null;

  return query select v_q,v_pay,v_inv,v_req,v_book,v_comm,v_onboarding,v_field,
    (v_q and v_pay and v_inv and v_req and v_book and v_comm and v_onboarding and v_field);
end;
$function$;

revoke all on function public.gmu_sales_earnings_summary(date) from public, anon;
grant execute on function public.gmu_sales_earnings_summary(date) to authenticated, service_role;
revoke all on function public.gmu_sales_e2e_health() from public, anon;
grant execute on function public.gmu_sales_e2e_health() to authenticated, service_role;

notify pgrst, 'reload schema';
