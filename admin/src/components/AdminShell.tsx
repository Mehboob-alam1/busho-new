import type { ReactNode } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { logoutAdmin } from "../api";
import { IconLogout, IconUsers, IconVideo } from "./Icons";

export default function AdminShell({
  children,
  title,
  subtitle,
  actions,
}: {
  children: ReactNode;
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  const location = useLocation();
  const navigate = useNavigate();

  const links = [
    { to: "/", label: "Users", icon: IconUsers },
    { to: "/videos", label: "Videos", icon: IconVideo },
  ];

  return (
    <div className="container stack">
      <header className="topnav card">
        <div className="row" style={{ justifyContent: "space-between", width: "100%" }}>
          <div className="row">
            <div className="brand-wrap">
              <div className="brand-mark" aria-hidden>
                B
              </div>
              <strong className="brand">BushoRat</strong>
            </div>
            <nav className="nav-links" aria-label="Main">
              {links.map((l) => {
                const active =
                  location.pathname === l.to ||
                  (l.to !== "/" && location.pathname.startsWith(l.to));
                const Icon = l.icon;
                return (
                  <Link key={l.to} to={l.to} className={`nav-link ${active ? "active" : ""}`}>
                    <Icon size={16} />
                    <span>{l.label}</span>
                  </Link>
                );
              })}
            </nav>
          </div>
          <button
            className="btn secondary btn-icon"
            onClick={async () => {
              await logoutAdmin();
              navigate("/login");
            }}
          >
            <IconLogout size={16} />
            Sign out
          </button>
        </div>
      </header>

      <div className="page-header">
        <div>
          <h1>{title}</h1>
          {subtitle ? <p className="muted">{subtitle}</p> : null}
        </div>
        {actions ? <div className="row">{actions}</div> : null}
      </div>

      {children}
    </div>
  );
}
