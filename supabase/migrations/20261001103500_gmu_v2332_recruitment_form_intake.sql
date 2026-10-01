-- GMU EduTrans v23.3.2 — Recruitment form intake metadata
-- Additive fields for Google Form/public intake integration.

alter table public.recruitment_candidates
  add column if not exists intake_data jsonb not null default '{}'::jsonb,
  add column if not exists form_response_id text,
  add column if not exists form_submitted_at timestamptz;

create unique index if not exists recruitment_candidates_form_response_uidx
  on public.recruitment_candidates(form_response_id)
  where form_response_id is not null;

comment on column public.recruitment_candidates.intake_data is
  'Structured raw/normalized answers from public recruitment intake forms.';
comment on column public.recruitment_candidates.form_response_id is
  'External form response identifier for idempotent ingestion.';
comment on column public.recruitment_candidates.form_submitted_at is
  'Original public form submission timestamp.';
