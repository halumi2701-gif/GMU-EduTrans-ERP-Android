-- GMU EduTrans ERP v11.1 — Event Orchestrator
-- Applied to production project gtgnwasijweewmaubvyg as migration 20260915023153.

create or replace function private.gmu_v111_touch_updated_at()
returns trigger language plpgsql set search_path = public, pg_temp as $$
begin new.updated_at := now(); return new; end;
$$;
revoke all on function private.gmu_v111_touch_updated_at() from public, anon, authenticated;

create or replace function private.gmu_v111_create_task(
  p_task_key text,p_source_event_id uuid,p_booking_id text,p_assigned_role text,p_assigned_to uuid,
  p_task_type text,p_title text,p_description text,p_due_at timestamptz,p_priority text default 'NORMAL',
  p_evidence_required boolean default false,p_approval_required boolean default false
) returns void language plpgsql security definer set search_path = public, private, pg_temp as $$
begin
  insert into public.automation_tasks(task_key,source_event_id,booking_id,assigned_role,assigned_to,task_type,title,description,due_at,priority,evidence_required,approval_required)
  values(p_task_key,p_source_event_id,p_booking_id,p_assigned_role,p_assigned_to,p_task_type,p_title,p_description,p_due_at,p_priority,p_evidence_required,p_approval_required)
  on conflict (task_key) do nothing;
end;
$$;
revoke all on function private.gmu_v111_create_task(text,uuid,text,text,uuid,text,text,text,timestamptz,text,boolean,boolean) from public, anon, authenticated;

create or replace function private.gmu_v111_emit_automation_event()
returns trigger language plpgsql security definer set search_path = public, private, pg_temp as $$
declare
  v_new jsonb := '{}'::jsonb; v_old jsonb := '{}'::jsonb; v_event_type text;
  v_entity_id text; v_booking_id text; v_key text;
begin
  if tg_op='INSERT' then v_new:=to_jsonb(new);
  elsif tg_op='UPDATE' then v_new:=to_jsonb(new); v_old:=to_jsonb(old);
  else v_old:=to_jsonb(old); end if;

  if tg_table_name='bookings' then
    if tg_op='INSERT' then v_event_type:='booking.created';
    elsif (v_old->>'status') is distinct from (v_new->>'status') then v_event_type:='booking.status.'||lower(v_new->>'status'); end if;
  elsif tg_table_name='quotations' then
    if tg_op='INSERT' then v_event_type:='quotation.created';
    elsif nullif(v_old->>'accepted_at','') is null and nullif(v_new->>'accepted_at','') is not null then v_event_type:='quotation.accepted';
    elsif nullif(v_old->>'rejected_at','') is null and nullif(v_new->>'rejected_at','') is not null then v_event_type:='quotation.rejected';
    elsif nullif(v_old->>'sent_at','') is null and nullif(v_new->>'sent_at','') is not null then v_event_type:='quotation.sent';
    elsif (v_old->>'status') is distinct from (v_new->>'status') then v_event_type:='quotation.status.'||lower(v_new->>'status'); end if;
  elsif tg_table_name='invoices' then
    if tg_op='INSERT' then v_event_type:='invoice.created';
    elsif nullif(v_old->>'paid_at','') is null and nullif(v_new->>'paid_at','') is not null then v_event_type:='invoice.paid';
    elsif nullif(v_old->>'issued_at','') is null and nullif(v_new->>'issued_at','') is not null then v_event_type:='invoice.issued';
    elsif (v_old->>'status') is distinct from (v_new->>'status') then v_event_type:='invoice.status.'||lower(v_new->>'status'); end if;
  elsif tg_table_name='payments' then
    if tg_op='INSERT' then v_event_type:='payment.recorded';
    elsif nullif(v_old->>'verified_at','') is null and nullif(v_new->>'verified_at','') is not null then v_event_type:='payment.verified'; end if;
  elsif tg_table_name='trips' then
    if tg_op='INSERT' then v_event_type:='trip.created';
    elsif nullif(v_old->>'completed_at','') is null and nullif(v_new->>'completed_at','') is not null then v_event_type:='trip.completed';
    elsif (v_old->>'trip_status') is distinct from (v_new->>'trip_status') then v_event_type:='trip.status.'||lower(v_new->>'trip_status'); end if;
  elsif tg_table_name='trip_closings' and tg_op='INSERT' then v_event_type:='trip.closed';
  elsif tg_table_name='customer_support_tickets' then
    if tg_op='INSERT' then v_event_type:='complaint.created';
    elsif (v_old->>'status') is distinct from (v_new->>'status') then v_event_type:='complaint.status.'||lower(v_new->>'status'); end if;
  elsif tg_table_name='customer_trip_feedback' and tg_op='INSERT' then v_event_type:='feedback.received';
  elsif tg_table_name='staff_assignments' then
    if tg_op='INSERT' then v_event_type:='staff.assignment_created';
    elsif (v_old->>'status') is distinct from (v_new->>'status') then v_event_type:='staff.assignment_status.'||lower(v_new->>'status'); end if;
  elsif tg_table_name='approvals' then
    if tg_op='INSERT' then v_event_type:='approval.requested';
    elsif (v_old->>'status') is distinct from (v_new->>'status') then v_event_type:='approval.status.'||lower(v_new->>'status'); end if;
  elsif tg_table_name='trip_costs' and tg_op='UPDATE' then
    if coalesce((v_old->>'actual_finalized')::boolean,false)=false and coalesce((v_new->>'actual_finalized')::boolean,false)=true then v_event_type:='trip_cost.actual_finalized'; end if;
  elsif tg_table_name='staff_leave' then
    if tg_op='INSERT' then v_event_type:='leave.requested';
    elsif (v_old->>'status') is distinct from (v_new->>'status') then v_event_type:='leave.status.'||lower(v_new->>'status'); end if;
  elsif tg_table_name='staff_reviews' and tg_op='INSERT' then v_event_type:='performance.reviewed';
  end if;

  if v_event_type is null then return coalesce(new,old); end if;
  v_entity_id:=coalesce(v_new->>'id',v_old->>'id','unknown');
  if tg_table_name='bookings' then v_booking_id:=v_entity_id;
  else v_booking_id:=coalesce(nullif(v_new->>'booking_id',''),nullif(v_old->>'booking_id','')); end if;
  v_key:=tg_table_name||':'||v_entity_id||':'||v_event_type||':'||md5(coalesce(v_old::text,'')||'|'||coalesce(v_new::text,''));
  insert into public.automation_events(event_key,event_type,entity_type,entity_id,booking_id,actor_id,payload,occurred_at)
  values(v_key,v_event_type,tg_table_name,v_entity_id,v_booking_id,(select auth.uid()),jsonb_build_object('op',tg_op,'old',v_old,'new',v_new),now())
  on conflict(event_key) do nothing;
  return coalesce(new,old);
exception when others then
  raise warning 'GMU v11.1 automation event emit failed on %.%: %',tg_table_name,tg_op,sqlerrm;
  return coalesce(new,old);
end;
$$;
revoke all on function private.gmu_v111_emit_automation_event() from public, anon, authenticated;

create or replace function private.gmu_v111_process_automation_event()
returns trigger language plpgsql security definer set search_path = public, private, pg_temp as $$
declare v_trip_date date; v_sales_id uuid; v_priority text; v_score integer; v_margin numeric; v_due timestamptz;
begin
  if new.booking_id is not null then select b.trip_date,b.sales_id into v_trip_date,v_sales_id from public.bookings b where b.id=new.booking_id limit 1; end if;

  if new.event_type='quotation.accepted' then
    perform private.gmu_v111_create_task(new.id::text||':issue-dp',new.id,new.booking_id,'Finance',null,'FINANCE','Terbitkan invoice/DP','Penawaran diterima. Siapkan invoice DP dan pastikan termin pembayaran.',now()+interval '1 day','HIGH',true,false);
    perform private.gmu_v111_create_task(new.id::text||':sales-handoff',new.id,new.booking_id,'Sales',v_sales_id,'SALES','Handoff pelanggan ke persiapan','Pastikan data pelanggan lengkap dan next action tercatat.',now()+interval '1 day','NORMAL',false,false);
  elsif new.event_type='booking.status.confirmed' then
    perform private.gmu_v111_create_task(new.id::text||':finance-dp',new.id,new.booking_id,'Finance',null,'FINANCE','Verifikasi DP dan piutang','Pastikan DP terverifikasi, invoice tercatat, dan sisa piutang memiliki jatuh tempo.',now()+interval '1 day','HIGH',true,false);
    v_due:=case when v_trip_date is null then now()+interval '2 days' else greatest(now()+interval '1 hour',v_trip_date::timestamptz-interval '7 days') end;
    perform private.gmu_v111_create_task(new.id::text||':ops-h7',new.id,new.booking_id,'Operation',null,'OPERATION','H-7 • Operation Sheet & vendor','Lengkapi Operation Sheet, vendor, crew awal, manifest, dan kebutuhan dokumen.',v_due,'HIGH',true,false);
    v_due:=case when v_trip_date is null then now()+interval '3 days' else greatest(now()+interval '2 hours',v_trip_date::timestamptz-interval '3 days') end;
    perform private.gmu_v111_create_task(new.id::text||':ops-h3',new.id,new.booking_id,'Operation',null,'OPERATION','H-3 • Konfirmasi crew & vendor','Pastikan semua crew menerima penugasan dan vendor terkonfirmasi.',v_due,'HIGH',true,false);
    v_due:=case when v_trip_date is null then now()+interval '4 days' else greatest(now()+interval '3 hours',v_trip_date::timestamptz-interval '1 day') end;
    perform private.gmu_v111_create_task(new.id::text||':manager-h1',new.id,new.booking_id,'Management',null,'CONTROL','H-1 • Final readiness review','Review kesiapan customer, crew, vendor, dokumen, cash, safety dan margin.',v_due,'CRITICAL',true,true);
    perform private.gmu_v111_create_task(new.id::text||':admin-h1',new.id,new.booking_id,'Admin',null,'CUSTOMER','H-1 • Konfirmasi pelanggan','Kirim info keberangkatan/final reminder dan pastikan kebutuhan khusus terkonfirmasi.',v_due,'HIGH',true,false);
  elsif new.event_type='payment.verified' then
    if upper(coalesce(new.payload#>>'{new,payment_type}',''))='DP' then perform private.gmu_v111_create_task(new.id::text||':activate-prep',new.id,new.booking_id,'Operation',null,'OPERATION','Aktifkan persiapan operasional','DP sudah terverifikasi. Mulai persiapan trip sesuai H-7/H-3/H-1.',now()+interval '2 hours','HIGH',false,false); end if;
  elsif new.event_type='invoice.issued' then
    v_due:=coalesce((nullif(new.payload#>>'{new,due_date}',''))::date::timestamptz,now()+interval '3 days')-interval '1 day';
    perform private.gmu_v111_create_task(new.id::text||':collection',new.id,new.booking_id,'Finance',null,'COLLECTION','Pantau pembayaran invoice','Pantau jatuh tempo, reminder pembayaran, dan eskalasi piutang.',v_due,'NORMAL',false,false);
  elsif new.event_type='trip.completed' then
    perform private.gmu_v111_create_task(new.id::text||':ops-report',new.id,new.booking_id,'Operation',null,'OPERATION','H+1 • Laporan operasional','Lengkapi laporan trip, dokumentasi, incident, dan bukti operasional.',now()+interval '1 day','HIGH',true,false);
    perform private.gmu_v111_create_task(new.id::text||':finance-actual',new.id,new.booking_id,'Finance',null,'FINANCE','H+1 • Finalisasi biaya aktual','Finalisasi RAB vs aktual, vendor bill, reimbursement, dan cash impact.',now()+interval '1 day','HIGH',true,false);
    perform private.gmu_v111_create_task(new.id::text||':manager-close',new.id,new.booking_id,'Management',null,'CONTROL','H+3 • Closing kegiatan','Review margin, variance, vendor settlement, laporan, feedback, dan tutup kegiatan.',now()+interval '3 days','CRITICAL',true,true);
    perform private.gmu_v111_create_task(new.id::text||':feedback',new.id,new.booking_id,'Admin',null,'CUSTOMER','H+1 • Minta feedback pelanggan','Kirim form feedback/CSAT dan dokumentasikan tindak lanjut.',now()+interval '1 day','NORMAL',false,false);
  elsif new.event_type='trip.closed' then
    perform private.gmu_v111_create_task(new.id::text||':repeat-order',new.id,new.booking_id,'Sales',v_sales_id,'SALES','Tindak lanjut repeat order','Hubungi kembali pelanggan, catat peluang lanjutan, referral, atau program berikutnya.',now()+interval '14 days','NORMAL',false,false);
    perform private.gmu_v111_create_task(new.id::text||':finance-close',new.id,new.booking_id,'Finance',null,'FINANCE','Final posting & rekonsiliasi','Pastikan jurnal, piutang, hutang vendor, kas, dan dokumen closing konsisten.',now()+interval '3 days','HIGH',true,false);
    v_margin:=nullif(new.payload#>>'{new,margin_pct}','')::numeric;
    if v_margin is not null and v_margin<20 then insert into public.ai_action_drafts(rule_key,booking_id,category,severity,title,rationale,recommended_action,owner_role,requires_approval) values('margin_below_20',new.booking_id,'PROFITABILITY','CRITICAL','Margin kegiatan di bawah 20%','Trip ditutup dengan margin '||round(v_margin,2)||'%.','Analisis penyebab variance, harga, vendor, fee, dan biaya aktual; siapkan recovery plan untuk Manager/Direktur.','Management',true) on conflict do nothing; end if;
  elsif new.event_type='complaint.created' then
    v_priority:=upper(coalesce(new.payload#>>'{new,priority}','NORMAL'));
    perform private.gmu_v111_create_task(new.id::text||':complaint-response',new.id,new.booking_id,'Management',null,'QUALITY','Respons keluhan & service recovery','Tentukan PIC, respons pelanggan, root cause dan kebutuhan CAPA.',case when v_priority in('HIGH','CRITICAL','URGENT') then now()+interval '4 hours' else now()+interval '1 day' end,case when v_priority in('HIGH','CRITICAL','URGENT') then 'CRITICAL' else 'HIGH' end,true,false);
    if v_priority in('HIGH','CRITICAL','URGENT') then
      insert into public.capa_cases(ticket_id,booking_id,severity,due_at,status,created_by) values((new.entity_id)::uuid,new.booking_id,case when v_priority in('CRITICAL','URGENT') then 'CRITICAL' else 'HIGH' end,now()+interval '3 days','OPEN',new.actor_id) on conflict do nothing;
      insert into public.ai_action_drafts(rule_key,booking_id,category,severity,title,rationale,recommended_action,owner_role,requires_approval) values('critical_complaint',new.booking_id,'QUALITY','CRITICAL','Keluhan prioritas tinggi','Terdapat keluhan pelanggan dengan prioritas '||v_priority||'.','Siapkan service recovery, root cause, CAPA, owner, SLA, dan bukti penutupan.','Management',true);
    end if;
  elsif new.event_type='feedback.received' then
    v_score:=nullif(new.payload#>>'{new,overall_score}','')::integer;
    if v_score is not null and v_score<=3 then
      perform private.gmu_v111_create_task(new.id::text||':low-feedback',new.id,new.booking_id,'Management',null,'QUALITY','Feedback rendah • tindak lanjut','Skor pelanggan <= 3. Lakukan service recovery dan analisis penyebab.',now()+interval '1 day','HIGH',true,false);
      insert into public.capa_cases(booking_id,severity,root_cause,due_at,status,created_by) values(new.booking_id,case when v_score<=2 then 'CRITICAL' else 'HIGH' end,'Belum dianalisis — dipicu otomatis dari feedback pelanggan.',now()+interval '5 days','OPEN',new.actor_id);
    end if;
  elsif new.event_type='trip_cost.actual_finalized' then
    perform private.gmu_v111_create_task(new.id::text||':margin-review',new.id,new.booking_id,'Management',null,'CONTROL','Review margin setelah biaya aktual','Biaya aktual difinalisasi. Tinjau variance RAB, margin, dan kebutuhan approval/mitigasi.',now()+interval '1 day','HIGH',true,false);
  elsif new.event_type='approval.requested' then
    perform private.gmu_v111_create_task(new.id::text||':approval-review',new.id,new.booking_id,'Leadership',null,'APPROVAL','Persetujuan menunggu keputusan','Tinjau alasan, dampak finansial, risiko, bukti, dan deadline approval.',coalesce((nullif(new.payload#>>'{new,sla_due_at}',''))::timestamptz,now()+interval '1 day'),'HIGH',true,true);
  elsif new.event_type='staff.assignment_created' then
    perform private.gmu_v111_create_task(new.id::text||':staff-accept',new.id,new.booking_id,null,(nullif(new.payload#>>'{new,staff_id}',''))::uuid,'PEOPLE','Konfirmasi penugasan','Baca jobdesk, tanggal, prioritas, bukti/output yang wajib, lalu konfirmasi penugasan.',coalesce((nullif(new.payload#>>'{new,due_date}',''))::date::timestamptz,now()+interval '1 day'),'NORMAL',false,false);
  elsif new.event_type='leave.requested' then
    perform private.gmu_v111_create_task(new.id::text||':leave-review',new.id,null,'Management',null,'PEOPLE','Review pengajuan cuti','Periksa dampak ke roster/trip, backup person, dan putuskan pengajuan.',now()+interval '1 day','NORMAL',false,true);
  end if;
  update public.automation_events set status='PROCESSED',processed_at=now(),error_text=null where id=new.id;
  return new;
exception when others then
  update public.automation_events set status='FAILED',processed_at=now(),error_text=sqlerrm where id=new.id;
  raise warning 'GMU v11.1 automation processor failed for %: %',new.event_type,sqlerrm;
  return new;
end;
$$;
revoke all on function private.gmu_v111_process_automation_event() from public, anon, authenticated;

create or replace function private.gmu_v111_audit_sensitive_change()
returns trigger language plpgsql security definer set search_path = public, private, pg_temp as $$
declare v_old jsonb:='{}'::jsonb; v_new jsonb:='{}'::jsonb; v_id text;
begin
  if tg_op='INSERT' then v_new:=to_jsonb(new);v_id:=v_new->>'id';
  elsif tg_op='UPDATE' then v_old:=to_jsonb(old);v_new:=to_jsonb(new);v_id:=coalesce(v_new->>'id',v_old->>'id');
  else v_old:=to_jsonb(old);v_id:=v_old->>'id'; end if;
  insert into public.audit_logs(user_id,action,table_name,record_id,message,old_data,new_data)
  values((select auth.uid()),tg_op,tg_table_name,v_id,'GMU v11.1 audit trail — perubahan sensitif tercatat otomatis.',nullif(v_old,'{}'::jsonb),nullif(v_new,'{}'::jsonb));
  return coalesce(new,old);
exception when others then raise warning 'GMU audit trigger failed on %: %',tg_table_name,sqlerrm; return coalesce(new,old); end;
$$;
revoke all on function private.gmu_v111_audit_sensitive_change() from public, anon, authenticated;

drop policy if exists automation_tasks_read on public.automation_tasks;
create policy automation_tasks_read on public.automation_tasks for select to authenticated using (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and (p.role::text in('Owner','Director') or assigned_to=(select auth.uid()) or assigned_role=p.role::text or (assigned_role='Management' and p.role::text in('Manager','Manager EduTrans')) or (assigned_role='Leadership' and p.role::text in('Owner','Director','Manager','Manager EduTrans'))))
);
drop policy if exists automation_tasks_update on public.automation_tasks;
create policy automation_tasks_update on public.automation_tasks for update to authenticated using (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and (p.role::text in('Owner','Director') or assigned_to=(select auth.uid()) or assigned_role=p.role::text or (assigned_role='Management' and p.role::text in('Manager','Manager EduTrans')) or (assigned_role='Leadership' and p.role::text in('Owner','Director','Manager','Manager EduTrans'))))
) with check (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and (p.role::text in('Owner','Director') or assigned_to=(select auth.uid()) or assigned_role=p.role::text or (assigned_role='Management' and p.role::text in('Manager','Manager EduTrans')) or (assigned_role='Leadership' and p.role::text in('Owner','Director','Manager','Manager EduTrans'))))
);

create trigger trg_v111_automation_event_process after insert on public.automation_events for each row execute function private.gmu_v111_process_automation_event();
create trigger trg_v111_booking_event after insert or update of status on public.bookings for each row execute function private.gmu_v111_emit_automation_event();
create trigger trg_v111_quotation_event after insert or update of status,sent_at,accepted_at,rejected_at on public.quotations for each row execute function private.gmu_v111_emit_automation_event();
create trigger trg_v111_invoice_event after insert or update of status,issued_at,paid_at on public.invoices for each row execute function private.gmu_v111_emit_automation_event();
create trigger trg_v111_payment_event after insert or update of verified_at on public.payments for each row execute function private.gmu_v111_emit_automation_event();
create trigger trg_v111_trip_event after insert or update of trip_status,completed_at on public.trips for each row execute function private.gmu_v111_emit_automation_event();
create trigger trg_v111_trip_closing_event after insert on public.trip_closings for each row execute function private.gmu_v111_emit_automation_event();
create trigger trg_v111_support_event after insert or update of status on public.customer_support_tickets for each row execute function private.gmu_v111_emit_automation_event();
create trigger trg_v111_feedback_event after insert on public.customer_trip_feedback for each row execute function private.gmu_v111_emit_automation_event();
create trigger trg_v111_staff_assignment_event after insert or update of status on public.staff_assignments for each row execute function private.gmu_v111_emit_automation_event();
create trigger trg_v111_approval_event after insert or update of status on public.approvals for each row execute function private.gmu_v111_emit_automation_event();
create trigger trg_v111_trip_cost_event after update of actual_finalized on public.trip_costs for each row execute function private.gmu_v111_emit_automation_event();
create trigger trg_v111_leave_event after insert or update of status on public.staff_leave for each row execute function private.gmu_v111_emit_automation_event();
create trigger trg_v111_review_event after insert on public.staff_reviews for each row execute function private.gmu_v111_emit_automation_event();

create trigger trg_v111_tasks_touch before update on public.automation_tasks for each row execute function private.gmu_v111_touch_updated_at();
create trigger trg_v111_approval_details_touch before update on public.approval_details for each row execute function private.gmu_v111_touch_updated_at();
create trigger trg_v111_payroll_touch before update on public.payroll_entries for each row execute function private.gmu_v111_touch_updated_at();
create trigger trg_v111_recruitment_touch before update on public.recruitment_cases for each row execute function private.gmu_v111_touch_updated_at();
create trigger trg_v111_capa_touch before update on public.capa_cases for each row execute function private.gmu_v111_touch_updated_at();
create trigger trg_v111_workforce_touch before update on public.workforce_plans for each row execute function private.gmu_v111_touch_updated_at();
create trigger trg_v111_risk_touch before update on public.risk_register for each row execute function private.gmu_v111_touch_updated_at();
create trigger trg_v111_ai_touch before update on public.ai_action_drafts for each row execute function private.gmu_v111_touch_updated_at();

create trigger trg_v111_audit_bookings after insert or update or delete on public.bookings for each row execute function private.gmu_v111_audit_sensitive_change();
create trigger trg_v111_audit_quotations after insert or update or delete on public.quotations for each row execute function private.gmu_v111_audit_sensitive_change();
create trigger trg_v111_audit_invoices after insert or update or delete on public.invoices for each row execute function private.gmu_v111_audit_sensitive_change();
create trigger trg_v111_audit_payments after insert or update or delete on public.payments for each row execute function private.gmu_v111_audit_sensitive_change();
create trigger trg_v111_audit_trip_costs after insert or update or delete on public.trip_costs for each row execute function private.gmu_v111_audit_sensitive_change();
create trigger trg_v111_audit_payroll after insert or update or delete on public.payroll_entries for each row execute function private.gmu_v111_audit_sensitive_change();
create trigger trg_v111_audit_approvals after insert or update or delete on public.approvals for each row execute function private.gmu_v111_audit_sensitive_change();
create trigger trg_v111_audit_trip_closings after insert or update or delete on public.trip_closings for each row execute function private.gmu_v111_audit_sensitive_change();
create trigger trg_v111_audit_risks after insert or update or delete on public.risk_register for each row execute function private.gmu_v111_audit_sensitive_change();
create trigger trg_v111_audit_capa after insert or update or delete on public.capa_cases for each row execute function private.gmu_v111_audit_sensitive_change();
