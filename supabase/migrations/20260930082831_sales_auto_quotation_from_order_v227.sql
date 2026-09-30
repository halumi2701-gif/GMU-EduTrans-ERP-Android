-- Sales Auto Quotation from Order v2.27
-- Production migration: 20260930082831

create or replace function private.gmu_auto_quotation_from_booking(p_booking_id text)
returns jsonb
language plpgsql
security invoker
set search_path = 'public','private','pg_catalog','pg_temp'
as $$
declare
  v_booking public.bookings%rowtype;
  v_customer public.customers%rowtype;
  v_package public.program_packages%rowtype;
  v_request_id uuid;
  v_quote_id uuid;
  v_quote_no text;
  v_sales uuid;
  v_existing public.quotations%rowtype;
  v_price numeric;
  v_subtotal numeric;
  v_valid_days integer := 7;
  v_notes text;
  v_terms text;
  v_defaults jsonb := '{}'::jsonb;
begin
  if nullif(trim(coalesce(p_booking_id,'')),'') is null then
    return jsonb_build_object('ok',false,'created',false,'reason','BOOKING_ID_REQUIRED');
  end if;

  perform pg_advisory_xact_lock(hashtext(p_booking_id));

  select * into v_booking
  from public.bookings
  where id=p_booking_id
  for update;

  if not found then
    return jsonb_build_object('ok',false,'created',false,'reason','BOOKING_NOT_FOUND');
  end if;

  select * into v_existing
  from public.quotations
  where booking_id=v_booking.id
    and revision_no=0
    and superseded_at is null
  order by created_at desc
  limit 1;

  if found then
    return jsonb_build_object(
      'ok',true,'created',false,'reason','ALREADY_EXISTS',
      'booking_id',v_booking.id,'quotation_id',v_existing.id,
      'quotation_no',v_existing.quotation_no,'booking_request_id',v_existing.booking_request_id
    );
  end if;

  if nullif(trim(coalesce(v_booking.package_code,'')),'') is null then
    return jsonb_build_object('ok',false,'created',false,'reason','PACKAGE_CODE_MISSING','booking_id',v_booking.id);
  end if;

  select * into v_package
  from public.program_packages pp
  where pp.package_code=v_booking.package_code
    and pp.is_active=true
    and pp.status='ACTIVE'
    and (pp.effective_from is null or pp.effective_from<=v_booking.trip_date)
    and (pp.effective_until is null or pp.effective_until>=v_booking.trip_date)
  order by pp.effective_from desc nulls last, pp.updated_at desc
  limit 1;

  if not found then
    return jsonb_build_object('ok',false,'created',false,'reason','ACTIVE_PACKAGE_NOT_FOUND','booking_id',v_booking.id,'package_code',v_booking.package_code);
  end if;

  v_price := coalesce(v_booking.customer_sell_price_per_pax, v_booking.price_per_pax, 0);
  if v_price<=0 then
    return jsonb_build_object('ok',false,'created',false,'reason','CUSTOMER_PRICE_MISSING','booking_id',v_booking.id);
  end if;

  select * into v_customer
  from public.customers
  where id=v_booking.customer_id
  limit 1;

  if not found then
    return jsonb_build_object('ok',false,'created',false,'reason','CUSTOMER_NOT_FOUND','booking_id',v_booking.id);
  end if;

  select br.id into v_request_id
  from public.booking_requests br
  where br.converted_booking_id=v_booking.id
     or br.erp_lead_booking_id=v_booking.id
  order by br.created_at desc
  limit 1;

  if v_request_id is null then
    insert into public.booking_requests(
      customer_type,institution_name,pic_name,whatsapp,email,address,city,
      program_id,package_id,custom_program,trip_date,pax,companion_pax,
      participant_group,meeting_point,facilities_requested,special_requirements,
      budget_per_pax,source,consent_terms,status,assigned_sales,internal_notes,
      converted_customer_id,converted_booking_id
    ) values (
      coalesce(nullif(trim(coalesce(v_customer.customer_type,'')),''),'Umum'),
      coalesce(nullif(trim(coalesce(v_customer.name,'')),''),'Customer GMU EduTrans'),
      coalesce(nullif(trim(coalesce(v_customer.pic_name,'')),''),nullif(trim(coalesce(v_customer.name,'')),''),'PIC Customer'),
      coalesce(nullif(trim(coalesce(v_customer.whatsapp,'')),''),'-'),
      nullif(trim(coalesce(v_customer.email,'')),''),
      nullif(trim(coalesce(v_customer.address,'')),''),
      null,
      v_package.program_id,
      v_package.id,
      coalesce(nullif(trim(coalesce(v_booking.program_name,'')),''),v_package.name),
      v_booking.trip_date,
      v_booking.pax,
      0,
      nullif(trim(coalesce(v_booking.participant_group,'')),''),
      nullif(trim(coalesce(v_booking.meeting_point,'')),''),
      coalesce(v_package.facilities,'{}'::text[]),
      nullif(trim(coalesce(v_booking.special_requirements,'')),''),
      v_price,
      'Sales App',
      true,
      'QUOTATION',
      v_booking.sales_id,
      'Auto quotation dari booking '||v_booking.booking_no,
      v_booking.customer_id,
      v_booking.id
    )
    returning id into v_request_id;
  else
    update public.booking_requests
    set package_id=coalesce(package_id,v_package.id),
        program_id=coalesce(program_id,v_package.program_id),
        status=case when status in ('NEW_REQUEST','VERIFICATION') then 'QUOTATION' else status end,
        assigned_sales=coalesce(assigned_sales,v_booking.sales_id),
        converted_customer_id=coalesce(converted_customer_id,v_booking.customer_id),
        converted_booking_id=coalesce(converted_booking_id,v_booking.id),
        updated_at=now()
    where id=v_request_id;
  end if;

  select assigned_sales into v_sales
  from public.booking_requests
  where id=v_request_id;

  if v_booking.sales_id is null and v_sales is not null then
    update public.bookings
    set sales_id=v_sales, updated_at=now()
    where id=v_booking.id and sales_id is null;
  end if;

  v_defaults := private.quotation_defaults_json();
  v_valid_days := greatest(1,least(coalesce((v_defaults->>'default_valid_days')::integer,7),30));
  v_notes := coalesce(
    nullif(trim(coalesce(v_package.sales_public_summary,'')),''),
    nullif(trim(coalesce(v_defaults->>'default_notes_customer','')),''),
    'Penawaran otomatis berdasarkan order yang diterima GMU EduTrans.'
  );
  v_terms := coalesce(
    nullif(trim(coalesce(v_defaults->>'default_terms','')),''),
    'Harga mengikuti data order dan master harga aktif GMU EduTrans.'
  );
  if coalesce(cardinality(v_package.public_terms),0)>0 then
    v_terms := v_terms || E'\n\nKetentuan program:\n- ' || array_to_string(v_package.public_terms,E'\n- ');
  end if;

  v_subtotal := greatest(coalesce(v_booking.pax,0),0)::numeric * v_price;

  insert into public.quotations(
    booking_request_id,booking_id,status,valid_until,currency,subtotal,discount,tax,total,
    notes_customer,terms,created_by
  ) values (
    v_request_id,v_booking.id,'DRAFT',current_date+v_valid_days,'IDR',
    v_subtotal,0,0,v_subtotal,v_notes,v_terms,coalesce(v_sales,v_booking.created_by)
  )
  returning id,quotation_no into v_quote_id,v_quote_no;

  insert into public.quotation_items(
    quotation_id,sort_order,description,qty,unit,unit_price
  ) values (
    v_quote_id,1,v_package.name,v_booking.pax,'pax',v_price
  );

  update public.bookings
  set status=case when status='Lead'::public.booking_status then 'Quotation'::public.booking_status else status end,
      updated_at=now()
  where id=v_booking.id;

  return jsonb_build_object(
    'ok',true,'created',true,
    'booking_id',v_booking.id,'booking_no',v_booking.booking_no,
    'booking_request_id',v_request_id,'quotation_id',v_quote_id,'quotation_no',v_quote_no,
    'status','DRAFT','valid_until',current_date+v_valid_days,
    'unit_price',v_price,'total',v_subtotal,'sales_id',v_sales
  );
end;
$$;

create or replace function public.create_sales_booking_v226(
  p_customer_id uuid,
  p_package_code text,
  p_trip_date date,
  p_booking_pax integer,
  p_channel text default 'DIRECT_PUBLIC'::text,
  p_partner_id uuid default null::uuid,
  p_session_type text default 'PRIVATE'::text,
  p_pricing_pax integer default null::integer,
  p_status text default 'Lead'::text,
  p_participant_group text default null::text,
  p_meeting_point text default null::text,
  p_shared_session_code text default null::text
)
returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
declare
  v_uid uuid:=(select auth.uid());
  v_role text;
  v_price jsonb;
  v_booking_no text;
  v_booking_id uuid;
  v_program_name text;
  v_gmu_net numeric;
  v_customer_price numeric;
  v_cashback numeric;
  v_partner_margin numeric;
  v_sales_commission numeric;
  v_status text;
  v_booking_status public.booking_status;
  v_auto_quote jsonb;
begin
  if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
  select role::text into v_role from public.profiles where id=v_uid and is_active=true limit 1;
  if coalesce(v_role,'') not in ('Owner','Director','Direktur','Manager','Manager EduTrans','Admin','Sales') then raise exception 'BOOKING_CREATE_NOT_ALLOWED'; end if;

  begin
    v_booking_status := p_status::public.booking_status;
  exception when invalid_text_representation then
    raise exception 'INVALID_BOOKING_STATUS:%',p_status;
  end;

  v_price:=public.resolve_sales_price_v226(
    p_package_code,p_booking_pax,upper(p_channel),p_partner_id,upper(p_session_type),p_pricing_pax,p_trip_date
  );
  v_status:=coalesce(v_price->>'status','BLOCKED');
  if v_status='BLOCKED' or not coalesce((v_price->>'eligible')::boolean,false) then
    raise exception 'PRICING_BLOCKED:%',coalesce(v_price->>'reason','UNKNOWN');
  end if;
  if v_status='REVIEW' and coalesce(v_role,'') not in ('Owner','Director','Direktur','Manager','Manager EduTrans') then
    raise exception 'OWNER_MANAGER_APPROVAL_REQUIRED';
  end if;

  v_booking_no:=public.next_booking_no();
  v_program_name:=coalesce(v_price->>'program_name','Program GMU EduTrans');
  v_gmu_net:=coalesce((v_price->>'gmu_net_price_per_pax')::numeric,0);
  v_customer_price:=coalesce((v_price->>'customer_price_per_pax')::numeric,0);
  v_cashback:=coalesce((v_price->>'school_cashback_per_pax')::numeric,0);
  v_partner_margin:=coalesce((v_price->>'partner_margin_per_pax')::numeric,0);
  v_sales_commission:=coalesce((v_price->>'sales_commission_per_pax')::numeric,0);

  insert into public.bookings(
    booking_no,customer_id,sales_id,program_name,trip_date,pax,price_per_pax,status,
    participant_group,meeting_point,created_by,package_code,price_channel,session_type,
    pricing_pax,b2b_partner_id,customer_sell_price_per_pax,school_cashback_per_pax,
    partner_margin_per_pax,sales_commission_per_pax,pricing_status,pricing_snapshot,shared_session_code
  )
  values(
    v_booking_no,p_customer_id,
    case when v_role='Sales' then v_uid else null end,
    v_program_name,p_trip_date,p_booking_pax,v_gmu_net,v_booking_status,
    nullif(trim(coalesce(p_participant_group,'')),''),
    nullif(trim(coalesce(p_meeting_point,'')),''),
    v_uid,p_package_code,upper(p_channel),upper(p_session_type),
    coalesce(p_pricing_pax,p_booking_pax),p_partner_id,v_customer_price,v_cashback,
    v_partner_margin,v_sales_commission,v_status,v_price,
    nullif(trim(coalesce(p_shared_session_code,'')),'')
  )
  returning id into v_booking_id;

  v_auto_quote:=private.gmu_auto_quotation_from_booking(v_booking_id::text);
  if not coalesce((v_auto_quote->>'ok')::boolean,false) then
    raise exception 'AUTO_QUOTATION_FAILED:%',coalesce(v_auto_quote->>'reason','UNKNOWN');
  end if;

  return jsonb_build_object(
    'ok',true,'booking_id',v_booking_id,'booking_no',v_booking_no,'pricing',v_price,
    'auto_quotation',v_auto_quote,
    'quotation_id',v_auto_quote->>'quotation_id',
    'quotation_no',v_auto_quote->>'quotation_no'
  );
end;
$function$;
