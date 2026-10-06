-- GMU EduTrans: owner-approved special agency station channel (additive only).
-- Does not alter DIRECT_PUBLIC / B2B_MOU tier rows or their resolver.
create table if not exists public.special_agency_partners (
  partner_code text primary key,
  display_name text not null,
  partner_label text not null,
  is_active boolean not null default true,
  effective_from date not null default date '2026-10-06',
  created_at timestamptz not null default now(),
  constraint special_agency_partner_code_check check (partner_code ~ '^[A-Z0-9_-]+$')
);

insert into public.special_agency_partners(partner_code,display_name,partner_label)
values
  ('DEDEN_TRAVEL','Pak Deden','Agen Tour & Travel'),
  ('HENDRY_MITRA','Pak Hendry','Mitra Khusus — afiliasi Dishub (bukan kerja sama instansi)')
on conflict (partner_code) do nothing;

alter table public.special_agency_partners enable row level security;
drop policy if exists special_agency_partners_staff_read on public.special_agency_partners;
create policy special_agency_partners_staff_read
on public.special_agency_partners for select to authenticated using (
  exists(
    select 1 from public.profiles p
    where p.id=(select auth.uid()) and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Finance','Keuangan')
  )
);
revoke all on public.special_agency_partners from anon;
revoke all on public.special_agency_partners from authenticated;
grant select on public.special_agency_partners to authenticated;

alter table public.bookings
  add column if not exists special_agency_partner_code text references public.special_agency_partners(partner_code),
  add column if not exists special_agency_commission_per_pax numeric(16,2) not null default 0,
  add column if not exists special_agency_request_key uuid;

create unique index if not exists bookings_special_agency_request_unique
  on public.bookings(special_agency_request_key) where special_agency_request_key is not null;
create index if not exists bookings_special_agency_partner_idx
  on public.bookings(special_agency_partner_code)
  where special_agency_partner_code is not null;

-- Prevent tampering with locked rates even through legacy booking forms/REST updates.
create or replace function public.gmu_guard_special_agency_booking()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare v_role text; v_active boolean;
begin
  if tg_op='UPDATE' and old.price_channel='AGENCY_SPECIAL' then
    if row(
      new.price_channel,new.package_code,new.special_agency_partner_code,
      new.price_per_pax,new.customer_sell_price_per_pax,new.pax,
      new.sales_commission_per_pax,new.special_agency_commission_per_pax,
      new.sales_id,new.pricing_snapshot,new.special_agency_request_key
    ) is distinct from row(
      old.price_channel,old.package_code,old.special_agency_partner_code,
      old.price_per_pax,old.customer_sell_price_per_pax,old.pax,
      old.sales_commission_per_pax,old.special_agency_commission_per_pax,
      old.sales_id,old.pricing_snapshot,old.special_agency_request_key
    ) then
      raise exception 'AGENCY_SPECIAL_PRICE_IMMUTABLE';
    end if;
  end if;

  if new.price_channel='AGENCY_SPECIAL' or new.special_agency_partner_code is not null then
    -- Manager/Owner must create the special booking. Later status/finance updates
    -- by authorized ERP roles are allowed, but rate fields remain immutable.
    if tg_op='INSERT' or (tg_op='UPDATE' and old.price_channel is distinct from 'AGENCY_SPECIAL') then
      select p.role::text into v_role from public.profiles p
        where p.id=(select auth.uid()) and p.is_active=true limit 1;
      if coalesce(v_role,'') not in ('Owner','Director','Direktur','Manager','Manager EduTrans') then
        raise exception 'AGENCY_SPECIAL_MANAGER_REQUIRED';
      end if;
    end if;
    -- Existing bookings remain serviceable by Finance if an agency is later disabled.
    if tg_op='UPDATE' and old.price_channel='AGENCY_SPECIAL' then
      v_active:=true;
    else
      select a.is_active into v_active from public.special_agency_partners a
        where a.partner_code=new.special_agency_partner_code
        and a.effective_from<=current_date;
    end if;
    if coalesce(v_active,false)=false
       or new.price_channel is distinct from 'AGENCY_SPECIAL'
       or new.package_code is distinct from 'STATION-PROF-2026'
       or coalesce(new.pax,0) < 20
       or new.price_per_pax is distinct from 55000
       or new.customer_sell_price_per_pax is distinct from 55000
       or new.sales_commission_per_pax is distinct from 0
       or new.special_agency_commission_per_pax is distinct from 5000
       or new.sales_id is not null then
      raise exception 'AGENCY_SPECIAL_LOCKED_RULE_VIOLATION';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_guard_special_agency_booking on public.bookings;
create trigger trg_guard_special_agency_booking
before insert or update on public.bookings
for each row execute function public.gmu_guard_special_agency_booking();
revoke all on function public.gmu_guard_special_agency_booking() from public, anon;

-- Server-side preview, never rely on client-controlled prices.
create or replace function public.gmu_special_agency_quote(
  p_partner_code text,
  p_pax integer
) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_role text; v_partner public.special_agency_partners%rowtype;
begin
  select p.role::text into v_role from public.profiles p
    where p.id=(select auth.uid()) and p.is_active=true limit 1;
  if coalesce(v_role,'') not in ('Owner','Director','Direktur','Manager','Manager EduTrans') then
    raise exception 'AGENCY_SPECIAL_MANAGER_REQUIRED';
  end if;
  if p_pax is null or p_pax<20 then raise exception 'AGENCY_SPECIAL_MIN_20'; end if;
  select * into v_partner from public.special_agency_partners
    where partner_code=p_partner_code and is_active=true
      and effective_from<=current_date;
  if not found then raise exception 'AGENCY_SPECIAL_PARTNER_NOT_ACTIVE'; end if;
  if not exists(select 1 from public.program_packages pp
    where pp.package_code='STATION-PROF-2026' and pp.is_active=true
      and pp.status='ACTIVE') then
    raise exception 'STATION_PACKAGE_NOT_ACTIVE';
  end if;
  return jsonb_build_object(
    'partner_code',v_partner.partner_code,'partner_name',v_partner.display_name,
    'package_code','STATION-PROF-2026','channel','AGENCY_SPECIAL',
    'pax',p_pax,'minimum_pax',20,
    'price_per_pax',55000,'commission_partner_per_pax',5000,
    'commission_sales_per_pax',0,'gross_total',p_pax*55000,
    'partner_commission_total',p_pax*5000,
    'gmu_after_partner_commission',p_pax*50000,
    'cost_review_required',p_pax>=40,
    'note','Harga jual kembali mitra tidak ditentukan GMU. Biaya aktual perlu verifikasi Finance.'
  );
end;
$$;
revoke all on function public.gmu_special_agency_quote(text,integer) from public, anon;
grant execute on function public.gmu_special_agency_quote(text,integer) to authenticated;

-- Create a standard ERP booking, without changing sales / B2B price books.
-- Uses a request key to avoid duplicate bookings after network retries.
create or replace function public.gmu_create_special_agency_booking(
  p_customer_id uuid,
  p_partner_code text,
  p_trip_date date,
  p_pax integer,
  p_request_key uuid default null
) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare
  v_uid uuid:=(select auth.uid());
  v_role text;
  v_quote jsonb;
  v_no text;
  v_id uuid;
  v_previous public.bookings%rowtype;
  v_snapshot jsonb;
begin
  select role::text into v_role from public.profiles
    where id=v_uid and is_active=true limit 1;
  if coalesce(v_role,'') not in ('Owner','Director','Direktur','Manager','Manager EduTrans') then
    raise exception 'AGENCY_SPECIAL_MANAGER_REQUIRED';
  end if;
  if p_customer_id is null or p_trip_date is null or p_trip_date<current_date then
    raise exception 'AGENCY_SPECIAL_INVALID_CUSTOMER_OR_DATE';
  end if;
  if p_request_key is not null then
    select * into v_previous from public.bookings b
      where b.special_agency_request_key=p_request_key;
    if found then
      if v_previous.created_by<>v_uid or v_previous.customer_id<>p_customer_id
        or v_previous.special_agency_partner_code<>p_partner_code
        or v_previous.trip_date<>p_trip_date or v_previous.pax<>p_pax then
        raise exception 'AGENCY_SPECIAL_REQUEST_KEY_CONFLICT';
      end if;
      return jsonb_build_object('ok',true,'booking_id',v_previous.id,
        'booking_no',v_previous.booking_no,'replayed',true);
    end if;
  end if;
  v_quote:=public.gmu_special_agency_quote(p_partner_code,p_pax);
  v_snapshot:=v_quote || jsonb_build_object('locked_by','gmu_create_special_agency_booking',
    'price_lock_version','AGENCY_SPECIAL_2026_10',
    'created_by',v_uid,
    'requires_finance_cost_review',true);
  v_no:=public.next_booking_no();
  insert into public.bookings(
    booking_no,customer_id,sales_id,program_name,trip_date,pax,price_per_pax,
    status,created_by,package_code,price_channel,session_type,pricing_pax,
    customer_sell_price_per_pax,school_cashback_per_pax,partner_margin_per_pax,
    sales_commission_per_pax,pricing_status,pricing_snapshot,
    special_agency_partner_code,special_agency_commission_per_pax,special_agency_request_key
  ) values (
    v_no,p_customer_id,null,'Edukasi Lingkungan & Profesi Stasiun',
    p_trip_date,p_pax,55000,'Lead',v_uid,'STATION-PROF-2026','AGENCY_SPECIAL',
    'PRIVATE',p_pax,55000,0,0,0,
    case when p_pax>=40 then 'REVIEW' else 'SAFE' end,v_snapshot,
    p_partner_code,5000,p_request_key
  ) returning id into v_id;
  return jsonb_build_object('ok',true,'booking_id',v_id,'booking_no',v_no,
    'replayed',false,'pricing',v_quote);
end;
$$;
revoke all on function public.gmu_create_special_agency_booking(uuid,text,date,integer,uuid) from public, anon;
grant execute on function public.gmu_create_special_agency_booking(uuid,text,date,integer,uuid) to authenticated;
comment on function public.gmu_create_special_agency_booking(uuid,text,date,integer,uuid)
is 'Manager/Owner-only booked special station agency; Rp55k gross incl Rp5k partner, Sales 0, 20+ pax. Finance cost check still required.';
