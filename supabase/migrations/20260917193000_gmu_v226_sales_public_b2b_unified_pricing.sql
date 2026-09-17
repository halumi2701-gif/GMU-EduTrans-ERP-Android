-- GMU EduTrans v22.6 — Unified Sales Public + B2B Pricing
-- Program scope finalized in this migration:
-- Edukasi Lingkungan & Profesi Stasiun.
--
-- Principles:
-- 1. Repository/migrations define the commercial rules.
-- 2. Supabase is the live source of truth for Sales/ERP transactions.
-- 3. Sales never types commercial price manually for a mastered package.
-- 4. PUBLIC and B2B use one resolver and one booking RPC.
-- 5. Sales can see selling/net/cashback/partner margin/own commission, never GMU HPP/profit.
-- 6. GMU margin guard stays >= 25%.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- A. Canonical Station package 2026
-- ---------------------------------------------------------------------------
do $$
declare
  v_program_id uuid;
begin
  select id into v_program_id
  from public.programs
  where slug='edukasi-profesi-lingkungan-stasiun'
  limit 1;

  if v_program_id is null then
    raise exception 'Program edukasi-profesi-lingkungan-stasiun belum tersedia';
  end if;

  update public.programs
  set name='Edukasi Lingkungan & Profesi Stasiun',
      min_pax=20,
      marketing_start_price=49500,
      marketing_price_note='Mulai Rp49.500/pax. Private 20–24 Rp65.000; 25–39 Rp58.000; 40+ Rp49.500. Shared Rp49.500 bila total sesi minimal 40 pax.',
      is_active=true
  where id=v_program_id;

  -- Retain legacy package row for history, but stop offering it for new Sales quotes.
  update public.program_packages
  set is_active=false,
      status='INACTIVE'
  where package_code='STATION-46';

  update public.program_packages
  set program_id=v_program_id,
      name='Edukasi Lingkungan & Profesi Stasiun 2026',
      description='Program edukasi sekitar 2 jam: lingkungan/fasilitas stasiun, keselamatan, alur perjalanan, profesi perkeretaapian, aktivitas edukatif, dan pendampingan GMU EduTrans.',
      price_per_pax=49500,
      min_pax=20,
      status='ACTIVE',
      is_active=true,
      sort_order=10
  where package_code='STATION-PROF-2026';

  if not found then
    insert into public.program_packages(
      package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order
    ) values (
      'STATION-PROF-2026',v_program_id,'Edukasi Lingkungan & Profesi Stasiun 2026',
      'Program edukasi sekitar 2 jam: lingkungan/fasilitas stasiun, keselamatan, alur perjalanan, profesi perkeretaapian, aktivitas edukatif, dan pendampingan GMU EduTrans.',
      49500,20,'ACTIVE',true,10
    );
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- B. Tier master shared by Sales UI + Booking RPC
-- pricing_pax may differ from booking pax for SHARED sessions.
-- ---------------------------------------------------------------------------
create table if not exists public.program_package_price_tiers (
  id uuid primary key default gen_random_uuid(),
  package_code text not null,
  channel text not null check (channel in ('DIRECT_PUBLIC','B2B_MOU')),
  session_type text not null default 'ANY' check (session_type in ('PRIVATE','SHARED','ANY')),
  min_pricing_pax integer not null check (min_pricing_pax > 0),
  max_pricing_pax integer,
  unit_price numeric(16,2) not null check (unit_price > 0),
  school_cashback_per_pax numeric(16,2) not null default 0 check (school_cashback_per_pax >= 0),
  sales_commission_per_pax numeric(16,2) not null default 0 check (sales_commission_per_pax >= 0),
  effective_from date not null default current_date,
  effective_until date,
  is_active boolean not null default true,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (max_pricing_pax is null or max_pricing_pax >= min_pricing_pax),
  check (effective_until is null or effective_until >= effective_from),
  unique(package_code,channel,session_type,min_pricing_pax,effective_from)
);

insert into public.program_package_price_tiers(
  package_code,channel,session_type,min_pricing_pax,max_pricing_pax,unit_price,
  school_cashback_per_pax,sales_commission_per_pax,effective_from,is_active,notes
) values
-- Public / umum — Private
('STATION-PROF-2026','DIRECT_PUBLIC','PRIVATE',20,24,65000,0,5000,date '2026-09-17',true,'Private 20–24 pax.'),
('STATION-PROF-2026','DIRECT_PUBLIC','PRIVATE',25,39,58000,0,5000,date '2026-09-17',true,'Private 25–39 pax.'),
('STATION-PROF-2026','DIRECT_PUBLIC','PRIVATE',40,null,49500,0,5000,date '2026-09-17',true,'Private 40+ pax.'),
-- Public / umum — Shared. pricing_pax is combined session pax; booking pax remains the school pax.
('STATION-PROF-2026','DIRECT_PUBLIC','SHARED',40,null,49500,0,5000,date '2026-09-17',true,'Shared Education Session; total sesi minimal 40 pax.'),
-- B2B — GMU net to partner. Official school selling price stays Rp49.500/pax.
('STATION-PROF-2026','B2B_MOU','ANY',40,59,45000,2500,5000,date '2026-09-17',true,'Partner spread 4.500; cashback sekolah 2.500; net partner margin 2.000/pax.'),
('STATION-PROF-2026','B2B_MOU','ANY',60,79,44500,2500,5000,date '2026-09-17',true,'Partner spread 5.000; cashback sekolah 2.500; net partner margin 2.500/pax.'),
('STATION-PROF-2026','B2B_MOU','ANY',80,99,44000,2500,5000,date '2026-09-17',true,'Partner spread 5.500; cashback sekolah 2.500; net partner margin 3.000/pax.'),
('STATION-PROF-2026','B2B_MOU','ANY',100,null,43500,2500,5000,date '2026-09-17',true,'Partner spread 6.000; cashback sekolah 2.500; net partner margin 3.500/pax.')
on conflict (package_code,channel,session_type,min_pricing_pax,effective_from)
do update set
  max_pricing_pax=excluded.max_pricing_pax,
  unit_price=excluded.unit_price,
  school_cashback_per_pax=excluded.school_cashback_per_pax,
  sales_commission_per_pax=excluded.sales_commission_per_pax,
  is_active=true,
  notes=excluded.notes,
  updated_at=now();

alter table public.program_package_price_tiers enable row level security;
drop policy if exists program_package_price_tiers_staff_read on public.program_package_price_tiers;
create policy program_package_price_tiers_staff_read
on public.program_package_price_tiers
for select to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid()) and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Admin','Sales')
  )
);
grant select on public.program_package_price_tiers to authenticated;

-- ---------------------------------------------------------------------------
-- C. Booking pricing snapshot columns.
-- price_per_pax remains GMU-recognized revenue rate.
-- customer_sell_price_per_pax is the school/end-customer official price.
-- ---------------------------------------------------------------------------
alter table public.bookings
  add column if not exists package_code text,
  add column if not exists price_channel text,
  add column if not exists session_type text,
  add column if not exists pricing_pax integer,
  add column if not exists b2b_partner_id uuid references public.b2b_partners(id),
  add column if not exists customer_sell_price_per_pax numeric(16,2),
  add column if not exists school_cashback_per_pax numeric(16,2) not null default 0,
  add column if not exists partner_margin_per_pax numeric(16,2) not null default 0,
  add column if not exists sales_commission_per_pax numeric(16,2) not null default 0,
  add column if not exists pricing_status text,
  add column if not exists pricing_snapshot jsonb,
  add column if not exists shared_session_code text;

create index if not exists idx_bookings_price_channel on public.bookings(price_channel);
create index if not exists idx_bookings_b2b_partner on public.bookings(b2b_partner_id);
create index if not exists idx_bookings_package_code on public.bookings(package_code);

-- ---------------------------------------------------------------------------
-- D. Sales-safe package catalog.
-- No internal HPP/profit/margin columns are exposed.
-- ---------------------------------------------------------------------------
create or replace view public.v_sales_commercial_catalog_v226
with (security_invoker=true)
as
select
  p.id as program_id,
  p.slug as program_slug,
  p.name as program_name,
  p.category,
  p.marketing_start_price,
  p.marketing_price_note,
  p.sort_order as program_sort_order,
  pp.id as package_id,
  pp.package_code,
  pp.name as package_name,
  pp.description as package_description,
  pp.price_per_pax as base_public_price_per_pax,
  pp.min_pax,
  pp.sort_order as package_sort_order
from public.programs p
join public.program_packages pp on pp.program_id=p.id
where p.is_active=true
  and pp.is_active=true
  and pp.status='ACTIVE';

grant select on public.v_sales_commercial_catalog_v226 to authenticated;

create or replace view public.v_sales_b2b_partners_v226
with (security_invoker=true)
as
select distinct on (p.id)
  p.id,
  p.partner_code,
  p.name,
  p.partner_type,
  p.contact_name,
  p.contact_phone,
  a.id as agreement_id,
  a.agreement_type,
  a.agreement_no,
  a.start_date,
  a.end_date
from public.b2b_partners p
join public.b2b_partner_agreements a on a.partner_id=p.id
where p.status='ACTIVE'
  and a.status='ACTIVE'
  and a.start_date <= current_date
  and (a.end_date is null or a.end_date >= current_date)
order by p.id,a.start_date desc;

grant select on public.v_sales_b2b_partners_v226 to authenticated;

-- ---------------------------------------------------------------------------
-- E. Sales-safe unified price resolver.
-- It computes margin internally only to enforce the guardrail; the margin number
-- is NOT returned to Sales.
-- ---------------------------------------------------------------------------
create or replace function public.resolve_sales_price_v226(
  p_package_code text,
  p_booking_pax integer,
  p_channel text default 'DIRECT_PUBLIC',
  p_partner_id uuid default null,
  p_session_type text default 'PRIVATE',
  p_pricing_pax integer default null,
  p_as_of date default current_date
) returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_pkg public.program_packages%rowtype;
  v_program public.programs%rowtype;
  v_tier public.program_package_price_tiers%rowtype;
  v_safe jsonb;
  v_channel text := upper(coalesce(p_channel,'DIRECT_PUBLIC'));
  v_session text := upper(coalesce(p_session_type,'PRIVATE'));
  v_pricing_pax integer := coalesce(p_pricing_pax,p_booking_pax);
  v_partner_ok boolean := false;
  v_customer_price numeric := 0;
  v_gmu_net numeric := 0;
  v_cashback numeric := 0;
  v_partner_margin numeric := 0;
  v_sales_commission numeric := 0;
  v_event_hpp numeric := 0;
  v_total_cost numeric := 0;
  v_margin numeric := 0;
  v_status text := 'SAFE';
  v_reason text := '';
begin
  if coalesce(p_booking_pax,0) <= 0 or coalesce(v_pricing_pax,0) <= 0 then
    return jsonb_build_object('eligible',false,'status','BLOCKED','reason','PAX_INVALID');
  end if;

  select * into v_pkg
  from public.program_packages
  where package_code=p_package_code and is_active=true and status='ACTIVE'
  limit 1;

  if not found then
    return jsonb_build_object('eligible',false,'status','BLOCKED','reason','PACKAGE_NOT_ACTIVE');
  end if;

  select * into v_program from public.programs where id=v_pkg.program_id limit 1;

  if p_booking_pax < v_pkg.min_pax then
    return jsonb_build_object(
      'eligible',false,'status','BLOCKED','reason','MIN_BOOKING_PAX_NOT_MET','min_pax',v_pkg.min_pax
    );
  end if;

  -- Station 2026 uses the finalized tier master.
  if p_package_code='STATION-PROF-2026' then
    if v_channel='DIRECT_PUBLIC' then
      if v_session not in ('PRIVATE','SHARED') then
        return jsonb_build_object('eligible',false,'status','BLOCKED','reason','SESSION_TYPE_INVALID');
      end if;

      if v_session='PRIVATE' then
        -- Private pricing uses the actual school booking pax.
        v_pricing_pax := p_booking_pax;
      elsif v_pricing_pax < 40 then
        return jsonb_build_object(
          'eligible',false,'status','BLOCKED','reason','SHARED_SESSION_MIN_40','required_pricing_pax',40
        );
      end if;

      select * into v_tier
      from public.program_package_price_tiers t
      where t.package_code=p_package_code
        and t.channel='DIRECT_PUBLIC'
        and t.session_type=v_session
        and t.is_active=true
        and t.min_pricing_pax <= v_pricing_pax
        and (t.max_pricing_pax is null or t.max_pricing_pax >= v_pricing_pax)
        and t.effective_from <= p_as_of
        and (t.effective_until is null or t.effective_until >= p_as_of)
      order by t.min_pricing_pax desc,t.effective_from desc
      limit 1;

      if not found then
        return jsonb_build_object('eligible',false,'status','BLOCKED','reason','PUBLIC_TIER_NOT_CONFIGURED');
      end if;

      v_customer_price := v_tier.unit_price;
      v_gmu_net := v_tier.unit_price;
      v_cashback := 0;
      v_partner_margin := 0;
      v_sales_commission := v_tier.sales_commission_per_pax;

    elsif v_channel='B2B_MOU' then
      if p_partner_id is null then
        return jsonb_build_object('eligible',false,'status','BLOCKED','reason','B2B_PARTNER_REQUIRED');
      end if;

      select exists(
        select 1
        from public.b2b_partners p
        join public.b2b_partner_agreements a on a.partner_id=p.id
        where p.id=p_partner_id
          and p.status='ACTIVE'
          and a.status='ACTIVE'
          and a.start_date <= p_as_of
          and (a.end_date is null or a.end_date >= p_as_of)
      ) into v_partner_ok;

      if not v_partner_ok then
        return jsonb_build_object('eligible',false,'status','BLOCKED','reason','ACTIVE_MOU_PKS_REQUIRED');
      end if;

      if v_pricing_pax < 40 then
        return jsonb_build_object('eligible',false,'status','BLOCKED','reason','B2B_MIN_40','required_pricing_pax',40);
      end if;

      select * into v_tier
      from public.program_package_price_tiers t
      where t.package_code=p_package_code
        and t.channel='B2B_MOU'
        and t.session_type='ANY'
        and t.is_active=true
        and t.min_pricing_pax <= v_pricing_pax
        and (t.max_pricing_pax is null or t.max_pricing_pax >= v_pricing_pax)
        and t.effective_from <= p_as_of
        and (t.effective_until is null or t.effective_until >= p_as_of)
      order by t.min_pricing_pax desc,t.effective_from desc
      limit 1;

      if not found then
        return jsonb_build_object('eligible',false,'status','BLOCKED','reason','B2B_TIER_NOT_CONFIGURED');
      end if;

      v_customer_price := 49500;
      v_gmu_net := v_tier.unit_price;
      v_cashback := v_tier.school_cashback_per_pax;
      v_partner_margin := greatest(v_customer_price-v_gmu_net-v_cashback,0);
      v_sales_commission := v_tier.sales_commission_per_pax;
    else
      return jsonb_build_object('eligible',false,'status','BLOCKED','reason','CHANNEL_INVALID');
    end if;

    -- Conservative station cost model finalized by GMU.
    -- Event base: 485K <=40; TL/Ops tier increments thereafter.
    v_event_hpp := case
      when v_pricing_pax <= 40 then 485000
      when v_pricing_pax <= 60 then 535000
      when v_pricing_pax <= 80 then 585000
      when v_pricing_pax <= 100 then 635000
      when v_pricing_pax <= 120 then 685000
      else 685000 + ceil((v_pricing_pax-120)::numeric/20::numeric)*50000
    end;

    -- Variable HPP GMU = Sales 5K + Snack 2.5K + Certificate 0.5K + Worksheet 0.5K = 8.5K/pax.
    -- Monthly fixed allocation baseline = 13K/pax at 200 pax/month.
    -- School cashback is outside GMU HPP in B2B because it is paid from partner spread.
    v_total_cost := v_event_hpp + (8500*v_pricing_pax) + (13000*v_pricing_pax);
    v_margin := case when v_gmu_net*v_pricing_pax > 0
      then ((v_gmu_net*v_pricing_pax-v_total_cost)/(v_gmu_net*v_pricing_pax))*100
      else -999 end;

    if v_margin < 25 then
      v_status := 'REVIEW';
      v_reason := 'MARGIN_BELOW_25_PERCENT';
    end if;

    return jsonb_build_object(
      'eligible',true,
      'status',v_status,
      'reason',v_reason,
      'program_name',v_program.name,
      'package_code',p_package_code,
      'package_name',v_pkg.name,
      'channel',v_channel,
      'session_type',v_session,
      'booking_pax',p_booking_pax,
      'pricing_pax',v_pricing_pax,
      'customer_price_per_pax',round(v_customer_price,2),
      'gmu_net_price_per_pax',round(v_gmu_net,2),
      'school_cashback_per_pax',round(v_cashback,2),
      'partner_margin_per_pax',round(v_partner_margin,2),
      'sales_commission_per_pax',round(v_sales_commission,2),
      'customer_total',round(v_customer_price*p_booking_pax,2),
      'gmu_net_total',round(v_gmu_net*p_booking_pax,2),
      'school_cashback_total',round(v_cashback*p_booking_pax,2),
      'partner_margin_total',round(v_partner_margin*p_booking_pax,2),
      'sales_commission_total',round(v_sales_commission*p_booking_pax,2),
      'approval_required',v_status='REVIEW',
      'required_role',case when v_status='REVIEW' then 'OWNER_MANAGER' else '' end
    );
  end if;

  -- Other packages continue through the existing v22.4 resolver.
  v_safe := public.resolve_program_package_price(
    p_package_code,
    p_booking_pax,
    case when v_channel='B2B_MOU' then 'B2B_MOU' else 'DIRECT_PUBLIC' end,
    p_partner_id,
    p_as_of
  );

  return jsonb_build_object(
    'eligible',coalesce((v_safe->>'eligible')::boolean,false),
    'status',coalesce(v_safe->>'status','BLOCKED'),
    'reason',coalesce(v_safe->>'reason',''),
    'program_name',v_program.name,
    'package_code',p_package_code,
    'package_name',v_pkg.name,
    'channel',v_channel,
    'session_type',v_session,
    'booking_pax',p_booking_pax,
    'pricing_pax',p_booking_pax,
    'customer_price_per_pax',coalesce((v_safe->>'customer_resale_price_per_pax')::numeric,0),
    'gmu_net_price_per_pax',coalesce((v_safe->>'price_per_pax')::numeric,0),
    'school_cashback_per_pax',0,
    'partner_margin_per_pax',coalesce((v_safe->>'partner_markup_per_pax')::numeric,0),
    'sales_commission_per_pax',0,
    'customer_total',coalesce((v_safe->>'customer_resale_price_per_pax')::numeric,0)*p_booking_pax,
    'gmu_net_total',coalesce((v_safe->>'price_per_pax')::numeric,0)*p_booking_pax,
    'school_cashback_total',0,
    'partner_margin_total',coalesce((v_safe->>'partner_markup_per_pax')::numeric,0)*p_booking_pax,
    'sales_commission_total',0,
    'approval_required',coalesce((v_safe->>'approval_required')::boolean,false),
    'required_role',coalesce(v_safe->>'required_role','')
  );
end;
$$;

revoke all on function public.resolve_sales_price_v226(text,integer,text,uuid,text,integer,date) from public;
grant execute on function public.resolve_sales_price_v226(text,integer,text,uuid,text,integer,date) to authenticated;

comment on function public.resolve_sales_price_v226(text,integer,text,uuid,text,integer,date) is
'Sales-safe unified PUBLIC/B2B price resolver. Does not expose GMU HPP, profit or margin values.';

-- ---------------------------------------------------------------------------
-- F. Server-side booking creation. Price is resolved on server, never trusted
-- from an Android text field.
-- ---------------------------------------------------------------------------
create or replace function public.create_sales_booking_v226(
  p_customer_id uuid,
  p_package_code text,
  p_trip_date date,
  p_booking_pax integer,
  p_channel text default 'DIRECT_PUBLIC',
  p_partner_id uuid default null,
  p_session_type text default 'PRIVATE',
  p_pricing_pax integer default null,
  p_status text default 'Lead',
  p_participant_group text default null,
  p_meeting_point text default null,
  p_shared_session_code text default null
) returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_uid uuid := (select auth.uid());
  v_role text;
  v_price jsonb;
  v_booking_no text;
  v_booking_id uuid;
  v_program_name text;
  v_gmu_net numeric;
  v_customer_price numeric;
  v_cashback numeric;
  v_partner_margin numeric;
  v_sales_commission numeric;
  v_status text;
begin
  if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;

  select role::text into v_role
  from public.profiles
  where id=v_uid and is_active=true
  limit 1;

  if coalesce(v_role,'') not in ('Owner','Director','Direktur','Manager','Manager EduTrans','Admin','Sales') then
    raise exception 'BOOKING_CREATE_NOT_ALLOWED';
  end if;

  v_price := public.resolve_sales_price_v226(
    p_package_code,p_booking_pax,upper(p_channel),p_partner_id,upper(p_session_type),p_pricing_pax,p_trip_date
  );

  v_status := coalesce(v_price->>'status','BLOCKED');
  if v_status='BLOCKED' or not coalesce((v_price->>'eligible')::boolean,false) then
    raise exception 'PRICING_BLOCKED:%',coalesce(v_price->>'reason','UNKNOWN');
  end if;

  if v_status='REVIEW' and coalesce(v_role,'') not in ('Owner','Director','Direktur','Manager','Manager EduTrans') then
    raise exception 'OWNER_MANAGER_APPROVAL_REQUIRED';
  end if;

  v_booking_no := public.next_booking_no();
  v_program_name := coalesce(v_price->>'program_name','Program GMU EduTrans');
  v_gmu_net := coalesce((v_price->>'gmu_net_price_per_pax')::numeric,0);
  v_customer_price := coalesce((v_price->>'customer_price_per_pax')::numeric,0);
  v_cashback := coalesce((v_price->>'school_cashback_per_pax')::numeric,0);
  v_partner_margin := coalesce((v_price->>'partner_margin_per_pax')::numeric,0);
  v_sales_commission := coalesce((v_price->>'sales_commission_per_pax')::numeric,0);

  insert into public.bookings(
    booking_no,customer_id,sales_id,program_name,trip_date,pax,price_per_pax,status,
    participant_group,meeting_point,created_by,package_code,price_channel,session_type,
    pricing_pax,b2b_partner_id,customer_sell_price_per_pax,school_cashback_per_pax,
    partner_margin_per_pax,sales_commission_per_pax,pricing_status,pricing_snapshot,
    shared_session_code
  ) values (
    v_booking_no,p_customer_id,case when v_role='Sales' then v_uid else null end,
    v_program_name,p_trip_date,p_booking_pax,v_gmu_net,p_status,
    nullif(trim(coalesce(p_participant_group,'')),''),
    nullif(trim(coalesce(p_meeting_point,'')),''),v_uid,p_package_code,upper(p_channel),
    upper(p_session_type),coalesce(p_pricing_pax,p_booking_pax),p_partner_id,
    v_customer_price,v_cashback,v_partner_margin,v_sales_commission,v_status,v_price,
    nullif(trim(coalesce(p_shared_session_code,'')),'')
  ) returning id into v_booking_id;

  return jsonb_build_object(
    'ok',true,
    'booking_id',v_booking_id,
    'booking_no',v_booking_no,
    'pricing',v_price
  );
end;
$$;

revoke all on function public.create_sales_booking_v226(uuid,text,date,integer,text,uuid,text,integer,text,text,text,text) from public;
grant execute on function public.create_sales_booking_v226(uuid,text,date,integer,text,uuid,text,integer,text,text,text,text) to authenticated;

comment on function public.create_sales_booking_v226(uuid,text,date,integer,text,uuid,text,integer,text,text,text,text) is
'Creates Sales booking using server-resolved master pricing. price_per_pax stores GMU recognized rate; customer sell price and B2B economics are snapshotted separately.';
