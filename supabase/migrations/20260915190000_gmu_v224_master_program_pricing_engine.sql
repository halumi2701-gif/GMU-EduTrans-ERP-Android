-- GMU EduTrans v22.4 — Master Program & Pricing Engine Integration
-- Goals:
-- 1) one canonical six-program master,
-- 2) separate marketing "mulai" price from bookable package price,
-- 3) B2B net pricing only for active MoU/PKS partners,
-- 4) hard 25% minimum margin guard,
-- 5) public-price parity for end customers,
-- 6) reusable resolver for Quotation / Booking / Finance / Operations.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- A. Program master: marketing price is NOT automatically a bookable price.
-- ---------------------------------------------------------------------------
alter table public.programs
  add column if not exists marketing_start_price numeric(16,2),
  add column if not exists marketing_price_note text;

-- Historical program is retained for referential integrity, but removed from active portfolio.
update public.programs
set is_active=false
where slug='company-factory-visit';

-- 1) Edukasi di Atas Kereta Api
update public.programs
set name='Edukasi di Atas Kereta Api',
    category='Railway Education',
    short_description='Program edukasi selama perjalanan kereta api: pengenalan fasilitas, etika, keselamatan, dan pengalaman perjalanan edukatif.',
    min_pax=20,
    marketing_start_price=29000,
    marketing_price_note='Harga mulai untuk materi marketing. Paket yang dapat dikutip mengikuti Package Master dan Margin Guard.',
    is_active=true,
    sort_order=10
where slug='edukasi-di-atas-kereta';

-- 2) Edukasi Lingkungan & Profesi Stasiun
update public.programs
set name='Edukasi Lingkungan & Profesi Stasiun',
    category='Railway Education',
    short_description='Pengenalan lingkungan stasiun, alur perjalanan, keselamatan, fasilitas, dan profesi perkeretaapian.',
    min_pax=20,
    marketing_start_price=46000,
    marketing_price_note='Paket dasar aktif minimum 20 peserta.',
    is_active=true,
    sort_order=20
where slug='edukasi-profesi-lingkungan-stasiun';

-- 3) Edukasi Bertani Padi Pandanwangi
update public.programs
set name='Edukasi Bertani Padi Pandanwangi',
    category='Agro Education',
    short_description='Edukasi Padi Pandanwangi, lingkungan persawahan, proses pertanian, dan praktik aktivitas pertanian.',
    min_pax=1,
    marketing_start_price=75000,
    marketing_price_note='Harga mulai; minimum dan HPP final mengikuti paket/quotation aktif.',
    is_active=true,
    sort_order=30
where slug='edukasi-padi-pandanwangi';

-- 4) Edukasi Membatik
update public.programs
set name='Edukasi Membatik',
    category='Creative Education',
    short_description='Teori dan praktik cap/nyanting dengan pilihan Basic, Regular, Experience, dan Full Experience.',
    min_pax=20,
    marketing_start_price=99000,
    marketing_price_note='Rp99.000 hanya Special School / Large Group Rate by quotation. Paket reguler minimum 20 peserta mengikuti Package Master.',
    is_active=true,
    sort_order=40
where slug='edukasi-membatik';

-- 5) Edukasi Melukis
insert into public.programs(slug,name,category,short_description,min_pax,is_active,sort_order,marketing_start_price,marketing_price_note)
select 'edukasi-melukis','Edukasi Melukis','Creative Education',
       'Pengenalan dasar seni, praktik melukis, media kegiatan, pendampingan, dan hasil karya peserta.',
       1,true,50,85000,
       'Harga mulai; media, minimum peserta, konsumsi, dan fasilitas tambahan mengikuti paket/quotation aktif.'
where not exists (select 1 from public.programs where slug='edukasi-melukis');

update public.programs
set name='Edukasi Melukis',
    category='Creative Education',
    short_description='Pengenalan dasar seni, praktik melukis, media kegiatan, pendampingan, dan hasil karya peserta.',
    marketing_start_price=85000,
    marketing_price_note='Harga mulai; media, minimum peserta, konsumsi, dan fasilitas tambahan mengikuti paket/quotation aktif.',
    is_active=true,
    sort_order=50
where slug='edukasi-melukis';

-- 6) Private & Custom EduTrip
update public.programs
set name='Private & Custom EduTrip',
    category='Custom EduTrip',
    short_description='Program edukasi custom sesuai kebutuhan customer: itinerary, transportasi, ticketing, konsumsi, TL, narasumber, dokumentasi, dan fasilitas lain.',
    min_pax=1,
    marketing_start_price=null,
    marketing_price_note='By Quotation',
    is_active=true,
    sort_order=60
where slug='custom-visit-edutrip';

-- ---------------------------------------------------------------------------
-- B. Bookable public packages.
-- Marketing start price remains independent and cannot bypass Package Master.
-- ---------------------------------------------------------------------------
do $$
declare
  v_station uuid;
  v_batik uuid;
begin
  select id into v_station from public.programs where slug='edukasi-profesi-lingkungan-stasiun' limit 1;
  select id into v_batik from public.programs where slug='edukasi-membatik' limit 1;

  if v_station is null or v_batik is null then
    raise exception 'v22.4 requires station and batik program master rows';
  end if;

  -- Station 46K / pax, minimum 20.
  update public.program_packages set
    program_id=v_station,
    name='Paket Edukasi Lingkungan & Profesi Stasiun',
    description='Durasi sekitar 2 jam. Pengenalan lingkungan/fasilitas stasiun, keselamatan, alur perjalanan, profesi perkeretaapian, aktivitas edukatif, dan pendampingan GMU EduTrans.',
    price_per_pax=46000,
    min_pax=20,
    status='ACTIVE',
    is_active=true,
    sort_order=10
  where package_code='STATION-46';
  if not found then
    insert into public.program_packages(package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order)
    values('STATION-46',v_station,'Paket Edukasi Lingkungan & Profesi Stasiun',
      'Durasi sekitar 2 jam. Pengenalan lingkungan/fasilitas stasiun, keselamatan, alur perjalanan, profesi perkeretaapian, aktivitas edukatif, dan pendampingan GMU EduTrans.',
      46000,20,'ACTIVE',true,10);
  end if;

  -- Batik public prices. Rp99K is intentionally NOT inserted as a standard package.
  update public.program_packages set program_id=v_batik,name='Basic Kids',
    description='Teori, praktik cap/nyanting, snack box, sertifikat, pendampingan kegiatan, dan tim operasional GMU EduTrans.',
    price_per_pax=140000,min_pax=20,status='ACTIVE',is_active=true,sort_order=10
  where package_code='BATIK-BASIC-140';
  if not found then
    insert into public.program_packages(package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order)
    values('BATIK-BASIC-140',v_batik,'Basic Kids','Teori, praktik cap/nyanting, snack box, sertifikat, pendampingan kegiatan, dan tim operasional GMU EduTrans.',140000,20,'ACTIVE',true,10);
  end if;

  update public.program_packages set program_id=v_batik,name='Regular',
    description='Teori, praktik cap/nyanting, snack box, nasi box, sertifikat, pendampingan kegiatan, dan tim operasional GMU EduTrans.',
    price_per_pax=175000,min_pax=20,status='ACTIVE',is_active=true,sort_order=20
  where package_code='BATIK-REGULAR-175';
  if not found then
    insert into public.program_packages(package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order)
    values('BATIK-REGULAR-175',v_batik,'Regular','Teori, praktik cap/nyanting, snack box, nasi box, sertifikat, pendampingan kegiatan, dan tim operasional GMU EduTrans.',175000,20,'ACTIVE',true,20);
  end if;

  update public.program_packages set program_id=v_batik,name='Experience 30x30 cm',
    description='Teori, praktik cap/nyanting, snack box, hasil karya 30 x 30 cm, sertifikat, pendampingan kegiatan, dan tim operasional GMU EduTrans.',
    price_per_pax=245000,min_pax=20,status='ACTIVE',is_active=true,sort_order=30
  where package_code='BATIK-EXPERIENCE-245';
  if not found then
    insert into public.program_packages(package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order)
    values('BATIK-EXPERIENCE-245',v_batik,'Experience 30x30 cm','Teori, praktik cap/nyanting, snack box, hasil karya 30 x 30 cm, sertifikat, pendampingan kegiatan, dan tim operasional GMU EduTrans.',245000,20,'ACTIVE',true,30);
  end if;

  update public.program_packages set program_id=v_batik,name='Full Experience 210x115 cm',
    description='Teori, praktik cap/nyanting, snack box, hasil karya 210 x 115 cm, sertifikat, pendampingan kegiatan, dan tim operasional GMU EduTrans.',
    price_per_pax=495000,min_pax=20,status='ACTIVE',is_active=true,sort_order=40
  where package_code='BATIK-FULL-495';
  if not found then
    insert into public.program_packages(package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order)
    values('BATIK-FULL-495',v_batik,'Full Experience 210x115 cm','Teori, praktik cap/nyanting, snack box, hasil karya 210 x 115 cm, sertifikat, pendampingan kegiatan, dan tim operasional GMU EduTrans.',495000,20,'ACTIVE',true,40);
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- C. Internal economics. Hidden from non-financial roles by existing economics RLS.
-- Batik facility HPP includes vendor + Rp50K crew meal at baseline 20 pax.
-- Vendor cashback is a participant/panitia benefit, never company profit.
-- ---------------------------------------------------------------------------
insert into public.program_package_economics(
  package_code,program_slug,package_name,baseline_pax,facility_hpp_locked,
  sales_fee_baseline,partner_fee_baseline,manager_fee_baseline,tl_tutor_fee_baseline,
  ops_documentation_fee_baseline,total_cost_baseline,trip_contribution_baseline,
  contribution_margin_pct,facility_hpp_is_locked,is_best_seller,crew_scaling_policy,notes
) values
('STATION-46','edukasi-profesi-lingkungan-stasiun','Paket Edukasi Lingkungan & Profesi Stasiun',20,
 290000,50000,50000,130000,110000,55000,685000,235000,25.543,true,true,'TIER_MANUAL_ABOVE_BASELINE',
 'HPP baseline: Kepala Stasiun 50K; 6 narasumber @20K; sertifikat 10K; worksheet 10K; snack 50K; makan crew 50K; Manager 130K; TL 110K; Ops/Dok 55K; Sales 50K; Mitra 50K.'),
('BATIK-BASIC-140','edukasi-membatik','Basic Kids',20,
 1550000,50000,0,130000,110000,55000,1895000,905000,32.321,true,true,'BASELINE_VARIABLE_VENDOR',
 'Vendor 75K/pax x20 + crew meal 50K. Cashback vendor 15K/pax adalah benefit panitia, bukan pendapatan/pengurang HPP GMU.'),
('BATIK-REGULAR-175','edukasi-membatik','Regular',20,
 2050000,50000,0,130000,110000,55000,2395000,1105000,31.571,true,false,'BASELINE_VARIABLE_VENDOR',
 'Vendor 100K/pax x20 + crew meal 50K. Cashback vendor 20K/pax adalah benefit panitia.'),
('BATIK-EXPERIENCE-245','edukasi-membatik','Experience 30x30 cm',20,
 3050000,50000,0,130000,110000,55000,3395000,1505000,30.714,true,false,'BASELINE_VARIABLE_VENDOR',
 'Vendor 150K/pax x20 + crew meal 50K. Cashback vendor 30K/pax adalah benefit panitia.'),
('BATIK-FULL-495','edukasi-membatik','Full Experience 210x115 cm',20,
 6550000,50000,0,130000,110000,55000,6895000,3005000,30.354,true,false,'BASELINE_VARIABLE_VENDOR',
 'Vendor 325K/pax x20 + crew meal 50K. Cashback vendor 50K/pax adalah benefit panitia.')
on conflict (package_code) do update set
  program_slug=excluded.program_slug,
  package_name=excluded.package_name,
  baseline_pax=excluded.baseline_pax,
  facility_hpp_locked=excluded.facility_hpp_locked,
  sales_fee_baseline=excluded.sales_fee_baseline,
  partner_fee_baseline=excluded.partner_fee_baseline,
  manager_fee_baseline=excluded.manager_fee_baseline,
  tl_tutor_fee_baseline=excluded.tl_tutor_fee_baseline,
  ops_documentation_fee_baseline=excluded.ops_documentation_fee_baseline,
  total_cost_baseline=excluded.total_cost_baseline,
  trip_contribution_baseline=excluded.trip_contribution_baseline,
  contribution_margin_pct=excluded.contribution_margin_pct,
  facility_hpp_is_locked=true,
  is_best_seller=excluded.is_best_seller,
  crew_scaling_policy=excluded.crew_scaling_policy,
  notes=excluded.notes,
  updated_at=now();

-- ---------------------------------------------------------------------------
-- D. B2B partner + MoU/PKS registry.
-- ---------------------------------------------------------------------------
create table if not exists public.b2b_partners (
  id uuid primary key default gen_random_uuid(),
  partner_code text not null unique,
  name text not null,
  partner_type text not null check (partner_type in ('ORGANIZATION','TRAVEL','EO','YAYASAN','SCHOOL_NETWORK','CORPORATE','OTHER')),
  status text not null default 'ACTIVE' check (status in ('ACTIVE','INACTIVE','SUSPENDED')),
  contact_name text,
  contact_phone text,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.b2b_partner_agreements (
  id uuid primary key default gen_random_uuid(),
  partner_id uuid not null references public.b2b_partners(id) on delete cascade,
  agreement_type text not null default 'MOU' check (agreement_type in ('MOU','PKS')),
  agreement_no text,
  start_date date not null,
  end_date date,
  status text not null default 'DRAFT' check (status in ('DRAFT','ACTIVE','EXPIRED','TERMINATED')),
  public_price_parity_required boolean not null default true,
  min_term_months integer not null default 6 check (min_term_months >= 1),
  volume_commitment_pax integer,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (end_date is null or end_date >= start_date)
);

create index if not exists idx_b2b_agreement_partner_status
  on public.b2b_partner_agreements(partner_id,status,start_date,end_date);

alter table public.b2b_partners enable row level security;
alter table public.b2b_partner_agreements enable row level security;

drop policy if exists b2b_partners_staff_read on public.b2b_partners;
create policy b2b_partners_staff_read on public.b2b_partners
for select to authenticated using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid()) and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales')
  )
);

drop policy if exists b2b_agreements_staff_read on public.b2b_partner_agreements;
create policy b2b_agreements_staff_read on public.b2b_partner_agreements
for select to authenticated using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid()) and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales')
  )
);

grant select on public.b2b_partners, public.b2b_partner_agreements to authenticated;

-- ---------------------------------------------------------------------------
-- E. Channel pricing. B2B rate is NET to GMU; partner resells at public GMU price.
-- No extra partner commission is embedded in B2B net rates.
-- ---------------------------------------------------------------------------
create table if not exists public.program_package_channel_prices (
  id uuid primary key default gen_random_uuid(),
  package_code text not null,
  channel text not null check (channel in ('B2B_MOU','STRATEGIC_B2B')),
  net_price_per_pax numeric(16,2) not null check (net_price_per_pax > 0),
  min_pax integer not null default 20 check (min_pax > 0),
  effective_from date not null default current_date,
  effective_until date,
  is_active boolean not null default true,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(package_code,channel,effective_from),
  check (effective_until is null or effective_until >= effective_from)
);

insert into public.program_package_channel_prices(package_code,channel,net_price_per_pax,min_pax,effective_from,is_active,notes)
values
('BATIK-BASIC-140','B2B_MOU',130000,20,date '2026-09-15',true,'Partner wajib MoU/PKS aktif. Harga ke konsumen akhir tetap harga publik GMU Rp140.000/pax.'),
('BATIK-REGULAR-175','B2B_MOU',160000,20,date '2026-09-15',true,'Partner wajib MoU/PKS aktif. Harga ke konsumen akhir tetap harga publik GMU Rp175.000/pax.'),
('BATIK-EXPERIENCE-245','B2B_MOU',230000,20,date '2026-09-15',true,'Partner wajib MoU/PKS aktif. Harga ke konsumen akhir tetap harga publik GMU Rp245.000/pax.'),
('BATIK-FULL-495','B2B_MOU',460000,20,date '2026-09-15',true,'Partner wajib MoU/PKS aktif. Harga ke konsumen akhir tetap harga publik GMU Rp495.000/pax.')
on conflict (package_code,channel,effective_from) do update set
  net_price_per_pax=excluded.net_price_per_pax,
  min_pax=excluded.min_pax,
  is_active=true,
  notes=excluded.notes,
  updated_at=now();

alter table public.program_package_channel_prices enable row level security;
drop policy if exists program_channel_prices_staff_read on public.program_package_channel_prices;
create policy program_channel_prices_staff_read on public.program_package_channel_prices
for select to authenticated using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid()) and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales')
  )
);
grant select on public.program_package_channel_prices to authenticated;

-- ---------------------------------------------------------------------------
-- F. Global commercial guardrail: minimum margin is 25%.
-- ---------------------------------------------------------------------------
create table if not exists public.program_pricing_guardrails (
  guardrail_key text primary key,
  target_margin_pct numeric(8,3) not null default 25,
  floor_margin_pct numeric(8,3) not null default 25,
  b2b_requires_active_agreement boolean not null default true,
  b2b_public_price_parity boolean not null default true,
  below_floor_action text not null default 'OWNER_MANAGER_APPROVAL' check (below_floor_action in ('BLOCK','OWNER_MANAGER_APPROVAL')),
  notes text,
  updated_at timestamptz not null default now()
);

insert into public.program_pricing_guardrails(
  guardrail_key,target_margin_pct,floor_margin_pct,b2b_requires_active_agreement,b2b_public_price_parity,below_floor_action,notes
) values (
  'GLOBAL',25,25,true,true,'OWNER_MANAGER_APPROVAL',
  'Margin GMU minimal 25%. B2B hanya untuk partner dengan MoU/PKS aktif. Partner menjual kepada konsumen/anggota pada harga publik GMU; selisih net B2B menjadi margin partner.'
)
on conflict (guardrail_key) do update set
  target_margin_pct=25,
  floor_margin_pct=25,
  b2b_requires_active_agreement=true,
  b2b_public_price_parity=true,
  below_floor_action='OWNER_MANAGER_APPROVAL',
  notes=excluded.notes,
  updated_at=now();

alter table public.program_pricing_guardrails enable row level security;
drop policy if exists pricing_guardrails_staff_read on public.program_pricing_guardrails;
create policy pricing_guardrails_staff_read on public.program_pricing_guardrails
for select to authenticated using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid()) and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales')
  )
);
grant select on public.program_pricing_guardrails to authenticated;

-- ---------------------------------------------------------------------------
-- G. Customer-safe resolver. It returns SAFE/REVIEW/BLOCKED but never HPP/laba.
-- Quotation can call this before applying a package/channel price.
-- ---------------------------------------------------------------------------
create or replace function public.resolve_program_package_price(
  p_package_code text,
  p_pax integer,
  p_channel text default 'DIRECT_PUBLIC',
  p_partner_id uuid default null,
  p_as_of date default current_date
) returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_pkg public.program_packages%rowtype;
  v_econ public.program_package_economics%rowtype;
  v_guard public.program_pricing_guardrails%rowtype;
  v_price numeric;
  v_public numeric;
  v_cost numeric;
  v_margin numeric;
  v_blocks integer;
  v_partner_ok boolean := false;
  v_status text := 'SAFE';
  v_reason text := '';
begin
  if coalesce(p_pax,0) <= 0 then
    return jsonb_build_object('eligible',false,'status','BLOCKED','reason','PAX_INVALID');
  end if;

  select * into v_pkg from public.program_packages
  where package_code=p_package_code and is_active=true and status='ACTIVE' limit 1;
  if not found then
    return jsonb_build_object('eligible',false,'status','BLOCKED','reason','PACKAGE_NOT_ACTIVE');
  end if;

  if p_pax < v_pkg.min_pax then
    return jsonb_build_object('eligible',false,'status','BLOCKED','reason','MIN_PAX_NOT_MET','min_pax',v_pkg.min_pax);
  end if;

  select * into v_guard from public.program_pricing_guardrails where guardrail_key='GLOBAL';
  v_public := v_pkg.price_per_pax;

  if p_channel='B2B_MOU' then
    if p_partner_id is null then
      return jsonb_build_object('eligible',false,'status','BLOCKED','reason','B2B_PARTNER_REQUIRED');
    end if;

    select exists(
      select 1
      from public.b2b_partners p
      join public.b2b_partner_agreements a on a.partner_id=p.id
      where p.id=p_partner_id and p.status='ACTIVE' and a.status='ACTIVE'
        and a.start_date <= p_as_of and (a.end_date is null or a.end_date >= p_as_of)
    ) into v_partner_ok;

    if not v_partner_ok then
      return jsonb_build_object('eligible',false,'status','BLOCKED','reason','ACTIVE_MOU_PKS_REQUIRED');
    end if;

    select cp.net_price_per_pax into v_price
    from public.program_package_channel_prices cp
    where cp.package_code=p_package_code and cp.channel='B2B_MOU' and cp.is_active=true
      and cp.min_pax <= p_pax
      and cp.effective_from <= p_as_of
      and (cp.effective_until is null or cp.effective_until >= p_as_of)
    order by cp.effective_from desc limit 1;

    if v_price is null then
      return jsonb_build_object('eligible',false,'status','BLOCKED','reason','B2B_RATE_NOT_CONFIGURED','public_price_per_pax',v_public);
    end if;
  elsif p_channel in ('DIRECT_PUBLIC','GROUP','REFERRAL') then
    v_price := v_public;
  else
    return jsonb_build_object('eligible',false,'status','BLOCKED','reason','CHANNEL_REQUIRES_CUSTOM_QUOTATION');
  end if;

  select * into v_econ from public.program_package_economics where package_code=p_package_code;
  if not found then
    return jsonb_build_object(
      'eligible',false,'status','REVIEW','reason','ECONOMICS_NOT_CONFIGURED',
      'channel',p_channel,'price_per_pax',v_price,'public_price_per_pax',v_public,
      'customer_resale_price_per_pax',v_public,'approval_required',true
    );
  end if;

  v_blocks := ceil(p_pax::numeric / greatest(v_econ.baseline_pax,1)::numeric)::integer;
  v_cost :=
      (v_econ.facility_hpp_locked * p_pax::numeric / greatest(v_econ.baseline_pax,1)::numeric)
      + v_econ.manager_fee_baseline
      + v_econ.tl_tutor_fee_baseline
      + v_econ.ops_documentation_fee_baseline
      + (v_econ.sales_fee_baseline * v_blocks)
      + (v_econ.partner_fee_baseline * v_blocks);

  v_margin := case when v_price*p_pax > 0
    then ((v_price*p_pax - v_cost) / (v_price*p_pax))*100
    else -999 end;

  if v_margin < coalesce(v_guard.floor_margin_pct,25) then
    v_status := case when coalesce(v_guard.below_floor_action,'OWNER_MANAGER_APPROVAL')='BLOCK' then 'BLOCKED' else 'REVIEW' end;
    v_reason := 'MARGIN_BELOW_25_PERCENT';
  end if;

  return jsonb_build_object(
    'eligible',v_status <> 'BLOCKED',
    'status',v_status,
    'reason',v_reason,
    'package_code',p_package_code,
    'channel',p_channel,
    'pax',p_pax,
    'price_per_pax',v_price,
    'public_price_per_pax',v_public,
    'customer_resale_price_per_pax',v_public,
    'partner_markup_per_pax',case when p_channel='B2B_MOU' then greatest(v_public-v_price,0) else 0 end,
    'approval_required',v_status='REVIEW',
    'required_role',case when v_status='REVIEW' then 'OWNER_MANAGER' else '' end,
    'price_parity_required',coalesce(v_guard.b2b_public_price_parity,true)
  );
end;
$$;

revoke all on function public.resolve_program_package_price(text,integer,text,uuid,date) from public;
grant execute on function public.resolve_program_package_price(text,integer,text,uuid,date) to authenticated;

-- ---------------------------------------------------------------------------
-- H. Finance-only resolver with internal economics.
-- ---------------------------------------------------------------------------
create or replace function public.resolve_program_package_price_internal(
  p_package_code text,
  p_pax integer,
  p_channel text default 'DIRECT_PUBLIC',
  p_partner_id uuid default null,
  p_as_of date default current_date
) returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  v_role text;
  v_safe jsonb;
  v_econ public.program_package_economics%rowtype;
  v_price numeric;
  v_cost numeric;
  v_margin numeric;
  v_blocks integer;
begin
  select p.role::text into v_role from public.profiles p
  where p.id=(select auth.uid()) and p.is_active=true limit 1;

  if coalesce(v_role,'') not in ('Owner','Director','Direktur','Manager','Manager EduTrans') then
    raise exception 'Financial pricing details are restricted to Owner / Manager';
  end if;

  v_safe := public.resolve_program_package_price(p_package_code,p_pax,p_channel,p_partner_id,p_as_of);
  if coalesce((v_safe->>'price_per_pax')::numeric,0) <= 0 then
    return v_safe;
  end if;

  select * into v_econ from public.program_package_economics where package_code=p_package_code;
  if not found then return v_safe; end if;

  v_price := (v_safe->>'price_per_pax')::numeric;
  v_blocks := ceil(p_pax::numeric / greatest(v_econ.baseline_pax,1)::numeric)::integer;
  v_cost :=
      (v_econ.facility_hpp_locked * p_pax::numeric / greatest(v_econ.baseline_pax,1)::numeric)
      + v_econ.manager_fee_baseline
      + v_econ.tl_tutor_fee_baseline
      + v_econ.ops_documentation_fee_baseline
      + (v_econ.sales_fee_baseline * v_blocks)
      + (v_econ.partner_fee_baseline * v_blocks);
  v_margin := ((v_price*p_pax-v_cost)/(v_price*p_pax))*100;

  return v_safe || jsonb_build_object(
    'estimated_total_cost',round(v_cost,2),
    'estimated_revenue',round(v_price*p_pax,2),
    'estimated_profit',round(v_price*p_pax-v_cost,2),
    'estimated_margin_pct',round(v_margin,3),
    'minimum_margin_pct',25
  );
end;
$$;

revoke all on function public.resolve_program_package_price_internal(text,integer,text,uuid,date) from public;
grant execute on function public.resolve_program_package_price_internal(text,integer,text,uuid,date) to authenticated;

-- ---------------------------------------------------------------------------
-- I. Commercial catalog view for Owner/Manager/Sales. Internal HPP is excluded.
-- ---------------------------------------------------------------------------
create or replace view public.v_program_commercial_catalog
with (security_invoker=true)
as
select
  p.id as program_id,
  p.slug as program_slug,
  p.name as program_name,
  p.category,
  p.marketing_start_price,
  p.marketing_price_note,
  p.min_pax as program_min_pax,
  pp.id as package_id,
  pp.package_code,
  pp.name as package_name,
  pp.description as package_description,
  pp.price_per_pax as public_price_per_pax,
  pp.min_pax as package_min_pax,
  pp.status as package_status,
  pp.is_active as package_active,
  cp.net_price_per_pax as b2b_net_price_per_pax,
  case when cp.net_price_per_pax is not null then greatest(pp.price_per_pax-cp.net_price_per_pax,0) end as partner_markup_per_pax,
  p.sort_order as program_sort_order,
  pp.sort_order as package_sort_order
from public.programs p
left join public.program_packages pp on pp.program_id=p.id and pp.is_active=true
left join lateral (
  select c.net_price_per_pax
  from public.program_package_channel_prices c
  where c.package_code=pp.package_code and c.channel='B2B_MOU' and c.is_active=true
    and c.effective_from <= current_date
    and (c.effective_until is null or c.effective_until >= current_date)
  order by c.effective_from desc limit 1
) cp on true
where p.is_active=true;

grant select on public.v_program_commercial_catalog to authenticated;

comment on function public.resolve_program_package_price(text,integer,text,uuid,date) is
'Customer-safe pricing resolver for Quotation/Booking. B2B requires active MoU/PKS and applies GMU public-price parity. Internal HPP/profit is intentionally hidden.';

comment on function public.resolve_program_package_price_internal(text,integer,text,uuid,date) is
'Owner/Manager-only pricing resolver that adds HPP, profit, and margin estimates to the customer-safe resolution.';
