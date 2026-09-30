type JsonRecord = Record<string, unknown>;

const allowedRoles = new Set([
  "Owner", "Director", "Direktur", "Manager", "Manager EduTrans", "Admin", "Finance"
]);

const ROOT_ARCHIVE_FOLDER_ID =
  Deno.env.get("GMU_DRIVE_ROOT_FOLDER_ID") || "16Czc7Bd3sI7Bc7OV7RMmknjyNAVDfwpm";
const ORDER_PARENT_FOLDER_ID =
  Deno.env.get("GMU_DRIVE_ORDER_FOLDER_ID") || "1eLza3RKK7sMPuHF55k1V8G09_KN9rPJR";

const ORDER_SUBFOLDERS = [
  "01 - PENAWARAN",
  "02 - BOOKING",
  "03 - PEMBAYARAN",
  "04 - DATA PESERTA",
  "05 - OPERASIONAL",
  "06 - TIKET",
  "07 - DOKUMENTASI",
  "08 - LAPORAN",
  "09 - DOKUMEN FINAL",
];

const headers = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-gmu-drive-smoke",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers });
}

function serverKey(): string {
  const named = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (named) {
    try {
      const parsed = JSON.parse(named) as Record<string, string>;
      if (parsed.default) return parsed.default;
    } catch (_) {}
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
  if (!base || !key) throw new Error("Backend Supabase belum terkonfigurasi.");
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
  if (!response.ok) throw new Error(`Database gagal (${response.status}). ${text.slice(0, 200)}`);
  return text;
}

async function currentUser(req: Request) {
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.startsWith("Bearer ")) return null;
  const base = Deno.env.get("SUPABASE_URL");
  const key = serverKey();
  if (!base || !key) throw new Error("Backend Supabase belum terkonfigurasi.");

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

async function googleAccessToken() {
  const clientId = (Deno.env.get("GOOGLE_DRIVE_CLIENT_ID") || "").trim();
  const clientSecret = (Deno.env.get("GOOGLE_DRIVE_CLIENT_SECRET") || "").trim();
  const refreshToken = (Deno.env.get("GOOGLE_DRIVE_REFRESH_TOKEN") || "").trim();
  if (!clientId || !clientSecret || !refreshToken) {
    throw new Error("Kredensial Google Drive GMU belum dipasang di Edge Function secrets.");
  }
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      refresh_token: refreshToken,
      grant_type: "refresh_token",
    }),
  });
  const body = await response.json();
  if (!response.ok || !body.access_token) {
    throw new Error(`Google OAuth gagal (${response.status}).`);
  }
  return String(body.access_token);
}

async function driveFetch(path: string, init: RequestInit = {}) {
  const token = await googleAccessToken();
  const response = await fetch(`https://www.googleapis.com/drive/v3/${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "application/json",
      ...(init.headers ?? {}),
    },
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`Google Drive gagal (${response.status}). ${text.slice(0, 220)}`);
  return text ? JSON.parse(text) : {};
}

async function createFolder(name: string, parentId: string) {
  const result = await driveFetch("files?supportsAllDrives=true&fields=id,name,webViewLink,parents", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      name,
      mimeType: "application/vnd.google-apps.folder",
      parents: [parentId],
    }),
  }) as JsonRecord;
  return result;
}

async function findChildFolder(name: string, parentId: string) {
  const escaped = name.replace(/'/g, "\\'");
  const q = encodeURIComponent(
    `name='${escaped}' and '${parentId}' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false`,
  );
  const result = await driveFetch(`files?q=${q}&spaces=drive&fields=files(id,name,webViewLink,parents)&pageSize=10`) as JsonRecord;
  const files = Array.isArray(result.files) ? result.files as JsonRecord[] : [];
  return files[0] ?? null;
}

async function ensureChildFolder(name: string, parentId: string) {
  return (await findChildFolder(name, parentId)) || await createFolder(name, parentId);
}

function safePart(value: unknown, fallback = "TANPA-NAMA") {
  return String(value ?? "")
    .trim()
    .replace(/[\\/:*?"<>|#%{}~&]/g, "-")
    .replace(/\s+/g, " ")
    .slice(0, 90) || fallback;
}

async function upsertFolderRecord(payload: JsonRecord) {
  await serviceRest("gmu_drive_folders?on_conflict=entity_type,entity_id", {
    method: "POST",
    headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
    body: JSON.stringify(payload),
  });
}

async function registerDocument(payload: JsonRecord) {
  await serviceRest("gmu_drive_documents?on_conflict=drive_file_id", {
    method: "POST",
    headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
    body: JSON.stringify(payload),
  });
}

async function writeSyncAudit(actorId: string, action: string, recordId: string, message: string) {
  if (!actorId) return;
  try {
    await serviceRest("audit_logs", {
      method: "POST",
      headers: { Prefer: "return=minimal" },
      body: JSON.stringify({
        user_id: actorId,
        action,
        table_name: "gmu_drive_archive",
        record_id: recordId,
        message,
      }),
    });
  } catch (auditError) {
    console.error("gmu-drive-audit", auditError);
  }
}

async function updateFolderMetadata(entityId: string, metadata: JsonRecord) {
  await serviceRest(
    `gmu_drive_folders?entity_type=eq.order&entity_id=eq.${encodeURIComponent(entityId)}`,
    {
      method: "PATCH",
      headers: { Prefer: "return=minimal" },
      body: JSON.stringify({ metadata, updated_at: new Date().toISOString() }),
    },
  );
}

async function uploadBase64(name: string, mimeType: string, parentId: string, base64: string) {
  if (base64.length > 8_500_000) throw new Error("Ukuran dokumen terlalu besar untuk upload inline.");
  const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  const boundary = "gmu_edutrans_" + crypto.randomUUID();
  const metadata = JSON.stringify({ name, parents: [parentId], mimeType });
  const encoder = new TextEncoder();
  const prefix = encoder.encode(
    `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${metadata}\r\n` +
    `--${boundary}\r\nContent-Type: ${mimeType}\r\n\r\n`,
  );
  const suffix = encoder.encode(`\r\n--${boundary}--`);
  const body = new Uint8Array(prefix.length + bytes.length + suffix.length);
  body.set(prefix, 0);
  body.set(bytes, prefix.length);
  body.set(suffix, prefix.length + bytes.length);

  const token = await googleAccessToken();
  const response = await fetch(
    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true&fields=id,name,webViewLink,mimeType,parents",
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": `multipart/related; boundary=${boundary}`,
      },
      body,
    },
  );
  const text = await response.text();
  if (!response.ok) throw new Error(`Upload Drive gagal (${response.status}). ${text.slice(0, 220)}`);
  return JSON.parse(text) as JsonRecord;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  let actor: { id: string; role: string } | null = null;
  try {
    const body = await req.json().catch(() => ({})) as JsonRecord;
    const action = String(body.action ?? "health");
    const configuredSmokeToken = (Deno.env.get("GMU_DRIVE_SMOKE_TOKEN") || "").trim();
    const suppliedSmokeToken = (req.headers.get("x-gmu-drive-smoke") || "").trim();
    const smokeAuthorized =
      action === "smoke_test" &&
      configuredSmokeToken.length >= 32 &&
      suppliedSmokeToken === configuredSmokeToken;

    if (action === "smoke_test" && !smokeAuthorized) {
      return json({ error: "Unauthorized smoke test" }, 401);
    }

    actor = smokeAuthorized
      ? { id: "", role: "SYSTEM_SMOKE" }
      : await currentUser(req);
    if (!actor) return json({ error: "Unauthorized" }, 401);

    if (action === "health") {
      const token = await googleAccessToken();
      const response = await fetch(
        `https://www.googleapis.com/drive/v3/files/${ROOT_ARCHIVE_FOLDER_ID}?fields=id,name,webViewLink,owners(emailAddress)`,
        { headers: { Authorization: `Bearer ${token}` } },
      );
      const folder = await response.json();
      if (!response.ok) {
        await writeSyncAudit(actor.id, "DRIVE_HEALTH_ERROR", "health", `Google Drive health gagal HTTP ${response.status}.`);
        return json({ ok: false, drive: folder }, 502);
      }
      await writeSyncAudit(actor.id, "DRIVE_HEALTH_OK", "health", `Google Drive terhubung ke ${String(folder?.name ?? "root arsip")}.`);
      return json({ ok: true, root: folder, actor_role: actor.role });
    }

    if (action === "ensure_order_folder" || action === "smoke_test") {
      const entityId = safePart(body.entity_id, "");
      const bookingCode = safePart(body.booking_code, entityId);
      const customerName = safePart(body.customer_name, "CUSTOMER");
      const activityDate = safePart(body.activity_date, "TANGGAL-BELUM-DITENTUKAN");
      if (!entityId) return json({ error: "entity_id wajib." }, 400);

      const existingText = await serviceRest(
        `gmu_drive_folders?select=*&entity_type=eq.order&entity_id=eq.${encodeURIComponent(entityId)}&limit=1`,
      );
      const existingRows = JSON.parse(existingText) as JsonRecord[];
      if (existingRows.length) {
        const existing = existingRows[0];
        const existingFolderId = String(existing.drive_folder_id ?? "");
        if (!existingFolderId) throw new Error("Record folder Drive ditemukan tetapi drive_folder_id kosong.");

        const children: Record<string, string> = {};
        for (const subfolder of ORDER_SUBFOLDERS) {
          const child = await ensureChildFolder(subfolder, existingFolderId);
          children[subfolder] = String(child.id ?? "");
        }

        const previousMetadata = (existing.metadata ?? {}) as JsonRecord;
        const metadata = {
          ...previousMetadata,
          children,
          customer_name: customerName,
          activity_date: activityDate,
          structure_verified_at: new Date().toISOString(),
        };
        await updateFolderMetadata(entityId, metadata);
        const repaired = { ...existing, metadata };
        await writeSyncAudit(
          actor.id,
          "DRIVE_ORDER_REUSED",
          entityId,
          `Folder booking ${bookingCode} digunakan ulang; 9 subfolder diverifikasi tanpa duplikasi.`,
        );
        return json({ ok: true, reused: true, folder: repaired, actor_role: actor.role });
      }

      const folderName = `${bookingCode} - ${customerName} - ${activityDate}`;
      const orderFolder = await ensureChildFolder(folderName, ORDER_PARENT_FOLDER_ID);
      const orderFolderId = String(orderFolder.id ?? "");
      if (!orderFolderId) throw new Error("Folder order gagal dibuat.");

      const children: Record<string, string> = {};
      for (const subfolder of ORDER_SUBFOLDERS) {
        const child = await ensureChildFolder(subfolder, orderFolderId);
        children[subfolder] = String(child.id ?? "");
      }

      const folderUrl = String(orderFolder.webViewLink ?? `https://drive.google.com/drive/folders/${orderFolderId}`);
      const record = {
        entity_type: "order",
        entity_id: entityId,
        booking_code: bookingCode,
        drive_folder_id: orderFolderId,
        drive_folder_url: folderUrl,
        folder_name: folderName,
        parent_drive_folder_id: ORDER_PARENT_FOLDER_ID,
        metadata: { children, customer_name: customerName, activity_date: activityDate },
        created_by: actor.id || null,
      };
      await upsertFolderRecord(record);
      await writeSyncAudit(
        actor.id,
        "DRIVE_ORDER_CREATED",
        entityId,
        `Folder booking ${bookingCode} + 9 subfolder berhasil dibuat.`,
      );
      return json({ ok: true, reused: false, folder: record, actor_role: actor.role });
    }

    if (action === "upload_document") {
      const entityType = safePart(body.entity_type, "order");
      const entityId = safePart(body.entity_id, "");
      const documentType = safePart(body.document_type, "DOKUMEN");
      const fileName = safePart(body.file_name, "dokumen");
      const mimeType = String(body.mime_type ?? "application/octet-stream").trim();
      const contentBase64 = String(body.content_base64 ?? "").trim();
      const targetSubfolder = safePart(body.target_subfolder, "09 - DOKUMEN FINAL");
      if (!entityId || !contentBase64) return json({ error: "entity_id dan content_base64 wajib." }, 400);

      const folderRows = JSON.parse(await serviceRest(
        `gmu_drive_folders?select=*&entity_type=eq.${encodeURIComponent(entityType)}&entity_id=eq.${encodeURIComponent(entityId)}&limit=1`,
      )) as JsonRecord[];
      if (!folderRows.length) return json({ error: "Folder Drive entitas belum dibuat." }, 409);
      const folder = folderRows[0];
      const metadata = (folder.metadata ?? {}) as JsonRecord;
      const children = (metadata.children ?? {}) as Record<string, string>;
      const parentId = children[targetSubfolder] || String(folder.drive_folder_id ?? "");
      const uploaded = await uploadBase64(fileName, mimeType, parentId, contentBase64);
      const fileId = String(uploaded.id ?? "");
      const fileUrl = String(uploaded.webViewLink ?? `https://drive.google.com/open?id=${fileId}`);

      const record = {
        entity_type: entityType,
        entity_id: entityId,
        document_type: documentType,
        file_name: fileName,
        drive_file_id: fileId,
        drive_file_url: fileUrl,
        mime_type: mimeType,
        source: String(body.source ?? "erp"),
        metadata: { target_subfolder: targetSubfolder },
        created_by: actor.id,
      };
      await registerDocument(record);
      await writeSyncAudit(
        actor.id,
        "DRIVE_DOCUMENT_UPLOADED",
        entityId,
        `Dokumen ${fileName} diunggah ke ${targetSubfolder}.`,
      );
      return json({ ok: true, document: record, actor_role: actor.role });
    }

    return json({ error: "Action tidak dikenal." }, 400);
  } catch (error) {
    console.error("gmu-drive-archive", error);
    const message = error instanceof Error ? error.message : "Integrasi Drive gagal.";
    if (actor?.id) {
      await writeSyncAudit(actor.id, "DRIVE_SYNC_ERROR", "runtime", message);
    }
    return json({ error: message }, 500);
  }
});
