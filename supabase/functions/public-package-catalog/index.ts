import { publicPackage, publicProgram } from "../_shared/public_catalog_media.ts";

type Row = Record<string, unknown>;

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

function asInt(value: string | null, fallback: number) {
  const n = Number.parseInt(String(value ?? ""), 10);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

function asDate(value: string | null): string {
  const raw = String(value ?? "").trim();
  return /^\d{4}-\d{2}-\d{2}$/.test(raw)
    ? raw
    : new Date().toISOString().slice(0, 10);
}

function num(value: unknown): number {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n : 0;
}

function text(value: unknown): string {
  return String(value ?? "").trim();
}

function serverKey(): string {
  const named = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (named) {
    try {
      const parsed = JSON.parse(named) as Record<string, string>;
      if (parsed.default) return parsed.default;
    } catch (_) {
      // Fall through for local/legacy environments.
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

function activeForTrip(row: Row, tripDate: string, pax: number) {
  if (row.is_active !== true) return false;
  if (text(row.status).toUpperCase() !== "ACTIVE") return false;
  if (num(row.price_per_pax) <= 0) return false;
  if (pax < Math.max(1, Math.trunc(num(row.min_pax) || 1))) return false;

  const from = text(row.effective_from);
  const until = text(row.effective_until);
  if (from && from > tripDate) return false;
  if (until && until < tripDate) return false;
  return true;
}

async function rest(path: string): Promise<Row[]> {
  const base = Deno.env.get("SUPABASE_URL");
  const key = serverKey();
  if (!base || !key) throw new Error("Backend catalog belum terkonfigurasi.");

  const response = await fetch(`${base}/rest/v1/${path}`, {
    headers: {
      ...adminHeaders(key),
      Accept: "application/json",
    },
  });
  if (!response.ok) {
    const detail = (await response.text()).slice(0, 240);
    throw new Error(`Catalog query gagal (${response.status}). ${detail}`);
  }
  return await response.json();
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "GET") return json({ error: "Method not allowed" }, 405);

  try {
    const url = new URL(req.url);
    const requestedProgramId = text(url.searchParams.get("program_id"));
    const tripDate = asDate(url.searchParams.get("trip_date"));
    const pax = asInt(url.searchParams.get("pax"), 1);

    // Explicit public-content projection only. Never select price_note or cost fields.
    const [programRows, packageRows] = await Promise.all([
      rest(
        "programs?select=id,slug,name,category,short_description,min_pax,is_active,sort_order,cover_image_url,gallery_urls&is_active=eq.true&order=sort_order.asc",
      ),
      rest(
        "program_packages?select=id,package_code,program_id,name,description,price_per_pax,min_pax,facilities,effective_from,effective_until,status,is_active,sort_order,cover_image_url,gallery_urls&is_active=eq.true&status=eq.ACTIVE&order=sort_order.asc",
      ),
    ]);

    const programById = new Map(programRows.map((row) => [text(row.id), row]));
    const requestedProgram = requestedProgramId ? programById.get(requestedProgramId) : undefined;
    const customBrowse = !requestedProgramId ||
      text(requestedProgram?.slug).toLowerCase() === "custom-educational-trip" ||
      text(requestedProgram?.name).toLowerCase() === "custom educational trip";

    // Keep v9 behavior: fetch ACTIVE packages once, then filter program in memory.
    const eligible = packageRows
      .filter((row) => activeForTrip(row, tripDate, pax))
      .filter((row) => customBrowse || text(row.program_id) === requestedProgramId)
      .map((row) => {
        const program = programById.get(text(row.program_id));
        const enriched: Row = {
          ...row,
          program_name: program?.name ?? null,
          program_category: program?.category ?? null,
        };
        return publicPackage(enriched, num(row.price_per_pax) * pax);
      });

    return json({
      ok: true,
      version: "v10-media-sync",
      catalog_status: eligible.length ? "AVAILABLE" : "NO_ACTIVE_PACKAGE",
      catalog_scope: customBrowse ? "ALL_ACTIVE_PACKAGES" : "PROGRAM",
      program_id: requestedProgramId || null,
      trip_date: tripDate,
      pax,
      programs: programRows.map(publicProgram),
      items: eligible,
    });
  } catch (error) {
    console.error("public-package-catalog v10", error);
    return json({
      ok: false,
      catalog_status: "ERROR",
      error: error instanceof Error ? error.message : "Catalog gagal dimuat.",
    }, 500);
  }
});
