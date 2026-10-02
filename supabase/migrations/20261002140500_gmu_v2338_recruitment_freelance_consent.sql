-- GMU EduTrans recruitment public form role qualification + freelance consent hardening
-- Applied to production 2026-10-02.

CREATE OR REPLACE FUNCTION public.gmu_public_recruitment_submit(p_payload jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_position text;
  v_alternate_position text;
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
  v_kecamatan text;
  v_education text;
  v_experience_years text;
  v_current_activity text;
  v_availability text;
  v_weekend text;
  v_experience_summary text;
  v_source text;
  v_consent boolean;
  v_freelance_assignment_consent boolean := false;
  v_recent_global integer;
  v_recent_contact integer;
  v_role_answers jsonb := coalesce(p_payload->'role_answers','{}'::jsonb);
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

  begin
    v_consent := coalesce((p_payload->>'consent')::boolean,false);
  exception when others then
    v_consent := false;
  end;
  if not v_consent then
    raise exception 'consent_required';
  end if;

  begin
    v_freelance_assignment_consent := coalesce((p_payload->>'freelance_assignment_consent')::boolean,false);
  exception when others then
    v_freelance_assignment_consent := false;
  end;

  v_full_name := nullif(trim(coalesce(p_payload->>'full_name','')), '');
  v_whatsapp := regexp_replace(coalesce(p_payload->>'whatsapp',''), '[^0-9+]', '', 'g');
  v_email := lower(nullif(trim(coalesce(p_payload->>'email','')), ''));
  v_domicile := nullif(trim(coalesce(p_payload->>'domicile','')), '');
  v_kecamatan := nullif(trim(coalesce(p_payload->>'kecamatan','')), '');
  v_education := nullif(trim(coalesce(p_payload->>'education','')), '');
  v_experience_years := nullif(trim(coalesce(p_payload->>'experience_years','')), '');
  v_current_activity := nullif(trim(coalesce(p_payload->>'current_activity','')), '');
  v_availability := nullif(trim(coalesce(p_payload->>'availability','')), '');
  v_weekend := nullif(trim(coalesce(p_payload->>'weekend','')), '');
  v_experience_summary := nullif(trim(coalesce(p_payload->>'experience_summary','')), '');
  v_source := nullif(trim(coalesce(p_payload->>'source','')), '');
  v_position := nullif(trim(coalesce(p_payload->>'primary_position','')), '');
  v_alternate_position := nullif(trim(coalesce(p_payload->>'alternate_position','')), '');

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
  if v_kecamatan is null or length(v_kecamatan) > 120 then
    raise exception 'invalid_kecamatan';
  end if;
  if v_education not in ('SMA/SMK/MA','D1-D3','D4-S1','S2+') then
    raise exception 'minimum_education_sma_required';
  end if;
  if v_experience_years not in ('Fresh graduate / belum 1 tahun','±1 tahun','2–3 tahun','>3 tahun') then
    raise exception 'invalid_experience_years';
  end if;
  if v_current_activity is null or length(v_current_activity) > 240 then
    raise exception 'invalid_current_activity';
  end if;
  if v_availability is null or length(v_availability) > 300 then
    raise exception 'invalid_availability';
  end if;
  if v_weekend not in ('Ya','Tidak','Kondisional') then
    raise exception 'invalid_weekend_availability';
  end if;
  if v_experience_summary is null or length(v_experience_summary) < 5 or length(v_experience_summary) > 4000 then
    raise exception 'invalid_experience_summary';
  end if;
  if v_source is null or length(v_source) > 120 then
    raise exception 'invalid_source';
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

  if v_employment in ('FREELANCER','ON_CALL') and not v_freelance_assignment_consent then
    raise exception 'freelance_assignment_consent_required';
  end if;

  if v_alternate_position is not null and v_alternate_position <> 'Tidak ada' then
    if v_alternate_position not in (
      'Admin Part Time','Finance Part Time','Marketing & Sales','Operasional Freelance',
      'TL / MC / Edukator','Dokumentasi Freelance','Helper Freelance'
    ) then
      raise exception 'invalid_alternate_position';
    end if;
    if v_alternate_position = v_position then
      v_alternate_position := null;
    end if;
  else
    v_alternate_position := null;
  end if;

  if jsonb_typeof(v_role_answers) <> 'object' then
    raise exception 'invalid_role_answers';
  end if;

  if v_position='Admin Part Time' and (
      nullif(trim(coalesce(v_role_answers->>'admin_experience','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'admin_accuracy','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'admin_missing','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'admin_erp','')), '') is null
  ) then raise exception 'role_answers_required'; end if;

  if v_position='Finance Part Time' and (
      nullif(trim(coalesce(v_role_answers->>'fin_basics','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'fin_receipt','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'fin_reconcile','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'fin_confidentiality','')), '') is null
  ) then raise exception 'role_answers_required'; end if;

  if v_position='Marketing & Sales' and (
      nullif(trim(coalesce(v_role_answers->>'sales_prospect','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'sales_followup','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'sales_rejection','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'sales_first','')), '') is null
  ) then raise exception 'role_answers_required'; end if;

  if v_position='Operasional Freelance' and (
      nullif(trim(coalesce(v_role_answers->>'ops_checklist','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'ops_change','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'ops_instruction','')), '') is null
  ) then raise exception 'role_answers_required'; end if;

  if v_position='TL / MC / Edukator' and (
      nullif(trim(coalesce(v_role_answers->>'tl_experience','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'tl_focus','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'tl_children','')), '') is null
  ) then raise exception 'role_answers_required'; end if;

  if v_position='Dokumentasi Freelance' and (
      nullif(trim(coalesce(v_role_answers->>'doc_device','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'doc_experience','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'doc_portfolio','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'doc_backup','')), '') is null
  ) then raise exception 'role_answers_required'; end if;

  if v_position='Helper Freelance' and (
      nullif(trim(coalesce(v_role_answers->>'helper_oncall','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'helper_instruction','')), '') is null or
      nullif(trim(coalesce(v_role_answers->>'helper_children','')), '') is null
  ) then raise exception 'role_answers_required'; end if;

  select count(*) into v_recent_global
  from public.recruitment_candidates
  where form_submitted_at >= v_now - interval '10 minutes'
    and coalesce(intake_data->>'source_payload_version','') in ('gmu-public-recruitment-v1','gmu-public-recruitment-v2');
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
    and coalesce(intake_data->>'source_payload_version','') in ('gmu-public-recruitment-v1','gmu-public-recruitment-v2');
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
      v_position,v_case_employment,'Public Recruitment Form v2','SOURCING',
      v_full_name,v_whatsapp,'Auto-created by secure recruitment intake RPC'
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
    v_source,
    nullif(trim(coalesce(p_payload->>'portfolio_url','')), ''),
    'APPLIED',
    v_whatsapp,
    v_email,
    trim(v_domicile || ' / ' || v_kecamatan),
    v_position,
    v_alternate_position,
    v_employment,
    v_availability,
    v_weekend,
    v_current_activity,
    v_experience_summary,
    nullif(trim(coalesce(p_payload->>'portfolio_url','')), ''),
    'APPLIED','NO','NOT_SENT','Admin Screening',
    (p_payload - 'website') || jsonb_build_object('source_payload_version','gmu-public-recruitment-v2'),
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
$function$


revoke all on function public.gmu_public_recruitment_submit(jsonb) from public;
grant execute on function public.gmu_public_recruitment_submit(jsonb) to anon, authenticated;
