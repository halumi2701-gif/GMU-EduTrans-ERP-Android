type JsonRecord = Record<string, unknown>;

const allowedRoles = new Set([
  "Owner", "Director", "Direktur", "Manager", "Manager EduTrans", "Admin",
]);
const allowedTables = new Set(["programs", "program_packages"]);

const headers = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers });
}

function strings(value: unknown, max = 5) {
  if (!Array.isArray(value)) return [];
  return value
    .map((x) => String(x ?? "").trim())
    .filter((x) => x.startsWith("https://"))
    .filter((x, i, list) => list.indexOf(x) === i)
    .slice(0, max);
}

function cover(value: unknown) {
  const raw = String(value ?? "").trim();
  return raw.startsWith("https://") ? raw : null;
}

async function serviceRest(path: string, init: RequestInit = {}) {
  const base = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!base || !serviceKey) throw new Error("Media backend belum terkonfigurasi.");
  const response = await fetch(`${base}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: serviceKey,
      Authorization: `Bearer ${serviceKey}`,
      Accept: "application/json",
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`Media database gagal (${response.status}). ${text.slice(0, 180)}`);
  return text;
}

async function currentUser(req: Request) {
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.startsWith("Bearer ")) return null;
  const base = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!base || !serviceKey) throw new Error("Media backend belum terkonfigurasi.");

  const userResponse = await fetch(`${base}/auth/v1/user`, {
    headers: { apikey: serviceKey, Authorization: auth },
  });
  if (!userResponse.ok) return null;
  const user = await userResponse.json();
  const userId = String(user?.id ?? "").trim();
  if (!userId) return null;

  const profileText = await serviceRest(
    `profiles?select=id,role,is_active&id=eq.${encodeURIComponent(userId)}&limit=1`,
  );
  const profiles = JSON.parse(profileText) as JsonRecord[];
  const profile = profiles[0];
  if (!profile || profile.is_active !== true || !allowedRoles.has(String(profile.role ?? ""))) return null;
  return { id: userId, role: String(profile.role) };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    const actor = await currentUser(req);
    if (!actor) return json({ error: "Unauthorized" }, 401);

    const body = await req.json().catch(() => ({})) as JsonRecord;
    const action = String(body.action ?? "load");
    const table = String(body.table ?? "");
    const entityId = String(body.entity_id ?? "").trim();
    if (!allowedTables.has(table) || !entityId) return json({ error: "Target media tidak valid." }, 400);

    if (action === "load") {
      const rows = JSON.parse(await serviceRest(
        `${table}?select=cover_image_url,gallery_urls&id=eq.${encodeURIComponent(entityId)}&limit=1`,
      )) as JsonRecord[];
      const row = rows[0] ?? {};
      return json({
        ok: true,
        media: {
          cover_image_url: cover(row.cover_image_url),
          gallery_urls: strings(row.gallery_urls),
        },
      });
    }

    if (action === "save") {
      const media = (body.media ?? {}) as JsonRecord;
      const payload = {
        cover_image_url: cover(media.cover_image_url),
        gallery_urls: strings(media.gallery_urls),
      };
      await serviceRest(
        `${table}?id=eq.${encodeURIComponent(entityId)}`,
        {
          method: "PATCH",
          headers: { Prefer: "return=minimal" },
          body: JSON.stringify(payload),
        },
      );
      return json({ ok: true, media: payload, actor_role: actor.role });
    }

    return json({ error: "Action tidak dikenal." }, 400);
  } catch (error) {
    console.error("internal-media-master", error);
    return json({ error: error instanceof Error ? error.message : "Media Master gagal." }, 500);
  }
});
