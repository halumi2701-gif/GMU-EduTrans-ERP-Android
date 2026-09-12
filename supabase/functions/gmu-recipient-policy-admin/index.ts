import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.116.0";

type Profile = {
  id: string;
  full_name?: string | null;
  role?: string | null;
  is_active?: boolean | null;
};

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json" },
  });

function firstSecretKey(): string {
  const raw = Deno.env.get("SUPABASE_SECRET_KEYS") ?? "";
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      if (parsed?.default) return String(parsed.default);
      const first = Object.values(parsed ?? {})[0];
      if (first) return String(first);
    } catch {
      // Fall through to the service role key.
    }
  }
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
}

const matrix: Record<string, { scope: string; roles: string[] }> = {
  APPROVAL_ESCALATED: { scope: "ROLE", roles: ["Owner", "Director"] },
  APPROVAL_RESOLVED: { scope: "REQUESTER", roles: ["ANY"] },
  OPERATION_ALERT: { scope: "ROLE", roles: ["Manager EduTrans", "Operation"] },
  PAYMENT_RECORDED: { scope: "ROLE", roles: ["Finance"] },
  TRIP_ASSIGNMENT: { scope: "ASSIGNED_USER", roles: ["TL"] },
  SYSTEM_CRITICAL: { scope: "ROLE", roles: ["Owner", "Director"] },
  DELIVERY_FAILURE_ESCALATION: { scope: "ROLE", roles: ["Owner", "Director"] },
};

const severities = new Set(["INFO", "WARNING", "CRITICAL"]);
const staffInboxRoles = new Set(["Manager EduTrans", "Operation", "Finance", "TL"]);

function normalizedRole(role: string): string {
  if (role === "Direktur") return "Director";
  if (role === "Manager") return "Manager EduTrans";
  return role;
}

function isOwner(role: string): boolean {
  return normalizedRole(role) === "Owner";
}

function isDirectorOrOwner(role: string): boolean {
  const normalized = normalizedRole(role);
  return normalized === "Owner" || normalized === "Director";
}

function canUseStaffInbox(role: string): boolean {
  return staffInboxRoles.has(normalizedRole(role));
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = firstSecretKey();
  if (!url || !serviceKey) return json({ error: "server_not_configured" }, 503);

  const authorization = req.headers.get("authorization") ?? "";
  const token = authorization.replace(/^Bearer\s+/i, "").trim();
  if (!token) return json({ error: "unauthorized" }, 401);

  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  const { data: authData, error: authError } = await admin.auth.getUser(token);
  const user = authData?.user;
  if (authError || !user) return json({ error: "unauthorized" }, 401);

  const { data: profileData, error: profileError } = await admin
    .from("profiles")
    .select("id,full_name,role,is_active")
    .eq("id", user.id)
    .maybeSingle();

  if (profileError) return json({ error: "profile_lookup_failed", detail: profileError.message }, 500);
  const profile = (profileData ?? null) as Profile | null;
  if (!profile || profile.is_active === false) return json({ error: "inactive_profile" }, 403);
  const role = String(profile.role ?? "");

  const body = await req.json().catch(() => ({}));
  const action = String(body?.action ?? "");

  if (action === "health") {
    return json({
      ok: true,
      service: "gmu-recipient-policy-admin",
      role: normalizedRole(role),
      policy_admin: isDirectorOrOwner(role),
      staff_inbox: canUseStaffInbox(role),
    });
  }

  if (action === "recipient_policies") {
    if (!isDirectorOrOwner(role)) return json({ error: "forbidden" }, 403);
    const { data, error } = await admin
      .from("notification_recipient_policies")
      .select("id,event_key,label,target_role,recipient_scope,min_severity,is_active,notes,updated_at")
      .order("event_key", { ascending: true })
      .order("target_role", { ascending: true });
    if (error) return json({ error: "recipient_policies_failed", detail: error.message }, 500);
    return json({ recipient_policies: data ?? [] });
  }

  if (action === "set_recipient_policy") {
    if (!isOwner(role)) return json({ error: "owner_required" }, 403);

    const eventKey = String(body?.event_key ?? "").toUpperCase();
    const targetRole = String(body?.target_role ?? "");
    const recipientScope = String(body?.recipient_scope ?? "").toUpperCase();
    const minSeverity = String(body?.min_severity ?? "INFO").toUpperCase();
    const active = body?.is_active === true;
    const label = String(body?.label ?? eventKey).slice(0, 160);
    const allowed = matrix[eventKey];

    if (!allowed) return json({ error: "unsupported_event" }, 400);
    if (recipientScope !== allowed.scope) return json({ error: "invalid_scope" }, 400);
    if (!allowed.roles.includes(targetRole)) return json({ error: "invalid_target_role" }, 400);
    if (!severities.has(minSeverity)) return json({ error: "invalid_severity" }, 400);

    const { data, error } = await admin
      .from("notification_recipient_policies")
      .upsert(
        {
          event_key: eventKey,
          label,
          target_role: targetRole,
          recipient_scope: recipientScope,
          min_severity: minSeverity,
          is_active: active,
          updated_at: new Date().toISOString(),
        },
        { onConflict: "event_key,target_role,recipient_scope" },
      )
      .select("id,event_key,label,target_role,recipient_scope,min_severity,is_active,notes,updated_at")
      .single();

    if (error) return json({ error: "recipient_policy_update_failed", detail: error.message }, 500);
    return json({ ok: true, recipient_policy: data });
  }

  if (action === "staff_inbox") {
    if (!canUseStaffInbox(role)) return json({ error: "forbidden" }, 403);
    const { data, error } = await admin.rpc("gmu_staff_notification_inbox", {
      p_user_id: user.id,
      p_role: role,
      p_limit: 30,
    });
    if (error) return json({ error: "staff_inbox_failed", detail: error.message }, 500);
    return json({ notifications: data ?? [] });
  }

  if (action === "mark_staff_notification_read") {
    if (!canUseStaffInbox(role)) return json({ error: "forbidden" }, 403);
    const id = String(body?.id ?? "").trim();
    if (!id || id.length > 128) return json({ error: "invalid_notification_id" }, 400);
    const { data, error } = await admin.rpc("gmu_staff_notification_mark_read", {
      p_id: id,
      p_user_id: user.id,
      p_role: role,
    });
    if (error) return json({ error: "mark_read_failed", detail: error.message }, 500);
    if (data !== true) return json({ error: "notification_not_found" }, 404);
    return json({ ok: true });
  }

  if (action === "mark_all_staff_notifications_read") {
    if (!canUseStaffInbox(role)) return json({ error: "forbidden" }, 403);
    const { data, error } = await admin.rpc("gmu_staff_notification_mark_all_read", {
      p_user_id: user.id,
      p_role: role,
    });
    if (error) return json({ error: "mark_all_read_failed", detail: error.message }, 500);
    return json({ ok: true, updated: Number(data ?? 0) });
  }

  return json({ error: "unsupported_action" }, 400);
});