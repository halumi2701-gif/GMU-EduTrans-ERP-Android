-- GMU EduTrans v23.3 — Recruitment System
-- Extends the existing recruitment_cases foundation with candidate-level pipeline,
-- structured evaluations, and status history. No existing recruitment data is deleted.

create table if not exists public.recruitment_candidates (
  id uuid primary key default gen_random_uuid(),
  candidate_code text not null unique,
  recruitment_case_id uuid references public.recruitment_cases(id) on delete set null,
  full_name text not null,
  whatsapp text,
  email text,
  domicile text,
  primary_position text not null,
  alternate_position text,
  employment_type text not null default 'FREELANCER'
    check (employment_type in ('FREELANCER','PART_TIME','FULL_TIME','CONTRACT','TARGET_BASED','ON_CALL')),
  availability text,
  weekend_availability text,
  current_activity text,
  experience_summary text,
  portfolio_url text,
  source text,
  pipeline_status text not null default 'APPLIED'
    check (pipeline_status in (
      'APPLIED','SCREENING','INTERVIEW','PRACTICAL_TEST','SHORTLISTED',
      'OFFERING','ONBOARDING','ACTIVE','TALENT_POOL','HOLD','REJECTED','INACTIVE'
    )),
  screening_score numeric(5,2) check (screening_score between 0 and 100),
  interview_score numeric(5,2) check (interview_score between 0 and 100),
  practical_score numeric(5,2) check (practical_score between 0 and 100),
  final_score numeric(5,2) generated always as (
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
  ) stored,
  recommendation text,
  red_flag_status text not null default 'NO'
    check (red_flag_status in ('NO','YES','NEEDS_REVIEW')),
  red_flag_notes text,
  final_decision text
    check (final_decision is null or final_decision in (
      'PROCEED','HOLD','TALENT_POOL','REJECTED','NEED_DIRECTOR_REVIEW'
    )),
  interviewer_id uuid references public.profiles(id) on delete set null,
  interview_at timestamptz,
  next_action text,
  next_action_due date,
  offer_status text not null default 'NOT_SENT'
    check (offer_status in ('NOT_SENT','SENT','ACCEPTED','DECLINED','EXPIRED','NA')),
  start_pool_date date,
  staff_profile_id uuid references public.profiles(id) on delete set null,
  drive_folder_url text,
  external_candidate_id text,
  notes text,
  created_by uuid references public.profiles(id) on delete set null,
  updated_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists recruitment_candidates_status_idx
  on public.recruitment_candidates(pipeline_status, primary_position);
create index if not exists recruitment_candidates_contact_idx
  on public.recruitment_candidates(whatsapp);
create index if not exists recruitment_candidates_case_idx
  on public.recruitment_candidates(recruitment_case_id);

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

create or replace function public.gmu_recruitment_candidate_before_update()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  new.updated_at := now();
  if new.updated_by is null then
    new.updated_by := (select auth.uid());
  end if;
  return new;
end;
$$;

drop trigger if exists trg_gmu_recruitment_candidate_before_update on public.recruitment_candidates;
create trigger trg_gmu_recruitment_candidate_before_update
before update on public.recruitment_candidates
for each row execute function public.gmu_recruitment_candidate_before_update();

create or replace function public.gmu_recruitment_candidate_status_history()
returns trigger
language plpgsql
security invoker
set search_path = public
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

drop trigger if exists trg_gmu_recruitment_candidate_status_history on public.recruitment_candidates;
create trigger trg_gmu_recruitment_candidate_status_history
after insert or update of pipeline_status on public.recruitment_candidates
for each row execute function public.gmu_recruitment_candidate_status_history();

alter table public.recruitment_candidates enable row level security;
alter table public.recruitment_evaluations enable row level security;
alter table public.recruitment_status_history enable row level security;

drop policy if exists recruitment_candidates_lead_manage on public.recruitment_candidates;
create policy recruitment_candidates_lead_manage
on public.recruitment_candidates for all to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid())
      and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans')
  )
)
with check (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid())
      and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans')
  )
);

drop policy if exists recruitment_candidates_admin_read on public.recruitment_candidates;
create policy recruitment_candidates_admin_read
on public.recruitment_candidates for select to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid())
      and p.is_active=true
      and p.role::text='Admin'
  )
);

drop policy if exists recruitment_evaluations_lead_manage on public.recruitment_evaluations;
create policy recruitment_evaluations_lead_manage
on public.recruitment_evaluations for all to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid())
      and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans')
  )
)
with check (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid())
      and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans')
  )
);

drop policy if exists recruitment_evaluations_admin_read on public.recruitment_evaluations;
create policy recruitment_evaluations_admin_read
on public.recruitment_evaluations for select to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid())
      and p.is_active=true
      and p.role::text='Admin'
  )
);

drop policy if exists recruitment_status_history_lead_read on public.recruitment_status_history;
create policy recruitment_status_history_lead_read
on public.recruitment_status_history for select to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid())
      and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Admin')
  )
);

drop policy if exists recruitment_status_history_lead_insert on public.recruitment_status_history;
create policy recruitment_status_history_lead_insert
on public.recruitment_status_history for insert to authenticated
with check (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid())
      and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans')
  )
);

grant select,insert,update,delete on public.recruitment_candidates to authenticated;
grant select,insert,update,delete on public.recruitment_evaluations to authenticated;
grant select,insert on public.recruitment_status_history to authenticated;

comment on table public.recruitment_candidates is
  'GMU EduTrans Recruitment System v23.3 candidate pipeline; source of truth for ERP recruitment.';
comment on table public.recruitment_evaluations is
  'Structured screening/interview/practical-test criteria and scores per candidate.';
comment on table public.recruitment_status_history is
  'Immutable recruitment pipeline movement log generated by candidate status changes.';
