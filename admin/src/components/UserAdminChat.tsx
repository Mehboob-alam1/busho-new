import { useEffect, useRef, useState } from "react";
import {
  AdminCallSignal,
  AdminChatMessage,
  clearAdminCall,
  formatTime,
  initiateAdminCall,
  markAdminChatRead,
  sendAdminChatText,
  setAdminDisplayName,
  uploadAdminChatFile,
  watchAdminCallSignal,
  watchAdminChatMessages,
  watchAdminDisplayName,
} from "../api";
import { IconPhone, IconVideo } from "./Icons";

type PeerInstance = {
  on: (event: string, cb: (...args: unknown[]) => void) => void;
  call: (
    peerId: string,
    stream: MediaStream
  ) => {
    on: (event: string, cb: (...args: unknown[]) => void) => void;
  };
  destroy: () => void;
};

declare global {
  interface Window {
    Peer?: new (
      id: string,
      opts?: {
        host?: string;
        port?: number;
        path?: string;
        secure?: boolean;
      }
    ) => PeerInstance;
  }
}

const PEER_OPTS = {
  host: "0.peerjs.com",
  port: 443,
  path: "/",
  secure: true,
};

function loadPeerScript(): Promise<void> {
  if (window.Peer) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const s = document.createElement("script");
    s.src = "https://cdnjs.cloudflare.com/ajax/libs/peerjs/1.3.2/peerjs.min.js";
    s.onload = () => resolve();
    s.onerror = () => reject(new Error("Could not load PeerJS"));
    document.head.appendChild(s);
  });
}

type ActiveCall = {
  connId: string;
  video: boolean;
  dialUserPeerId?: string;
};

export default function UserAdminChat({
  userId,
  userDisplayName,
  chatFocused = false,
}: {
  userId: string;
  userDisplayName: string;
  chatFocused?: boolean;
}) {
  const [messages, setMessages] = useState<AdminChatMessage[]>([]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [uploadPct, setUploadPct] = useState<number | null>(null);
  const [callSignal, setCallSignal] = useState<AdminCallSignal | null>(null);
  const [activeCall, setActiveCall] = useState<ActiveCall | null>(null);
  const [callError, setCallError] = useState("");
  const [callStatus, setCallStatus] = useState("");
  const [adminDisplayName, setAdminDisplayNameLocal] = useState("Support");
  const [nameDraft, setNameDraft] = useState("");
  const [savingName, setSavingName] = useState(false);

  const listRef = useRef<HTMLDivElement>(null);
  const localVideoRef = useRef<HTMLVideoElement>(null);
  const remoteVideoRef = useRef<HTMLVideoElement>(null);
  const peerRef = useRef<PeerInstance | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const callSignalSeenRef = useRef(false);
  const userPeerDialedRef = useRef(false);

  useEffect(() => {
    const unsub = watchAdminDisplayName((name) => {
      setAdminDisplayNameLocal(name);
      setNameDraft(name);
    });
    return unsub;
  }, []);

  useEffect(() => {
    const unsub = watchAdminChatMessages(
      userId,
      (list) => {
        setMessages(list);
        if (chatFocused) {
          void markAdminChatRead(userId);
        }
      },
      (err) => console.error(err)
    );
    return unsub;
  }, [userId, chatFocused]);

  useEffect(() => {
    if (chatFocused) {
      void markAdminChatRead(userId);
    }
  }, [chatFocused, userId]);

  useEffect(() => {
    const unsub = watchAdminCallSignal(userId, setCallSignal);
    return unsub;
  }, [userId]);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: "smooth" });
  }, [messages]);

  function teardownLocalCall() {
    peerRef.current?.destroy();
    peerRef.current = null;
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    if (localVideoRef.current) localVideoRef.current.srcObject = null;
    if (remoteVideoRef.current) remoteVideoRef.current.srcObject = null;
  }

  useEffect(() => {
    if (callSignal) {
      callSignalSeenRef.current = true;
    }
  }, [callSignal]);

  useEffect(() => {
    if (!activeCall) {
      callSignalSeenRef.current = false;
      return;
    }
    if (callSignal?.status === "ended") {
      teardownLocalCall();
      setActiveCall(null);
      setCallStatus("Call ended");
      setCallError("");
      callSignalSeenRef.current = false;
      return;
    }
    if (!callSignal && callSignalSeenRef.current) {
      teardownLocalCall();
      setActiveCall(null);
      setCallStatus("Call ended");
      setCallError("");
      callSignalSeenRef.current = false;
    }
  }, [callSignal, activeCall]);

  useEffect(() => {
    if (!activeCall || !callSignal?.userPeerId || !peerRef.current || !streamRef.current) {
      return;
    }
    if (userPeerDialedRef.current || callStatus === "Connected") {
      return;
    }
    const target = callSignal.userPeerId;
    const peer = peerRef.current;
    const stream = streamRef.current;

    const tryDial = (left: number) => {
      setCallStatus(left > 10 ? "User answered — connecting…" : "Connecting to user…");
      try {
        const outgoing = peer.call(target, stream);
        if (outgoing) {
          userPeerDialedRef.current = true;
          outgoing.on("stream", (...streamArgs: unknown[]) => {
            const remoteStream = streamArgs[0] as MediaStream;
            setCallStatus("Connected");
            if (remoteVideoRef.current) {
              remoteVideoRef.current.srcObject = remoteStream;
            }
          });
          return;
        }
      } catch {
        /* retry */
      }
      if (left > 0) {
        setTimeout(() => tryDial(left - 1), 450);
      }
    };
    tryDial(25);
  }, [callSignal?.userPeerId, activeCall, callStatus]);

  useEffect(() => {
    if (!activeCall) return;
    let cancelled = false;
    const dialUserPeerId = activeCall.dialUserPeerId;

    (async () => {
      setCallError("");
      setCallStatus("Connecting…");
      try {
        await loadPeerScript();
        if (cancelled || !window.Peer) return;
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: true,
          video: activeCall.video,
        });
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        streamRef.current = stream;
        if (localVideoRef.current) {
          localVideoRef.current.srcObject = stream;
        }

        const peer = new window.Peer!(activeCall.connId, PEER_OPTS);
        peerRef.current = peer;

        peer.on("call", (...args: unknown[]) => {
          const call = args[0] as {
            answer: (s: MediaStream) => void;
            on: (e: string, cb: (s: MediaStream) => void) => void;
          };
          call.answer(stream);
          call.on("stream", (remoteStream: MediaStream) => {
            setCallStatus("Connected");
            if (remoteVideoRef.current) {
              remoteVideoRef.current.srcObject = remoteStream;
            }
          });
        });

        const dialWhenReady = (attemptsLeft: number) => {
          if (cancelled) return;
          if (!dialUserPeerId) {
            setCallStatus("Waiting for the user to tap Answer on their phone…");
            return;
          }
          setCallStatus(attemptsLeft > 12 ? "Joining…" : "Calling user…");
          try {
            const outgoing = peer.call(dialUserPeerId, stream);
            if (outgoing) {
              outgoing.on("stream", (...streamArgs: unknown[]) => {
                const remoteStream = streamArgs[0] as MediaStream;
                setCallStatus("Connected");
                if (remoteVideoRef.current) {
                  remoteVideoRef.current.srcObject = remoteStream;
                }
              });
              return;
            }
          } catch {
            /* retry */
          }
          if (attemptsLeft > 0) {
            setTimeout(() => dialWhenReady(attemptsLeft - 1), 400);
          } else {
            setCallError("Could not reach the user. Ask them to open the app.");
          }
        };

        peer.on("open", () => {
          if (cancelled) return;
          dialWhenReady(20);
        });

        peer.on("error", (...args: unknown[]) => {
          const err = args[0];
          setCallError(err instanceof Error ? err.message : String(err));
        });
      } catch (e) {
        setCallError(e instanceof Error ? e.message : "Call failed");
      }
    })();

    return () => {
      cancelled = true;
      teardownLocalCall();
    };
  }, [activeCall]);

  async function saveDisplayName() {
    const trimmed = nameDraft.trim();
    if (!trimmed || savingName) return;
    setSavingName(true);
    try {
      await setAdminDisplayName(trimmed);
    } catch (e) {
      alert(e instanceof Error ? e.message : "Could not save name");
    } finally {
      setSavingName(false);
    }
  }

  async function sendMessage() {
    const trimmed = text.trim();
    if (!trimmed || busy) return;
    setBusy(true);
    try {
      await sendAdminChatText(userId, trimmed);
      setText("");
    } catch (e) {
      alert(e instanceof Error ? e.message : "Send failed");
    } finally {
      setBusy(false);
    }
  }

  async function onPickFile(file: File | undefined) {
    if (!file || busy) return;
    setBusy(true);
    setUploadPct(0);
    try {
      await uploadAdminChatFile(userId, file, setUploadPct);
    } catch (e) {
      alert(e instanceof Error ? e.message : "Upload failed");
    } finally {
      setBusy(false);
      setUploadPct(null);
    }
  }

  async function startCall(video: boolean) {
    setCallError("");
    try {
      const dialUserPeerId =
        callSignal?.status === "requested" && callSignal?.from === "user"
          ? callSignal.userPeerId
          : undefined;
      callSignalSeenRef.current = false;
      userPeerDialedRef.current = false;
      const connId = await initiateAdminCall(userId, video);
      callSignalSeenRef.current = true;
      setActiveCall({ connId, video, dialUserPeerId });
    } catch (e) {
      alert(e instanceof Error ? e.message : "Could not start call");
    }
  }

  async function endCall() {
    teardownLocalCall();
    setActiveCall(null);
    setCallStatus("Call ended");
    setCallError("");
    await clearAdminCall(userId);
  }

  const userRequestedCall =
    callSignal?.status === "requested" && callSignal?.from === "user";

  const userLabel =
    userDisplayName && userDisplayName.trim() ? userDisplayName.trim() : "User";

  return (
    <div className="admin-chat stack">
      <div className="card admin-chat-identity row" style={{ flexWrap: "wrap", gap: 12 }}>
        <label className="stack" style={{ flex: "1 1 200px", minWidth: 0 }}>
          <span className="muted tiny">Your chat &amp; call name</span>
          <div className="row">
            <input
              className="input"
              value={nameDraft}
              disabled={savingName}
              onChange={(e) => setNameDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") void saveDisplayName();
              }}
            />
            <button
              type="button"
              className="btn secondary"
              disabled={savingName || !nameDraft.trim() || nameDraft.trim() === adminDisplayName}
              onClick={() => void saveDisplayName()}
            >
              Save
            </button>
          </div>
        </label>
        <div className="muted tiny" style={{ alignSelf: "flex-end" }}>
          Chatting with <strong>{userLabel}</strong>
        </div>
      </div>

      {userRequestedCall ? (
        <div className="card notice row" style={{ justifyContent: "space-between" }}>
          <span>
            {userLabel} requested a{" "}
            {callSignal?.callType === "audio" ? "audio" : "video"} call
          </span>
          <button
            type="button"
            className="btn ok"
            onClick={() => startCall(callSignal?.callType !== "audio")}
          >
            Answer
          </button>
        </div>
      ) : null}

      <div className="admin-chat-messages card" ref={listRef}>
        {messages.length === 0 ? (
          <p className="muted">No messages yet. Say hello to {userLabel}.</p>
        ) : (
          messages.map((m) => {
            const fromAdmin = m.sender === "admin";
            return (
              <div
                key={m.id}
                className={`admin-chat-bubble ${fromAdmin ? "out" : "in"}`}
              >
                <div className="admin-chat-meta">
                  <strong>{fromAdmin ? adminDisplayName : userLabel}</strong>
                  <span className="muted tiny">{formatTime(m.timestamp)}</span>
                </div>
                {m.text ? <p>{m.text}</p> : null}
                {m.fileUrl ? (
                  <p>
                    <a href={m.fileUrl} target="_blank" rel="noreferrer">
                      📎 {m.fileName || "Attachment"}
                    </a>
                    {m.mimeType?.startsWith("image/") ? (
                      <img
                        src={m.fileUrl}
                        alt=""
                        className="admin-chat-thumb"
                        loading="lazy"
                      />
                    ) : null}
                    {m.mimeType?.startsWith("video/") ? (
                      <video src={m.fileUrl} controls className="admin-chat-video" />
                    ) : null}
                  </p>
                ) : null}
              </div>
            );
          })
        )}
      </div>

      {uploadPct != null ? (
        <p className="muted tiny">Uploading… {uploadPct}%</p>
      ) : null}

      <div className="admin-chat-compose card row">
        <input
          ref={fileInputRef}
          type="file"
          hidden
          onChange={(e) => {
            void onPickFile(e.target.files?.[0]);
            e.target.value = "";
          }}
        />
        <button
          type="button"
          className="btn secondary btn-icon"
          title="Attach file"
          disabled={busy}
          onClick={() => fileInputRef.current?.click()}
        >
          📎
        </button>
        <button
          type="button"
          className="btn secondary btn-icon"
          title="Audio call"
          disabled={busy || Boolean(activeCall)}
          onClick={() => void startCall(false)}
        >
          <IconPhone size={16} />
        </button>
        <button
          type="button"
          className="btn secondary btn-icon"
          title="Video call"
          disabled={busy || Boolean(activeCall)}
          onClick={() => void startCall(true)}
        >
          <IconVideo size={16} />
        </button>
        <input
          className="input"
          placeholder={`Message ${userLabel}…`}
          value={text}
          disabled={busy}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              void sendMessage();
            }
          }}
        />
        <button
          type="button"
          className="btn ok"
          disabled={busy || !text.trim()}
          onClick={() => void sendMessage()}
        >
          Send
        </button>
      </div>

      {activeCall ? (
        <div className="admin-call-overlay">
          <div className="admin-call-panel card stack">
            <div className="row" style={{ justifyContent: "space-between" }}>
              <strong>
                {activeCall.video ? "Video call" : "Audio call"} · {userLabel}
              </strong>
              <button type="button" className="btn danger" onClick={() => void endCall()}>
                End
              </button>
            </div>
            {callError ? <p className="error-text">{callError}</p> : null}
            <div className="admin-call-videos">
              <video ref={remoteVideoRef} autoPlay playsInline className="admin-call-remote" />
              {activeCall.video ? (
                <video ref={localVideoRef} autoPlay muted playsInline className="admin-call-local" />
              ) : (
                <video ref={localVideoRef} autoPlay muted playsInline hidden />
              )}
            </div>
            <p className="muted tiny">{callStatus || "Setting up call…"}</p>
          </div>
        </div>
      ) : null}
    </div>
  );
}
