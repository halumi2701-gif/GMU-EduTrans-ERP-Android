-- GMU EduTrans v22.4 hotfix — normalize legacy production slugs before v22.4 master migration.
-- Idempotent and additive: existing canonical rows win; legacy rows are renamed only when target slug is absent.

do $$
begin
  -- Train
  if not exists (select 1 from public.programs where slug='edukasi-di-atas-kereta') then
    update public.programs set slug='edukasi-di-atas-kereta' where slug='edukasi-kereta-api';
  else
    update public.programs set is_active=false where slug='edukasi-kereta-api';
  end if;

  -- Station
  if not exists (select 1 from public.programs where slug='edukasi-profesi-lingkungan-stasiun') then
    update public.programs set slug='edukasi-profesi-lingkungan-stasiun' where slug='edukasi-lingkungan-stasiun';
  else
    update public.programs set is_active=false where slug='edukasi-lingkungan-stasiun';
  end if;

  -- Pandanwangi/agriculture
  if not exists (select 1 from public.programs where slug='edukasi-padi-pandanwangi') then
    update public.programs set slug='edukasi-padi-pandanwangi' where slug='edukasi-pertanian';
  else
    update public.programs set is_active=false where slug='edukasi-pertanian';
  end if;

  -- Batik
  if not exists (select 1 from public.programs where slug='edukasi-membatik') then
    update public.programs set slug='edukasi-membatik' where slug='pelatihan-membatik';
  else
    update public.programs set is_active=false where slug='pelatihan-membatik';
  end if;

  -- Private/custom
  if not exists (select 1 from public.programs where slug='custom-visit-edutrip') then
    update public.programs set slug='custom-visit-edutrip' where slug='custom-educational-trip';
  else
    update public.programs set is_active=false where slug='custom-educational-trip';
  end if;

  -- Legacy factory visit is retained for history but normalized so v22.4 can deactivate it cleanly.
  if not exists (select 1 from public.programs where slug='company-factory-visit') then
    update public.programs set slug='company-factory-visit' where slug='factory-company-visit';
  else
    update public.programs set is_active=false where slug='factory-company-visit';
  end if;
end $$;
