-- GMU EduTrans v21.9 — Official Program Master
-- Purpose: seed the six official GMU EduTrans programs before cross-program sales aggregation.
-- Idempotent: existing rows are updated by slug; missing rows are inserted.

-- 1) Edukasi di Atas Kereta
update public.programs
set name = 'Edukasi di Atas Kereta',
    category = 'Railway Education',
    short_description = 'Program edukasi di atas kereta api GMU EduTrans. Minimum 20 peserta. Paket dan HPP dikelola di master paket.',
    min_pax = 20,
    is_active = true,
    sort_order = 10
where slug = 'edukasi-di-atas-kereta';

insert into public.programs (slug, name, category, short_description, min_pax, is_active, sort_order)
select 'edukasi-di-atas-kereta', 'Edukasi di Atas Kereta', 'Railway Education',
       'Program edukasi di atas kereta api GMU EduTrans. Minimum 20 peserta. Paket dan HPP dikelola di master paket.',
       20, true, 10
where not exists (select 1 from public.programs where slug = 'edukasi-di-atas-kereta');

-- 2) Edukasi Profesi & Lingkungan Stasiun
update public.programs
set name = 'Edukasi Profesi & Lingkungan Stasiun',
    category = 'Railway Education',
    short_description = 'Pengenalan profesi, lingkungan, operasional, dan keselamatan di stasiun. Minimum paket aktif mengikuti master paket.',
    min_pax = 20,
    is_active = true,
    sort_order = 20
where slug = 'edukasi-profesi-lingkungan-stasiun';

insert into public.programs (slug, name, category, short_description, min_pax, is_active, sort_order)
select 'edukasi-profesi-lingkungan-stasiun', 'Edukasi Profesi & Lingkungan Stasiun', 'Railway Education',
       'Pengenalan profesi, lingkungan, operasional, dan keselamatan di stasiun. Minimum paket aktif mengikuti master paket.',
       20, true, 20
where not exists (select 1 from public.programs where slug = 'edukasi-profesi-lingkungan-stasiun');

-- 3) Edukasi Padi Pandanwangi
update public.programs
set name = 'Edukasi Padi Pandanwangi',
    category = 'Agro Education',
    short_description = 'Field trip edukasi agrikultur Padi Pandanwangi. Harga, HPP, dan minimum final mengikuti paket/quotation aktif.',
    min_pax = 1,
    is_active = true,
    sort_order = 30
where slug = 'edukasi-padi-pandanwangi';

insert into public.programs (slug, name, category, short_description, min_pax, is_active, sort_order)
select 'edukasi-padi-pandanwangi', 'Edukasi Padi Pandanwangi', 'Agro Education',
       'Field trip edukasi agrikultur Padi Pandanwangi. Harga, HPP, dan minimum final mengikuti paket/quotation aktif.',
       1, true, 30
where not exists (select 1 from public.programs where slug = 'edukasi-padi-pandanwangi');

-- 4) Edukasi Membatik
update public.programs
set name = 'Edukasi Membatik',
    category = 'Creative Education',
    short_description = 'Workshop teori dan praktik membatik. Harga, HPP vendor, dan minimum final mengikuti paket/quotation aktif.',
    min_pax = 1,
    is_active = true,
    sort_order = 40
where slug = 'edukasi-membatik';

insert into public.programs (slug, name, category, short_description, min_pax, is_active, sort_order)
select 'edukasi-membatik', 'Edukasi Membatik', 'Creative Education',
       'Workshop teori dan praktik membatik. Harga, HPP vendor, dan minimum final mengikuti paket/quotation aktif.',
       1, true, 40
where not exists (select 1 from public.programs where slug = 'edukasi-membatik');

-- 5) Company / Factory Visit
update public.programs
set name = 'Company / Factory Visit',
    category = 'Corporate Education',
    short_description = 'Kunjungan edukasi ke perusahaan atau pabrik. Harga dan minimum peserta mengikuti quotation dan kebutuhan mitra.',
    min_pax = 1,
    is_active = true,
    sort_order = 50
where slug = 'company-factory-visit';

insert into public.programs (slug, name, category, short_description, min_pax, is_active, sort_order)
select 'company-factory-visit', 'Company / Factory Visit', 'Corporate Education',
       'Kunjungan edukasi ke perusahaan atau pabrik. Harga dan minimum peserta mengikuti quotation dan kebutuhan mitra.',
       1, true, 50
where not exists (select 1 from public.programs where slug = 'company-factory-visit');

-- 6) Custom Visit / Custom EduTrip
update public.programs
set name = 'Custom Visit / Custom EduTrip',
    category = 'Custom EduTrip',
    short_description = 'Program edukasi custom sesuai kebutuhan customer. RAB, harga, minimum peserta, dan margin ditentukan per quotation.',
    min_pax = 1,
    is_active = true,
    sort_order = 60
where slug = 'custom-visit-edutrip';

insert into public.programs (slug, name, category, short_description, min_pax, is_active, sort_order)
select 'custom-visit-edutrip', 'Custom Visit / Custom EduTrip', 'Custom EduTrip',
       'Program edukasi custom sesuai kebutuhan customer. RAB, harga, minimum peserta, dan margin ditentukan per quotation.',
       1, true, 60
where not exists (select 1 from public.programs where slug = 'custom-visit-edutrip');

comment on table public.programs is 'Master program GMU EduTrans. min_pax=1 pada program quotation-based berarti minimum final ditentukan oleh paket/quotation aktif, bukan penawaran retail 1 pax.';
