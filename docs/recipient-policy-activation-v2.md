# Recipient & Notification Policy v2 — Activation Gate

This stage keeps the current Android production path unchanged until the backend is verified.

## Target routing

- Owner: critical system events and critical approval escalation.
- Director/Direktur: critical and approval events.
- Manager EduTrans/Manager: operational EduTrans alerts.
- Operation: operational EduTrans alerts.
- Finance: payment recorded events.
- TL: only trip assignment events where the authenticated user is the assigned TL.
- Approval result: returns to the original requester.

## Dedicated endpoint

Use `gmu-recipient-policy-admin` for recipient-policy administration and staff inbox actions. Do not replace `gmu-notification-delivery-admin`; the existing Delivery Center must remain isolated.

Supported actions:

- `recipient_policies` — Owner/Director read.
- `set_recipient_policy` — Owner only.
- `staff_inbox` — authenticated staff, filtered by role and assignment context.
- `mark_staff_notification_read` — only a notification visible to that user.
- `mark_all_staff_notifications_read` — only notifications visible to that user.

## Activation order

1. Confirm Supabase connector access and inspect the current production schema/RLS before changing it.
2. Verify the existing `gmu_erp.user_notification` table and Data API exposure model.
3. Install the routing/database helpers using the least-privilege model supported by the current schema.
4. Deploy `supabase/functions/gmu-recipient-policy-admin/index.ts` with JWT verification enabled.
5. Test Owner policy read/update.
6. Insert a controlled test payment and verify it appears only for Finance.
7. Assign a controlled test trip to one TL and verify it appears only for that assigned TL, not other TL accounts.
8. Verify read/mark-all operations cannot mutate another role/user's inbox.
9. Point the dormant Android Recipient Policy and Staff Inbox clients to the dedicated endpoint.
10. Wire the UI into the Dashboard root and run Debug + Release builds.

## Release gate

Do not enable the Android UI until all backend tests above pass. This avoids broadcasting TL assignments or exposing payment notifications to unintended roles.
