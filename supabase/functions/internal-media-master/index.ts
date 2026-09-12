type JsonRecord = Record<string, unknown>;

const allowedRoles = new Set([
  "Owner", "Director", "Direktur", "Manager", "Manager EduTrans", "Admin",
]);
const allowedTables = new Set(["programs", "program_packages"]);
const MEDIA_BUCKET = "edutrans-media";

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

function mediaUrls(row: JsonRecord): string[] {
  return [cover(row.cover_image_url), ...strings(row.gallery_urls)]
    .filter((x): x is string => Boolean(x));
}

function serverKey(): string {
  const named = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (named) {
    try {
      const parsed = JSON.parse(named) as Record<string, string>;
      if (parsed.default) return parsed.default;
    } catch (_) {
      // Fall through to single-key / legacy environments.
    }
  }
  return Deno.env.get("SUPABASE_SECRET_KEY") ||
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
}

function adminHeaders(key: string): Record<string, string> {
  const h: Record<string, string> = { apikey: key };
  if (!key.startsWith("sb_secret_")) h.Authorization = `Bearer ${key}`;
  return h;
}

async function serviceRest(path: string, init: RequestInit = {}) {
  const base = Deno.env.get("SUPABASE_URL");
  const key = serverKey();
  if (!base || !key) throw new Error("Media backend belum terkonfigurasi.");
  const response = await fetch(`${base}/rest/v1/${path}`, {
    ...init,
    headers: {
      ...adminHeaders(key),
      Accept: "application/json",
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`Media database gagal (${response.status}). ${text.slice(0, 180)}`);
  return text;
}

function entityFolder(table: string, entityId: string) {
  const type = table === "programs" ? "program" : "package";
  return `${type}/${entityId}/`;
}

function ownedStoragePath(urlValue: unknown, table: string, entityId: string): string | null {
  const base = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "") ?? "";
  const url = String(urlValue ?? "").trim();
  const publicPrefix = `${base}/storage/v1/object/public/${MEDIA_BUCKET}/`;
  const requiredFolder = entityFolder(table, entityId);
  if (!base || !url.startsWith(publicPrefix)) return null;
  const rawPath = url.slice(publicPrefix.length);
  let path = rawPath;
  try { path = decodeURIComponent(rawPath); } catch (_) {}
  if (!path.startsWith(requiredFolder) || path.includes("..")) return null;
  return path;
}

async function removeStoragePaths(paths: string[]): Promise<number> {
  const unique = [...new Set(paths.filter(Boolean))];
  if (!unique.length) return 0;
  const base = Deno.env.get("SUPABASE_URL");
  const key = serverKey();
  if (!base || !key) return 0;
  const response = await fetch(`${base}/storage/v1/object/${MEDIA_BUCKET}`, {
    method: "DELETE",
    headers: {
      ...adminHeaders(key),
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ prefixes: unique }),
  });
  if (!response.ok) {
    console.warn("Media cleanup gagal", response.status, (await response.text()).slice(0, 180));
    return 0;
  }
  return unique.length;
}

async function currentUser(req: Request) {
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.startsWith("Bearer ")) return null;
  const base = Deno.env.get("SUPABASE_URL");
  const key = serverKey();
  if (!base || !key) throw new Error("Media backend belum terkonfigurasi.");

  const userResponse = await fetch(`${base}/auth/v1/user`, {
    headers: { apikey: key, Authorization: auth },
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

    if (action === "catalog") {
      const [programText, packageText] = await Promise.all([
        serviceRest(
          "programs?select=id,name,category,is_active,cover_image_url,gallery_urls&is_active=eq.true&order=sort_order.asc",
        ),
        serviceRest(
          "program_packages?select=id,package_code,program_id,name,status,is_active,cover_image_url,gallery_urls&order=sort_order.asc",
        ),
      ]);
      const programRows = JSON.parse(programText) as JsonRecord[];
      const packageRows = JSON.parse(packageText) as JsonRecord[];
      return json({
        ok: true,
        programs: programRows.map((row) => ({
          id: row.id ?? null,
          name: row.name ?? null,
          category: row.category ?? null,
          is_active: row.is_active === true,
          cover_image_url: cover(row.cover_image_url),
          gallery_urls: strings(row.gallery_urls),
        })),
        packages: packageRows.map((row) => ({
          id: row.id ?? null,
          package_code: row.package_code ?? null,
          program_id: row.program_id ?? null,
          name: row.name ?? null,
          status: row.status ?? null,
          is_active: row.is_active === true,
          cover_image_url: cover(row.cover_image_url),
          gallery_urls: strings(row.gallery_urls),
        })),
      });
    }

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

    if (action === "discard") {
      const candidates = Array.isArray(body.urls) ? body.urls : [];
      const paths = candidates
        .map((url) => ownedStoragePath(url, table, entityId))
        .filter((path): path is string => Boolean(path));
      const removed = await removeStoragePaths(paths);
      return json({ ok: true, removed, actor_role: actor.role });
    }

    if (action === "save") {
      const previousRows = JSON.parse(await serviceRest(
        `${table}?select=cover_image_url,gallery_urls&id=eq.${encodeURIComponent(entityId)}&limit=1`,
      )) as JsonRecord[];
      const previous = previousRows[0] ?? {};
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

      const retained = new Set(mediaUrls(payload));
      const removedOldPaths = mediaUrls(previous)
        .filter((url) => !retained.has(url))
        .map((url) => ownedStoragePath(url, table, entityId))
        .filter((path): path is string => Boolean(path));
      const cleaned = await removeStoragePaths(removedOldPaths);

      return json({ ok: true, media: payload, cleaned, actor_role: actor.role });
    }

    return json({ error: "Action tidak dikenal." }, 400);
  } catch (error) {
    console.error("internal-media-master", error);
    return json({ error: error instanceof Error ? error.message : "Media Master gagal." }, 500);
  }
});
