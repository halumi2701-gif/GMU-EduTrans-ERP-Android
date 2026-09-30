-- Manager Ops handover readiness v1.5.1

create or replace function private.trip_readiness(p_booking_id text)
returns jsonb
language plpgsql
set search_path='public','private','pg_temp'
as $function$
declare
  br public.booking_requests;
  b public.bookings;
  t public.trips;
  os public.operation_sheets;
  ci public.trip_customer_info;
  v_rundown_count integer := 0;
  v_doc_count integer := 0;
  v_payment boolean := false;
  v_team boolean := false;
  v_operation boolean := false;
  v_customer_info boolean := false;
  v_rundown boolean := false;
  v_done integer := 0;
  v_progress integer := 0;
begin
  select * into b from public.bookings where id=p_booking_id;
  if not found then raise exception 'Booking not found'; end if;

  select * into br
  from public.booking_requests
  where converted_booking_id=p_booking_id or erp_lead_booking_id=p_booking_id
  order by created_at desc limit 1;

  select * into t from public.trips where booking_id=p_booking_id;
  select * into os from public.operation_sheets where booking_id=p_booking_id;
  select * into ci from public.trip_customer_info where booking_id=p_booking_id;

  select count(*) into v_rundown_count
  from public.rundown_items
  where booking_id=p_booking_id and audience='CUSTOMER' and customer_visible=true;

  select count(*) into v_doc_count
  from public.documents
  where booking_id=p_booking_id
    and customer_visible=true and published_at is not null
    and lower(document_type) not in ('quotation','invoice','payment receipt');

  v_payment :=
    (br.id is not null and br.status in ('PREPARATION','READY','ON_TRIP','PAID','COMPLETED','CLOSED'))
    or exists(
      select 1 from public.invoices i
      where i.booking_request_id=br.id and i.invoice_type='DP' and i.status='PAID'
    );

  v_team := t.id is not null
    and exists(select 1 from public.profiles p where p.id=t.tl_id and p.is_active=true and p.role='TL')
    and exists(select 1 from public.profiles p where p.id=t.operation_pic_id and p.is_active=true and p.role in ('Operation','Manager','Owner'));

  v_operation := os.id is not null and lower(coalesce(os.readiness_status,''))='ready';

  v_customer_info := ci.id is not null
    and ci.status='PUBLISHED'
    and nullif(trim(coalesce(ci.meeting_point,b.meeting_point,'')),'') is not null
    and ci.meeting_time is not null;

  v_rundown := v_rundown_count > 0;

  v_done :=
    (case when v_payment then 1 else 0 end) +
    (case when v_team then 1 else 0 end) +
    (case when v_operation then 1 else 0 end) +
    (case when v_customer_info then 1 else 0 end) +
    (case when v_rundown then 1 else 0 end);

  v_progress := round((v_done::numeric/5)*100);

  if t.id is not null then
    update public.trips set operational_progress=v_progress,updated_at=now() where id=t.id;
  end if;

  return jsonb_build_object(
    'all_ready',v_done=5,'progress',v_progress,'required_done',v_done,'required_total',5,
    'documents_published',v_doc_count,'customer_rundown_count',v_rundown_count,
    'checks',jsonb_build_array(
      jsonb_build_object('key','payment_clear','label','DP terverifikasi / payment clear','required',true,'completed',v_payment),
      jsonb_build_object('key','team_assigned','label','TL dan Operation PIC ditugaskan','required',true,'completed',v_team),
      jsonb_build_object('key','operation_ready','label','Operation Sheet siap','required',true,'completed',v_operation),
      jsonb_build_object('key','customer_info','label','Titik kumpul & waktu dipublikasikan','required',true,'completed',v_customer_info),
      jsonb_build_object('key','rundown_published','label','Rundown customer dipublikasikan','required',true,'completed',v_rundown),
      jsonb_build_object('key','trip_documents','label','Dokumen perjalanan customer','required',false,'completed',v_doc_count>0)
    )
  );
end;
$function$;

create or replace function private.gmu_auto_prepare_ops_from_handover()
returns trigger
language plpgsql
security definer
set search_path='public','private','pg_catalog','pg_temp'
as $function$
declare
  v_br public.booking_requests%rowtype;
  v_b public.bookings%rowtype;
  v_program_name text;
  v_facilities text[] := '{}'::text[];
begin
  if new.status not in ('READY_FOR_OPS','ACKNOWLEDGED','IN_PROGRESS') then return new; end if;
  if tg_op='UPDATE' and old.status is not distinct from new.status
     and old.booking_id is not distinct from new.booking_id then return new; end if;

  select * into v_br from public.booking_requests where id=new.booking_request_id limit 1;
  select * into v_b from public.bookings where id=new.booking_id limit 1;
  if not found then return new; end if;

  v_program_name := coalesce(
    nullif(trim(coalesce(v_b.program_name,'')),''),
    nullif(trim(coalesce(v_br.custom_program,'')),''),
    'Program GMU EduTrans'
  );
  if v_br.package_id is not null then
    select coalesce(pp.facilities,'{}'::text[]) into v_facilities
    from public.program_packages pp where pp.id=v_br.package_id limit 1;
  end if;

  insert into public.trips(booking_id,operational_progress,trip_status,updated_at)
  values(new.booking_id,10,'PREPARATION',now())
  on conflict (booking_id) do update
    set trip_status=case when public.trips.trip_status='DRAFT' then 'PREPARATION' else public.trips.trip_status end,
        operational_progress=greatest(public.trips.operational_progress,10),updated_at=now();

  insert into public.operation_sheets(
    booking_id,booking_request_id,handover_event_id,institution_name,pic_name,
    customer_whatsapp,program_name_snapshot,trip_date,pax,meeting_point_snapshot,
    sales_id,facilities_snapshot,special_requirements_snapshot,readiness_status,
    generated_from,generated_at,updated_at
  ) values (
    new.booking_id,new.booking_request_id,new.id,
    nullif(trim(coalesce(v_br.institution_name,'')),''),
    nullif(trim(coalesce(v_br.pic_name,'')),''),
    nullif(trim(coalesce(v_br.whatsapp,'')),''),
    v_program_name,v_b.trip_date,v_b.pax,
    coalesce(nullif(trim(coalesce(v_b.meeting_point,'')),''),nullif(trim(coalesce(v_br.meeting_point,'')),'')),
    new.sales_id,v_facilities,nullif(trim(coalesce(v_br.special_requirements,'')),''),
    'Draft','AUTO_HANDOVER',now(),now()
  )
  on conflict (booking_id) do update
    set booking_request_id=excluded.booking_request_id,
        handover_event_id=excluded.handover_event_id,
        institution_name=excluded.institution_name,
        pic_name=excluded.pic_name,
        customer_whatsapp=excluded.customer_whatsapp,
        program_name_snapshot=excluded.program_name_snapshot,
        trip_date=excluded.trip_date,
        pax=excluded.pax,
        meeting_point_snapshot=excluded.meeting_point_snapshot,
        sales_id=excluded.sales_id,
        facilities_snapshot=excluded.facilities_snapshot,
        special_requirements_snapshot=excluded.special_requirements_snapshot,
        generated_from=case when public.operation_sheets.generated_from='MANUAL'
                            then public.operation_sheets.generated_from else 'AUTO_HANDOVER' end,
        generated_at=coalesce(public.operation_sheets.generated_at,excluded.generated_at),
        updated_at=now();

  update public.bookings
  set status=case when status in ('DP'::public.booking_status,'Confirmed'::public.booking_status)
                  then 'Preparation'::public.booking_status else status end,
      updated_at=now()
  where id=new.booking_id;

  update public.booking_requests
  set status=case when status in ('WAITING_DP','CONFIRMED','PAID') then 'PREPARATION' else status end,
      updated_at=now()
  where id=new.booking_request_id;

  if not exists(
    select 1 from public.crm_activities a
    where a.booking_request_id=new.booking_request_id and a.activity_type='OPS_AUTO_HANDOVER_READY'
  ) then
    insert into public.crm_activities(
      booking_request_id,sales_id,channel,activity_type,outcome,lead_source,notes,created_by
    ) values (
      new.booking_request_id,new.sales_id,'LAINNYA','OPS_AUTO_HANDOVER_READY',
      'Auto handover ke Manager/Ops selesai; Trip dan Operation Sheet otomatis dibuat',
      'Operations Automation','Booking '||new.booking_id||' siap diproses Manager/Ops',new.sales_id
    );
  end if;

  insert into public.audit_logs(user_id,action,table_name,record_id,message,new_data)
  values (
    new.sales_id,'AUTO_OPS_HANDOVER','sales_handover_events',new.id::text,
    'Auto handover created/updated Trip and Operation Sheet',
    jsonb_build_object(
      'booking_request_id',new.booking_request_id,'booking_id',new.booking_id,
      'handover_status',new.status,'operation_sheet_generated',true,'trip_status','PREPARATION'
    )
  );
  return new;
end;
$function$;
