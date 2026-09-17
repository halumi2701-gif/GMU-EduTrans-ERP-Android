-- GMU EduTrans v22.6 — Station package + auditable cost master
-- Production-aligned migration. Activates STATION-PROF-2026 through the existing pricing guard.

do $$
declare
  v_program_id uuid;
  v_approved_by uuid;
  v_approved_at timestamptz;
  v_created_by uuid;
begin
  select id into v_program_id
  from public.programs
  where slug='edukasi-profesi-lingkungan-stasiun'
  limit 1;

  if v_program_id is null then
    raise exception 'Program stasiun belum tersedia';
  end if;

  select approved_by, approved_at, created_by
    into v_approved_by, v_approved_at, v_created_by
  from public.program_packages
  where package_code='STATION-46'
  limit 1;

  if v_approved_by is null or v_approved_at is null then
    raise exception 'Approval STATION-46 belum lengkap';
  end if;

  update public.programs
  set name='Edukasi Lingkungan & Profesi Stasiun',
      min_pax=20,
      marketing_start_price=49500,
      marketing_price_note='Mulai Rp49.500/pax. Private 20–24 Rp65.000; 25–39 Rp58.000; 40+ Rp49.500. Shared Rp49.500 bila total sesi minimal 40 pax.',
      is_active=true
  where id=v_program_id;

  update public.program_packages
  set is_active=false,
      status='ARCHIVED'
  where package_code='STATION-46';

  update public.program_packages
  set program_id=v_program_id,
      name='Edukasi Lingkungan & Profesi Stasiun 2026',
      description='Program edukasi sekitar 2 jam: lingkungan/fasilitas stasiun, keselamatan, alur perjalanan, profesi perkeretaapian, aktivitas edukatif, dan pendampingan GMU EduTrans.',
      price_per_pax=65000,
      min_pax=20,
      status='DRAFT',
      is_active=false,
      sort_order=10,
      price_note='Base Private 20–24 Rp65.000. Harga final booking ditentukan pricing tier PUBLIC/B2B v22.6.',
      effective_from=coalesce(effective_from,date '2026-09-17'),
      created_by=coalesce(created_by,v_created_by),
      approved_by=coalesce(approved_by,v_approved_by),
      approved_at=coalesce(approved_at,v_approved_at)
  where package_code='STATION-PROF-2026';

  if not found then
    insert into public.program_packages(
      package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order,
      price_note,effective_from,created_by,approved_by,approved_at
    ) values (
      'STATION-PROF-2026',v_program_id,'Edukasi Lingkungan & Profesi Stasiun 2026',
      'Program edukasi sekitar 2 jam: lingkungan/fasilitas stasiun, keselamatan, alur perjalanan, profesi perkeretaapian, aktivitas edukatif, dan pendampingan GMU EduTrans.',
      65000,20,'DRAFT',false,10,
      'Base Private 20–24 Rp65.000. Harga final booking ditentukan pricing tier PUBLIC/B2B v22.6.',
      date '2026-09-17',v_created_by,v_approved_by,v_approved_at
    );
  end if;

  delete from public.pricing_cost_templates
  where scope_type='PROGRAM'
    and program_id=v_program_id
    and notes='GMU v22.6 Station cost master';

  insert into public.pricing_cost_templates(
    scope_type,program_id,category,description,cost_mode,amount,min_pax,max_pax,
    effective_from,is_active,notes,created_by,updated_by
  ) values
  ('PROGRAM',v_program_id,'PERMIT','Kepala Stasiun','FIXED',50000,null,null,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'SPEAKER','6 narasumber x Rp20.000','FIXED',120000,null,null,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'DOCUMENTATION','Dokumentasi','FIXED',55000,null,null,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'MEALS','Uang makan crew','FIXED',50000,null,null,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'FACILITATOR','TL/MC 20–40','FIXED',110000,1,40,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'FACILITATOR','TL/MC 41–60','FIXED',135000,41,60,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'FACILITATOR','TL/MC 61–80','FIXED',160000,61,80,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'FACILITATOR','TL/MC 81–100','FIXED',185000,81,100,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'FACILITATOR','TL/MC 101–120','FIXED',210000,101,120,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'FACILITATOR','TL/MC 121–140','FIXED',235000,121,140,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'FACILITATOR','TL/MC 141–160','FIXED',260000,141,160,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'FACILITATOR','TL/MC 161–180','FIXED',285000,161,180,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'FACILITATOR','TL/MC 181–200','FIXED',310000,181,200,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'OPERATOR','Operasional 20–40','FIXED',100000,1,40,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'OPERATOR','Operasional 41–60','FIXED',125000,41,60,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'OPERATOR','Operasional 61–80','FIXED',150000,61,80,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'OPERATOR','Operasional 81–100','FIXED',175000,81,100,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'OPERATOR','Operasional 101–120','FIXED',200000,101,120,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'OPERATOR','Operasional 121–140','FIXED',225000,121,140,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'OPERATOR','Operasional 141–160','FIXED',250000,141,160,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'OPERATOR','Operasional 161–180','FIXED',275000,161,180,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'OPERATOR','Operasional 181–200','FIXED',300000,181,200,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'OTHER','Komisi Sales GMU','PER_PAX',5000,null,null,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'MEALS','Snack peserta','PER_PAX',2500,null,null,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'MERCHANDISE','Sertifikat + worksheet','PER_PAX',1000,null,null,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by),
  ('PROGRAM',v_program_id,'OTHER','Alokasi fixed bulanan baseline 200 pax','PER_PAX',13000,null,null,date '2026-09-17',true,'GMU v22.6 Station cost master',v_created_by,v_created_by);

  update public.program_packages
  set status='ACTIVE',
      is_active=true,
      approved_by=v_approved_by,
      approved_at=v_approved_at,
      effective_from=date '2026-09-17'
  where package_code='STATION-PROF-2026';
end $$;