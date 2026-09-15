-- GMU EduTrans v21 Company Autopilot
-- Production project: gtgnwasijweewmaubvyg
-- Additive automation layer: lead -> follow-up -> resources -> retention -> exception truth closure.

create extension if not exists pgcrypto;

create table if not exists public.autopilot_inbound_events (
 id uuid primary key default gen_random_uuid(), source_channel text not null, external_id text, sender_key text,
 payload jsonb not null default '{}'::jsonb, status text not null default 'RECEIVED' check(status in ('RECEIVED','MATCHED','LEAD_CREATED','IGNORED','ERROR')),
 booking_request_id uuid references public.booking_requests(id) on delete set null, received_at timestamptz not null default now(), processed_at timestamptz,
 unique(source_channel,external_id)
);

create table if not exists public.sales_followup_sequences (
 id uuid primary key default gen_random_uuid(), booking_request_id uuid not null references public.booking_requests(id) on delete cascade,
 owner_id uuid references public.profiles(id) on delete set null, state text not null default 'ACTIVE' check(state in ('ACTIVE','PAUSED','STOPPED','COMPLETED')),
 next_step_no integer not null default 1, next_due_at timestamptz, stop_reason text, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), unique(booking_request_id)
);
create table if not exists public.sales_followup_steps (
 id uuid primary key default gen_random_uuid(), sequence_id uuid not null references public.sales_followup_sequences(id) on delete cascade,
 step_no integer not null, day_offset integer not null, channel text not null check(channel in ('WHATSAPP','EMAIL','AUTO')),
 status text not null default 'PENDING' check(status in ('PENDING','QUEUED','SENT','SKIPPED','FAILED')), due_at timestamptz not null,
 customer_notification_id uuid references public.customer_notifications(id) on delete set null, sent_at timestamptz, last_error text, unique(sequence_id,step_no)
);

create table if not exists public.autopilot_resource_requests (
 id uuid primary key default gen_random_uuid(), booking_id text not null,
 resource_type text not null check(resource_type in ('CREW','VENDOR','DOCUMENT','CUSTOMER_INFO','FINANCE')),
 resource_role text, vendor_category text, quantity numeric not null default 1,
 status text not null default 'NEEDED' check(status in ('NEEDED','REQUESTED','CONFIRMED','REJECTED','REPLACEMENT_NEEDED','CANCELLED')),
 assigned_staff_id uuid references public.profiles(id) on delete set null, vendor_id text references public.vendors(id) on delete set null,
 required_by timestamptz, response_due_at timestamptz, notes text, created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 unique(booking_id,resource_type,resource_role,vendor_category)
);

create table if not exists public.customer_retention_cycles (
 id uuid primary key default gen_random_uuid(), booking_id text not null, booking_request_id uuid references public.booking_requests(id) on delete set null,
 customer_id text, stage text not null default 'THANK_YOU' check(stage in ('THANK_YOU','FEEDBACK','TESTIMONIAL','REPEAT_OPPORTUNITY','NURTURE','COMPLETED')),
 next_action_at timestamptz, score integer, repeat_probability smallint not null default 0 check(repeat_probability between 0 and 100),
 status text not null default 'ACTIVE' check(status in ('ACTIVE','PAUSED','COMPLETED')), created_at timestamptz not null default now(), updated_at timestamptz not null default now(), unique(booking_id)
);

create table if not exists public.autopilot_execution_log (
 id uuid primary key default gen_random_uuid(), run_key text not null unique, run_type text not null, status text not null,
 detail jsonb not null default '{}'::jsonb, created_at timestamptz not null default now()
);

alter table public.autopilot_inbound_events enable row level security;
alter table public.sales_followup_sequences enable row level security;
alter table public.sales_followup_steps enable row level security;
alter table public.autopilot_resource_requests enable row level security;
alter table public.customer_retention_cycles enable row level security;
alter table public.autopilot_execution_log enable row level security;

create or replace function private.gmu_v21_is_manager_or_owner() returns boolean language sql stable set search_path=public,pg_temp as $$
 select exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans'));
$$;
revoke all on function private.gmu_v21_is_manager_or_owner() from public,anon,authenticated;

create policy v21_manager_read_inbound on public.autopilot_inbound_events for select to authenticated using (private.gmu_v21_is_manager_or_owner() or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Sales','Admin')));
create policy v21_manager_read_sequences on public.sales_followup_sequences for select to authenticated using (private.gmu_v21_is_manager_or_owner() or owner_id=(select auth.uid()));
create policy v21_manager_read_steps on public.sales_followup_steps for select to authenticated using (private.gmu_v21_is_manager_or_owner() or exists(select 1 from public.sales_followup_sequences s where s.id=sales_followup_steps.sequence_id and s.owner_id=(select auth.uid())));
create policy v21_ops_read_resources on public.autopilot_resource_requests for select to authenticated using (private.gmu_v21_is_manager_or_owner() or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Operation','Admin','Finance')));
create policy v21_leader_read_retention on public.customer_retention_cycles for select to authenticated using (private.gmu_v21_is_manager_or_owner() or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Sales','Admin')));
create policy v21_owner_read_log on public.autopilot_execution_log for select to authenticated using (private.gmu_v21_is_manager_or_owner());
grant select on public.autopilot_inbound_events,public.sales_followup_sequences,public.sales_followup_steps,public.autopilot_resource_requests,public.customer_retention_cycles,public.autopilot_execution_log to authenticated;

create or replace function private.gmu_v21_pick_sales() returns uuid language sql security definer set search_path=public,pg_temp as $$
 select p.id from public.profiles p left join public.crm_lead_controls c on c.owner_id=p.id and c.stage not in ('WON','LOST')
 where p.is_active=true and p.role::text='Sales' group by p.id order by count(c.booking_request_id),p.id limit 1;
$$;
revoke all on function private.gmu_v21_pick_sales() from public,anon,authenticated;

create or replace function private.gmu_v21_seed_followup(p_request uuid,p_owner uuid) returns void language plpgsql security definer set search_path=public,private,pg_temp as $$
declare sid uuid; base timestamptz:=now();
begin
 insert into public.sales_followup_sequences(booking_request_id,owner_id,next_step_no,next_due_at) values(p_request,p_owner,1,base+interval '1 day')
 on conflict(booking_request_id) do update set owner_id=coalesce(public.sales_followup_sequences.owner_id,excluded.owner_id) returning id into sid;
 if sid is null then select id into sid from public.sales_followup_sequences where booking_request_id=p_request; end if;
 insert into public.sales_followup_steps(sequence_id,step_no,day_offset,channel,due_at) values
 (sid,1,1,'AUTO',base+interval '1 day'),(sid,2,3,'AUTO',base+interval '3 days'),(sid,3,7,'AUTO',base+interval '7 days'),(sid,4,14,'AUTO',base+interval '14 days')
 on conflict(sequence_id,step_no) do nothing;
end;$$;
revoke all on function private.gmu_v21_seed_followup(uuid,uuid) from public,anon,authenticated;

create or replace function private.gmu_v21_booking_request_autopilot() returns trigger language plpgsql security definer set search_path=public,private,pg_temp as $$
declare sales uuid;
begin
 if new.assigned_sales is null then sales:=private.gmu_v21_pick_sales(); if sales is not null then update public.booking_requests set assigned_sales=sales where id=new.id; new.assigned_sales:=sales; end if; else sales:=new.assigned_sales; end if;
 perform private.gmu_v21_seed_followup(new.id,sales); return new;
end;$$;
revoke all on function private.gmu_v21_booking_request_autopilot() from public,anon,authenticated;
drop trigger if exists trg_v21_booking_request_autopilot on public.booking_requests;
create trigger trg_v21_booking_request_autopilot after insert on public.booking_requests for each row execute function private.gmu_v21_booking_request_autopilot();

create or replace function private.gmu_v21_resource_seed() returns trigger language plpgsql security definer set search_path=public,private,pg_temp as $$
declare td date; bid text:=new.id;
begin
 if lower(new.status::text)='confirmed' then td:=new.trip_date;
   insert into public.autopilot_resource_requests(booking_id,resource_type,resource_role,required_by,response_due_at,notes) values
   (bid,'CREW','TL',coalesce(td::timestamptz,now()+interval '7 days')-interval '1 day',coalesce(td::timestamptz,now()+interval '7 days')-interval '3 days','Auto kebutuhan TL'),
   (bid,'CREW','Operation',coalesce(td::timestamptz,now()+interval '7 days')-interval '1 day',coalesce(td::timestamptz,now()+interval '7 days')-interval '3 days','Auto kebutuhan operasional'),
   (bid,'DOCUMENT','Trip Folder',coalesce(td::timestamptz,now()+interval '7 days')-interval '1 day',coalesce(td::timestamptz,now()+interval '7 days')-interval '3 days','Trip Folder, rundown, operation sheet, manifest'),
   (bid,'CUSTOMER_INFO','H-1',coalesce(td::timestamptz,now()+interval '7 days')-interval '1 day',coalesce(td::timestamptz,now()+interval '7 days')-interval '1 day','Final info pelanggan') on conflict do nothing;
 end if; return new;
end;$$;
revoke all on function private.gmu_v21_resource_seed() from public,anon,authenticated;
drop trigger if exists trg_v21_resource_seed on public.bookings;
create trigger trg_v21_resource_seed after insert or update of status on public.bookings for each row execute function private.gmu_v21_resource_seed();

create or replace function private.gmu_v21_retention_seed() returns trigger language plpgsql security definer set search_path=public,private,pg_temp as $$
declare req uuid; cust text;
begin
 select br.id,br.converted_customer_id into req,cust from public.booking_requests br where br.converted_booking_id=new.booking_id order by br.updated_at desc limit 1;
 insert into public.customer_retention_cycles(booking_id,booking_request_id,customer_id,stage,next_action_at,repeat_probability)
 values(new.booking_id,req,cust,'THANK_YOU',now()+interval '1 day',case when coalesce(new.margin_pct,0)>=25 then 60 else 40 end) on conflict(booking_id) do nothing;
 return new;
end;$$;
revoke all on function private.gmu_v21_retention_seed() from public,anon,authenticated;
drop trigger if exists trg_v21_retention_seed on public.trip_closings;
create trigger trg_v21_retention_seed after insert on public.trip_closings for each row execute function private.gmu_v21_retention_seed();

create or replace function private.gmu_v21_enqueue_notification(p_req uuid,p_event text,p_title text,p_message text) returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare nid uuid;
begin insert into public.customer_notifications(booking_request_id,event_key,category,severity,title,message,is_read,customer_visible,event_at)
 values(p_req,p_event,'AUTOPILOT','INFO',p_title,p_message,false,true,now()) returning id into nid; return nid; end;$$;
revoke all on function private.gmu_v21_enqueue_notification(uuid,text,text,text) from public,anon,authenticated;

create or replace function private.gmu_v21_tick() returns void language plpgsql security definer set search_path=public,private,pg_temp as $$
declare r record; req record; nid uuid; st text;
begin
 for r in select fs.*,s.booking_request_id,s.owner_id from public.sales_followup_steps fs join public.sales_followup_sequences s on s.id=fs.sequence_id where fs.status='PENDING' and fs.due_at<=now() and s.state='ACTIVE' order by fs.due_at limit 100 loop
   select br.*,coalesce(c.stage,'NEW') as crm_stage into req from public.booking_requests br left join public.crm_lead_controls c on c.booking_request_id=br.id where br.id=r.booking_request_id; st:=upper(coalesce(req.crm_stage,''));
   if req.converted_booking_id is not null or st in ('WON','LOST') then update public.sales_followup_steps set status='SKIPPED' where id=r.id; update public.sales_followup_sequences set state='STOPPED',stop_reason=case when st='LOST' then 'LOST' else 'BOOKING/WON' end,updated_at=now() where id=r.sequence_id;
   else nid:=private.gmu_v21_enqueue_notification(r.booking_request_id,'AUTO_FOLLOWUP_D'||r.day_offset,'Tindak lanjut GMU EduTrans','Halo '||coalesce(req.pic_name,req.institution_name,'Bapak/Ibu')||', kami menindaklanjuti rencana kegiatan edukasi bersama GMU EduTrans. Apakah ada hal yang ingin kami bantu terkait program, tanggal, peserta, atau penawaran?'); update public.sales_followup_steps set status='QUEUED',customer_notification_id=nid where id=r.id; end if;
 end loop;
 for r in select * from public.customer_retention_cycles where status='ACTIVE' and next_action_at<=now() order by next_action_at limit 100 loop
   if r.booking_request_id is not null then
     if r.stage='THANK_YOU' then nid:=private.gmu_v21_enqueue_notification(r.booking_request_id,'AFTER_TRIP_THANK_YOU','Terima kasih dari GMU EduTrans','Terima kasih telah berkegiatan bersama GMU EduTrans. Kami berharap perjalanan edukasinya berkesan.'); update public.customer_retention_cycles set stage='FEEDBACK',next_action_at=now()+interval '1 day',updated_at=now() where id=r.id;
     elsif r.stage='FEEDBACK' then nid:=private.gmu_v21_enqueue_notification(r.booking_request_id,'AFTER_TRIP_FEEDBACK','Bagikan feedback kegiatan','Mohon bantu kami dengan feedback singkat agar layanan GMU EduTrans terus membaik.'); update public.customer_retention_cycles set stage='TESTIMONIAL',next_action_at=now()+interval '3 days',updated_at=now() where id=r.id;
     elsif r.stage='TESTIMONIAL' then update public.customer_retention_cycles set stage='REPEAT_OPPORTUNITY',next_action_at=now()+interval '10 days',updated_at=now() where id=r.id;
     elsif r.stage='REPEAT_OPPORTUNITY' then insert into public.automation_tasks(task_key,booking_id,assigned_role,task_type,title,description,due_at,priority,status,evidence_required,approval_required) values('v21-repeat:'||r.booking_id,r.booking_id,'Sales','SALES','Peluang repeat order','Hubungi customer untuk program berikutnya, referral, atau kalender kegiatan baru.',now()+interval '1 day','NORMAL','OPEN',false,false) on conflict(task_key) do nothing; update public.customer_retention_cycles set stage='NURTURE',next_action_at=now()+interval '60 days',updated_at=now() where id=r.id;
     elsif r.stage='NURTURE' then nid:=private.gmu_v21_enqueue_notification(r.booking_request_id,'RETENTION_NURTURE','Program edukasi berikutnya','Halo, kami dari GMU EduTrans. Bila sekolah/instansi sedang menyusun agenda edukasi berikutnya, kami siap membantu rekomendasi program yang sesuai.'); update public.customer_retention_cycles set next_action_at=now()+interval '90 days',updated_at=now() where id=r.id; end if;
   end if;
 end loop;
 update public.enterprise_exception_queue e set status='RESOLVED',resolution='AUTO-CLOSED v21: follow-up source data healthy',resolved_at=now(),updated_at=now()
 where e.status in ('OPEN','IN_PROGRESS','WAITING') and e.exception_type='FOLLOWUP_OVERDUE' and not exists(select 1 from public.crm_lead_controls c where c.stage not in ('WON','LOST') and c.next_follow_up_at<now());
 insert into public.autopilot_execution_log(run_key,run_type,status,detail) values('tick:'||to_char(date_trunc('minute',now()),'YYYYMMDDHH24MI'),'AUTOPILOT_TICK','SUCCESS',jsonb_build_object('at',now())) on conflict(run_key) do nothing;
end;$$;
revoke all on function private.gmu_v21_tick() from public,anon,authenticated;

create or replace function public.internal_company_autopilot_v21() returns jsonb language sql security definer set search_path=public,private,pg_temp as $$
 select jsonb_build_object('version','v21-company-autopilot','automation',jsonb_build_object(
  'active_followup_sequences',(select count(*) from public.sales_followup_sequences where state='ACTIVE'),
  'followup_due',(select count(*) from public.sales_followup_steps where status='PENDING' and due_at<=now()),
  'resource_requests_open',(select count(*) from public.autopilot_resource_requests where status in ('NEEDED','REQUESTED','REPLACEMENT_NEEDED')),
  'retention_active',(select count(*) from public.customer_retention_cycles where status='ACTIVE'),
  'notifications_queued',(select count(*) from public.customer_notification_deliveries where status in ('PENDING','RETRY')),
  'payment_orders_pending',(select count(*) from public.payment_gateway_orders where status not in ('PAID','FAILED','EXPIRED','CANCELLED'))),
 'manager_score',jsonb_build_object('open_recovery',(select count(*) from public.automation_tasks where task_type='RECOVERY_ACTION' and status not in ('DONE','CANCELLED')),'overdue_tasks',(select count(*) from public.automation_tasks where status='OVERDUE'),'open_exceptions',(select count(*) from public.enterprise_exception_queue where status in ('OPEN','IN_PROGRESS','WAITING'))),
 'director',jsonb_build_object('critical_exceptions',(select count(*) from public.enterprise_exception_queue where status in ('OPEN','IN_PROGRESS','WAITING') and severity='CRITICAL' and assigned_role in ('Owner','Director','Direktur')),'action_required',exists(select 1 from public.enterprise_exception_queue where status in ('OPEN','IN_PROGRESS','WAITING') and severity='CRITICAL' and assigned_role in ('Owner','Director','Direktur'))));
$$;
revoke all on function public.internal_company_autopilot_v21() from public,anon;
grant execute on function public.internal_company_autopilot_v21() to authenticated;

select cron.unschedule(jobid) from cron.job where jobname='gmu_v21_company_autopilot_tick';
select cron.schedule('gmu_v21_company_autopilot_tick','*/15 * * * *','select private.gmu_v21_tick();');