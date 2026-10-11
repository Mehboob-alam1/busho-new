let permissionRequested = false;

export async function ensureNotifyPermission(): Promise<boolean> {
  if (typeof Notification === "undefined") return false;
  if (Notification.permission === "granted") return true;
  if (Notification.permission === "denied") return false;
  if (permissionRequested) return false;
  permissionRequested = true;
  try {
    const result = await Notification.requestPermission();
    return result === "granted";
  } catch {
    return false;
  }
}

export function playAdminAlertTone() {
  try {
    const ctx = new AudioContext();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.frequency.value = 880;
    gain.gain.value = 0.08;
    osc.start();
    setTimeout(() => {
      osc.stop();
      void ctx.close();
    }, 180);
  } catch {
    /* ignore */
  }
}

export function notifyAdmin(title: string, body: string, tag?: string) {
  void ensureNotifyPermission().then((ok) => {
    playAdminAlertTone();
    if (!ok || typeof Notification === "undefined") return;
    try {
      new Notification(title, { body, tag, silent: false });
    } catch {
      /* ignore */
    }
  });
}

/** Set while admin is actively on a user's Live chat tab (suppress duplicate alerts). */
export function setAdminChatFocusUserId(userId: string | null) {
  if (userId) {
    sessionStorage.setItem("admin_chat_focus_user", userId);
  } else {
    sessionStorage.removeItem("admin_chat_focus_user");
  }
}

export function getAdminChatFocusUserId(): string | null {
  return sessionStorage.getItem("admin_chat_focus_user");
}
