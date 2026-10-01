-- GMU EduTrans v23.3 — Recruitment System
-- Compatibility migration: extends the existing v21.4 recruitment_candidates table
-- instead of replacing it. Existing recruitment_cases + legacy stage fields remain usable.

alter table public.recruitment_candidates
  add column if not exists candidate_code text,
  add column if not exists whatsapp text,
  add column if not exists email text,
  add column if not exists domicile text,
  add column if not exists primary_position text,
  add column if not exists alternate_position text,
  add column if not exists employment_type text,
  add column if not exists availability text,
  add column if not exists weekend_availability text,
  add column if not exists current_activity text,
  add column if not exists experience_summary text,
  add column if not exists portfolio_url text,
  add column if not exists pipeline_status text,
  add column if not exists screening_score numeric(5,2),
  add column if not exists interview_score numeric(5,2),
  add column if not exists practical_score numeric(5,2),
  add column if not exists recommendation text,
  add column if not exists red_flag_status text,
  add column if not exists red_flag_notes text,
  add column if not exists final_decision text,
  add column if not exists interviewer_id uuid references public.profiles(id) on delete set null,
  add column if not exists interview_at timestamptz,
  add column if not exists next_action text,
  add column if not exists next_action_due date,
  add column if not exists offer_status text,
  add column if not exists start_pool_date date,
  add column if not exists staff_profile_id uuid references public.profiles(id) on delete set null,
  add column if not exists drive_folder_url text,
  add column if not exists external_candidate_id text,
  add column if not exists created_by uuid references public.profiles(id) on delete set null,
  add column if not exists updated_by uuid references public.profiles(id) on delete set null;

update public.recruitment_candidates
set
  candidate_code = coalesce(candidate_code, 'CAND-' || upper(substr(replace(id::text,'-',''),1,12))),
  whatsapp = coalesce(whatsapp, contact),
  employment_type = coalesce(
    employment_type,
    (
      select case
        when rc.employment_type in ('FREELANCER','PART_TIME','FULL_TIME','CONTRACT') then rc.employment_type
        else 'FREELANCER'
      end
      from public.recruitment_cases rc
      where rc.id = recruitment_candidates.recruitment_case_id
    ),
    'FREELANCER'
  ),
  primary_position = coalesce(
    primary_position,
    (select rc.position_title from public.recruitment_cases rc where rc.id = recruitment_candidates.recruitment_case_id)
  ),
  pipeline_status = coalesce(
    pipeline_status,
    case stage
      when 'APPLIED' then 'APPLIED'
      when 'SCREENING' then 'SCREENING'
      when 'INTERVIEW' then 'INTERVIEW'
      when 'TRIAL' then 'PRACTICAL_TEST'
      when 'OFFER' then 'OFFERING'
      when 'HIRED' then 'ACTIVE'
      when 'REJECTED' then 'REJECTED'
      when 'WITHDRAWN' then 'INACTIVE'
      else 'APPLIED'
    end
  ),
  red_flag_status = coalesce(red_flag_status,'NO'),
  offer_status = coalesce(offer_status,'NOT_SENT');

alter table public.recruitment_candidates
  alter column candidate_code set not null,
  alter column employment_type set default 'FREELANCER',
  alter column employment_type set not null,
  alter column pipeline_status set default 'APPLIED',
  alter column pipeline_status set not null,
  alter column red_flag_status set default 'NO',
  alter column red_flag_status set not null,
  alter column offer_status set default 'NOT_SENT',
  alter column offer_status set not null;

create unique index if not exists recruitment_candidates_candidate_code_uidx
  on public.recruitment_candidates(candidate_code);
create index if not exists recruitment_candidates_pipeline_idx
  on public.recruitment_candidates(pipeline_status, primary_position);
create index if not exists recruitment_candidates_whatsapp_idx
  on public.recruitment_candidates(whatsapp);

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid='public.recruitment_candidates'::regclass
      and conname='recruitment_candidates_employment_type_v233_check'
  ) then
    alter table public.recruitment_candidates
      add constraint recruitment_candidates_employment_type_v233_check
      check (employment_type in ('FREELANCER','PART_TIME','FULL_TIME','CONTRACT','TARGET_BASED','ON_CALL'));
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid='public.recruitment_candidates'::regclass
      and conname='recruitment_candidates_pipeline_status_v233_check'
  ) then
    alter table public.recruitment_candidates
      add constraint recruitment_candidates_pipeline_status_v233_check
      check (pipeline_status in (
        'APPLIED','SCREENING','INTERVIEW','PRACTICAL_TEST','SHORTLISTED',
        'OFFERING','ONBOARDING','ACTIVE','TALENT_POOL','HOLD','REJECTED','INACTIVE'
      ));
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid='public.recruitment_candidates'::regclass
      and conname='recruitment_candidates_screening_score_v233_check'
  ) then
    alter table public.recruitment_candidates
      add constraint recruitment_candidates_screening_score_v233_check
      check (screening_score is null or screening_score between 0 and 100);
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid='public.recruitment_candidates'::regclass
      and conname='recruitment_candidates_interview_score_v233_check'
  ) then
    alter table public.recruitment_candidates
      add constraint recruitment_candidates_interview_score_v233_check
      check (interview_score is null or interview_score between 0 and 100);
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid='public.recruitment_candidates'::regclass
      and conname='recruitment_candidates_practical_score_v233_check'
  ) then
    alter table public.recruitment_candidates
      add constraint recruitment_candidates_practical_score_v233_check
      check (practical_score is null or practical_score between 0 and 100);
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid='public.recruitment_candidates'::regclass
      and conname='recruitment_candidates_red_flag_v233_check'
  ) then
    alter table public.recruitment_candidates
      add constraint recruitment_candidates_red_flag_v233_check
      check (red_flag_status in ('NO','YES','NEEDS_REVIEW'));
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid='public.recruitment_candidates'::regclass
      and conname='recruitment_candidates_final_decision_v233_check'
  ) then
    alter table public.recruitment_candidates
      add constraint recruitment_candidates_final_decision_v233_check
      check (final_decision is null or final_decision in (
        'PROCEED','HOLD','TALENT_POOL','REJECTED','NEED_DIRECTOR_REVIEW'
      ));
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid='public.recruitment_candidates'::regclass
      and conname='recruitment_candidates_offer_status_v233_check'
  ) then
    alter table public.recruitment_candidates
      add constraint recruitment_candidates_offer_status_v233_check
      check (offer_status in ('NOT_SENT','SENT','ACCEPTED','DECLINED','EXPIRED','NA'));
  end if;
end $$;

alter table public.recruitment_candidates
  add column if not exists final_score numeric(5,2)
  generated always as (
    case
      when screening_score is null and interview_score is null and practical_score is null then null
      else round(
        (
          coalesce(screening_score,0) +
          coalesce(interview_score,0) +
          coalesce(practical_score,0)
        ) /
        nullif(
          (case when screening_score is null then 0 else 1 end) +
          (case when interview_score is null then 0 else 1 end) +
          (case when practical_score is null then 0 else 1 end),
          0
        ),
        2
      )
    end
  ) stored;

create table if not exists public.recruitment_evaluations (
  id uuid primary key default gen_random_uuid(),
  candidate_id uuid not null references public.recruitment_candidates(id) on delete cascade,
  evaluation_stage text not null
    check (evaluation_stage in ('SCREENING','INTERVIEW','PRACTICAL_TEST')),
  role_key text not null,
  criterion_code text not null,
  criterion_name text not null,
  weight numeric(5,2) not null check (weight >= 0 and weight <= 100),
  score numeric(5,2) check (score between 0 and 100),
  weighted_score numeric(7,2) generated always as (
    case when score is null then null else round(weight * score / 100.0, 2) end
  ) stored,
  evidence text,
  notes text,
  evaluated_by uuid references public.profiles(id) on delete set null,
  evaluated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(candidate_id, evaluation_stage, criterion_code)
);

create index if not exists recruitment_evaluations_candidate_idx
  on public.recruitment_evaluations(candidate_id, evaluation_stage);

create table if not exists public.recruitment_status_history (
  id uuid primary key default gen_random_uuid(),
  candidate_id uuid not null references public.recruitment_candidates(id) on delete cascade,
  from_status text,
  to_status text not null,
  reason text,
  actor_id uuid references public.profiles(id) on delete set null,
  changed_at timestamptz not null default now()
);

create index if not exists recruitment_status_history_candidate_idx
  on public.recruitment_status_history(candidate_id, changed_at desc);

create or replace function public.gmu_recruitment_pipeline_to_legacy_stage(p_status text)
returns text
language sql
immutable
security invoker
set search_path = ''
as $$
  select case p_status
    when 'APPLIED' then 'APPLIED'
    when 'SCREENING' then 'SCREENING'
    when 'INTERVIEW' then 'INTERVIEW'
    when 'PRACTICAL_TEST' then 'TRIAL'
    when 'SHORTLISTED' then 'OFFER'
    when 'OFFERING' then 'OFFER'
    when 'ONBOARDING' then 'HIRED'
    when 'ACTIVE' then 'HIRED'
    when 'TALENT_POOL' then 'SCREENING'
    when 'HOLD' then 'SCREENING'
    when 'REJECTED' then 'REJECTED'
    when 'INACTIVE' then 'WITHDRAWN'
    else 'APPLIED'
  end
$$;

create or replace function public.gmu_recruitment_legacy_stage_to_pipeline(p_stage text)
returns text
language sql
immutable
security invoker
set search_path = ''
as $$
  select case p_stage
    when 'APPLIED' then 'APPLIED'
    when 'SCREENING' then 'SCREENING'
    when 'INTERVIEW' then 'INTERVIEW'
    when 'TRIAL' then 'PRACTICAL_TEST'
    when 'OFFER' then 'OFFERING'
    when 'HIRED' then 'ACTIVE'
    when 'REJECTED' then 'REJECTED'
    when 'WITHDRAWN' then 'INACTIVE'
    else 'APPLIED'
  end
$$;

create or replace function public.gmu_recruitment_candidate_before_write_v233()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    if new.pipeline_status is null then
      new.pipeline_status := public.gmu_recruitment_legacy_stage_to_pipeline(new.stage);
    end if;
    new.stage := public.gmu_recruitment_pipeline_to_legacy_stage(new.pipeline_status);
  else
    if new.pipeline_status is distinct from old.pipeline_status then
      new.stage := public.gmu_recruitment_pipeline_to_legacy_stage(new.pipeline_status);
    elsif new.stage is distinct from old.stage then
      new.pipeline_status := public.gmu_recruitment_legacy_stage_to_pipeline(new.stage);
    end if;
  end if;

  if new.whatsapp is null and new.contact is not null then
    new.whatsapp := new.contact;
  elsif new.whatsapp is not null and (new.contact is null or (tg_op='UPDATE' and new.whatsapp is distinct from old.whatsapp)) then
    new.contact := new.whatsapp;
  end if;

  if new.primary_position is null and new.recruitment_case_id is not null then
    select rc.position_title into new.primary_position
    from public.recruitment_cases rc
    where rc.id=new.recruitment_case_id;
  end if;

  new.updated_at := now();
  if new.updated_by is null then
    new.updated_by := (select auth.uid());
  end if;
  return new;
end;
$$;

drop trigger if exists trg_gmu_recruitment_candidate_before_write_v233 on public.recruitment_candidates;
create trigger trg_gmu_recruitment_candidate_before_write_v233
before insert or update on public.recruitment_candidates
for each row execute function public.gmu_recruitment_candidate_before_write_v233();

create or replace function public.gmu_recruitment_candidate_status_history_v233()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    insert into public.recruitment_status_history(candidate_id,from_status,to_status,reason,actor_id)
    values(new.id,null,new.pipeline_status,'Candidate created',coalesce(new.created_by,(select auth.uid())));
  elsif new.pipeline_status is distinct from old.pipeline_status then
    insert into public.recruitment_status_history(candidate_id,from_status,to_status,reason,actor_id)
    values(new.id,old.pipeline_status,new.pipeline_status,new.next_action,(select auth.uid()));
  end if;
  return new;
end;
$$;

drop trigger if exists trg_gmu_recruitment_candidate_status_history_v233 on public.recruitment_candidates;
create trigger trg_gmu_recruitment_candidate_status_history_v233
after insert or update of pipeline_status on public.recruitment_candidates
for each row execute function public.gmu_recruitment_candidate_status_history_v233();

alter table public.recruitment_candidates enable row level security;
alter table public.recruitment_evaluations enable row level security;
alter table public.recruitment_status_history enable row level security;

drop policy if exists recruitment_candidates_admin_read_v233 on public.recruitment_candidates;
create policy recruitment_candidates_admin_read_v233
on public.recruitment_candidates for select to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid())
      and p.is_active=true
      and p.role::text='Admin'
  )
);

drop policy if exists recruitment_evaluations_manage_v233 on public.recruitment_evaluations;
create policy recruitment_evaluations_manage_v233
on public.recruitment_evaluations for all to authenticated
using (private.gmu_v200_is_management())
with check (private.gmu_v200_is_management());

drop policy if exists recruitment_evaluations_admin_read_v233 on public.recruitment_evaluations;
create policy recruitment_evaluations_admin_read_v233
on public.recruitment_evaluations for select to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid())
      and p.is_active=true
      and p.role::text='Admin'
  )
);

drop policy if exists recruitment_status_history_read_v233 on public.recruitment_status_history;
create policy recruitment_status_history_read_v233
on public.recruitment_status_history for select to authenticated
using (
  private.gmu_v200_is_management()
  or exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid())
      and p.is_active=true
      and p.role::text='Admin'
  )
);

drop policy if exists recruitment_status_history_insert_v233 on public.recruitment_status_history;
create policy recruitment_status_history_insert_v233
on public.recruitment_status_history for insert to authenticated
with check (private.gmu_v200_is_management());

grant select,insert,update,delete on public.recruitment_candidates to authenticated;
grant select,insert,update,delete on public.recruitment_evaluations to authenticated;
grant select,insert on public.recruitment_status_history to authenticated;

comment on table public.recruitment_candidates is
  'GMU EduTrans Recruitment System v23.3 candidate pipeline; backwards-compatible extension of v21.4.';
comment on table public.recruitment_evaluations is
  'Structured screening/interview/practical-test criteria and scores per candidate.';
comment on table public.recruitment_status_history is
  'Recruitment pipeline movement log generated by candidate status changes.';
