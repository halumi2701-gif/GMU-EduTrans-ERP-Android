-- Sales App v6 special price requests. Sales sees only public/requested price, never HPP/margin.
create table if not exists public.sales_price_requests (
  id uuid primary key default gen_random_uuid(),
  sales_id uuid not null references public.profiles(id) on delete cascade,
  booking_request_id uuid not null references public.booking_requests(id) on delete cascade,
  package_id uuid not null references public.program_packages(id),
  pax integer not null check (pax>0),
  public_price_per_pax numeric not null check (public_price_per_pax>0),
  requested_price_per_pax numeric not null check (requested_price_per_pax>0),
  reason text not null,
  status text not null default 'PENDING' check (status in ('PENDING','APPROVED','REJECTED','REVISION','CANCELLED')),
  decision_note text,
  decided_by uuid references public.profiles(id),
  decided_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists sales_price_requests_sales_created_idx on public.sales_price_requests(sales_id,created_at desc);
create index if not exists sales_price_requests_status_idx on public.sales_price_requests(status,created_at desc);
alter table public.sales_price_requests enable row level security;
drop policy if exists sales_price_requests_select on public.sales_price_requests;
create policy sales_price_requests_select on public.sales_price_requests for select to authenticated using (
 sales_id=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Manager','Manager EduTrans','Director'))
);
drop policy if exists sales_price_requests_insert_own on public.sales_price_requests;
create policy sales_price_requests_insert_own on public.sales_price_requests for insert to authenticated with check (sales_id=(select auth.uid()));
grant select,insert on public.sales_price_requests to authenticated;

create or replace function public.gmu_sales_request_special_price(p_booking_request_id uuid,p_package_id uuid,p_requested_price_per_pax numeric,p_reason text)
returns table(id uuid,status text,public_price_per_pax numeric,requested_price_per_pax numeric)
language plpgsql security definer set search_path=public,auth as $$
declare v_uid uuid:=auth.uid(); v_role text; v_lead public.booking_requests%rowtype; v_pack public.program_packages%rowtype; v_id uuid;
begin
 select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
 if v_uid is null or v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;
 select * into v_lead from public.booking_requests br where br.id=p_booking_request_id and br.assigned_sales=v_uid;
 if not found then raise exception 'Lead not found or not assigned to this Sales'; end if;
 select * into v_pack from public.program_packages pp where pp.id=p_package_id and pp.is_active=true and pp.status='ACTIVE';
 if not found or coalesce(v_pack.price_per_pax,0)<=0 then raise exception 'Active package with public price required'; end if;
 if v_lead.program_id is not null and v_pack.program_id<>v_lead.program_id then raise exception 'Package does not belong to lead program'; end if;
 if p_requested_price_per_pax is null or p_requested_price_per_pax<=0 then raise exception 'Requested price must be greater than zero'; end if;
 if p_requested_price_per_pax>=v_pack.price_per_pax then raise exception 'Special price request is only needed below the public price'; end if;
 if coalesce(trim(p_reason),'')='' then raise exception 'Reason is required'; end if;
 insert into public.sales_price_requests(sales_id,booking_request_id,package_id,pax,public_price_per_pax,requested_price_per_pax,reason)
 values(v_uid,v_lead.id,v_pack.id,v_lead.pax,v_pack.price_per_pax,p_requested_price_per_pax,trim(p_reason)) returning sales_price_requests.id into v_id;
 insert into public.crm_activities(booking_request_id,sales_id,channel,activity_type,outcome,lead_source,notes,created_by)
 values(v_lead.id,v_uid,'LAINNYA','SPECIAL_PRICE_REQUEST','Permintaan harga khusus diajukan','Sales App V6',trim(p_reason),v_uid);
 return query select r.id,r.status,r.public_price_per_pax,r.requested_price_per_pax from public.sales_price_requests r where r.id=v_id;
end $$;

create or replace function public.gmu_sales_my_price_requests(p_limit integer default 30)
returns table(id uuid,booking_request_id uuid,institution_name text,package_name text,pax integer,public_price_per_pax numeric,requested_price_per_pax numeric,reason text,status text,decision_note text,created_at timestamptz,decided_at timestamptz)
language plpgsql security definer set search_path=public,auth as $$
declare v_uid uuid:=auth.uid(); v_role text;
begin
 select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
 if v_uid is null or v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;
 return query select r.id,r.booking_request_id,br.institution_name,pp.name,r.pax,r.public_price_per_pax,r.requested_price_per_pax,r.reason,r.status,r.decision_note,r.created_at,r.decided_at
 from public.sales_price_requests r join public.booking_requests br on br.id=r.booking_request_id join public.program_packages pp on pp.id=r.package_id
 where r.sales_id=v_uid order by r.created_at desc limit greatest(1,least(coalesce(p_limit,30),100));
end $$;
revoke all on function public.gmu_sales_request_special_price(uuid,uuid,numeric,text) from public,anon;
revoke all on function public.gmu_sales_my_price_requests(integer) from public,anon;
grant execute on function public.gmu_sales_request_special_price(uuid,uuid,numeric,text) to authenticated;
grant execute on function public.gmu_sales_my_price_requests(integer) to authenticated;
