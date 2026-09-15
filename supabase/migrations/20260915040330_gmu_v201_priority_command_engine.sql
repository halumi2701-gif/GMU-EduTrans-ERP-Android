create or replace function private.gmu_v201_refresh_priority()
returns void
language plpgsql
security definer
set search_path=public,private,cron,pg_temp
as $$
declare
  v_month_start date := date_trunc('month',current_date)::date;
  v_month_end date := (date_trunc('month',current_date)+interval '1 month - 1 day')::date;
  v_month_key text := to_char(current_date,'YYYY-MM');
  v_days_month numeric := extract(day from v_month_end);
  v_day numeric := extract(day from current_date);
  v_prospect_target int;
  v_prospects int;
  v_followup_overdue int;
  v_overdue_ar int;
  v_incomplete_packages int;
  v_closed_without_profit int;
  v_open_priority int;
begin
  insert into public.finance_periods(period_no,period_type,period_start,period_end,status,notes)
  values ('M-'||v_month_key,'MONTHLY',v_month_start,v_month_end,'OPEN','Auto-opened by GMU Priority Engine v20.1')
  on conflict do nothing;

  v_prospect_target := greatest(1,ceil(200*(v_day/v_days_month))::int);
  select count(*) into v_prospects from public.booking_requests where created_at>=v_month_start and created_at<(v_month_end+1);
  select count(*) into v_followup_overdue from public.crm_lead_controls where stage not in ('WON','LOST') and next_follow_up_at is not null and next_follow_up_at<now();

  with paid as (
    select invoice_id,coalesce(sum(amount),0) paid_amount
    from public.payments
    where invoice_id is not null and verified_at is not null
    group by invoice_id
  )
  select count(*) into v_overdue_ar
  from public.invoices i left join paid p on p.invoice_id=i.id
  where i.due_date<current_date and greatest(i.total-coalesce(p.paid_amount,0),0)>0 and upper(coalesce(i.status,'')) not in ('PAID','CANCELLED','CANCELED','VOID');

  select count(*) into v_incomplete_packages
  from public.program_packages pp
  where pp.is_active=true and (
    pp.approved_at is null or
    not exists(select 1 from public.pricing_cost_templates pct where pct.package_id=pp.id and pct.is_active=true and pct.effective_from<=current_date and (pct.effective_until is null or pct.effective_until>=current_date))
  );

  select count(*) into v_closed_without_profit
  from public.bookings b
  where upper(coalesce(b.status::text,''))='CLOSED'
    and not exists(select 1 from public.trip_closings tc where tc.booking_id=b.id);

  if v_prospects < v_prospect_target then
    insert into public.enterprise_exception_queue(exception_key,domain,exception_type,severity,title,description,entity_type,entity_id,assigned_role,due_at)
    values('sales-activity-gap:'||v_month_key,'SALES','SALES_TARGET_GAP',case when v_prospects < greatest(1,ceil(v_prospect_target*.5)) then 'CRITICAL' else 'HIGH' end,
      'Aktivitas Sales tertinggal dari target berjalan',
      format('Prospek bulan berjalan %s dari target prorata %s. Manager wajib membuat recovery plan harian.',v_prospects,v_prospect_target),
      'month',v_month_key,'Manager',now()+interval '4 hours')
    on conflict(exception_key) do update set severity=excluded.severity,description=excluded.description,status=case when enterprise_exception_queue.status='RESOLVED' then 'OPEN' else enterprise_exception_queue.status end,due_at=excluded.due_at,updated_at=now();
  else
    update public.enterprise_exception_queue set status='RESOLVED',resolved_at=coalesce(resolved_at,now()),resolution=coalesce(resolution,'Sales activity recovered'),updated_at=now()
    where exception_key='sales-activity-gap:'||v_month_key and status not in ('RESOLVED','DISMISSED');
  end if;

  if v_followup_overdue>0 then
    insert into public.enterprise_exception_queue(exception_key,domain,exception_type,severity,title,description,entity_type,entity_id,assigned_role,due_at)
    values('sales-followup-overdue:'||v_month_key,'SALES','FOLLOWUP_OVERDUE',case when v_followup_overdue>=5 then 'CRITICAL' else 'HIGH' end,
      'Follow-up Sales melewati jadwal',format('%s lead memiliki next follow-up yang sudah lewat.',v_followup_overdue),'month',v_month_key,'Manager',now()+interval '2 hours')
    on conflict(exception_key) do update set severity=excluded.severity,description=excluded.description,status=case when enterprise_exception_queue.status='RESOLVED' then 'OPEN' else enterprise_exception_queue.status end,due_at=excluded.due_at,updated_at=now();
  else
    update public.enterprise_exception_queue set status='RESOLVED',resolved_at=coalesce(resolved_at,now()),resolution=coalesce(resolution,'No overdue follow-up'),updated_at=now()
    where exception_key='sales-followup-overdue:'||v_month_key and status not in ('RESOLVED','DISMISSED');
  end if;

  if v_overdue_ar>0 then
    insert into public.enterprise_exception_queue(exception_key,domain,exception_type,severity,title,description,entity_type,entity_id,assigned_role,due_at)
    values('finance-overdue-ar:'||v_month_key,'FINANCE','AR_OVERDUE','HIGH','Piutang melewati jatuh tempo',format('%s invoice masih memiliki saldo lewat jatuh tempo.',v_overdue_ar),'month',v_month_key,'Finance',now()+interval '4 hours')
    on conflict(exception_key) do update set description=excluded.description,status=case when enterprise_exception_queue.status='RESOLVED' then 'OPEN' else enterprise_exception_queue.status end,due_at=excluded.due_at,updated_at=now();
  else
    update public.enterprise_exception_queue set status='RESOLVED',resolved_at=coalesce(resolved_at,now()),resolution=coalesce(resolution,'No overdue AR'),updated_at=now()
    where exception_key='finance-overdue-ar:'||v_month_key and status not in ('RESOLVED','DISMISSED');
  end if;

  if v_incomplete_packages>0 then
    insert into public.enterprise_exception_queue(exception_key,domain,exception_type,severity,title,description,entity_type,entity_id,assigned_role,due_at)
    values('package-readiness:'||v_month_key,'COMMERCIAL','PACKAGE_NOT_READY','HIGH','Master Paket belum siap dijual',format('%s paket aktif belum approved atau belum memiliki cost template aktif.',v_incomplete_packages),'month',v_month_key,'Manager',now()+interval '24 hours')
    on conflict(exception_key) do update set description=excluded.description,status=case when enterprise_exception_queue.status='RESOLVED' then 'OPEN' else enterprise_exception_queue.status end,due_at=excluded.due_at,updated_at=now();
  else
    update public.enterprise_exception_queue set status='RESOLVED',resolved_at=coalesce(resolved_at,now()),resolution=coalesce(resolution,'All active packages commercially ready'),updated_at=now()
    where exception_key='package-readiness:'||v_month_key and status not in ('RESOLVED','DISMISSED');
  end if;

  if v_closed_without_profit>0 then
    insert into public.enterprise_exception_queue(exception_key,domain,exception_type,severity,title,description,entity_type,entity_id,assigned_role,due_at)
    values('profit-closing-gap:'||v_month_key,'FINANCE','TRIP_PROFIT_NOT_CLOSED','CRITICAL','Kegiatan Closed belum memiliki closing profit',format('%s booking berstatus Closed belum memiliki Trip Closing/profitability.',v_closed_without_profit),'month',v_month_key,'Finance',now()+interval '4 hours')
    on conflict(exception_key) do update set description=excluded.description,status=case when enterprise_exception_queue.status='RESOLVED' then 'OPEN' else enterprise_exception_queue.status end,due_at=excluded.due_at,updated_at=now();
  else
    update public.enterprise_exception_queue set status='RESOLVED',resolved_at=coalesce(resolved_at,now()),resolution=coalesce(resolution,'All closed trips have profitability closing'),updated_at=now()
    where exception_key='profit-closing-gap:'||v_month_key and status not in ('RESOLVED','DISMISSED');
  end if;

  insert into public.enterprise_exception_queue(exception_key,domain,exception_type,severity,title,description,entity_type,entity_id,assigned_role,due_at)
  select 'trip-low-margin:'||tc.booking_id,'PROFITABILITY','LOW_MARGIN',case when tc.margin_pct<20 then 'CRITICAL' else 'HIGH' end,
    'Margin kegiatan di bawah standar',format('Margin %.2f%%; laba %s. Target sehat minimal 25%%.',tc.margin_pct,tc.net_profit::text),'booking',tc.booking_id,
    case when tc.margin_pct<20 then 'Director' else 'Manager' end,now()+interval '4 hours'
  from public.trip_closings tc
  where tc.closed_at>=now()-interval '90 days' and tc.margin_pct<25
  on conflict(exception_key) do update set severity=excluded.severity,description=excluded.description,assigned_role=excluded.assigned_role,status=case when enterprise_exception_queue.status='RESOLVED' then 'OPEN' else enterprise_exception_queue.status end,due_at=excluded.due_at,updated_at=now();

  update public.enterprise_exception_queue e set status='RESOLVED',resolved_at=coalesce(resolved_at,now()),resolution=coalesce(resolution,'Margin is now healthy or record outside active review window'),updated_at=now()
  where e.exception_type='LOW_MARGIN' and e.status not in ('RESOLVED','DISMISSED') and not exists(
    select 1 from public.trip_closings tc where tc.booking_id=e.entity_id and tc.closed_at>=now()-interval '90 days' and tc.margin_pct<25
  );

  select count(*) into v_open_priority from public.enterprise_exception_queue
  where status in ('OPEN','IN_PROGRESS','WAITING') and severity in ('HIGH','CRITICAL') and domain in ('SALES','FINANCE','PROFITABILITY','COMMERCIAL');

  if v_open_priority>0 and not exists(
    select 1 from public.ai_action_drafts where rule_key='V201_PRIORITY_RECOVERY' and status in ('DRAFT','APPROVED') and created_at>=date_trunc('day',now())
  ) then
    insert into public.ai_action_drafts(rule_key,category,severity,title,rationale,recommended_action,owner_role,requires_approval,status)
    values('V201_PRIORITY_RECOVERY','MANAGEMENT',case when exists(select 1 from public.enterprise_exception_queue where status in ('OPEN','IN_PROGRESS','WAITING') and severity='CRITICAL' and domain in ('SALES','FINANCE','PROFITABILITY','COMMERCIAL')) then 'CRITICAL' else 'HIGH' end,
      'Rencana Pemulihan Prioritas GMU',format('%s exception prioritas bisnis membutuhkan tindakan.',v_open_priority),
      'Urutkan tindakan: Sales Engine → Profitability → Finance Closing → Master Paket. Tetapkan PIC dan due time; eskalasi ke Direktur untuk margin <20% atau keputusan strategis.','Manager',false,'DRAFT');
  end if;
end $$;

revoke all on function private.gmu_v201_refresh_priority() from public,anon,authenticated;

create or replace function public.internal_gmu_priority_command(p_as_of date default current_date)
returns jsonb
language plpgsql
stable
security invoker
set search_path=public,private,pg_temp
as $$
declare
  v_start date := date_trunc('month',p_as_of)::date;
  v_end date := (date_trunc('month',p_as_of)+interval '1 month - 1 day')::date;
  v_days numeric := extract(day from (date_trunc('month',p_as_of)+interval '1 month - 1 day'));
  v_elapsed numeric := extract(day from p_as_of);
  v_prospects int; v_qualified int; v_quotes int; v_bookings int;
  v_unassigned int; v_overdue_followups int; v_weighted numeric;
  v_profit numeric; v_revenue numeric; v_avg_profit numeric; v_avg_margin numeric; v_low_margin int; v_closed int;
  v_required_bookings int; v_required_revenue numeric; v_profit_gap numeric;
  v_overdue_ar int; v_overdue_ar_amount numeric; v_current_period text;
  v_active_packages int; v_unapproved_packages int; v_uncosted_packages int;
  v_ai_open int; v_exceptions int; v_critical int;
  v_target_prospects int := 200; v_target_qualified int := 20; v_target_quotes int := 12; v_target_bookings int := 3; v_target_profit numeric := 15000000;
  v_prorated_prospects int; v_prorated_qualified int; v_prorated_quotes int; v_prorated_bookings int;
begin
  if not private.gmu_v200_is_management() then raise exception 'forbidden'; end if;

  v_prorated_prospects:=greatest(1,ceil(v_target_prospects*(v_elapsed/v_days))::int);
  v_prorated_qualified:=greatest(1,ceil(v_target_qualified*(v_elapsed/v_days))::int);
  v_prorated_quotes:=greatest(1,ceil(v_target_quotes*(v_elapsed/v_days))::int);
  v_prorated_bookings:=greatest(1,ceil(v_target_bookings*(v_elapsed/v_days))::int);

  select count(*) into v_prospects from public.booking_requests where created_at>=v_start and created_at<(v_end+1);
  select count(*) into v_qualified from public.crm_lead_controls where created_at>=v_start and created_at<(v_end+1) and stage in ('QUALIFIED','QUOTATION','NEGOTIATION','WAITING_DP','WON');
  select count(*) into v_quotes from public.quotations where created_at>=v_start and created_at<(v_end+1) and upper(coalesce(status,'')) not in ('CANCELLED','CANCELED','SUPERSEDED');
  select count(*) into v_bookings from public.bookings where created_at>=v_start and created_at<(v_end+1) and upper(coalesce(status::text,'')) in ('DP','TRIP','CLOSED');
  select count(*) into v_unassigned from public.crm_lead_controls where owner_id is null and stage not in ('WON','LOST');
  select count(*) into v_overdue_followups from public.crm_lead_controls where stage not in ('WON','LOST') and next_follow_up_at is not null and next_follow_up_at<now();
  select coalesce(sum(estimated_value*probability_pct/100),0) into v_weighted from public.crm_lead_controls where stage not in ('WON','LOST');

  select count(*),coalesce(sum(net_profit),0),coalesce(sum(contract_revenue),0),coalesce(avg(nullif(net_profit,0)),0),coalesce(avg(nullif(margin_pct,0)),0),count(*) filter(where margin_pct<25)
  into v_closed,v_profit,v_revenue,v_avg_profit,v_avg_margin,v_low_margin
  from public.trip_closings where closed_at>=v_start and closed_at<(v_end+1);

  if v_avg_profit<=0 then
    select coalesce(avg(nullif(net_profit,0)),0),coalesce(avg(nullif(margin_pct,0)),0)
    into v_avg_profit,v_avg_margin from public.trip_closings where closed_at>=p_as_of-90 and closed_at<p_as_of+1;
  end if;
  v_profit_gap:=greatest(v_target_profit-v_profit,0);
  v_required_bookings:=case when v_avg_profit>0 then ceil(v_profit_gap/v_avg_profit)::int else null end;
  v_required_revenue:=case when v_avg_margin>0 then round(v_profit_gap/(v_avg_margin/100),0) else null end;

  with paid as (select invoice_id,coalesce(sum(amount),0) amt from public.payments where invoice_id is not null and verified_at is not null group by invoice_id), ar as (
    select i.id,greatest(i.total-coalesce(p.amt,0),0) outstanding from public.invoices i left join paid p on p.invoice_id=i.id where i.due_date<p_as_of and upper(coalesce(i.status,'')) not in ('PAID','CANCELLED','CANCELED','VOID')
  ) select count(*) filter(where outstanding>0),coalesce(sum(outstanding),0) into v_overdue_ar,v_overdue_ar_amount from ar;
  select status into v_current_period from public.finance_periods where period_type='MONTHLY' and period_start<=p_as_of and period_end>=p_as_of order by period_start desc limit 1;

  select count(*) into v_active_packages from public.program_packages where is_active=true;
  select count(*) into v_unapproved_packages from public.program_packages where is_active=true and approved_at is null;
  select count(*) into v_uncosted_packages from public.program_packages pp where pp.is_active=true and not exists(select 1 from public.pricing_cost_templates pct where pct.package_id=pp.id and pct.is_active=true and pct.effective_from<=p_as_of and (pct.effective_until is null or pct.effective_until>=p_as_of));

  select count(*) into v_ai_open from public.ai_action_drafts where status in ('DRAFT','APPROVED');
  select count(*),count(*) filter(where severity='CRITICAL') into v_exceptions,v_critical from public.enterprise_exception_queue where status in ('OPEN','IN_PROGRESS','WAITING');

  return jsonb_build_object(
    'generated_at',now(),'as_of',p_as_of,
    'sales',jsonb_build_object('target',jsonb_build_object('prospects',v_target_prospects,'qualified',v_target_qualified,'quotations',v_target_quotes,'bookings',v_target_bookings),'prorated',jsonb_build_object('prospects',v_prorated_prospects,'qualified',v_prorated_qualified,'quotations',v_prorated_quotes,'bookings',v_prorated_bookings),'actual',jsonb_build_object('prospects',v_prospects,'qualified',v_qualified,'quotations',v_quotes,'bookings',v_bookings),'unassigned',v_unassigned,'overdue_followups',v_overdue_followups,'weighted_pipeline',v_weighted),
    'profitability',jsonb_build_object('target_net_profit',v_target_profit,'month_net_profit',v_profit,'profit_gap',v_profit_gap,'month_revenue',v_revenue,'closed_trips',v_closed,'avg_profit_per_trip',nullif(v_avg_profit,0),'avg_margin_pct',nullif(v_avg_margin,0),'low_margin_trips',v_low_margin,'required_bookings',v_required_bookings,'required_revenue',v_required_revenue,'data_ready',(v_avg_profit>0 and v_avg_margin>0)),
    'finance_closing',jsonb_build_object('current_period_status',coalesce(v_current_period,'MISSING'),'overdue_invoice_count',v_overdue_ar,'overdue_ar_amount',v_overdue_ar_amount,'period_ready',(v_current_period is not null)),
    'master_package',jsonb_build_object('active_packages',v_active_packages,'unapproved_packages',v_unapproved_packages,'uncosted_packages',v_uncosted_packages,'ready',(v_active_packages>0 and v_unapproved_packages=0 and v_uncosted_packages=0)),
    'manager_ai',jsonb_build_object('open_action_drafts',v_ai_open,'open_exceptions',v_exceptions,'critical_exceptions',v_critical),
    'executive',jsonb_build_object('priority_order',jsonb_build_array('Sales Engine','Profitability','Finance Closing','Master Paket','Manager AI','Executive Control Tower'),'net_profit_target',v_target_profit,'critical_exceptions',v_critical,'status',case when v_critical>0 then 'CRITICAL' when v_exceptions>0 then 'ACTION_REQUIRED' else 'CONTROLLED' end)
  );
end $$;

revoke all on function public.internal_gmu_priority_command(date) from public,anon;
grant execute on function public.internal_gmu_priority_command(date) to authenticated;

select private.gmu_v201_refresh_priority();

do $$
declare j bigint;
begin
  for j in select jobid from cron.job where jobname='gmu_v201_priority_refresh' loop perform cron.unschedule(j); end loop;
  perform cron.schedule('gmu_v201_priority_refresh','*/10 * * * *','select private.gmu_v201_refresh_priority();');
end $$;
