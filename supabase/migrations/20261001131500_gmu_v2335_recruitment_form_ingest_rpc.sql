-- GMU EduTrans v23.3.5 — secure recruitment form intake RPC
-- Allows Google Forms / Apps Script to push a normalized response without a new Edge Function.

create or replace function public.gmu_recruitment_form_ingest(
  p_secret text,
  p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_position text;
  v_employment text;
  v_case_employment text;
  v_case_id uuid;
  v_candidate_id uuid;
  v_candidate_code text;
  v_existing_id uuid;
  v_response_id text;
  v_submitted_at timestamptz;
  v_domicile text;
  v_result jsonb;
begin
  if not public.gmu_validate_recruitment_form_secret(p_secret) then
    raise exception 'unauthorized';
  end if;

  v_response_id := nullif(trim(coalesce(p_payload->>'response_id','')), '');
  v_position := nullif(trim(coalesce(p_payload->>'primary_position','')), '');

  if v_response_id is null then raise exception 'missing response_id'; end if;
  if nullif(trim(coalesce(p_payload->>'full_name','')), '') is null then raise exception 'missing full_name'; end if;
  if nullif(trim(coalesce(p_payload->>'whatsapp','')), '') is null then raise exception 'missing whatsapp'; end if;
  if nullif(trim(coalesce(p_payload->>'email','')), '') is null then raise exception 'missing email'; end if;
  if nullif(trim(coalesce(p_payload->>'domicile','')), '') is null then raise exception 'missing domicile'; end if;
  if v_position is null then raise exception 'missing primary_position'; end if;

  v_employment := case v_position
    when 'Admin Part Time' then 'PART_TIME'
    when 'Finance Part Time' then 'PART_TIME'
    when 'Marketing & Sales' then 'TARGET_BASED'
    when 'Operasional Freelance' then 'FREELANCER'
    when 'TL / MC / Edukator' then 'FREELANCER'
    when 'Dokumentasi Freelance' then 'FREELANCER'
    when 'Helper Freelance' then 'ON_CALL'
    else null
  end;

  if v_employment is null then raise exception 'invalid primary_position'; end if;

  v_case_employment := case when v_employment='PART_TIME' then 'PART_TIME' else 'FREELANCER' end;

  begin
    v_submitted_at := coalesce(nullif(p_payload->>'submitted_at','')::timestamptz, now());
  exception when others then
    raise exception 'invalid submitted_at';
  end;

  v_domicile := trim(coalesce(p_payload->>'domicile',''));
  if nullif(trim(coalesce(p_payload->>'kecamatan','')), '') is not null then
    v_domicile := v_domicile || ' / ' || trim(p_payload->>'kecamatan');
  end if;

  select c.id into v_existing_id
  from public.recruitment_candidates c
  where c.form_response_id=v_response_id
  limit 1;

  if v_existing_id is not null then
    update public.recruitment_candidates
    set
      full_name=trim(p_payload->>'full_name'),
      contact=trim(p_payload->>'whatsapp'),
      whatsapp=trim(p_payload->>'whatsapp'),
      email=trim(p_payload->>'email'),
      domicile=v_domicile,
      primary_position=v_position,
      alternate_position=nullif(trim(coalesce(p_payload->>'alternate_position','')), ''),
      employment_type=v_employment,
      availability=nullif(trim(coalesce(p_payload->>'availability','')), ''),
      weekend_availability=nullif(trim(coalesce(p_payload->>'weekend','')), ''),
      current_activity=nullif(trim(coalesce(p_payload->>'current_activity','')), ''),
      experience_summary=nullif(trim(coalesce(p_payload->>'experience_summary','')), ''),
      portfolio_url=nullif(trim(coalesce(p_payload->>'portfolio_url','')), ''),
      cv_reference=nullif(trim(coalesce(p_payload->>'portfolio_url','')), ''),
      source=nullif(trim(coalesce(p_payload->>'source','')), ''),
      intake_data=coalesce(p_payload,'{}'::jsonb) || jsonb_build_object('source_payload_version','gmu-recruitment-form-v1'),
      form_submitted_at=v_submitted_at,
      pipeline_status='APPLIED',
      stage='APPLIED',
      red_flag_status='NO',
      offer_status='NOT_SENT',
      next_action='Admin Screening',
      updated_at=now()
    where id=v_existing_id
    returning id,candidate_code,recruitment_case_id into v_candidate_id,v_candidate_code,v_case_id;
  else
    select rc.id into v_case_id
    from public.recruitment_cases rc
    where rc.position_title=v_position
      and rc.status in ('NEED_REVIEW','APPROVED','SOURCING','INTERVIEW','OFFER','ONBOARDING')
    order by rc.created_at desc
    limit 1;

    if v_case_id is null then
      insert into public.recruitment_cases(
        position_title,employment_type,reason,status,candidate_name,candidate_contact,notes
      )
      values(
        v_position,v_case_employment,'Public Recruitment Form v1','SOURCING',
        trim(p_payload->>'full_name'),trim(p_payload->>'whatsapp'),
        'Auto-created by secure recruitment intake RPC'
      )
      returning id into v_case_id;
    end if;

    insert into public.recruitment_candidates(
      recruitment_case_id,full_name,contact,source,cv_reference,stage,
      whatsapp,email,domicile,primary_position,alternate_position,employment_type,
      availability,weekend_availability,current_activity,experience_summary,portfolio_url,
      pipeline_status,red_flag_status,offer_status,next_action,
      intake_data,form_response_id,form_submitted_at
    )
    values(
      v_case_id,trim(p_payload->>'full_name'),trim(p_payload->>'whatsapp'),
      nullif(trim(coalesce(p_payload->>'source','')), ''),
      nullif(trim(coalesce(p_payload->>'portfolio_url','')), ''),
      'APPLIED',
      trim(p_payload->>'whatsapp'),trim(p_payload->>'email'),v_domicile,v_position,
      nullif(trim(coalesce(p_payload->>'alternate_position','')), ''),
      v_employment,
      nullif(trim(coalesce(p_payload->>'availability','')), ''),
      nullif(trim(coalesce(p_payload->>'weekend','')), ''),
      nullif(trim(coalesce(p_payload->>'current_activity','')), ''),
      nullif(trim(coalesce(p_payload->>'experience_summary','')), ''),
      nullif(trim(coalesce(p_payload->>'portfolio_url','')), ''),
      'APPLIED','NO','NOT_SENT','Admin Screening',
      coalesce(p_payload,'{}'::jsonb) || jsonb_build_object('source_payload_version','gmu-recruitment-form-v1'),
      v_response_id,v_submitted_at
    )
    returning id,candidate_code into v_candidate_id,v_candidate_code;
  end if;

  select jsonb_build_object(
    'ok',true,
    'candidate_id',c.id,
    'candidate_code',c.candidate_code,
    'form_response_id',c.form_response_id,
    'pipeline_status',c.pipeline_status,
    'primary_position',c.primary_position,
    'employment_type',c.employment_type,
    'tracker_row',jsonb_build_object(
      'Candidate ID',c.candidate_code,
      'Applied Date',c.form_submitted_at,
      'Full Name',c.full_name,
      'WhatsApp',c.whatsapp,
      'Email',c.email,
      'Domisili',c.domicile,
      'Primary Position',c.primary_position,
      'Alternate Position',c.alternate_position,
      'Work Type',c.employment_type,
      'Availability',c.availability,
      'Weekend',c.weekend_availability,
      'Current Activity',c.current_activity,
      'Experience Summary',c.experience_summary,
      'Portfolio / Link',c.portfolio_url,
      'Source',c.source,
      'Status','Applied',
      'ERP Sync Status','Synced'
    )
  ) into v_result
  from public.recruitment_candidates c
  where c.id=v_candidate_id;

  return v_result;
end;
$$;

revoke all on function public.gmu_recruitment_form_ingest(text,jsonb) from public, authenticated;
grant execute on function public.gmu_recruitment_form_ingest(text,jsonb) to anon, service_role;

comment on function public.gmu_recruitment_form_ingest(text,jsonb) is
  'Secure public recruitment intake. Shared secret is validated against Supabase Vault before any write.';
