-- GMU EduTrans v23.3.1 — Recruitment compatibility defaults
-- Keep legacy inserts safe when candidate_code is omitted by older modules.

alter table public.recruitment_candidates
  alter column candidate_code
  set default ('CAND-' || upper(substr(replace(gen_random_uuid()::text,'-',''),1,12)));
