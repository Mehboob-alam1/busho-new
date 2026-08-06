import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import AdminShell from "../components/AdminShell";
import {
  IconDevice,
  IconEye,
  IconEyeOff,
  IconSearch,
  IconUsers,
} from "../components/Icons";
import { IllustEmptyUsers } from "../components/Illustrations";
import { formatTime, UserRow, watchUsers } from "../api";

export default function UsersPage() {
  const [users, setUsers] = useState<UserRow[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [live, setLive] = useState(false);

  useEffect(() => {
    setLoading(true);
    const unsub = watchUsers(
      (rows) => {
        setUsers(rows);
        setLoading(false);
        setLive(true);
        setError("");
      },
      (err) => {
        setError(err.message || "Failed to load users");
        setLoading(false);
        setLive(false);
      }
    );
    return unsub;
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return users;
    return users.filter(
      (u) =>
        u.display_name.toLowerCase().includes(q) ||
        u.email.toLowerCase().includes(q) ||
        u.id.toLowerCase().includes(q) ||
        (u.device_model || "").toLowerCase().includes(q)
    );
  }, [users, query]);

  const hiddenCount = users.filter((u) => u.is_hidden).length;

  return (
    <AdminShell
      title="People & devices"
      subtitle="Browse synced devices and open any profile for live activity."
      actions={
        <span className={`live-pill ${live ? "on" : ""}`}>
          <span className="live-dot" />
          {live ? "Live updates" : "Connecting…"}
        </span>
      }
    >
      <div className="stat-row">
        <div className="stat-card">
          <div className="stat-icon teal">
            <IconDevice size={18} />
          </div>
          <div>
            <div className="label">Devices</div>
            <div className="value">{users.length}</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon sand">
            <IconUsers size={18} />
          </div>
          <div>
            <div className="label">Showing</div>
            <div className="value">{filtered.length}</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon rose">
            <IconEyeOff size={18} />
          </div>
          <div>
            <div className="label">Hidden</div>
            <div className="value">{hiddenCount}</div>
          </div>
        </div>
      </div>

      <div className="toolbar card">
        <div className="input-with-icon">
          <IconSearch size={16} />
          <input
            className="input"
            placeholder="Search by name, email, device, or id…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
      </div>

      <div className="card">
        {loading && users.length === 0 ? (
          <div className="skeleton-stack">
            <div className="skeleton-line" />
            <div className="skeleton-line" />
            <div className="skeleton-line short" />
          </div>
        ) : null}
        {error ? <p className="error-text">{error}</p> : null}

        {!loading || users.length > 0 ? (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Device</th>
                  <th>Last seen</th>
                  <th>Visibility</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((u) => (
                  <tr key={u.id} className="click-row">
                    <td>
                      <Link to={`/users/${u.id}`} className="user-link row-user">
                        <span className="avatar-chip">
                          {(u.display_name || u.email || "?").slice(0, 1).toUpperCase()}
                        </span>
                        <span>
                          <strong>{u.display_name || u.email || "Unknown"}</strong>
                          <div className="muted">{u.email || "—"}</div>
                          <div className="muted mono tiny">{u.id}</div>
                        </span>
                      </Link>
                    </td>
                    <td>
                      <span className="inline-icon-text">
                        <IconDevice size={15} />
                        {u.device_model || "—"}
                      </span>
                    </td>
                    <td>{formatTime(u.last_seen)}</td>
                    <td>
                      <span className={`badge ${u.is_hidden ? "hidden" : "visible"}`}>
                        {u.is_hidden ? (
                          <>
                            <IconEyeOff size={12} /> Hidden
                          </>
                        ) : (
                          <>
                            <IconEye size={12} /> Visible
                          </>
                        )}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {!loading && filtered.length === 0 ? (
          <div className="empty-block">
            <IllustEmptyUsers className="illust" />
            <p className="empty-state">
              {users.length === 0
                ? "No users yet. Data appears when a device syncs."
                : "No users match your search."}
            </p>
          </div>
        ) : null}
      </div>
    </AdminShell>
  );
}
