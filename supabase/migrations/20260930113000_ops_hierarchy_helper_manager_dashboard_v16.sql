-- Ops hierarchy + conditional helper + Manager dashboard v1.6
alter table public.program_packages
  add column if not exists helper_required boolean not null default false,
  add column if not exists helper_trigger_pax integer not null default 31;

alter table public.operation_sheets
  add column if not exists crew_requirements jsonb not null default '{}'::jsonb,
  add column if not exists helper_required boolean not null default false,
  add column if not exists helper_reason text,
  add column if not exists crew_hierarchy_version text not null default 'v1';

CREATE OR REPLACE FUNCTION private.gmu_auto_prepare_ops_from_handover()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'private', 'pg_catalog', 'pg_temp'
AS $function$
declare
  v_br public.booking_requests%rowtype;
  v_b public.bookings%rowtype;
  v_program_name text;
  v_facilities text[] := '{}'::text[];
  v_helper_by_program boolean := false;
  v_helper_trigger_pax integer := 31;
  v_helper_required boolean := false;
  v_helper_reason text := null;
  v_crew_requirements jsonb := '{}'::jsonb;
begin
  if new.status not in ('READY_FOR_OPS','ACKNOWLEDGED','IN_PROGRESS') then
    return new;
  end if;

  if tg_op='UPDATE'
     and old.status is not distinct from new.status
     and old.booking_id is not distinct from new.booking_id then
    return new;
  end if;

  select * into v_br
  from public.booking_requests
  where id=new.booking_request_id
  limit 1;

  select * into v_b
  from public.bookings
  where id=new.booking_id
  limit 1;

  if not found then return new; end if;

  v_program_name := coalesce(
    nullif(trim(coalesce(v_b.program_name,'')),''),
    nullif(trim(coalesce(v_br.custom_program,'')),''),
    'Program GMU EduTrans'
  );

  if v_br.package_id is not null then
    select
      coalesce(pp.facilities,'{}'::text[]),
      coalesce(pp.helper_required,false),
      greatest(coalesce(pp.helper_trigger_pax,31),1)
    into v_facilities,v_helper_by_program,v_helper_trigger_pax
    from public.program_packages pp
    where pp.id=v_br.package_id
    limit 1;
  end if;

  v_helper_required := v_helper_by_program or coalesce(v_b.pax,0) >= v_helper_trigger_pax;

  v_helper_reason := case
    when v_helper_by_program and coalesce(v_b.pax,0) >= v_helper_trigger_pax
      then 'PROGRAM_REQUIRED_AND_PAX_'||v_helper_trigger_pax::text||'_PLUS'
    when v_helper_by_program
      then 'PROGRAM_REQUIRED'
    when coalesce(v_b.pax,0) >= v_helper_trigger_pax
      then 'PAX_'||v_helper_trigger_pax::text||'_PLUS'
    else null
  end;

  v_crew_requirements := jsonb_build_object(
    'hierarchy',jsonb_build_object(
      'manager',jsonb_build_object(
        'label','Manager EduTrans',
        'authority','CONTROL_ASSIGN_APPROVE_MONITOR'
      ),
      'execution_lead',jsonb_build_object(
        'label','Staff Operasional',
        'account_role','Operation',
        'reports_to','Manager EduTrans'
      )
    ),
    'field_functions',jsonb_build_array(
      jsonb_build_object(
        'code','STAFF_OPERATIONAL',
        'label','Staff Operasional',
        'required',true,
        'reports_to','Manager EduTrans',
        'account_role','Operation'
      ),
      jsonb_build_object(
        'code','TOUR_LEADER_EDUKATOR',
        'label','Tour Leader / Edukator',
        'required',true,
        'reports_to','Staff Operasional',
        'account_role','TL'
      ),
      jsonb_build_object(
        'code','DOCUMENTATION',
        'label','Dokumentasi',
        'required',true,
        'reports_to','Staff Operasional',
        'assignment_title','Dokumentasi'
      ),
      jsonb_build_object(
        'code','HELPER',
        'label','Helper',
        'required',v_helper_required,
        'reports_to','Staff Operasional',
        'assignment_title','Helper',
        'reason',v_helper_reason,
        'trigger_pax',v_helper_trigger_pax
      )
    )
  );

  insert into public.trips(booking_id,operational_progress,trip_status,updated_at)
  values(new.booking_id,10,'PREPARATION',now())
  on conflict (booking_id) do update
    set trip_status=case
          when public.trips.trip_status='DRAFT' then 'PREPARATION'
          else public.trips.trip_status
        end,
        operational_progress=greatest(public.trips.operational_progress,10),
        updated_at=now();

  insert into public.operation_sheets(
    booking_id,booking_request_id,handover_event_id,
    institution_name,pic_name,customer_whatsapp,
    program_name_snapshot,trip_date,pax,meeting_point_snapshot,
    sales_id,facilities_snapshot,special_requirements_snapshot,
    readiness_status,generated_from,generated_at,updated_at,
    crew_requirements,helper_required,helper_reason,crew_hierarchy_version
  ) values (
    new.booking_id,new.booking_request_id,new.id,
    nullif(trim(coalesce(v_br.institution_name,'')),''),
    nullif(trim(coalesce(v_br.pic_name,'')),''),
    nullif(trim(coalesce(v_br.whatsapp,'')),''),
    v_program_name,v_b.trip_date,v_b.pax,
    coalesce(
      nullif(trim(coalesce(v_b.meeting_point,'')),''),
      nullif(trim(coalesce(v_br.meeting_point,'')),'')
    ),
    new.sales_id,v_facilities,
    nullif(trim(coalesce(v_br.special_requirements,'')),''),
    'Draft','AUTO_HANDOVER',now(),now(),
    v_crew_requirements,v_helper_required,v_helper_reason,'v1'
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
        crew_requirements=excluded.crew_requirements,
        helper_required=excluded.helper_required,
        helper_reason=excluded.helper_reason,
        crew_hierarchy_version='v1',
        generated_from=case
          when public.operation_sheets.generated_from='MANUAL'
          then public.operation_sheets.generated_from
          else 'AUTO_HANDOVER'
        end,
        generated_at=coalesce(public.operation_sheets.generated_at,excluded.generated_at),
        updated_at=now();

  update public.bookings
  set status=case
        when status in ('DP'::public.booking_status,'Confirmed'::public.booking_status)
        then 'Preparation'::public.booking_status
        else status
      end,
      updated_at=now()
  where id=new.booking_id;

  update public.booking_requests
  set status=case
        when status in ('WAITING_DP','CONFIRMED','PAID') then 'PREPARATION'
        else status
      end,
      updated_at=now()
  where id=new.booking_request_id;

  if not exists(
    select 1 from public.crm_activities a
    where a.booking_request_id=new.booking_request_id
      and a.activity_type='OPS_AUTO_HANDOVER_READY'
  ) then
    insert into public.crm_activities(
      booking_request_id,sales_id,channel,activity_type,outcome,
      lead_source,notes,created_by
    ) values (
      new.booking_request_id,new.sales_id,'LAINNYA','OPS_AUTO_HANDOVER_READY',
      'Auto handover ke Manager EduTrans selesai; Operation Sheet dan struktur crew otomatis dibuat',
      'Operations Automation',
      'Booking '||new.booking_id||
      ' | Manager EduTrans > Staff Operasional > TL/Edukator + Dokumentasi + Helper'||
      case when v_helper_required then ' | Helper WAJIB: '||coalesce(v_helper_reason,'RULE') else ' | Helper tidak wajib' end,
      new.sales_id
    );
  end if;

  insert into public.audit_logs(
    user_id,action,table_name,record_id,message,new_data
  ) values (
    new.sales_id,'AUTO_OPS_HANDOVER','sales_handover_events',new.id::text,
    'Auto handover created/updated Trip, Operation Sheet, and crew requirements',
    jsonb_build_object(
      'booking_request_id',new.booking_request_id,
      'booking_id',new.booking_id,
      'handover_status',new.status,
      'operation_sheet_generated',true,
      'trip_status','PREPARATION',
      'helper_required',v_helper_required,
      'helper_reason',v_helper_reason,
      'crew_hierarchy','Manager EduTrans > Staff Operasional > TL/Edukator + Dokumentasi + Helper'
    )
  );

  return new;
end;
$function$
;

CREATE OR REPLACE FUNCTION private.trip_readiness(p_booking_id text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO 'public', 'private', 'pg_temp'
AS $function$
declare
  br public.booking_requests;
  b public.bookings;
  t public.trips;
  os public.operation_sheets;
  ci public.trip_customer_info;
  v_rundown_count integer:=0;
  v_doc_count integer:=0;
  v_payment boolean:=false;
  v_tl_assigned boolean:=false;
  v_ops_assigned boolean:=false;
  v_documentation_assigned boolean:=false;
  v_helper_assigned boolean:=false;
  v_helper_required boolean:=false;
  v_team boolean:=false;
  v_operation boolean:=false;
  v_customer_info boolean:=false;
  v_rundown boolean:=false;
  v_done integer:=0;
  v_progress integer:=0;
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
    and customer_visible=true
    and published_at is not null
    and lower(document_type) not in ('quotation','invoice','payment receipt');

  v_payment :=
    (br.id is not null and br.status in ('PREPARATION','READY','ON_TRIP','PAID','COMPLETED','CLOSED'))
    or exists(
      select 1 from public.invoices i
      where i.booking_request_id=br.id and i.invoice_type='DP' and i.status='PAID'
    );

  v_tl_assigned := t.id is not null
    and exists(
      select 1 from public.profiles p
      where p.id=t.tl_id and p.is_active=true and p.role='TL'
    );

  v_ops_assigned := t.id is not null
    and exists(
      select 1 from public.profiles p
      where p.id=t.operation_pic_id and p.is_active=true and p.role='Operation'
    );

  v_documentation_assigned := exists(
    select 1
    from public.staff_assignments sa
    join public.profiles p on p.id=sa.staff_id and p.is_active=true
    where sa.booking_id=p_booking_id
      and sa.status <> 'Cancelled'
      and lower(sa.title) like '%dokumentasi%'
  );

  v_helper_required := coalesce(os.helper_required,false);

  v_helper_assigned := not v_helper_required or exists(
    select 1
    from public.staff_assignments sa
    join public.profiles p on p.id=sa.staff_id and p.is_active=true
    where sa.booking_id=p_booking_id
      and sa.status <> 'Cancelled'
      and lower(sa.title) like '%helper%'
  );

  v_team := v_ops_assigned
    and v_tl_assigned
    and v_documentation_assigned
    and v_helper_assigned;

  v_operation:=os.id is not null and lower(coalesce(os.readiness_status,''))='ready';

  v_customer_info:=ci.id is not null
    and ci.status='PUBLISHED'
    and nullif(trim(coalesce(ci.meeting_point,b.meeting_point,'')),'') is not null
    and ci.meeting_time is not null;

  v_rundown:=v_rundown_count>0;

  v_done :=
    (case when v_payment then 1 else 0 end)+
    (case when v_team then 1 else 0 end)+
    (case when v_operation then 1 else 0 end)+
    (case when v_customer_info then 1 else 0 end)+
    (case when v_rundown then 1 else 0 end);

  v_progress:=round((v_done::numeric/5)*100);

  if t.id is not null then
    update public.trips set operational_progress=v_progress,updated_at=now() where id=t.id;
  end if;

  return jsonb_build_object(
    'all_ready',v_done=5,
    'progress',v_progress,
    'required_done',v_done,
    'required_total',5,
    'documents_published',v_doc_count,
    'customer_rundown_count',v_rundown_count,
    'crew_hierarchy',jsonb_build_object(
      'manager','Manager EduTrans',
      'execution_lead','Staff Operasional',
      'field_functions',jsonb_build_array('Tour Leader / Edukator','Dokumentasi','Helper')
    ),
    'crew_assignment',jsonb_build_object(
      'staff_operasional',v_ops_assigned,
      'tour_leader_edukator',v_tl_assigned,
      'documentation',v_documentation_assigned,
      'helper_required',v_helper_required,
      'helper',v_helper_assigned,
      'helper_reason',os.helper_reason
    ),
    'checks',jsonb_build_array(
      jsonb_build_object('key','payment_clear','label','DP terverifikasi / payment clear','required',true,'completed',v_payment),
      jsonb_build_object(
        'key','team_assigned',
        'label',case
          when v_helper_required
          then 'Staff Operasional + TL/Edukator + Dokumentasi + Helper ditugaskan'
          else 'Staff Operasional + TL/Edukator + Dokumentasi ditugaskan'
        end,
        'required',true,
        'completed',v_team
      ),
      jsonb_build_object('key','operation_ready','label','Operation Sheet siap oleh Staff Operasional','required',true,'completed',v_operation),
      jsonb_build_object('key','customer_info','label','Titik kumpul & waktu dipublikasikan','required',true,'completed',v_customer_info),
      jsonb_build_object('key','rundown_published','label','Rundown customer dipublikasikan','required',true,'completed',v_rundown),
      jsonb_build_object('key','trip_documents','label','Dokumen perjalanan customer','required',false,'completed',v_doc_count>0)
    )
  );
end;
$function$
;

CREATE OR REPLACE FUNCTION manager_control.edutrans_ops_booking_internal(p_booking_id text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  caller_id uuid := auth.uid();
  b public.bookings%rowtype;
  t public.trips%rowtype;
  os public.operation_sheets%rowtype;
  ci public.trip_customer_info%rowtype;
  v_readiness jsonb;
  v_docs int := 0;
  v_vendor_po int := 0;
  v_vendor_pending int := 0;
  v_vendor_revalidation int := 0;
  v_pending_approvals int := 0;
  v_days int;
  v_tl_name text;
  v_ops_name text;
  v_doc_name text;
  v_helper_name text;
  v_actions jsonb := '[]'::jsonb;
  v_blockers jsonb := '[]'::jsonb;
  v_risk text := 'low';
  v_all_ready boolean := false;
begin
  if caller_id is null or not exists (
    select 1 from public.profiles p
    where p.id=caller_id
      and p.is_active=true
      and p.role::text in ('Owner','Manager','Manager EduTrans')
  ) then
    raise exception 'manager edutrans or owner access required' using errcode='42501';
  end if;

  select * into b from public.bookings where id=p_booking_id;
  if not found then raise exception 'Booking not found' using errcode='P0002'; end if;

  select * into t from public.trips where booking_id=p_booking_id order by updated_at desc limit 1;
  select * into os from public.operation_sheets where booking_id=p_booking_id order by updated_at desc limit 1;
  select * into ci from public.trip_customer_info where booking_id=p_booking_id order by updated_at desc limit 1;

  v_readiness := private.trip_readiness(p_booking_id);
  v_all_ready := coalesce((v_readiness->>'all_ready')::boolean,false);
  v_days := b.trip_date-current_date;

  if t.tl_id is not null then
    select full_name into v_tl_name from public.profiles where id=t.tl_id;
  end if;
  if t.operation_pic_id is not null then
    select full_name into v_ops_name from public.profiles where id=t.operation_pic_id;
  end if;

  select p.full_name into v_doc_name
  from public.staff_assignments sa
  join public.profiles p on p.id=sa.staff_id
  where sa.booking_id=p_booking_id
    and sa.status<>'Cancelled'
    and lower(sa.title) like '%dokumentasi%'
  order by sa.created_at desc
  limit 1;

  select p.full_name into v_helper_name
  from public.staff_assignments sa
  join public.profiles p on p.id=sa.staff_id
  where sa.booking_id=p_booking_id
    and sa.status<>'Cancelled'
    and lower(sa.title) like '%helper%'
  order by sa.created_at desc
  limit 1;

  select count(*) into v_docs
  from public.documents
  where booking_id=p_booking_id
    and customer_visible=true
    and published_at is not null
    and lower(document_type) not in ('quotation','invoice','payment receipt');

  select count(*),
         count(*) filter(where lower(coalesce(status,'')) not in ('approved','issued','confirmed','paid','settled','completed','closed')),
         count(*) filter(where coalesce(revalidation_required,false))
  into v_vendor_po,v_vendor_pending,v_vendor_revalidation
  from public.vendor_pos
  where booking_id=p_booking_id;

  select count(*) into v_pending_approvals
  from public.approvals
  where booking_id=p_booking_id
    and lower(coalesce(status,'')) in ('pending','requested','waiting');

  if t.id is null then
    v_blockers:=v_blockers||jsonb_build_array(jsonb_build_object('code','TRIP_RECORD_MISSING','label','Trip record belum dibuat'));
    v_actions:=v_actions||jsonb_build_array('CREATE_TRIP_RECORD');
  end if;

  if not coalesce((select (x->>'completed')::boolean from jsonb_array_elements(v_readiness->'checks') x where x->>'key'='payment_clear' limit 1),false) then
    v_blockers:=v_blockers||jsonb_build_array(jsonb_build_object('code','PAYMENT_NOT_CLEAR','label','Pembayaran belum clear untuk operasional'));
    v_actions:=v_actions||jsonb_build_array('VERIFY_PAYMENT_CLEARANCE');
  end if;

  if not coalesce((select (x->>'completed')::boolean from jsonb_array_elements(v_readiness->'checks') x where x->>'key'='team_assigned' limit 1),false) then
    v_blockers:=v_blockers||jsonb_build_array(
      jsonb_build_object(
        'code','CREW_NOT_ASSIGNED',
        'label',case when coalesce(os.helper_required,false)
          then 'Staff Operasional / TL-Edukator / Dokumentasi / Helper belum lengkap'
          else 'Staff Operasional / TL-Edukator / Dokumentasi belum lengkap'
        end,
        'helper_required',coalesce(os.helper_required,false),
        'helper_reason',os.helper_reason
      )
    );
    v_actions:=v_actions||jsonb_build_array('ASSIGN_CREW');
  end if;

  if not coalesce((select (x->>'completed')::boolean from jsonb_array_elements(v_readiness->'checks') x where x->>'key'='operation_ready' limit 1),false) then
    v_blockers:=v_blockers||jsonb_build_array(jsonb_build_object('code','OPERATION_SHEET_NOT_READY','label','Operation Sheet belum READY oleh Staff Operasional'));
    v_actions:=v_actions||jsonb_build_array('COMPLETE_OPERATION_SHEET');
  end if;

  if not coalesce((select (x->>'completed')::boolean from jsonb_array_elements(v_readiness->'checks') x where x->>'key'='customer_info' limit 1),false) then
    v_blockers:=v_blockers||jsonb_build_array(jsonb_build_object('code','DEPARTURE_INFO_NOT_PUBLISHED','label','Info titik kumpul / waktu belum dipublikasikan'));
    v_actions:=v_actions||jsonb_build_array('PUBLISH_DEPARTURE_INFO');
  end if;

  if not coalesce((select (x->>'completed')::boolean from jsonb_array_elements(v_readiness->'checks') x where x->>'key'='rundown_published' limit 1),false) then
    v_blockers:=v_blockers||jsonb_build_array(jsonb_build_object('code','RUNDOWN_NOT_PUBLISHED','label','Rundown customer belum dipublikasikan'));
    v_actions:=v_actions||jsonb_build_array('PUBLISH_RUNDOWN');
  end if;

  if v_vendor_pending>0 or v_vendor_revalidation>0 then
    v_blockers:=v_blockers||jsonb_build_array(jsonb_build_object('code','VENDOR_PO_REVIEW','label','Vendor/PO masih perlu review','pending_po',v_vendor_pending,'revalidation',v_vendor_revalidation));
    v_actions:=v_actions||jsonb_build_array('REVIEW_VENDOR_PO');
  end if;

  if v_pending_approvals>0 then
    v_blockers:=v_blockers||jsonb_build_array(jsonb_build_object('code','APPROVAL_PENDING','label','Approval operasional masih pending','count',v_pending_approvals));
    v_actions:=v_actions||jsonb_build_array('FOLLOW_UP_APPROVAL');
  end if;

  if v_docs=0 then v_actions:=v_actions||jsonb_build_array('PREPARE_TRIP_DOCUMENTS'); end if;

  v_risk:=case
    when v_all_ready then 'ready'
    when v_days<0 then 'critical'
    when v_days<=1 then 'critical'
    when v_days<=7 then 'high'
    when v_days<=14 then 'medium'
    else 'low'
  end;

  return jsonb_build_object(
    'booking',jsonb_build_object(
      'id',b.id,'booking_no',b.booking_no,'program_name',b.program_name,
      'trip_date',b.trip_date,'days_to_trip',v_days,'pax',b.pax,
      'status',b.status::text,'meeting_point',b.meeting_point
    ),
    'risk_level',v_risk,
    'readiness',v_readiness,
    'crew',jsonb_build_object(
      'hierarchy','Manager EduTrans > Staff Operasional > TL/Edukator + Dokumentasi + Helper',
      'manager_role','Manager EduTrans',
      'execution_lead',jsonb_build_object(
        'role','Staff Operasional',
        'profile_role','Operation',
        'id',t.operation_pic_id,
        'name',v_ops_name
      ),
      'tour_leader_edukator',jsonb_build_object(
        'profile_role','TL',
        'id',t.tl_id,
        'name',v_tl_name,
        'reports_to','Staff Operasional'
      ),
      'documentation',jsonb_build_object(
        'name',v_doc_name,
        'required',true,
        'reports_to','Staff Operasional'
      ),
      'helper',jsonb_build_object(
        'name',v_helper_name,
        'required',coalesce(os.helper_required,false),
        'reason',os.helper_reason,
        'reports_to','Staff Operasional'
      )
    ),
    'operation',jsonb_build_object(
      'trip_record_exists',t.id is not null,
      'trip_status',t.trip_status,
      'operation_sheet_exists',os.id is not null,
      'operation_sheet_status',os.readiness_status,
      'operation_sheet_generated_from',os.generated_from,
      'crew_requirements',os.crew_requirements,
      'execution_owner','Staff Operasional',
      'manager_authority','Control / Assign / Approve / Monitor'
    ),
    'customer_trip_info',jsonb_build_object('exists',ci.id is not null,'status',ci.status,'published_at',ci.published_at),
    'documents',jsonb_build_object('published_customer_documents',v_docs),
    'vendors',jsonb_build_object('po_count',v_vendor_po,'pending_or_unready',v_vendor_pending,'revalidation_required',v_vendor_revalidation),
    'approvals',jsonb_build_object('pending_count',v_pending_approvals),
    'blockers',v_blockers,
    'recommended_actions',v_actions,
    'actionable',jsonb_array_length(v_actions)>0,
    'role_separation',jsonb_build_object(
      'manager_edutrans','control_assign_approve_monitor',
      'staff_operasional','execute_and_coordinate_field_team',
      'tour_leader_edukator','field_education_and_participant_flow_under_operation',
      'documentation','photo_video_archive_under_operation',
      'helper','conditional_support_under_operation'
    ),
    'generated_at',now()
  );
end;
$function$
;

CREATE OR REPLACE FUNCTION manager_control.edutrans_ops_dashboard_internal()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
  v_helper_required int := 0;
begin
  if caller_id is null or not exists (
    select 1
    from public.profiles p
    where p.id=caller_id
      and p.is_active=true
      and p.role::text in ('Owner','Manager','Manager EduTrans')
  ) then
    raise exception 'manager or owner access required' using errcode='42501';
  end if;

  select
    count(*) filter(where trip_date between current_date and current_date+7),
    count(*) filter(where trip_date between current_date and current_date+30)
  into v_upcoming7,v_upcoming30
  from public.bookings
  where lower(status::text) not in ('closed','rejected','cancelled','canceled');

  q := manager_control.edutrans_ops_action_queue_internal();

  with items as (
    select value as item
    from jsonb_array_elements(q)
  )
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
  into
    v_ready,v_critical,v_high,v_missing_crew,v_op_not_ready,
    v_info_missing,v_rundown_missing,v_vendor_block,v_pending_approval
  from items;

  select
    count(*) filter(where h.status='READY_FOR_OPS'),
    count(*) filter(where os.generated_from='AUTO_HANDOVER'),
    count(*) filter(where coalesce(os.helper_required,false)),
    coalesce(
      jsonb_agg(
        jsonb_build_object(
          'handover_id',h.id,
          'handover_status',h.status,
          'source',h.source,
          'created_at',h.created_at,
          'booking_request_id',br.id,
          'booking_id',b.id,
          'booking_no',b.booking_no,
          'institution_name',br.institution_name,
          'pic_name',br.pic_name,
          'program_name',b.program_name,
          'trip_date',b.trip_date,
          'pax',b.pax,
          'booking_status',b.status::text,
          'operation_sheet_id',os.id,
          'operation_sheet_status',os.readiness_status,
          'operation_sheet_generated_from',os.generated_from,
          'crew_hierarchy','Manager EduTrans > Staff Operasional > TL/Edukator + Dokumentasi + Helper',
          'crew_requirements',os.crew_requirements,
          'helper_required',coalesce(os.helper_required,false),
          'helper_reason',os.helper_reason,
          'trip_status',t.trip_status,
          'operational_progress',t.operational_progress
        )
        order by
          case h.status
            when 'READY_FOR_OPS' then 1
            when 'ACKNOWLEDGED' then 2
            when 'IN_PROGRESS' then 3
            else 4
          end,
          b.trip_date,
          h.created_at
      )
      filter(where h.id is not null),
      '[]'::jsonb
    )
  into v_handover_ready,v_auto_sheet,v_helper_required,handovers
  from public.sales_handover_events h
  join public.booking_requests br on br.id=h.booking_request_id
  join public.bookings b on b.id=h.booking_id
  left join public.operation_sheets os on os.booking_id=b.id
  left join public.trips t on t.booking_id=b.id
  where h.status in ('READY_FOR_OPS','ACKNOWLEDGED','IN_PROGRESS');

  begin
    sla := public.owner_customer_sla_dashboard();
  exception when others then
    sla := jsonb_build_object(
      'open_count',0,
      'urgent_count',0,
      'warning_count',0,
      'alerts','[]'::jsonb
    );
  end;

  return jsonb_build_object(
    'success',true,
    'meta',jsonb_build_object(
      'product','GMU EduTrans Manager Ops Agent',
      'version','v1.6',
      'generated_at',now(),
      'mode','AUTO_HANDOVER'
    ),
    'organization',jsonb_build_object(
      'top','Director / Owner',
      'manager','Manager EduTrans',
      'manager_scope','Control / Assign / Approve / Monitor',
      'units',jsonb_build_array(
        jsonb_build_object(
          'code','SALES',
          'label','Sales',
          'profile_role','Sales',
          'reports_to','Manager EduTrans'
        ),
        jsonb_build_object(
          'code','ADMIN',
          'label','Admin',
          'profile_role','Admin',
          'reports_to','Manager EduTrans'
        ),
        jsonb_build_object(
          'code','FINANCE',
          'label','Finance',
          'profile_role','Finance',
          'reports_to','Manager EduTrans',
          'sensitive_approval','Director / Owner sesuai limit'
        ),
        jsonb_build_object(
          'code','OPERATIONS',
          'label','Staff Operasional',
          'profile_role','Operation',
          'reports_to','Manager EduTrans',
          'field_functions',jsonb_build_array(
            jsonb_build_object('label','Tour Leader / Edukator','profile_role','TL','reports_to','Staff Operasional'),
            jsonb_build_object('label','Dokumentasi','assignment_title','Dokumentasi','reports_to','Staff Operasional'),
            jsonb_build_object('label','Helper','assignment_title','Helper','reports_to','Staff Operasional','conditional',true)
          )
        )
      )
    ),
    'summary',jsonb_build_object(
      'upcoming_7d',v_upcoming7,
      'upcoming_30d',v_upcoming30,
      'critical',v_critical,
      'high_risk',v_high,
      'ready_in_queue',v_ready,
      'handover_ready',v_handover_ready,
      'auto_operation_sheets',v_auto_sheet,
      'helper_required',v_helper_required,
      'missing_crew',v_missing_crew,
      'operation_sheet_not_ready',v_op_not_ready,
      'departure_info_missing',v_info_missing,
      'rundown_missing',v_rundown_missing,
      'vendor_review_needed',v_vendor_block,
      'pending_approvals',v_pending_approval
    ),
    'handover_queue',handovers,
    'customer_sla',sla,
    'action_queue',q,
    'agent_capabilities',jsonb_build_array(
      'DASHBOARD',
      'ACTION_QUEUE',
      'BOOKING_BRIEF',
      'CHECK_READINESS',
      'CHECK_CREW',
      'CHECK_VENDOR',
      'CHECK_DOCUMENTS',
      'AUTO_HANDOVER',
      'AUTO_OPERATION_SHEET',
      'HELPER_RULES'
    )
  );
end;
$function$
;
