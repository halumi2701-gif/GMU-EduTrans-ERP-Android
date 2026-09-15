-- GMU EduTrans v22.4 preflight — prepare Pricing Master so canonical packages can activate safely.
-- Runs before 20260915190000_gmu_v224_master_program_pricing_engine.sql.

-- Reuse the existing production station package instead of creating a duplicate.
update public.program_packages pp
set package_code='STATION-46',
    name='Paket Edukasi Lingkungan & Profesi Stasiun',
    price_per_pax=46000,
    min_pax=20,
    updated_at=now()
from public.programs p
where pp.program_id=p.id
  and p.slug='edukasi-profesi-lingkungan-stasiun'
  and pp.is_active=true
  and pp.price_per_pax=46000
  and not exists (select 1 from public.program_packages x where x.package_code='STATION-46');

-- Batik packages must exist as DRAFT first because Pricing Master blocks direct ACTIVE inserts.
do $$
declare
  v_batik uuid;
begin
  select id into v_batik from public.programs where slug='edukasi-membatik' limit 1;
  if v_batik is null then raise exception 'Batik canonical program missing before v22.4 preflight'; end if;

  if not exists (select 1 from public.program_packages where package_code='BATIK-BASIC-140') then
    insert into public.program_packages(package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order)
    values('BATIK-BASIC-140',v_batik,'Basic Kids','Teori, praktik cap/nyanting, snack box, sertifikat, pendampingan kegiatan, dan tim operasional GMU EduTrans.',140000,20,'DRAFT',false,10);
  end if;
  if not exists (select 1 from public.program_packages where package_code='BATIK-REGULAR-175') then
    insert into public.program_packages(package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order)
    values('BATIK-REGULAR-175',v_batik,'Regular','Teori, praktik cap/nyanting, snack box, nasi box, sertifikat, pendampingan kegiatan, dan tim operasional GMU EduTrans.',175000,20,'DRAFT',false,20);
  end if;
  if not exists (select 1 from public.program_packages where package_code='BATIK-EXPERIENCE-245') then
    insert into public.program_packages(package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order)
    values('BATIK-EXPERIENCE-245',v_batik,'Experience 30x30 cm','Teori, praktik cap/nyanting, snack box, hasil karya 30 x 30 cm, sertifikat, pendampingan kegiatan, dan tim operasional GMU EduTrans.',245000,20,'DRAFT',false,30);
  end if;
  if not exists (select 1 from public.program_packages where package_code='BATIK-FULL-495') then
    insert into public.program_packages(package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order)
    values('BATIK-FULL-495',v_batik,'Full Experience 210x115 cm','Teori, praktik cap/nyanting, snack box, hasil karya 210 x 115 cm, sertifikat, pendampingan kegiatan, dan tim operasional GMU EduTrans.',495000,20,'DRAFT',false,40);
  end if;
end $$;

-- Program-level 25% target/floor policy for Batik.
insert into public.pricing_policies(
  scope_type,program_id,target_margin_pct,floor_margin_pct,max_discount_pct,
  contingency_pct,rounding_increment,effective_from,is_active,notes
)
select 'PROGRAM',p.id,25,25,0,0,1000,date '2026-09-15',true,'v22.4 BATIK MINIMUM MARGIN 25%'
from public.programs p
where p.slug='edukasi-membatik'
  and not exists (
    select 1 from public.pricing_policies x
    where x.scope_type='PROGRAM' and x.program_id=p.id and x.is_active=true
      and x.notes='v22.4 BATIK MINIMUM MARGIN 25%'
  );

-- Package-level baseline cost templates used by the existing Pricing Master activation guard.
-- These reflect the 20-pax baseline total cost; the v22.4 runtime resolver separately scales economics for actual pax.
insert into public.pricing_cost_templates(
  scope_type,program_id,package_id,category,description,cost_mode,amount,
  min_pax,effective_from,is_active,notes
)
select 'PACKAGE',pp.program_id,pp.id,'OTHER','v22.4 baseline total cost — Basic Kids','FIXED',1895000,
       20,date '2026-09-15',true,'v22.4 BATIK-BASIC-140 BASELINE'
from public.program_packages pp
where pp.package_code='BATIK-BASIC-140'
  and not exists (select 1 from public.pricing_cost_templates x where x.package_id=pp.id and x.notes='v22.4 BATIK-BASIC-140 BASELINE');

insert into public.pricing_cost_templates(scope_type,program_id,package_id,category,description,cost_mode,amount,min_pax,effective_from,is_active,notes)
select 'PACKAGE',pp.program_id,pp.id,'OTHER','v22.4 baseline total cost — Regular','FIXED',2395000,20,date '2026-09-15',true,'v22.4 BATIK-REGULAR-175 BASELINE'
from public.program_packages pp
where pp.package_code='BATIK-REGULAR-175'
  and not exists (select 1 from public.pricing_cost_templates x where x.package_id=pp.id and x.notes='v22.4 BATIK-REGULAR-175 BASELINE');

insert into public.pricing_cost_templates(scope_type,program_id,package_id,category,description,cost_mode,amount,min_pax,effective_from,is_active,notes)
select 'PACKAGE',pp.program_id,pp.id,'OTHER','v22.4 baseline total cost — Experience 30x30','FIXED',3395000,20,date '2026-09-15',true,'v22.4 BATIK-EXPERIENCE-245 BASELINE'
from public.program_packages pp
where pp.package_code='BATIK-EXPERIENCE-245'
  and not exists (select 1 from public.pricing_cost_templates x where x.package_id=pp.id and x.notes='v22.4 BATIK-EXPERIENCE-245 BASELINE');

insert into public.pricing_cost_templates(scope_type,program_id,package_id,category,description,cost_mode,amount,min_pax,effective_from,is_active,notes)
select 'PACKAGE',pp.program_id,pp.id,'OTHER','v22.4 baseline total cost — Full Experience 210x115','FIXED',6895000,20,date '2026-09-15',true,'v22.4 BATIK-FULL-495 BASELINE'
from public.program_packages pp
where pp.package_code='BATIK-FULL-495'
  and not exists (select 1 from public.pricing_cost_templates x where x.package_id=pp.id and x.notes='v22.4 BATIK-FULL-495 BASELINE');
