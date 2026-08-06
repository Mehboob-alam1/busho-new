import { useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { User } from "firebase/auth";
import {
  hasAdminSession,
  isAdminUser,
  logoutAdmin,
  watchAuth,
} from "./api";
import LoginPage from "./pages/LoginPage";
import UsersPage from "./pages/UsersPage";
import UserDetailPage from "./pages/UserDetailPage";
import VideosPage from "./pages/VideosPage";

function Private({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [allowed, setAllowed] = useState(false);

  useEffect(() => {
    let active = true;

    const unsub = watchAuth((next: User | null) => {
      if (!active) return;

      if (next && !isAdminUser(next)) {
        void logoutAdmin();
        setAllowed(false);
        setReady(true);
        return;
      }

      // Prefer Firebase admin session; fall back to local session while auth hydrates.
      setAllowed(Boolean(next && isAdminUser(next)) || hasAdminSession());
      setReady(true);
    });

    // If auth is slow, still honor an existing local session quickly.
    if (hasAdminSession()) {
      setAllowed(true);
      setReady(true);
    }

    return () => {
      active = false;
      unsub();
    };
  }, []);

  if (!ready) {
    return <div className="login-wrap muted">Preparing your workspace…</div>;
  }
  if (!allowed) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <Private>
            <UsersPage />
          </Private>
        }
      />
      <Route
        path="/users/:userId"
        element={
          <Private>
            <UserDetailPage />
          </Private>
        }
      />
      <Route
        path="/videos"
        element={
          <Private>
            <VideosPage />
          </Private>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
