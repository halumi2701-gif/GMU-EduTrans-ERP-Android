-- GMU EduTrans Market Intelligence v1
-- Fokus operasional tahap pertama: Cianjur + Sukabumi.
-- Migration is intentionally additive and does not modify existing booking/customer tables.

create extension if not exists pgcrypto;

create table if not exists public.market_targets (
    id uuid primary key default gen_random_uuid(),
    organization_name text not null,
    organization_type text not null,
    region text not null,
    district text,
    address text,
    website_url text,
    public_phone text,
    public_email text,
    target_pic_role text,
    potential_participants integer check (potential_participants is null or potential_participants >= 0),
    program_fit text[] not null default '{}',
    source_type text not null default 'Riset Pasar',
    source_url text,
    verification_status text not null default 'PERLU VERIFIKASI'
        check (verification_status in ('PERLU VERIFIKASI','TERVERIFIKASI','TIDAK VALID')),
    potential_score smallint not null default 0
        check (potential_score between 0 and 100),
    priority_grade text not null default 'C'
        check (priority_grade in ('A','B','C')),
    sales_status text not null default 'BELUM DIHUBUNGI'
        check (sales_status in (
            'BELUM DIHUBUNGI','SUDAH DIHUBUNGI','TERTARIK','PELUANG POTENSIAL',
            'PENAWARAN','NEGOSIASI','MENUNGGU DP','BERHASIL','TIDAK BERHASIL','PEMELIHARAAN HUBUNGAN'
        )),
    assigned_sales uuid references public.profiles(id) on delete set null,
    last_contact_at timestamptz,
    next_follow_up_at timestamptz,
    estimated_revenue numeric(16,2) not null default 0 check (estimated_revenue >= 0),
    estimated_profit numeric(16,2) not null default 0,
    lost_reason text,
    notes text,
    created_by uuid references public.profiles(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.market_target_activities (
    id uuid primary key default gen_random_uuid(),
    market_target_id uuid not null references public.market_targets(id) on delete cascade,
    activity_type text not null
        check (activity_type in ('TELEPON','WHATSAPP','EMAIL','KUNJUNGAN','RAPAT','PENAWARAN','CATATAN')),
    activity_at timestamptz not null default now(),
    outcome text,
    next_follow_up_at timestamptz,
    created_by uuid references public.profiles(id) on delete set null,
    created_at timestamptz not null default now()
);

create index if not exists market_targets_region_idx on public.market_targets(region);
create index if not exists market_targets_type_idx on public.market_targets(organization_type);
create index if not exists market_targets_status_idx on public.market_targets(sales_status);
create index if not exists market_targets_priority_idx on public.market_targets(priority_grade, potential_score desc);
create index if not exists market_targets_sales_idx on public.market_targets(assigned_sales);
create index if not exists market_targets_followup_idx on public.market_targets(next_follow_up_at);
create unique index if not exists market_targets_identity_idx
    on public.market_targets(lower(organization_name), lower(region), coalesce(lower(district), ''));
create index if not exists market_target_activities_target_idx
    on public.market_target_activities(market_target_id, activity_at desc);

comment on table public.market_targets is 'GMU EduTrans target pasar/prospek institusi. Fokus awal Cianjur dan Sukabumi.';
comment on column public.market_targets.public_phone is 'Kontak publik organisasi, bukan nomor pribadi yang dikumpulkan tanpa kebutuhan bisnis.';
comment on column public.market_targets.target_pic_role is 'Jabatan/fungsi PIC yang ditargetkan, mis. Kesiswaan, Humas, HR, GA, CSR.';
comment on column public.market_targets.potential_score is 'Skor prioritas 0-100 berdasarkan kecocokan program, potensi peserta, momentum, akses kontak, dan riwayat hubungan.';
comment on column public.market_targets.verification_status is 'Data hasil riset harus diberi status verifikasi; AI tidak boleh menganggap data belum diverifikasi sebagai fakta.';

alter table public.market_targets enable row level security;
alter table public.market_target_activities enable row level security;

-- Roles are read from the existing profiles table. Policies intentionally keep
-- market intelligence away from Finance/TL/Crew because they do not need the full prospect database.
drop policy if exists market_targets_read on public.market_targets;
create policy market_targets_read on public.market_targets
for select to authenticated
using (
    exists (
        select 1 from public.profiles p
        where p.id = auth.uid()
          and coalesce(p.is_active, true) = true
          and p.role in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales','Admin','Operation')
    )
);

drop policy if exists market_targets_insert on public.market_targets;
create policy market_targets_insert on public.market_targets
for insert to authenticated
with check (
    exists (
        select 1 from public.profiles p
        where p.id = auth.uid()
          and coalesce(p.is_active, true) = true
          and p.role in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales')
    )
);

drop policy if exists market_targets_update on public.market_targets;
create policy market_targets_update on public.market_targets
for update to authenticated
using (
    exists (
        select 1 from public.profiles p
        where p.id = auth.uid()
          and coalesce(p.is_active, true) = true
          and p.role in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales')
    )
)
with check (
    exists (
        select 1 from public.profiles p
        where p.id = auth.uid()
          and coalesce(p.is_active, true) = true
          and p.role in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales')
    )
);

drop policy if exists market_targets_delete on public.market_targets;
create policy market_targets_delete on public.market_targets
for delete to authenticated
using (
    exists (
        select 1 from public.profiles p
        where p.id = auth.uid()
          and coalesce(p.is_active, true) = true
          and p.role in ('Owner','Director','Direktur','Manager','Manager EduTrans')
    )
);

drop policy if exists market_activities_read on public.market_target_activities;
create policy market_activities_read on public.market_target_activities
for select to authenticated
using (
    exists (
        select 1 from public.profiles p
        where p.id = auth.uid()
          and coalesce(p.is_active, true) = true
          and p.role in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales','Admin','Operation')
    )
);

drop policy if exists market_activities_write on public.market_target_activities;
create policy market_activities_write on public.market_target_activities
for all to authenticated
using (
    exists (
        select 1 from public.profiles p
        where p.id = auth.uid()
          and coalesce(p.is_active, true) = true
          and p.role in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales')
    )
)
with check (
    exists (
        select 1 from public.profiles p
        where p.id = auth.uid()
          and coalesce(p.is_active, true) = true
          and p.role in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales')
    )
);

grant select, insert, update, delete on public.market_targets to authenticated;
grant select, insert, update, delete on public.market_target_activities to authenticated;
