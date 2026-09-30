-- Sales customer decision + automatic DP invoice v6.5.4
-- ACCEPT => WAITING_DP + issued DP invoice
-- REJECT => REJECTED + CRM LOST
-- REVISION => revision draft (-R<n>) without overwriting the prior quotation
-- DP invoice PDF is queued asynchronously through internal-commercial-pdf.

alter table public.customer_quotation_decisions
  drop constraint if exists customer_quotation_decisions_decision_check;

alter table public.customer_quotation_decisions
  add constraint customer_quotation_decisions_decision_check
  check (decision = any (array['ACCEPT'::text,'REVISION'::text,'REJECT'::text]));

create or replace function public.gmu_sales_record_customer_quotation_decision(
  p_quotation_id uuid,
  p_decision text,
  p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = 'public','private','auth','pg_catalog','pg_temp'
as $function$
declare
  v_uid uuid := auth.uid();
  v_role text;
  v_decision text := upper(trim(coalesce(p_decision,'')));
  v_q public.quotations%rowtype;
  v_br public.booking_requests%rowtype;
  v_result jsonb := '{}'::jsonb;
  v_revision public.quotations%rowtype;
  v_change public.commercial_change_requests%rowtype;
  v_revision_no integer;
  v_base_no text;
  v_revision_no_text text;
  v_lost_reason text;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;

  select p.role::text into v_role
  from public.profiles p
  where p.id=v_uid and p.is_active=true;

  if v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;
  if v_decision not in ('ACCEPT','REJECT','REVISION') then raise exception 'Invalid customer decision'; end if;

  select q.* into v_q
  from public.quotations q
  join public.booking_requests br on br.id=q.booking_request_id
  where q.id=p_quotation_id
    and br.assigned_sales=v_uid
    and q.superseded_at is null
  for update of q;

  if not found then raise exception 'Quotation not found or not assigned to this Sales'; end if;

  select * into v_br
  from public.booking_requests
  where id=v_q.booking_request_id
  for update;

  if v_q.valid_until is not null and v_q.valid_until < current_date and v_q.status='SENT' then
    update public.quotations
    set status='EXPIRED',expired_at=coalesce(expired_at,now()),updated_at=now()
    where id=v_q.id;
    raise exception 'Quotation expired';
  end if;

  if v_decision='ACCEPT' then
    if v_q.status='ACCEPTED' then
      select jsonb_build_object(
        'ok',true,'duplicate',true,'decision','ACCEPT',
        'quotation_id',v_q.id,'quotation_no',v_q.quotation_no,
        'quotation_status','ACCEPTED',
        'invoice_no',i.invoice_no,'invoice_status',i.status,
        'invoice_total',i.total,'dp_percent',i.dp_percent,'invoice_id',i.id
      )
      into v_result
      from public.invoices i
      where i.booking_request_id=v_br.id
        and i.quotation_id=v_q.id
        and i.invoice_type='DP'
      order by i.created_at desc
      limit 1;

      return coalesce(v_result,jsonb_build_object(
        'ok',true,'duplicate',true,'decision','ACCEPT',
        'quotation_id',v_q.id,'quotation_no',v_q.quotation_no,
        'quotation_status','ACCEPTED'
      ));
    end if;

    if v_q.status <> 'SENT' then raise exception 'Only SENT quotation can be accepted'; end if;

    v_result := public.gmu_process_customer_quotation_decision(
      v_br.id,v_q.id,'ACCEPT',v_br.pic_name,p_note
    );

    return v_result || jsonb_build_object(
      'quotation_id',v_q.id,'quotation_no',v_q.quotation_no
    );
  end if;

  if v_decision='REJECT' then
    if v_q.status='REJECTED' then
      return jsonb_build_object(
        'ok',true,'duplicate',true,'decision','REJECT',
        'quotation_id',v_q.id,'quotation_no',v_q.quotation_no,
        'quotation_status','REJECTED','booking_status',v_br.status
      );
    end if;

    if v_q.status <> 'SENT' then raise exception 'Only SENT quotation can be rejected'; end if;

    v_lost_reason := coalesce(
      nullif(trim(coalesce(p_note,'')),''),
      'Customer menolak quotation'
    );

    insert into public.customer_quotation_decisions(
      booking_request_id,quotation_id,decision,customer_name,note,
      status,processed_at,created_at,updated_at
    ) values (
      v_br.id,v_q.id,'REJECT',nullif(trim(coalesce(v_br.pic_name,'')),''),
      v_lost_reason,'PROCESSED',now(),now(),now()
    )
    on conflict (quotation_id) do update
      set decision='REJECT',customer_name=excluded.customer_name,note=excluded.note,
          status='PROCESSED',processed_at=now(),updated_at=now();

    update public.quotations
    set status='REJECTED',rejected_at=coalesce(rejected_at,now()),
        rejected_by=null,rejection_reason=v_lost_reason,updated_at=now()
    where id=v_q.id;

    update public.booking_requests
    set status='REJECTED',updated_at=now()
    where id=v_br.id;

    insert into public.crm_lead_controls(
      booking_request_id,stage,owner_id,lead_source,probability_pct,
      last_contact_at,next_follow_up_at,lost_reason,lost_at,created_by,updated_by
    ) values (
      v_br.id,'LOST',v_uid,'Sales App',0,now(),null,v_lost_reason,now(),v_uid,v_uid
    )
    on conflict (booking_request_id) do update
      set stage='LOST',owner_id=v_uid,probability_pct=0,last_contact_at=now(),
          next_follow_up_at=null,lost_reason=v_lost_reason,lost_at=now(),
          won_at=null,updated_by=v_uid,updated_at=now();

    insert into public.crm_activities(
      booking_request_id,sales_id,channel,activity_type,outcome,lead_source,notes,created_by
    ) values (
      v_br.id,v_uid,'WHATSAPP','CUSTOMER_QUOTATION_REJECTED',
      'Customer menolak quotation','Sales App',v_lost_reason,v_uid
    );

    insert into public.audit_logs(user_id,action,table_name,record_id,message,new_data)
    values (
      v_uid,'CUSTOMER_REJECT_QUOTATION','quotations',v_q.id::text,
      'Sales recorded customer rejection of quotation',
      jsonb_build_object('booking_request_id',v_br.id,'quotation_no',v_q.quotation_no,'reason',v_lost_reason)
    );

    return jsonb_build_object(
      'ok',true,'decision','REJECT',
      'quotation_id',v_q.id,'quotation_no',v_q.quotation_no,
      'quotation_status','REJECTED','booking_status','REJECTED',
      'message','Penolakan customer tercatat. Lead dipindahkan ke LOST.'
    );
  end if;

  if v_q.status <> 'SENT' then
    select rq.* into v_revision
    from public.quotations rq
    where rq.revision_of_quotation_id=v_q.id and rq.superseded_at is null
    order by rq.revision_no desc,rq.created_at desc
    limit 1;

    if found then
      return jsonb_build_object(
        'ok',true,'duplicate',true,'decision','REVISION',
        'quotation_id',v_q.id,'quotation_no',v_q.quotation_no,
        'revision_quotation_id',v_revision.id,
        'revision_quotation_no',v_revision.quotation_no,
        'revision_status',v_revision.status
      );
    end if;

    raise exception 'Revision can only be requested for SENT quotation';
  end if;

  select rq.* into v_revision
  from public.quotations rq
  where rq.revision_of_quotation_id=v_q.id and rq.superseded_at is null
  order by rq.revision_no desc,rq.created_at desc
  limit 1;

  if found then
    return jsonb_build_object(
      'ok',true,'duplicate',true,'decision','REVISION',
      'quotation_id',v_q.id,'quotation_no',v_q.quotation_no,
      'revision_quotation_id',v_revision.id,
      'revision_quotation_no',v_revision.quotation_no,
      'revision_status',v_revision.status
    );
  end if;

  v_result := public.gmu_process_customer_quotation_decision(
    v_br.id,v_q.id,'REVISION',v_br.pic_name,p_note
  );

  select coalesce(max(q2.revision_no),0)+1
  into v_revision_no
  from public.quotations q2
  where q2.booking_request_id=v_br.id;

  v_base_no := regexp_replace(v_q.quotation_no,'-R[0-9]+$','');
  v_revision_no_text := v_base_no || '-R' || v_revision_no::text;

  insert into public.commercial_change_requests(
    booking_request_id,booking_id,status,source,changed_fields,
    old_snapshot,new_snapshot,reason,requested_by,requested_at,
    base_quotation_id,revised_total,updated_at
  ) values (
    v_br.id,coalesce(v_br.converted_booking_id,v_br.erp_lead_booking_id,v_q.booking_id),
    'REVISION_DRAFT','ERP',array['CUSTOMER_REVISION_REQUEST']::text[],
    jsonb_build_object(
      'quotation_id',v_q.id,'quotation_no',v_q.quotation_no,
      'total',v_q.total,'revision_no',v_q.revision_no
    ),
    jsonb_build_object(
      'requested_note',nullif(trim(coalesce(p_note,'')),''),
      'base_total',v_q.total
    ),
    coalesce(nullif(trim(coalesce(p_note,'')),''),'Customer meminta revisi quotation'),
    v_uid,now(),v_q.id,v_q.total,now()
  )
  returning * into v_change;

  insert into public.quotations(
    quotation_no,booking_request_id,booking_id,status,valid_until,currency,
    subtotal,discount,tax,total,notes_customer,terms,created_by,
    revision_no,revision_of_quotation_id,change_request_id
  ) values (
    v_revision_no_text,v_q.booking_request_id,v_q.booking_id,'DRAFT',
    current_date + greatest(1,least(coalesce(v_q.valid_until-current_date,7),30)),
    v_q.currency,v_q.subtotal,v_q.discount,v_q.tax,v_q.total,
    v_q.notes_customer,v_q.terms,v_uid,v_revision_no,v_q.id,v_change.id
  )
  returning * into v_revision;

  insert into public.quotation_items(
    quotation_id,sort_order,description,qty,unit,unit_price
  )
  select v_revision.id,qi.sort_order,qi.description,qi.qty,qi.unit,qi.unit_price
  from public.quotation_items qi
  where qi.quotation_id=v_q.id
  order by qi.sort_order,qi.created_at;

  update public.commercial_change_requests
  set revision_quotation_id=v_revision.id,revised_total=v_revision.total,updated_at=now()
  where id=v_change.id;

  update public.booking_requests
  set commercial_revision_required=true,
      commercial_revision_state='REVISION_DRAFT',
      commercial_revision_reason=coalesce(
        nullif(trim(coalesce(p_note,'')),''),
        'Customer meminta revisi quotation.'
      ),
      commercial_revision_version=coalesce(commercial_revision_version,0)+1,
      commercial_revision_updated_at=now(),
      latest_commercial_change_id=v_change.id,
      updated_at=now()
  where id=v_br.id;

  insert into public.audit_logs(user_id,action,table_name,record_id,message,new_data)
  values (
    v_uid,'CREATE_CUSTOMER_REVISION_DRAFT','quotations',v_revision.id::text,
    'Revision quotation draft created from customer request',
    jsonb_build_object(
      'base_quotation_id',v_q.id,
      'base_quotation_no',v_q.quotation_no,
      'revision_quotation_no',v_revision.quotation_no,
      'change_request_id',v_change.id,
      'note',p_note
    )
  );

  return coalesce(v_result,'{}'::jsonb) || jsonb_build_object(
    'ok',true,'decision','REVISION',
    'quotation_id',v_q.id,'quotation_no',v_q.quotation_no,
    'revision_quotation_id',v_revision.id,
    'revision_quotation_no',v_revision.quotation_no,
    'revision_status',v_revision.status,
    'change_request_id',v_change.id,
    'message','Permintaan revisi tercatat. Draft revisi baru sudah dibuat.'
  );
end;
$function$;

revoke all on function public.gmu_sales_record_customer_quotation_decision(uuid,text,text)
  from public, anon;
grant execute on function public.gmu_sales_record_customer_quotation_decision(uuid,text,text)
  to authenticated;

create or replace function private.queue_dp_invoice_pdf()
returns trigger
language plpgsql
security definer
set search_path='pg_catalog','public','private','vault','net','pg_temp'
as $function$
declare
  v_token text;
  v_request_id bigint;
begin
  if new.invoice_type <> 'DP'
     or new.status not in ('ISSUED','PARTIAL','OVERDUE','PAID') then
    return new;
  end if;

  if tg_op='UPDATE' and old.status is not distinct from new.status then
    return new;
  end if;

  select decrypted_secret into v_token
  from vault.decrypted_secrets
  where name='gmu_internal_pdf_token'
  limit 1;

  if nullif(v_token,'') is null then return new; end if;

  begin
    select net.http_post(
      url := 'https://gtgnwasijweewmaubvyg.supabase.co/functions/v1/internal-commercial-pdf',
      headers := jsonb_build_object('Content-Type','application/json'),
      body := jsonb_build_object(
        'action','invoice','id',new.id::text,'internal_token',v_token
      )
    ) into v_request_id;
  exception when others then
    null;
  end;

  return new;
end;
$function$;

revoke all on function private.queue_dp_invoice_pdf()
  from public, anon, authenticated;

drop trigger if exists trg_auto_dp_invoice_pdf on public.invoices;
create trigger trg_auto_dp_invoice_pdf
after insert or update of status on public.invoices
for each row
execute function private.queue_dp_invoice_pdf();

create or replace function private.gmu_v112_sync_lead_control()
returns trigger
language plpgsql
security definer
set search_path='public','private','pg_temp'
as $function$
declare
  v_stage text := 'NEW';
  v_status text := upper(coalesce(new.status,''));
  v_estimated numeric := greatest(coalesce(new.budget_per_pax,0) * coalesce(new.pax,0),0);
  v_dp_paid boolean := false;
begin
  if new.converted_booking_id is not null then
    select exists(
      select 1
      from public.invoices i
      where i.booking_request_id=new.id
        and i.invoice_type='DP'
        and i.status='PAID'
    ) into v_dp_paid;
  end if;

  if v_status like '%LOST%' or v_status like '%REJECT%' then
    v_stage := 'LOST';
  elsif v_dp_paid
     or v_status in ('CONFIRMED','PREPARATION','READY','ON_TRIP','PAID','COMPLETED','CLOSED') then
    v_stage := 'WON';
  elsif v_status like '%WAIT%DP%' or v_status like '%PAYMENT%' then
    v_stage := 'WAITING_DP';
  elsif v_status like '%NEGOT%' or coalesce(new.commercial_revision_required,false) then
    v_stage := 'NEGOTIATION';
  elsif v_status like '%QUOT%' then
    v_stage := 'QUOTATION';
  elsif v_status like '%QUAL%' then
    v_stage := 'QUALIFIED';
  elsif v_status like '%CONTACT%' then
    v_stage := 'CONTACTED';
  else
    v_stage := 'NEW';
  end if;

  insert into public.crm_lead_controls(
    booking_request_id,stage,owner_id,lead_source,probability_pct,estimated_value,
    won_at,lost_at,created_by,updated_by
  ) values(
    new.id,v_stage,new.assigned_sales,new.source,
    private.gmu_v112_probability_for_stage(v_stage),v_estimated,
    case when v_stage='WON' then now() end,
    case when v_stage='LOST' then now() end,
    (select auth.uid()),(select auth.uid())
  )
  on conflict (booking_request_id) do update set
    owner_id=coalesce(excluded.owner_id,public.crm_lead_controls.owner_id),
    lead_source=coalesce(excluded.lead_source,public.crm_lead_controls.lead_source),
    estimated_value=excluded.estimated_value,
    stage=case
      when public.crm_lead_controls.stage in ('LOST','NURTURE')
        and excluded.stage not in ('WON','LOST')
      then public.crm_lead_controls.stage
      else excluded.stage
    end,
    probability_pct=case
      when public.crm_lead_controls.stage in ('LOST','NURTURE')
        and excluded.stage not in ('WON','LOST')
      then public.crm_lead_controls.probability_pct
      else excluded.probability_pct
    end,
    won_at=case
      when excluded.stage='WON' then coalesce(public.crm_lead_controls.won_at,excluded.won_at)
      when excluded.stage<>'WON' then null
      else public.crm_lead_controls.won_at
    end,
    lost_at=case
      when excluded.stage='LOST' then coalesce(public.crm_lead_controls.lost_at,excluded.lost_at)
      when excluded.stage<>'LOST' then null
      else public.crm_lead_controls.lost_at
    end,
    updated_by=(select auth.uid()),
    updated_at=now();

  return new;
end;
$function$;
