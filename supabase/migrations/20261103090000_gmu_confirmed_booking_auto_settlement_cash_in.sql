-- Confirmed booking is treated as fully paid in GMU EduTrans.
-- Create a settlement payment for only the outstanding balance. This runs at
-- database level so the web ERP and Android clients share the same behavior.
CREATE OR REPLACE FUNCTION public.gmu_auto_settle_confirmed_booking()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_total numeric(14,2);
  v_net_paid numeric(14,2);
  v_outstanding numeric(14,2);
BEGIN
  IF NEW.status::text <> 'Confirmed' THEN
    RETURN NEW;
  END IF;

  v_total := round(COALESCE(NEW.pax, 0) * COALESCE(NEW.price_per_pax, 0), 2);

  SELECT COALESCE(SUM(
    CASE WHEN payment_type::text = 'Refund' THEN -amount ELSE amount END
  ), 0)
  INTO v_net_paid
  FROM public.payments
  WHERE booking_id = NEW.id;

  v_outstanding := round(v_total - v_net_paid, 2);

  IF v_outstanding > 0.009 THEN
    INSERT INTO public.payments (
      booking_id, payment_type, amount, payment_date, method,
      notes, verified_by, verified_at
    )
    VALUES (
      NEW.id, 'Pelunasan', v_outstanding, CURRENT_DATE,
      'Auto - Status Confirmed',
      'Pelunasan otomatis karena status booking Confirmed',
      auth.uid(), now()
    );
  END IF;

  RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.gmu_auto_settle_confirmed_booking() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS trg_gmu_auto_settle_confirmed_booking ON public.bookings;
CREATE TRIGGER trg_gmu_auto_settle_confirmed_booking
AFTER INSERT OR UPDATE ON public.bookings
FOR EACH ROW
EXECUTE FUNCTION public.gmu_auto_settle_confirmed_booking();
