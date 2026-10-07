-- GMU EduTrans — Owner-approved public pricing sync
-- Effective: 2026-10-07
-- Scope: DIRECT_PUBLIC only. B2B tiers remain unchanged.

begin;

-- Public marketing display.
update public.programs
set marketing_start_price=62000,
    marketing_price_note='Harga publik Paket A Lengkap mulai Rp62.000/pax. Tier: 20–29 Rp65.000; 30–39 Rp63.000; 40+ Rp62.000. Minimum 20 siswa berbayar. Acuan Owner 7 Oktober 2026.',
    updated_at=now()
where slug='edukasi-di-atas-kereta';

update public.programs
set marketing_start_price=49500,
    marketing_price_note='Harga publik: 20–29 Rp65.000/pax; 30–39 Rp59.000/pax; 40+ Rp49.500/pax. Shared Education Rp49.500/pax bila total sesi minimal 40 peserta. Acuan Owner 7 Oktober 2026.',
    updated_at=now()
where slug='edukasi-profesi-lingkungan-stasiun';

-- Pricing policy required by package activation guard.
insert into public.pricing_policies
(scope_type,program_id,target_margin_pct,floor_margin_pct,max_discount_pct,contingency_pct,rounding_increment,effective_from,effective_until,is_active,notes,created_by,updated_by)
select
  'PROGRAM',
  p.id,
  25,25,0,0,1000,
  date '2026-10-07',null,true,
  'Owner public Package A pricing policy 7 Oct 2026. Minimum margin 25%; public tiers controlled in program_package_price_tiers.',
  (select id from public.profiles where role='Owner' and is_active=true order by created_at limit 1),
  (select id from public.profiles where role='Owner' and is_active=true order by created_at limit 1)
from public.programs p
where p.slug='edukasi-di-atas-kereta'
and not exists (
  select 1 from public.pricing_policies x
  where x.scope_type='PROGRAM'
    and x.program_id=p.id
    and x.effective_from=date '2026-10-07'
    and x.is_active=true
);

-- Train Package A cost master (must exist before activating the package).
with ids as (
  select p.id program_id, pp.id package_id
  from public.programs p
  join public.program_packages pp on pp.program_id=p.id
  where p.slug='edukasi-di-atas-kereta'
    and pp.package_code='PKG-GMU-00002'
),
owner_row as (
  select id from public.profiles
  where role='Owner' and is_active=true
  order by created_at limit 1
),
costs(category,description,cost_mode,amount) as (
  values
    ('TICKET','Tiket KA anak PP','PER_PAX',6000::numeric),
    ('TICKET','Alokasi tiket pendamping PP','PER_PAX',6000),
    ('MEALS','Snack peserta','PER_PAX',3000),
    ('MEALS','Minuman peserta','PER_PAX',1500),
    ('MERCHANDISE','Worksheet edukasi','PER_PAX',1000),
    ('MERCHANDISE','Kuis dan hadiah fisik','PER_PAX',2500),
    ('OPERATOR','Fee Manager','FIXED',130000),
    ('FACILITATOR','TL / Edukator','FIXED',100000),
    ('OPERATOR','Operasional & Dokumentasi','FIXED',100000),
    ('OPERATOR','Helper','FIXED',55000),
    ('OTHER','Komisi Sales penjualan langsung','PER_PAX',5000)
)
insert into public.pricing_cost_templates
(scope_type,program_id,package_id,category,description,cost_mode,amount,min_pax,max_pax,effective_from,effective_until,is_active,notes,created_by,updated_by)
select
  'PACKAGE',ids.program_id,ids.package_id,c.category,c.description,c.cost_mode,c.amount,
  20,null,date '2026-10-07',null,true,
  case when c.description='Komisi Sales penjualan langsung'
       then 'Direct public only; no B2B partner commission'
       else 'Owner public Package A 7 Oct 2026' end,
  owner_row.id,owner_row.id
from ids cross join costs c cross join owner_row
where not exists (
  select 1 from public.pricing_cost_templates t
  where t.package_id=ids.package_id
    and t.description=c.description
    and t.effective_from=date '2026-10-07'
);

-- Archive obsolete station price without deleting history.
update public.program_packages
set status='ARCHIVED',
    is_active=false,
    effective_until=date '2026-10-06',
    price_note='Arsip master lama Rp46.000. Digantikan harga publik Owner 7 Okt 2026 pada STATION-PROF-2026.',
    updated_at=now()
where package_code='STATION-46';

-- Current station public package.
update public.program_packages
set name='Edukasi Lingkungan & Profesi Stasiun — Harga Publik 2026',
    price_per_pax=65000,
    min_pax=20,
    status='ACTIVE',
    is_active=true,
    effective_from=date '2026-10-07',
    effective_until=null,
    price_note='Harga publik Owner 7 Okt 2026. 20–29 Rp65.000; 30–39 Rp59.000; 40+ Rp49.500. Shared minimal total sesi 40 pax Rp49.500. B2B tetap jalur terpisah.',
    sales_public_summary='Harga publik: 20–29 Rp65.000/pax; 30–39 Rp59.000/pax; 40+ Rp49.500/pax. Shared Education Rp49.500/pax dengan total sesi minimal 40 pax.',
    updated_at=now()
where package_code='STATION-PROF-2026';

update public.program_package_price_tiers
set is_active=false,
    effective_until=date '2026-10-06',
    updated_at=now(),
    notes=coalesce(notes,'') || ' Superseded by Owner public pricing effective 7 Oct 2026.'
where package_code='STATION-PROF-2026'
  and channel='DIRECT_PUBLIC'
  and effective_from<date '2026-10-07'
  and is_active=true;

insert into public.program_package_price_tiers
(package_code,channel,session_type,min_pricing_pax,max_pricing_pax,unit_price,school_cashback_per_pax,sales_commission_per_pax,effective_from,effective_until,is_active,notes)
values
('STATION-PROF-2026','DIRECT_PUBLIC','PRIVATE',20,29,65000,0,5000,'2026-10-07',null,true,'Harga publik Owner 7 Okt 2026 — Private 20–29 pax.'),
('STATION-PROF-2026','DIRECT_PUBLIC','PRIVATE',30,39,59000,0,5000,'2026-10-07',null,true,'Harga publik Owner 7 Okt 2026 — Private 30–39 pax.'),
('STATION-PROF-2026','DIRECT_PUBLIC','PRIVATE',40,null,49500,0,5000,'2026-10-07',null,true,'Harga publik Owner 7 Okt 2026 — Private 40+ pax.'),
('STATION-PROF-2026','DIRECT_PUBLIC','SHARED',40,null,49500,0,5000,'2026-10-07',null,true,'Harga publik Owner 7 Okt 2026 — Shared Education; total sesi minimal 40 pax.')
on conflict (package_code,channel,session_type,min_pricing_pax,effective_from)
do update set
  max_pricing_pax=excluded.max_pricing_pax,
  unit_price=excluded.unit_price,
  school_cashback_per_pax=excluded.school_cashback_per_pax,
  sales_commission_per_pax=excluded.sales_commission_per_pax,
  effective_until=null,
  is_active=true,
  notes=excluded.notes,
  updated_at=now();

-- Activate/update Train Package A after pricing guard prerequisites exist.
update public.program_packages
set name='Paket A — Lengkap | Edukasi di Atas Kereta Api',
    description='Paket publik lengkap: tiket KA anak pulang-pergi sesuai rute yang dikonfirmasi, alokasi tiket pendamping PP sesuai quotation, snack, minuman, worksheet, kuis/hadiah, TL/Edukator, dan dokumentasi.',
    price_per_pax=65000,
    min_pax=20,
    facilities=array[
      'Tiket KA anak pulang-pergi sesuai rute yang dikonfirmasi',
      'Alokasi tiket pendamping PP sesuai quotation',
      'Snack peserta','Minuman peserta','Worksheet edukasi',
      'Kuis dan hadiah fisik','Pendampingan TL / Edukator','Dokumentasi kegiatan'
    ]::text[],
    status='ACTIVE',
    is_active=true,
    effective_from=date '2026-10-07',
    effective_until=null,
    price_note='Harga publik Paket A Lengkap Owner 7 Okt 2026. 20–29 Rp65.000; 30–39 Rp63.000; 40+ Rp62.000. Minimum 20 siswa berbayar. B2B tidak berubah.',
    sales_public_summary='Paket A Lengkap — harga publik: 20–29 Rp65.000/pax; 30–39 Rp63.000/pax; 40+ Rp62.000/pax. Minimum 20 siswa berbayar.',
    duration_text='Menyesuaikan rute dan jadwal kereta',
    public_terms=array[
      'Minimum 20 siswa berbayar per kegiatan',
      'Rute, jadwal, dan ketersediaan tiket wajib dikonfirmasi',
      'Fasilitas guru/pendamping mengikuti quotation',
      'Harga publik tidak mengubah master B2B'
    ]::text[],
    updated_at=now()
where package_code='PKG-GMU-00002';

insert into public.program_package_price_tiers
(package_code,channel,session_type,min_pricing_pax,max_pricing_pax,unit_price,school_cashback_per_pax,sales_commission_per_pax,effective_from,effective_until,is_active,notes)
values
('PKG-GMU-00002','DIRECT_PUBLIC','PRIVATE',20,29,65000,0,5000,'2026-10-07',null,true,'Harga publik Paket A Owner 7 Okt 2026 — 20–29 siswa.'),
('PKG-GMU-00002','DIRECT_PUBLIC','PRIVATE',30,39,63000,0,5000,'2026-10-07',null,true,'Harga publik Paket A Owner 7 Okt 2026 — 30–39 siswa.'),
('PKG-GMU-00002','DIRECT_PUBLIC','PRIVATE',40,null,62000,0,5000,'2026-10-07',null,true,'Harga publik Paket A Owner 7 Okt 2026 — 40+ siswa.')
on conflict (package_code,channel,session_type,min_pricing_pax,effective_from)
do update set
  max_pricing_pax=excluded.max_pricing_pax,
  unit_price=excluded.unit_price,
  school_cashback_per_pax=excluded.school_cashback_per_pax,
  sales_commission_per_pax=excluded.sales_commission_per_pax,
  effective_until=null,
  is_active=true,
  notes=excluded.notes,
  updated_at=now();

insert into public.program_package_economics
(package_code,program_slug,package_name,baseline_pax,facility_hpp_locked,sales_fee_baseline,partner_fee_baseline,manager_fee_baseline,tl_tutor_fee_baseline,ops_documentation_fee_baseline,total_cost_baseline,trip_contribution_baseline,contribution_margin_pct,facility_hpp_is_locked,is_best_seller,crew_scaling_policy,notes,updated_at)
values
('PKG-GMU-00002','edukasi-di-atas-kereta','Paket A — Lengkap | Edukasi di Atas Kereta Api',
20,400000,100000,0,130000,100000,155000,885000,415000,31.923,true,true,
'TIER_MANUAL_ABOVE_BASELINE',
'Owner public master 7 Oct 2026. Facility HPP Rp20.000/pax ×20. Ops/documentation field includes Ops/Dokumentasi Rp100.000 + Helper Rp55.000. Direct public sales commission Rp5.000/pax; no B2B partner commission.',
now())
on conflict (package_code)
do update set
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
  notes=excluded.notes,
  updated_at=now();

-- Sales App live resource references.
update public.sales_resources
set description='Master harga publik terbaru (Owner 7 Okt 2026). Kereta Paket A: 20–29 Rp65.000; 30–39 Rp63.000; 40+ Rp62.000. Stasiun: 20–29 Rp65.000; 30–39 Rp59.000; 40+ Rp49.500; Shared 40+ Rp49.500.',
    url='https://drive.google.com/drive/folders/12Hy4_9DuKO6V9E0LumyBQTQMqsefKHsY',
    share_text='Gunakan hanya master harga publik terbaru yang ditetapkan Owner 7 Oktober 2026. Harga B2B/agen adalah jalur terpisah.',
    updated_at=now()
where id='sales_kit_katalog_program';

insert into public.sales_resources
(id,resource_type,title,description,url,share_text,sort_order,is_active,updated_at)
values
('price_public_train_a_2026','LINK','Harga Publik — Edukasi di Atas Kereta Api (Paket A)',
 'Master publik Owner 7 Okt 2026: 20–29 Rp65.000/pax; 30–39 Rp63.000/pax; 40+ Rp62.000/pax.',
 'https://docs.google.com/document/d/11AfEXtjIfFr2E4ckzSzQuTyXPA96i8DHObNXZCp5TXg/edit',
 'Kereta Paket A: 20–29 Rp65.000/pax; 30–39 Rp63.000/pax; 40+ Rp62.000/pax. Minimum 20 siswa berbayar.',3,true,now()),
('price_public_station_2026','LINK','Harga Publik — Edukasi Lingkungan & Profesi Stasiun',
 'Master publik Owner 7 Okt 2026: 20–29 Rp65.000/pax; 30–39 Rp59.000/pax; 40+ Rp49.500/pax; Shared minimal 40 Rp49.500/pax.',
 'https://docs.google.com/document/d/1jn9oI5OArFuP8zSeYACH25KjHjGkW4m2E5b7vZFhYWs/edit',
 'Stasiun: 20–29 Rp65.000/pax; 30–39 Rp59.000/pax; 40+ Rp49.500/pax. Shared Education minimal 40 peserta/sesi Rp49.500/pax.',4,true,now())
on conflict (id)
do update set
  resource_type=excluded.resource_type,
  title=excluded.title,
  description=excluded.description,
  url=excluded.url,
  share_text=excluded.share_text,
  sort_order=excluded.sort_order,
  is_active=true,
  updated_at=now();

update public.sales_resources
set share_text='Gunakan harga publik/master DIRECT_PUBLIC aktif dari ERP. Kereta Paket A: 20–29 Rp65.000; 30–39 Rp63.000; 40+ Rp62.000. Stasiun: 20–29 Rp65.000; 30–39 Rp59.000; 40+ Rp49.500; Shared minimal 40 Rp49.500. Diskon, cashback, harga B2B/khusus atau fasilitas tambahan wajib approval Manager/Director.',
    updated_at=now()
where id='pricing_guardrail';

commit;
