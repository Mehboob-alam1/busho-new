import { useEffect, useRef } from "react";
import { onValue, ref, Unsubscribe } from "firebase/database";
import { UserRow, watchUsers } from "../api";
import { db } from "../firebase";
import {
  ensureNotifyPermission,
  getAdminChatFocusUserId,
  notifyAdmin,
} from "../helpers/browserNotify";

type MetaSnap = {
  unreadForAdmin?: boolean;
  lastMessageAt?: number;
  lastPreview?: string;
};

/**
 * Browser notifications when a user sends chat or requests a call.
 */
export default function AdminAlertsWatcher() {
  const subsRef = useRef<Unsubscribe[]>([]);
  const lastMsgTsRef = useRef<Record<string, number>>({});
  const lastCallTsRef = useRef<Record<string, number>>({});
  const usersRef = useRef<UserRow[]>([]);

  useEffect(() => {
    void ensureNotifyPermission();
    const unsubs: Unsubscribe[] = [];

    const clearSubs = () => {
      subsRef.current.forEach((u) => u());
      subsRef.current = [];
    };

    const attachUserListeners = (users: UserRow[]) => {
      clearSubs();
      usersRef.current = users;
      for (const u of users) {
        const uid = u.id;
        const metaUnsub = onValue(ref(db, `admin_chat/${uid}/meta`), (snap) => {
          const meta = (snap.val() || {}) as MetaSnap;
          if (!meta.unreadForAdmin) return;
          const ts = Number(meta.lastMessageAt || 0);
          if (ts <= 0 || ts <= (lastMsgTsRef.current[uid] || 0)) return;
          lastMsgTsRef.current[uid] = ts;
          if (getAdminChatFocusUserId() === uid) return;
          const name = u.display_name || u.email || uid.slice(0, 8);
          const preview = meta.lastPreview || "New message";
          notifyAdmin(`Message from ${name}`, preview, `chat-${uid}-${ts}`);
        });
        subsRef.current.push(metaUnsub);

        const callUnsub = onValue(ref(db, `admin_calls/${uid}`), (snap) => {
          if (!snap.exists()) return;
          const val = snap.val() as {
            status?: string;
            from?: string;
            callType?: string;
            timestamp?: number;
          };
          if (val.status !== "requested" || val.from !== "user") return;
          const ts = Number(val.timestamp || Date.now());
          if (ts <= (lastCallTsRef.current[uid] || 0)) return;
          lastCallTsRef.current[uid] = ts;
          if (getAdminChatFocusUserId() === uid) return;
          const name = u.display_name || u.email || uid.slice(0, 8);
          const kind = val.callType === "audio" ? "Audio call" : "Video call";
          notifyAdmin(`${kind} request`, `${name} wants to talk`, `call-${uid}-${ts}`);
        });
        subsRef.current.push(callUnsub);
      }
    };

    const usersUnsub = watchUsers(
      (rows) => attachUserListeners(rows),
      () => {}
    );
    unsubs.push(usersUnsub);

    return () => {
      clearSubs();
      unsubs.forEach((u) => u());
    };
  }, []);

  return null;
}
