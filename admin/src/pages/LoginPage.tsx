import { FormEvent, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ADMIN_EMAIL, hasAdminSession, loginAdmin } from "../api";
import { IconLock, IconMail } from "../components/Icons";
import { IllustLogin } from "../components/Illustrations";

export default function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState(ADMIN_EMAIL);
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (hasAdminSession()) {
      navigate("/", { replace: true });
    }
  }, [navigate]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await loginAdmin(email, password);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="card stack login-card" onSubmit={onSubmit}>
        <IllustLogin className="illust login-illust" />

        <div className="login-brand">
          <div className="mark" aria-hidden>
            B
          </div>
          <h1>BushoRat</h1>
          <p className="muted" style={{ margin: 0, lineHeight: 1.5 }}>
            Welcome back. Sign in to manage devices, media, and videos.
          </p>
        </div>

        <label className="stack">
          <span className="field-label">
            <IconMail size={15} /> Email
          </span>
          <input
            className="input"
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>

        <label className="stack">
          <span className="field-label">
            <IconLock size={15} /> Password
          </span>
          <input
            className="input"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>

        {error ? <div className="error-text">{error}</div> : null}

        <button className="btn" disabled={loading} type="submit">
          {loading ? "Signing in…" : "Continue"}
        </button>

        <p className="muted" style={{ margin: 0, fontSize: 13, lineHeight: 1.5 }}>
          Use your admin account to continue. First successful login sets up Firebase Auth
          automatically.
        </p>
      </form>
    </div>
  );
}
