-- Manager Auto Handover -> Ops Dashboard -> Operation Sheet v1.5

alter table public.operation_sheets
  add column if not exists booking_request_id uuid references public.booking_requests(id) on delete set null,
  add column if not exists handover_event_id uuid references public.sales_handover_events(id) on delete set null,
  add column if not exists institution_name text,
  add column if not exists pic_name text,
  add column if not exists customer_whatsapp text,
  add column if not exists program_name_snapshot text,
  add column if not exists trip_date date,
  add column if not exists pax integer,
  add column if not exists meeting_point_snapshot text,
  add column if not exists sales_id uuid references public.profiles(id),
  add column if not exists facilities_snapshot text[],
  add column if not exists special_requirements_snapshot text,
  add column if not exists generated_from text not null default 'MANUAL',
  add column if not exists generated_at timestamptz;

create index if not exists idx_operation_sheets_booking_request
  on public.operation_sheets(booking_request_id);
create index if not exists idx_operation_sheets_handover
  on public.operation_sheets(handover_event_id);

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
  if tg_op='UPDATE'
     and old.status is not distinct from new.status
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
        operational_progress=greatest(public.trips.operational_progress,10),
        updated_at=now();

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
    new.sales_id,v_facilities,
    nullif(trim(coalesce(v_br.special_requirements,'')),''),
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
        generated_from=case
          when public.operation_sheets.generated_from='MANUAL' then public.operation_sheets.generated_from
          else 'AUTO_HANDOVER'
        end,
        generated_at=coalesce(public.operation_sheets.generated_at,excluded.generated_at),
        updated_at=now();

  update public.bookings
  set status=case
        when status in ('DP'::public.booking_status,'Confirmed'::public.booking_status)
        then 'Preparation'::public.booking_status else status end,
      updated_at=now()
  where id=new.booking_id;

  update public.booking_requests
  set status=case when status in ('CONFIRMED','PAID') then 'PREPARATION' else status end,
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
      'booking_request_id',new.booking_request_id,
      'booking_id',new.booking_id,
      'handover_status',new.status,
      'operation_sheet_generated',true,
      'trip_status','PREPARATION'
    )
  );
  return new;
end;
$function$;

revoke all on function private.gmu_auto_prepare_ops_from_handover() from public,anon,authenticated;

drop trigger if exists trg_auto_prepare_ops_from_handover on public.sales_handover_events;
create trigger trg_auto_prepare_ops_from_handover
after insert or update of status,booking_id on public.sales_handover_events
for each row execute function private.gmu_auto_prepare_ops_from_handover();

create or replace function manager_control.edutrans_ops_dashboard_internal()
returns jsonb
language plpgsql
security definer
set search_path=''
as $function$
declare
  caller_id uuid := auth.uid();
  q jsonb;
  sla jsonb;
  handovers jsonb := '[]'::jsonb;
  v_upcoming7 int;
  v_upcoming30 int;
  v_ready int := 0;
  v_critical int := 0;
  v_high int := 0;
  v_missing_crew int := 0;
  v_op_not_ready int := 0;
  v_info_missing int := 0;
  v_rundown_missing int := 0;
  v_vendor_block int := 0;
  v_pending_approval int := 0;
  v_handover_ready int := 0;
  v_auto_sheet int := 0;
begin
  if caller_id is null or not exists (
    select 1 from public.profiles p
    where p.id=caller_id and p.is_active=true and p.role::text in ('Owner','Manager')
  ) then
    raise exception 'manager or owner access required' using errcode='42501';
  end if;

  select count(*) filter(where trip_date between current_date and current_date+7),
         count(*) filter(where trip_date between current_date and current_date+30)
  into v_upcoming7,v_upcoming30
  from public.bookings
  where lower(status::text) not in ('closed','rejected','cancelled','canceled');

  q := manager_control.edutrans_ops_action_queue_internal();

  with items as (select value as item from jsonb_array_elements(q))
  select
    count(*) filter(where item->>'risk_level'='ready'),
    count(*) filter(where item->>'risk_level'='critical'),
    count(*) filter(where item->>'risk_level'='high'),
    count(*) filter(where item->'recommended_actions' ? 'ASSIGN_CREW'),
    count(*) filter(where item->'recommended_actions' ? 'COMPLETE_OPERATION_SHEET'),
    count(*) filter(where item->'recommended_actions' ? 'PUBLISH_DEPARTURE_INFO'),
    count(*) filter(where item->'recommended_actions' ? 'PUBLISH_RUNDOWN'),
    count(*) filter(where item->'recommended_actions' ? 'REVIEW_VENDOR_PO'),
    count(*) filter(where item->'recommended_actions' ? 'FOLLOW_UP_APPROVAL')
  into v_ready,v_critical,v_high,v_missing_crew,v_op_not_ready,
       v_info_missing,v_rundown_missing,v_vendor_block,v_pending_approval
  from items;

  select
    count(*) filter(where h.status='READY_FOR_OPS'),
    count(*) filter(where os.generated_from='AUTO_HANDOVER'),
    coalesce(jsonb_agg(
      jsonb_build_object(
        'handover_id',h.id,'handover_status',h.status,'source',h.source,'created_at',h.created_at,
        'booking_request_id',br.id,'booking_id',b.id,'booking_no',b.booking_no,
        'institution_name',br.institution_name,'pic_name',br.pic_name,
        'program_name',b.program_name,'trip_date',b.trip_date,'pax',b.pax,
        'booking_status',b.status::text,'operation_sheet_id',os.id,
        'operation_sheet_status',os.readiness_status,
        'operation_sheet_generated_from',os.generated_from,
        'trip_status',t.trip_status,'operational_progress',t.operational_progress
      )
      order by case h.status when 'READY_FOR_OPS' then 1 when 'ACKNOWLEDGED' then 2 when 'IN_PROGRESS' then 3 else 4 end,
               b.trip_date,h.created_at
    ) filter(where h.id is not null),'[]'::jsonb)
  into v_handover_ready,v_auto_sheet,handovers
  from public.sales_handover_events h
  join public.booking_requests br on br.id=h.booking_request_id
  join public.bookings b on b.id=h.booking_id
  left join public.operation_sheets os on os.booking_id=b.id
  left join public.trips t on t.booking_id=b.id
  where h.status in ('READY_FOR_OPS','ACKNOWLEDGED','IN_PROGRESS');

  begin
    sla := public.owner_customer_sla_dashboard();
  exception when others then
    sla := jsonb_build_object('open_count',0,'urgent_count',0,'warning_count',0,'alerts','[]'::jsonb);
  end;

  return jsonb_build_object(
    'success',true,
    'meta',jsonb_build_object('product','GMU EduTrans Manager Ops Agent','version','v1.5','generated_at',now(),'mode','AUTO_HANDOVER'),
    'summary',jsonb_build_object(
      'upcoming_7d',v_upcoming7,'upcoming_30d',v_upcoming30,'critical',v_critical,'high_risk',v_high,
      'ready_in_queue',v_ready,'handover_ready',v_handover_ready,'auto_operation_sheets',v_auto_sheet,
      'missing_crew',v_missing_crew,'operation_sheet_not_ready',v_op_not_ready,
      'departure_info_missing',v_info_missing,'rundown_missing',v_rundown_missing,
      'vendor_review_needed',v_vendor_block,'pending_approvals',v_pending_approval
    ),
    'handover_queue',handovers,'customer_sla',sla,'action_queue',q,
    'agent_capabilities',jsonb_build_array(
      'DASHBOARD','ACTION_QUEUE','BOOKING_BRIEF','CHECK_READINESS','CHECK_CREW',
      'CHECK_VENDOR','CHECK_DOCUMENTS','AUTO_HANDOVER','AUTO_OPERATION_SHEET'
    )
  );
end;
$function$;
