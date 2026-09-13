-- GMU EduTrans Media Sync v1
-- Adds public-content media metadata only. Storage policies are managed separately.

alter table public.programs
    add column if not exists cover_image_url text,
    add column if not exists gallery_urls jsonb not null default '[]'::jsonb;

alter table public.program_packages
    add column if not exists cover_image_url text,
    add column if not exists gallery_urls jsonb not null default '[]'::jsonb;

-- Defense in depth: the ERP/API already validates media, but the database must
-- reject malformed metadata too. Gallery is capped at five images everywhere.
alter table public.programs
    drop constraint if exists programs_cover_image_https_chk,
    drop constraint if exists programs_gallery_array_chk,
    drop constraint if exists programs_gallery_max5_chk;

alter table public.programs
    add constraint programs_cover_image_https_chk
        check (cover_image_url is null or cover_image_url like 'https://%'),
    add constraint programs_gallery_array_chk
        check (jsonb_typeof(gallery_urls) = 'array'),
    add constraint programs_gallery_max5_chk
        check (jsonb_array_length(gallery_urls) <= 5);

alter table public.program_packages
    drop constraint if exists program_packages_cover_image_https_chk,
    drop constraint if exists program_packages_gallery_array_chk,
    drop constraint if exists program_packages_gallery_max5_chk;

alter table public.program_packages
    add constraint program_packages_cover_image_https_chk
        check (cover_image_url is null or cover_image_url like 'https://%'),
    add constraint program_packages_gallery_array_chk
        check (jsonb_typeof(gallery_urls) = 'array'),
    add constraint program_packages_gallery_max5_chk
        check (jsonb_array_length(gallery_urls) <= 5);

comment on column public.programs.cover_image_url is 'Public HTTPS cover image URL managed from GMU EduTrans ERP.';
comment on column public.programs.gallery_urls is 'Public gallery image URLs managed from GMU EduTrans ERP; JSON array, maximum 5 items.';
comment on column public.program_packages.cover_image_url is 'Public HTTPS cover image URL managed from GMU EduTrans ERP.';
comment on column public.program_packages.gallery_urls is 'Public gallery image URLs managed from GMU EduTrans ERP; JSON array, maximum 5 items.';
