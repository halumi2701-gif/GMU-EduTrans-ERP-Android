drop policy if exists crm_lead_controls_read on public.crm_lead_controls;
create policy crm_lead_controls_read on public.crm_lead_controls for select to authenticated using (
  owner_id=(select auth.uid()) or exists (
    select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Admin')
  )
);

drop policy if exists crm_lead_controls_insert on public.crm_lead_controls;
create policy crm_lead_controls_insert on public.crm_lead_controls for insert to authenticated with check (
  owner_id=(select auth.uid()) or exists (
    select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Admin')
  )
);

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
  select p.id,p.full_name from public.profiles p
  where p.is_active=true and p.role::text='Sales'
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
  where q.created_at::date between p_start and p_end
  group by br.assigned_sales
)
select s.id,s.full_name,
       coalesce(l.prospects,0),coalesce(a.activities,0),coalesce(l.qualified_leads,0),coalesce(q.quotations,0),coalesce(l.won_leads,0),coalesce(l.lost_leads,0),coalesce(l.overdue_followups,0),coalesce(l.pipeline_value,0),
       case when coalesce(l.prospects,0)=0 then 0 else round((coalesce(l.won_leads,0)::numeric/coalesce(l.prospects,0)::numeric)*100,2) end
from sales_scope s
left join leads l on l.owner_id=s.id
left join acts a on a.sales_id=s.id
left join quotes q on q.sales_id=s.id
order by s.full_name;
$$;

grant execute on function public.internal_crm_sales_scorecard(date,date) to authenticated;
