-- Sales App v6: attendance, field visit, daily report.
-- Production migration applied to project gtgnwasijweewmaubvyg on 2026-09-16.

create table if not exists public.sales_visits (
  id uuid primary key default gen_random_uuid(),
  sales_id uuid not null references public.profiles(id) on delete cascade,
  booking_request_id uuid not null references public.booking_requests(id) on delete cascade,
  institution_name text not null,
  pic_name text,
  visit_status text not null default 'CHECKED_IN' check (visit_status in ('CHECKED_IN','COMPLETED','CANCELLED')),
  check_in_at timestamptz not null default now(),
  check_out_at timestamptz,
  outcome text,
  notes text,
  next_follow_up_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists sales_visits_sales_id_created_at_idx on public.sales_visits(sales_id,created_at desc);
create index if not exists sales_visits_booking_request_id_idx on public.sales_visits(booking_request_id);
alter table public.sales_visits enable row level security;
drop policy if exists sales_visits_select_own_or_management on public.sales_visits;
create policy sales_visits_select_own_or_management on public.sales_visits for select to authenticated using (
  sales_id=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Manager','Manager EduTrans','Director'))
);
drop policy if exists sales_visits_insert_own on public.sales_visits;
create policy sales_visits_insert_own on public.sales_visits for insert to authenticated with check (sales_id=(select auth.uid()));
drop policy if exists sales_visits_update_own_or_management on public.sales_visits;
create policy sales_visits_update_own_or_management on public.sales_visits for update to authenticated using (
  sales_id=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Manager','Manager EduTrans','Director'))
) with check (
  sales_id=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Manager','Manager EduTrans','Director'))
);
grant select,insert,update on public.sales_visits to authenticated;

create table if not exists public.sales_daily_reports (
  id uuid primary key default gen_random_uuid(),
  sales_id uuid not null references public.profiles(id) on delete cascade,
  report_date date not null,
  attendance_status text,
  check_in time,
  check_out time,
  new_leads integer not null default 0,
  visits_completed integer not null default 0,
  followup_activities integer not null default 0,
  quotations_created integer not null default 0,
  quotations_sent integer not null default 0,
  won_leads integer not null default 0,
  obstacles text,
  tomorrow_plan text,
  notes text,
  submitted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(sales_id,report_date)
);
alter table public.sales_daily_reports enable row level security;
drop policy if exists sales_daily_reports_select_own_or_management on public.sales_daily_reports;
create policy sales_daily_reports_select_own_or_management on public.sales_daily_reports for select to authenticated using (
  sales_id=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Manager','Manager EduTrans','Director'))
);
drop policy if exists sales_daily_reports_insert_own on public.sales_daily_reports;
create policy sales_daily_reports_insert_own on public.sales_daily_reports for insert to authenticated with check (sales_id=(select auth.uid()));
drop policy if exists sales_daily_reports_update_own_or_management on public.sales_daily_reports;
create policy sales_daily_reports_update_own_or_management on public.sales_daily_reports for update to authenticated using (
  sales_id=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Manager','Manager EduTrans','Director'))
) with check (
  sales_id=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Manager','Manager EduTrans','Director'))
);
grant select,insert,update on public.sales_daily_reports to authenticated;

create or replace function public.gmu_sales_attendance_today()
returns table(attendance_date date,status text,check_in time,check_out time,notes text)
language plpgsql security definer set search_path=public,auth as $$
declare v_uid uuid:=auth.uid(); v_role text; v_date date:=(now() at time zone 'Asia/Jakarta')::date;
begin
 select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
 if v_uid is null or v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;
 return query select sa.attendance_date,sa.status,sa.check_in,sa.check_out,sa.notes from public.staff_attendance sa where sa.staff_id=v_uid and sa.attendance_date=v_date limit 1;
end $$;

create or replace function public.gmu_sales_attendance_check_in(p_notes text default null)
returns table(attendance_date date,status text,check_in time,check_out time,notes text)
language plpgsql security definer set search_path=public,auth as $$
declare v_uid uuid:=auth.uid(); v_role text; v_date date:=(now() at time zone 'Asia/Jakarta')::date; v_time time:=(now() at time zone 'Asia/Jakarta')::time(0);
begin
 select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
 if v_uid is null or v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;
 insert into public.staff_attendance(staff_id,attendance_date,status,check_in,notes,created_by) values(v_uid,v_date,'Hadir',v_time,nullif(trim(coalesce(p_notes,'')),''),v_uid)
 on conflict(staff_id,attendance_date) do update set check_in=coalesce(public.staff_attendance.check_in,excluded.check_in),status=case when public.staff_attendance.status='Tidak Hadir' then 'Hadir' else public.staff_attendance.status end,notes=coalesce(nullif(trim(coalesce(excluded.notes,'')),''),public.staff_attendance.notes);
 return query select sa.attendance_date,sa.status,sa.check_in,sa.check_out,sa.notes from public.staff_attendance sa where sa.staff_id=v_uid and sa.attendance_date=v_date;
end $$;

create or replace function public.gmu_sales_attendance_check_out(p_notes text default null)
returns table(attendance_date date,status text,check_in time,check_out time,notes text)
language plpgsql security definer set search_path=public,auth as $$
declare v_uid uuid:=auth.uid(); v_role text; v_date date:=(now() at time zone 'Asia/Jakarta')::date; v_time time:=(now() at time zone 'Asia/Jakarta')::time(0);
begin
 select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
 if v_uid is null or v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;
 if not exists(select 1 from public.staff_attendance sa where sa.staff_id=v_uid and sa.attendance_date=v_date and sa.check_in is not null) then raise exception 'Check-in kerja belum dilakukan'; end if;
 update public.staff_attendance sa set check_out=v_time,notes=coalesce(nullif(trim(coalesce(p_notes,'')),''),sa.notes) where sa.staff_id=v_uid and sa.attendance_date=v_date;
 return query select sa.attendance_date,sa.status,sa.check_in,sa.check_out,sa.notes from public.staff_attendance sa where sa.staff_id=v_uid and sa.attendance_date=v_date;
end $$;

create or replace function public.gmu_sales_visit_check_in(p_booking_request_id uuid,p_notes text default null)
returns table(id uuid,institution_name text,check_in_at timestamptz,visit_status text)
language plpgsql security definer set search_path=public,auth as $$
declare v_uid uuid:=auth.uid(); v_role text; v_lead public.booking_requests%rowtype; v_id uuid;
begin
 select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
 if v_uid is null or v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;
 select * into v_lead from public.booking_requests br where br.id=p_booking_request_id and br.assigned_sales=v_uid;
 if not found then raise exception 'Lead not found or not assigned to this Sales'; end if;
 if exists(select 1 from public.sales_visits sv where sv.sales_id=v_uid and sv.visit_status='CHECKED_IN') then raise exception 'Selesaikan visit yang sedang aktif sebelum check-in ke lokasi lain'; end if;
 insert into public.sales_visits(sales_id,booking_request_id,institution_name,pic_name,notes) values(v_uid,v_lead.id,v_lead.institution_name,v_lead.pic_name,nullif(trim(coalesce(p_notes,'')),'')) returning sales_visits.id into v_id;
 insert into public.crm_activities(booking_request_id,sales_id,channel,activity_type,outcome,lead_source,notes,created_by) values(v_lead.id,v_uid,'KUNJUNGAN','VISIT_CHECK_IN','Sales check-in kunjungan','Sales App V6',nullif(trim(coalesce(p_notes,'')),''),v_uid);
 return query select sv.id,sv.institution_name,sv.check_in_at,sv.visit_status from public.sales_visits sv where sv.id=v_id;
end $$;

create or replace function public.gmu_sales_visit_check_out(p_visit_id uuid,p_stage text,p_outcome text default null,p_notes text default null,p_next_follow_up_at timestamptz default null)
returns table(id uuid,institution_name text,check_in_at timestamptz,check_out_at timestamptz,visit_status text)
language plpgsql security definer set search_path=public,auth as $$
declare v_uid uuid:=auth.uid(); v_role text; v_visit public.sales_visits%rowtype; v_probability integer;
begin
 select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
 if v_uid is null or v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;
 if p_stage not in ('NEW','CONTACTED','QUALIFIED','QUOTATION','NEGOTIATION','WAITING_DP','WON','LOST','NURTURE') then raise exception 'Invalid CRM stage'; end if;
 select * into v_visit from public.sales_visits sv where sv.id=p_visit_id and sv.sales_id=v_uid and sv.visit_status='CHECKED_IN';
 if not found then raise exception 'Active visit not found'; end if;
 v_probability:=case p_stage when 'NEW' then 10 when 'CONTACTED' then 20 when 'QUALIFIED' then 40 when 'QUOTATION' then 55 when 'NEGOTIATION' then 70 when 'WAITING_DP' then 85 when 'WON' then 100 when 'LOST' then 0 when 'NURTURE' then 15 else 10 end;
 update public.sales_visits sv set visit_status='COMPLETED',check_out_at=now(),outcome=nullif(trim(coalesce(p_outcome,'')),''),notes=coalesce(nullif(trim(coalesce(p_notes,'')),''),sv.notes),next_follow_up_at=p_next_follow_up_at,updated_at=now() where sv.id=v_visit.id;
 update public.crm_lead_controls clc set stage=p_stage,probability_pct=v_probability,last_contact_at=now(),next_follow_up_at=p_next_follow_up_at,updated_by=v_uid,updated_at=now() where clc.booking_request_id=v_visit.booking_request_id and clc.owner_id=v_uid;
 insert into public.crm_activities(booking_request_id,sales_id,channel,activity_type,outcome,lead_source,notes,next_follow_up_at,created_by) values(v_visit.booking_request_id,v_uid,'KUNJUNGAN','VISIT_CHECK_OUT',coalesce(nullif(trim(coalesce(p_outcome,'')),''),'Kunjungan selesai'),'Sales App V6',nullif(trim(coalesce(p_notes,'')),''),p_next_follow_up_at,v_uid);
 return query select sv.id,sv.institution_name,sv.check_in_at,sv.check_out_at,sv.visit_status from public.sales_visits sv where sv.id=v_visit.id;
end $$;

create or replace function public.gmu_sales_my_visits(p_limit integer default 20)
returns table(id uuid,booking_request_id uuid,institution_name text,pic_name text,visit_status text,check_in_at timestamptz,check_out_at timestamptz,outcome text,notes text,next_follow_up_at timestamptz)
language plpgsql security definer set search_path=public,auth as $$
declare v_uid uuid:=auth.uid(); v_role text;
begin
 select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
 if v_uid is null or v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;
 return query select sv.id,sv.booking_request_id,sv.institution_name,sv.pic_name,sv.visit_status,sv.check_in_at,sv.check_out_at,sv.outcome,sv.notes,sv.next_follow_up_at from public.sales_visits sv where sv.sales_id=v_uid order by sv.created_at desc limit greatest(1,least(coalesce(p_limit,20),100));
end $$;

create or replace function public.gmu_sales_daily_report_upsert(p_obstacles text default null,p_tomorrow_plan text default null,p_notes text default null)
returns table(id uuid,report_date date,new_leads integer,visits_completed integer,followup_activities integer,quotations_created integer,quotations_sent integer,won_leads integer,submitted_at timestamptz)
language plpgsql security definer set search_path=public,auth as $$
declare v_uid uuid:=auth.uid(); v_role text; v_date date:=(now() at time zone 'Asia/Jakarta')::date; v_att public.staff_attendance%rowtype; v_new int; v_visits int; v_follow int; v_qcreated int; v_qsent int; v_won int; v_id uuid;
begin
 select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
 if v_uid is null or v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;
 select * into v_att from public.staff_attendance sa where sa.staff_id=v_uid and sa.attendance_date=v_date;
 select count(*)::int into v_new from public.booking_requests br where br.assigned_sales=v_uid and (br.created_at at time zone 'Asia/Jakarta')::date=v_date;
 select count(*)::int into v_visits from public.sales_visits sv where sv.sales_id=v_uid and sv.visit_status='COMPLETED' and (sv.check_out_at at time zone 'Asia/Jakarta')::date=v_date;
 select count(*)::int into v_follow from public.crm_activities ca where ca.sales_id=v_uid and (ca.created_at at time zone 'Asia/Jakarta')::date=v_date;
 select count(*)::int into v_qcreated from public.quotations q where q.created_by=v_uid and (q.created_at at time zone 'Asia/Jakarta')::date=v_date;
 select count(*)::int into v_qsent from public.quotations q where q.sent_by=v_uid and q.sent_at is not null and (q.sent_at at time zone 'Asia/Jakarta')::date=v_date;
 select count(*)::int into v_won from public.crm_lead_controls c where c.owner_id=v_uid and c.stage='WON' and (c.updated_at at time zone 'Asia/Jakarta')::date=v_date;
 insert into public.sales_daily_reports(sales_id,report_date,attendance_status,check_in,check_out,new_leads,visits_completed,followup_activities,quotations_created,quotations_sent,won_leads,obstacles,tomorrow_plan,notes,submitted_at)
 values(v_uid,v_date,v_att.status,v_att.check_in,v_att.check_out,v_new,v_visits,v_follow,v_qcreated,v_qsent,v_won,nullif(trim(coalesce(p_obstacles,'')),''),nullif(trim(coalesce(p_tomorrow_plan,'')),''),nullif(trim(coalesce(p_notes,'')),''),now())
 on conflict(sales_id,report_date) do update set attendance_status=excluded.attendance_status,check_in=excluded.check_in,check_out=excluded.check_out,new_leads=excluded.new_leads,visits_completed=excluded.visits_completed,followup_activities=excluded.followup_activities,quotations_created=excluded.quotations_created,quotations_sent=excluded.quotations_sent,won_leads=excluded.won_leads,obstacles=excluded.obstacles,tomorrow_plan=excluded.tomorrow_plan,notes=excluded.notes,submitted_at=now(),updated_at=now() returning sales_daily_reports.id into v_id;
 return query select r.id,r.report_date,r.new_leads,r.visits_completed,r.followup_activities,r.quotations_created,r.quotations_sent,r.won_leads,r.submitted_at from public.sales_daily_reports r where r.id=v_id;
end $$;

create or replace function public.gmu_sales_my_daily_reports(p_limit integer default 14)
returns table(id uuid,report_date date,attendance_status text,check_in time,check_out time,new_leads integer,visits_completed integer,followup_activities integer,quotations_created integer,quotations_sent integer,won_leads integer,obstacles text,tomorrow_plan text,notes text,submitted_at timestamptz)
language plpgsql security definer set search_path=public,auth as $$
declare v_uid uuid:=auth.uid(); v_role text;
begin
 select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
 if v_uid is null or v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;
 return query select r.id,r.report_date,r.attendance_status,r.check_in,r.check_out,r.new_leads,r.visits_completed,r.followup_activities,r.quotations_created,r.quotations_sent,r.won_leads,r.obstacles,r.tomorrow_plan,r.notes,r.submitted_at from public.sales_daily_reports r where r.sales_id=v_uid order by r.report_date desc limit greatest(1,least(coalesce(p_limit,14),90));
end $$;

revoke all on function public.gmu_sales_attendance_today() from public,anon;
revoke all on function public.gmu_sales_attendance_check_in(text) from public,anon;
revoke all on function public.gmu_sales_attendance_check_out(text) from public,anon;
revoke all on function public.gmu_sales_visit_check_in(uuid,text) from public,anon;
revoke all on function public.gmu_sales_visit_check_out(uuid,text,text,text,timestamptz) from public,anon;
revoke all on function public.gmu_sales_my_visits(integer) from public,anon;
revoke all on function public.gmu_sales_daily_report_upsert(text,text,text) from public,anon;
revoke all on function public.gmu_sales_my_daily_reports(integer) from public,anon;
grant execute on function public.gmu_sales_attendance_today() to authenticated;
grant execute on function public.gmu_sales_attendance_check_in(text) to authenticated;
grant execute on function public.gmu_sales_attendance_check_out(text) to authenticated;
grant execute on function public.gmu_sales_visit_check_in(uuid,text) to authenticated;
grant execute on function public.gmu_sales_visit_check_out(uuid,text,text,text,timestamptz) to authenticated;
grant execute on function public.gmu_sales_my_visits(integer) to authenticated;
grant execute on function public.gmu_sales_daily_report_upsert(text,text,text) to authenticated;
grant execute on function public.gmu_sales_my_daily_reports(integer) to authenticated;
