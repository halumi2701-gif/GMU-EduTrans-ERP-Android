-- GMU EduTrans Google Drive archive integration
create table if not exists public.gmu_drive_folders (
  id uuid primary key default gen_random_uuid(),
  entity_type text not null,
  entity_id text not null,
  booking_code text,
  drive_folder_id text not null,
  drive_folder_url text not null,
  folder_name text not null,
  parent_drive_folder_id text,
  status text not null default 'active' check (status in ('active','archived','error')),
  metadata jsonb not null default '{}'::jsonb,
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(entity_type, entity_id)
);

create table if not exists public.gmu_drive_documents (
  id uuid primary key default gen_random_uuid(),
  entity_type text not null,
  entity_id text not null,
  folder_id uuid references public.gmu_drive_folders(id) on delete set null,
  document_type text not null,
  file_name text not null,
  drive_file_id text not null,
  drive_file_url text not null,
  mime_type text,
  source text not null default 'erp',
  metadata jsonb not null default '{}'::jsonb,
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  unique(drive_file_id)
);

alter table public.gmu_drive_folders enable row level security;
alter table public.gmu_drive_documents enable row level security;

drop policy if exists "drive folders internal read" on public.gmu_drive_folders;
create policy "drive folders internal read" on public.gmu_drive_folders
for select to authenticated
using (exists (
  select 1 from public.profiles p
  where p.id = (select auth.uid()) and p.is_active = true
));

drop policy if exists "drive documents internal read" on public.gmu_drive_documents;
create policy "drive documents internal read" on public.gmu_drive_documents
for select to authenticated
using (exists (
  select 1 from public.profiles p
  where p.id = (select auth.uid()) and p.is_active = true
));

-- Writes are server-side only through the Edge Function/service role.
revoke insert, update, delete on public.gmu_drive_folders from anon, authenticated;
revoke insert, update, delete on public.gmu_drive_documents from anon, authenticated;
grant select on public.gmu_drive_folders to authenticated;
grant select on public.gmu_drive_documents to authenticated;

create index if not exists gmu_drive_folders_booking_code_idx
  on public.gmu_drive_folders(booking_code);
create index if not exists gmu_drive_documents_entity_idx
  on public.gmu_drive_documents(entity_type, entity_id);
