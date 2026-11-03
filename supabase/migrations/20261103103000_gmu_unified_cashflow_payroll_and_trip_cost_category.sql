-- Unified cash flow: include payroll disbursements in the same treasury ledger
-- used by the GMU ERP web and Android finance dashboards.
ALTER TABLE public.treasury_ledger
  DROP CONSTRAINT IF EXISTS treasury_ledger_source_type_check;
ALTER TABLE public.treasury_ledger
  ADD CONSTRAINT treasury_ledger_source_type_check
  CHECK (source_type = ANY (ARRAY['CUSTOMER_PAYMENT','VENDOR_PAYMENT','MANUAL','PAYROLL_PAYMENT']::text[]));

CREATE OR REPLACE FUNCTION private.gl_sync_treasury_ledger()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$
DECLARE
  a public.treasury_accounts; t public.treasury_manual_transactions;
  vp public.vendor_payments; po public.vendor_pos; pe public.payroll_entries;
  v_cash_key text; v_counter_key text; v_debit text; v_credit text;
  v_group text; v_category text; v_desc text; v_actor uuid;
BEGIN
  IF NEW.status <> 'POSTED' THEN
    PERFORM private.gl_reverse_automated_journal('TREASURY_LEDGER',NEW.id::text,'CASH_MOVEMENT','Treasury ledger reversed');
    RETURN NEW;
  END IF;
  SELECT * INTO a FROM public.treasury_accounts WHERE id=NEW.account_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Treasury account not found for GL'; END IF;
  v_cash_key:=CASE WHEN a.is_system THEN 'CASH_CLEARING' ELSE 'CASH_BANK' END;
  v_desc:=NEW.description;

  IF NEW.source_type='CUSTOMER_PAYMENT' THEN
    v_counter_key:='ACCOUNTS_RECEIVABLE'; v_group:='OPERATING';
    v_category:=CASE WHEN NEW.direction='INFLOW' THEN 'CUSTOMER_COLLECTION' ELSE 'CUSTOMER_REFUND' END;
    IF NEW.direction='INFLOW' THEN v_debit:=v_cash_key; v_credit:=v_counter_key;
    ELSE v_debit:=v_counter_key; v_credit:=v_cash_key; END IF;
    SELECT verified_by INTO v_actor FROM public.payments WHERE id=NEW.source_id;

  ELSIF NEW.source_type='VENDOR_PAYMENT' THEN
    SELECT p.* INTO vp FROM public.vendor_payments p WHERE p.id::text=NEW.source_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Vendor payment source not found for GL'; END IF;
    SELECT * INTO po FROM public.vendor_pos WHERE id=vp.vendor_po_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Vendor PO source not found for GL'; END IF;
    v_group:='OPERATING';
    IF NEW.direction='OUTFLOW' THEN
      v_counter_key:='ACCOUNTS_PAYABLE'; v_debit:=v_counter_key; v_credit:=v_cash_key; v_category:='VENDOR_PAYMENT';
    ELSE
      v_counter_key:='ACCOUNTS_PAYABLE'; v_debit:=v_cash_key; v_credit:=v_counter_key; v_category:='VENDOR_REFUND';
    END IF;
    v_actor:=vp.verified_by;

  ELSIF NEW.source_type='MANUAL' THEN
    SELECT * INTO t FROM public.treasury_manual_transactions WHERE id::text=NEW.source_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Manual treasury source not found for GL'; END IF;
    v_actor:=COALESCE(t.reversed_by,t.created_by); v_category:=t.category;
    CASE t.category
      WHEN 'CAPITAL_INJECTION' THEN v_counter_key:='OWNER_CAPITAL'; v_group:='FINANCING';
      WHEN 'OWNER_DRAW' THEN v_counter_key:='OWNER_DRAWINGS'; v_group:='FINANCING';
      WHEN 'OVERHEAD' THEN v_counter_key:='EXPENSE_GA'; v_group:='OPERATING';
      WHEN 'TAX' THEN v_counter_key:='EXPENSE_TAX'; v_group:='OPERATING';
      WHEN 'FEE' THEN v_counter_key:='EXPENSE_FEE'; v_group:='OPERATING';
      WHEN 'TRIP_COST_SETTLEMENT' THEN v_counter_key:='ACCRUED_TRIP_COST'; v_group:='OPERATING';
      WHEN 'REFUND' THEN v_counter_key:=CASE WHEN NEW.direction='INFLOW' THEN 'OTHER_INCOME' ELSE 'EXPENSE_OTHER' END; v_group:='OPERATING';
      ELSE v_counter_key:=CASE WHEN NEW.direction='INFLOW' THEN 'OTHER_INCOME' ELSE 'EXPENSE_OTHER' END; v_group:='OPERATING';
    END CASE;
    IF NEW.direction='INFLOW' THEN v_debit:=v_cash_key; v_credit:=v_counter_key;
    ELSE v_debit:=v_counter_key; v_credit:=v_cash_key; END IF;

  ELSIF NEW.source_type='PAYROLL_PAYMENT' THEN
    SELECT * INTO pe FROM public.payroll_entries WHERE id::text=NEW.source_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Payroll payment source not found for GL'; END IF;
    v_actor:=COALESCE(pe.approved_by,pe.created_by);
    v_group:='OPERATING'; v_category:='PAYROLL'; v_counter_key:='EXPENSE_GA';
    v_debit:=v_counter_key; v_credit:=v_cash_key;
  ELSE
    RAISE EXCEPTION 'Unsupported treasury ledger source for GL: %',NEW.source_type;
  END IF;

  PERFORM private.gl_replace_two_line_journal(
    'TREASURY_LEDGER',NEW.id::text,'CASH_MOVEMENT',NEW.transaction_date,NEW.booking_id,v_desc,
    v_debit,v_credit,NEW.amount,v_group,v_category,NEW.account_id,v_actor,
    jsonb_build_object('treasury_ledger_id',NEW.id,'treasury_account_id',NEW.account_id,
      'treasury_account_code',a.account_code,'source_type',NEW.source_type,
      'source_id',NEW.source_id,'direction',NEW.direction)
  );
  RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION private.sync_payroll_payment_to_treasury()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$
DECLARE v_account uuid; v_paid boolean;
BEGIN
  v_paid:=NEW.state='PAID' AND NEW.paid_at IS NOT NULL AND COALESCE(NEW.amount,0)>0;
  SELECT account_id INTO v_account FROM public.treasury_ledger
  WHERE source_type='PAYROLL_PAYMENT' AND source_id=NEW.id::text;
  v_account:=COALESCE(v_account,private.treasury_unallocated_account_id());
  IF v_account IS NULL AND v_paid THEN RAISE EXCEPTION 'Treasury unallocated account is missing for payroll cash movement'; END IF;

  IF v_paid THEN
    INSERT INTO public.treasury_ledger(account_id,source_type,source_id,booking_id,transaction_date,direction,amount,description,status)
    VALUES(v_account,'PAYROLL_PAYMENT',NEW.id::text,NEW.booking_id,NEW.paid_at::date,'OUTFLOW',NEW.amount,
      'Payroll payment · '||COALESCE(NEW.component_type,'Staff payroll'),'POSTED')
    ON CONFLICT(source_type,source_id) DO UPDATE SET
      account_id=COALESCE(public.treasury_ledger.account_id,excluded.account_id),
      booking_id=excluded.booking_id,transaction_date=excluded.transaction_date,
      direction='OUTFLOW',amount=excluded.amount,description=excluded.description,status='POSTED',updated_at=now();
  ELSE
    UPDATE public.treasury_ledger SET status='REVERSED',updated_at=now()
    WHERE source_type='PAYROLL_PAYMENT' AND source_id=NEW.id::text AND status<>'REVERSED';
  END IF;
  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS trg_payroll_payment_95_treasury ON public.payroll_entries;
CREATE TRIGGER trg_payroll_payment_95_treasury
AFTER INSERT OR UPDATE OF state,paid_at,amount,booking_id,component_type ON public.payroll_entries
FOR EACH ROW EXECUTE FUNCTION private.sync_payroll_payment_to_treasury();

CREATE OR REPLACE FUNCTION public.internal_post_treasury_manual(
  p_submission_id uuid,p_account_id uuid,p_booking_id text,p_direction text,p_category text,
  p_amount numeric,p_transaction_date date,p_description text,p_reference_no text,p_actor uuid
)
RETURNS jsonb LANGUAGE plpgsql SET search_path TO 'public','private','pg_temp'
AS $function$
DECLARE actor_role public.app_role; a public.treasury_accounts; t public.treasury_manual_transactions;
BEGIN
  SELECT role INTO actor_role FROM public.profiles WHERE id=p_actor AND is_active=true;
  IF actor_role NOT IN ('Owner'::public.app_role,'Manager'::public.app_role) THEN RAISE EXCEPTION 'Only active Owner / Manager can post treasury movements'; END IF;
  IF p_submission_id IS NULL THEN RAISE EXCEPTION 'Submission ID is required'; END IF;
  IF p_direction NOT IN ('INFLOW','OUTFLOW') THEN RAISE EXCEPTION 'Invalid direction'; END IF;
  IF p_category NOT IN ('CAPITAL_INJECTION','OWNER_DRAW','OVERHEAD','TAX','FEE','REFUND','OTHER','TRIP_COST_SETTLEMENT') THEN RAISE EXCEPTION 'Invalid treasury category'; END IF;
  IF p_amount IS NULL OR p_amount<=0 THEN RAISE EXCEPTION 'Amount must be > 0'; END IF;
  IF NULLIF(trim(p_description),'') IS NULL THEN RAISE EXCEPTION 'Description is required'; END IF;
  SELECT * INTO t FROM public.treasury_manual_transactions WHERE submission_id=p_submission_id;
  IF FOUND THEN RETURN jsonb_build_object('ok',true,'duplicate',true,'transaction_id',t.id,'transaction_no',t.transaction_no,'status',t.status); END IF;
  SELECT * INTO a FROM public.treasury_accounts WHERE id=p_account_id AND is_active=true;
  IF NOT FOUND THEN RAISE EXCEPTION 'Active treasury account not found'; END IF;
  IF p_booking_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.bookings WHERE id=p_booking_id) THEN RAISE EXCEPTION 'Booking not found'; END IF;
  INSERT INTO public.treasury_manual_transactions(submission_id,account_id,booking_id,direction,category,amount,transaction_date,description,reference_no,status,created_by)
  VALUES(p_submission_id,p_account_id,p_booking_id,p_direction,p_category,round(p_amount,2),COALESCE(p_transaction_date,private.gmu_today()),trim(p_description),NULLIF(trim(p_reference_no),''),'POSTED',p_actor)
  RETURNING * INTO t;
  INSERT INTO public.audit_logs(user_id,action,table_name,record_id,message,new_data)
  VALUES(p_actor,'POST_TREASURY_MANUAL','treasury_manual_transactions',t.id::text,'Manual treasury movement posted',
    jsonb_build_object('transaction_no',t.transaction_no,'account_code',a.account_code,'direction',t.direction,'category',t.category,'amount',t.amount));
  RETURN jsonb_build_object('ok',true,'transaction_id',t.id,'transaction_no',t.transaction_no,'status',t.status,'account_id',t.account_id,'amount',t.amount);
END;
$function$;

REVOKE EXECUTE ON FUNCTION private.sync_payroll_payment_to_treasury() FROM PUBLIC,anon,authenticated;
