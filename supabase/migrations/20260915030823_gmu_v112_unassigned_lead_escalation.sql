create or replace function public.internal_crm_sales_scorecard(p_start date, p_end date)
returns table(
  sales_id uuid,
  sales_name text,
  prospects bigint,
  activities bigint,
  qualified_leads bigint,
  quotations bigint,
  won_leads bigint,
  lost_leads bigint,
  overdue_followups bigint,
  pipeline_value numeric,
  conversion_pct numeric
)
language sql
security invoker
set search_path=public,pg_temp
as $$
with sales_scope as (
  select p.id,p.full_name from public.profiles p where p.is_active=true and p.role::text='Sales'
  union all
  select null::uuid,'Belum Ditugaskan'::text where exists (select 1 from public.crm_lead_controls c where c.owner_id is null)
), leads as (
  select c.owner_id,
         count(*) filter (where br.created_at::date between p_start and p_end) as prospects,
         count(*) filter (where c.stage in ('QUALIFIED','QUOTATION','NEGOTIATION','WAITING_DP','WON') and coalesce(c.won_at,c.updated_at)::date between p_start and p_end) as qualified_leads,
         count(*) filter (where c.stage='WON' and c.won_at::date between p_start and p_end) as won_leads,
         count(*) filter (where c.stage='LOST' and c.lost_at::date between p_start and p_end) as lost_leads,
         count(*) filter (where c.stage not in ('WON','LOST') and c.next_follow_up_at<now()) as overdue_followups,
         coalesce(sum(case when c.stage not in ('WON','LOST') then c.estimated_value*c.probability_pct/100 else 0 end),0) as pipeline_value
  from public.crm_lead_controls c join public.booking_requests br on br.id=c.booking_request_id
  group by c.owner_id
), acts as (
  select a.sales_id,count(*) as activities from public.crm_activities a where a.created_at::date between p_start and p_end group by a.sales_id
), quotes as (
  select br.assigned_sales as sales_id,count(distinct q.id) as quotations
  from public.quotations q join public.booking_requests br on br.id=q.booking_request_id
  where q.created_at::date between p_start and p_end group by br.assigned_sales
)
select s.id,s.full_name,
       coalesce(l.prospects,0),coalesce(a.activities,0),coalesce(l.qualified_leads,0),coalesce(q.quotations,0),coalesce(l.won_leads,0),coalesce(l.lost_leads,0),coalesce(l.overdue_followups,0),coalesce(l.pipeline_value,0),
       case when coalesce(l.prospects,0)=0 then 0 else round((coalesce(l.won_leads,0)::numeric/coalesce(l.prospects,0)::numeric)*100,2) end
from sales_scope s
left join leads l on l.owner_id is not distinct from s.id
left join acts a on a.sales_id is not distinct from s.id
left join quotes q on q.sales_id is not distinct from s.id
order by (s.id is null) desc,s.full_name;
$$;

grant execute on function public.internal_crm_sales_scorecard(date,date) to authenticated;

create or replace function private.gmu_v112_crm_time_maintenance()
returns void language plpgsql security definer set search_path=public,private,pg_temp as $$
begin
  update public.automation_tasks
     set status='OVERDUE',updated_at=now()
   where due_at < now() and status in ('OPEN','IN_PROGRESS','WAITING_APPROVAL');

  update public.crm_lead_controls
     set recovery_status='NEEDED',recovery_due_at=coalesce(recovery_due_at,now()+interval '1 day'),updated_at=now()
   where stage not in ('WON','LOST') and next_follow_up_at < now()-interval '24 hours' and recovery_status='NONE';

  insert into public.automation_tasks(task_key,booking_id,assigned_to,assigned_role,task_type,title,description,due_at,priority,status,evidence_required,approval_required)
  select 'crm-assign:'||c.booking_request_id::text,
         br.converted_booking_id,null,'Management','SALES','Tetapkan Sales/PIC Lead Baru',
         'Lead '||coalesce(br.institution_name,br.pic_name,br.booking_code,'')||' belum memiliki Sales/PIC. Tetapkan owner agar follow-up tidak terlewat.',
         coalesce(br.created_at,now())+interval '4 hours','HIGH',
         case when coalesce(br.created_at,now())+interval '4 hours' < now() then 'OVERDUE' else 'OPEN' end,false,false
    from public.crm_lead_controls c join public.booking_requests br on br.id=c.booking_request_id
   where c.owner_id is null and c.stage not in ('WON','LOST')
  on conflict (task_key) do nothing;

  insert into public.automation_tasks(task_key,booking_id,assigned_to,assigned_role,task_type,title,description,due_at,priority,status,evidence_required,approval_required)
  select 'crm-followup:'||c.booking_request_id::text||':'||extract(epoch from c.next_follow_up_at)::bigint,
         br.converted_booking_id,c.owner_id,'Sales','SALES','Tindak lanjut CRM terlambat',
         'Follow-up lead '||coalesce(br.institution_name,br.pic_name,br.booking_code,'')||' terlambat. Catat hasil kontak dan next follow-up.',
         c.next_follow_up_at,case when c.next_follow_up_at < now()-interval '24 hours' then 'HIGH' else 'NORMAL' end,'OVERDUE',false,false
    from public.crm_lead_controls c join public.booking_requests br on br.id=c.booking_request_id
   where c.owner_id is not null and c.stage not in ('WON','LOST') and c.next_follow_up_at is not null and c.next_follow_up_at < now()
  on conflict (task_key) do nothing;
end;
$$;
revoke all on function private.gmu_v112_crm_time_maintenance() from public, anon, authenticated;
