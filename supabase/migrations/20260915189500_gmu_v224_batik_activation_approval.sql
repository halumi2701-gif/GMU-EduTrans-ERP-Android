-- GMU EduTrans v22.4 hotfix — activate Batik packages using existing approval consistency rules.
-- Must run after 20260915189000_gmu_v224_pricing_master_preflight.sql
-- and before 20260915190000_gmu_v224_master_program_pricing_engine.sql.

do $$
declare
  v_approver uuid;
begin
  select p.id into v_approver
  from public.profiles p
  where p.is_active=true
    and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans')
  order by case
    when p.role::text='Owner' then 1
    when p.role::text in ('Director','Direktur') then 2
    else 3
  end, p.created_at asc
  limit 1;

  if v_approver is null then
    raise exception 'No active Owner/Manager profile available to approve v22.4 Batik packages';
  end if;

  update public.program_packages
  set status='ACTIVE',
      is_active=true,
      approved_by=coalesce(approved_by,v_approver),
      approved_at=coalesce(approved_at,now()),
      updated_at=now()
  where package_code in (
    'BATIK-BASIC-140',
    'BATIK-REGULAR-175',
    'BATIK-EXPERIENCE-245',
    'BATIK-FULL-495'
  )
    and not (status='ACTIVE' and is_active=true);
end $$;
