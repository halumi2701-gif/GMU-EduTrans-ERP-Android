-- GMU EduTrans v23.3.6 — public recruitment form submission
-- Purpose-built anonymous RPC for the public recruitment page.
-- It only inserts validated APPLIED candidates and includes database-side anti-abuse controls.

create or replace function public.gmu_public_recruitment_submit(p_payload jsonb)
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
  v_response_id text;
  v_started_at timestamptz;
  v_now timestamptz := now();
  v_whatsapp text;
  v_email text;
  v_full_name text;
  v_domicile text;
  v_consent boolean;
  v_recent_global integer;
  v_recent_contact integer;
begin
  if length(coalesce(p_payload::text,'')) > 40000 then
    raise exception 'payload_too_large';
  end if;

  if nullif(trim(coalesce(p_payload->>'website','')), '') is not null then
    raise exception 'bot_rejected';
  end if;

  begin
    v_started_at := (p_payload->>'started_at')::timestamptz;
  exception when others then
    raise exception 'invalid_started_at';
  end;

  if v_started_at is null
     or v_started_at > v_now
     or v_started_at < v_now - interval '2 hours'
     or v_started_at > v_now - interval '5 seconds' then
    raise exception 'submission_timing_rejected';
  end if;

  v_consent := coalesce((p_payload->>'consent')::boolean,false);
  if not v_consent then
    raise exception 'consent_required';
  end if;

  v_full_name := nullif(trim(coalesce(p_payload->>'full_name','')), '');
  v_whatsapp := regexp_replace(coalesce(p_payload->>'whatsapp',''), '[^0-9+]', '', 'g');
  v_email := lower(nullif(trim(coalesce(p_payload->>'email','')), ''));
  v_domicile := nullif(trim(coalesce(p_payload->>'domicile','')), '');
  v_position := nullif(trim(coalesce(p_payload->>'primary_position','')), '');

  if v_full_name is null or length(v_full_name) < 3 or length(v_full_name) > 120 then
    raise exception 'invalid_full_name';
  end if;
  if v_whatsapp is null or v_whatsapp !~ '^((\+62)|62|0)8[0-9]{7,13}$' then
    raise exception 'invalid_whatsapp';
  end if;
  if v_email is null or length(v_email) > 160 or v_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then
    raise exception 'invalid_email';
  end if;
  if v_domicile is null or length(v_domicile) > 160 then
    raise exception 'invalid_domicile';
  end if;

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
  if v_employment is null then
    raise exception 'invalid_position';
  end if;

  select count(*) into v_recent_global
  from public.recruitment_candidates
  where form_submitted_at >= v_now - interval '10 minutes'
    and coalesce(intake_data->>'source_payload_version','')='gmu-public-recruitment-v1';
  if v_recent_global >= 60 then
    raise exception 'rate_limit_global';
  end if;

  select count(*) into v_recent_contact
  from public.recruitment_candidates
  where form_submitted_at >= v_now - interval '24 hours'
    and (
      lower(coalesce(email,''))=v_email
      or regexp_replace(coalesce(whatsapp,''), '[^0-9+]', '', 'g')=v_whatsapp
    )
    and coalesce(intake_data->>'source_payload_version','')='gmu-public-recruitment-v1';
  if v_recent_contact >= 3 then
    raise exception 'rate_limit_contact';
  end if;

  v_case_employment := case when v_employment='PART_TIME' then 'PART_TIME' else 'FREELANCER' end;
  v_response_id := 'WEB-' || upper(substr(replace(extensions.gen_random_uuid()::text,'-',''),1,20));

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
      v_full_name,v_whatsapp,'Auto-created by GMU public recruitment form'
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
    v_case_id,
    v_full_name,
    v_whatsapp,
    nullif(trim(coalesce(p_payload->>'source','')), ''),
    nullif(trim(coalesce(p_payload->>'portfolio_url','')), ''),
    'APPLIED',
    v_whatsapp,
    v_email,
    trim(v_domicile || case when nullif(trim(coalesce(p_payload->>'kecamatan','')), '') is null then '' else ' / ' || trim(p_payload->>'kecamatan') end),
    v_position,
    nullif(trim(coalesce(p_payload->>'alternate_position','')), ''),
    v_employment,
    nullif(trim(coalesce(p_payload->>'availability','')), ''),
    nullif(trim(coalesce(p_payload->>'weekend','')), ''),
    nullif(trim(coalesce(p_payload->>'current_activity','')), ''),
    nullif(trim(coalesce(p_payload->>'experience_summary','')), ''),
    nullif(trim(coalesce(p_payload->>'portfolio_url','')), ''),
    'APPLIED','NO','NOT_SENT','Admin Screening',
    (p_payload - 'website') || jsonb_build_object('source_payload_version','gmu-public-recruitment-v1'),
    v_response_id,v_now
  )
  returning id,candidate_code into v_candidate_id,v_candidate_code;

  return jsonb_build_object(
    'ok',true,
    'candidate_id',v_candidate_id,
    'candidate_code',v_candidate_code,
    'response_id',v_response_id,
    'status','APPLIED',
    'next_action','Admin Screening'
  );
end;
$$;

revoke all on function public.gmu_public_recruitment_submit(jsonb) from public, authenticated;
grant execute on function public.gmu_public_recruitment_submit(jsonb) to anon;

comment on function public.gmu_public_recruitment_submit(jsonb) is
  'Validated anonymous recruitment intake with honeypot, timing checks, contact throttling, global throttling, fixed pipeline status, and no arbitrary table access.';
