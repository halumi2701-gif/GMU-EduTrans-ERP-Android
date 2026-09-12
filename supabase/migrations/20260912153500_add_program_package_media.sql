-- GMU EduTrans Media Sync v1
-- Adds public-content media metadata only. Storage policies are managed separately.

alter table public.programs
    add column if not exists cover_image_url text,
    add column if not exists gallery_urls jsonb not null default '[]'::jsonb;

alter table public.program_packages
    add column if not exists cover_image_url text,
    add column if not exists gallery_urls jsonb not null default '[]'::jsonb;

comment on column public.programs.cover_image_url is 'Public cover image URL managed from GMU EduTrans ERP.';
comment on column public.programs.gallery_urls is 'Public gallery image URLs managed from GMU EduTrans ERP.';
comment on column public.program_packages.cover_image_url is 'Public cover image URL managed from GMU EduTrans ERP.';
comment on column public.program_packages.gallery_urls is 'Public gallery image URLs managed from GMU EduTrans ERP.';
