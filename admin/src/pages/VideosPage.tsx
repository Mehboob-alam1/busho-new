import { FormEvent, useEffect, useRef, useState } from "react";
import AdminShell from "../components/AdminShell";
import {
  IconExternal,
  IconTrash,
  IconUpload,
  IconVideo,
} from "../components/Icons";
import { IllustEmptyVideos, IllustUpload } from "../components/Illustrations";
import {
  deleteVideo,
  formatTime,
  uploadVideo,
  VideoItem,
  watchVideos,
} from "../api";

export default function VideosPage() {
  const [videos, setVideos] = useState<VideoItem[]>([]);
  const [error, setError] = useState("");
  const [live, setLive] = useState(false);
  const [loading, setLoading] = useState(true);
  const [title, setTitle] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [progress, setProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [busyId, setBusyId] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const unsub = watchVideos(
      (list) => {
        setVideos(list);
        setLoading(false);
        setLive(true);
        setError("");
      },
      (err) => {
        setError(err.message || "Failed to load videos");
        setLoading(false);
        setLive(false);
      }
    );
    return unsub;
  }, []);

  async function onUpload(e: FormEvent) {
    e.preventDefault();
    if (!file) {
      setError("Choose a video file first.");
      return;
    }
    setUploading(true);
    setProgress(0);
    setError("");
    try {
      await uploadVideo({
        file,
        title,
        onProgress: setProgress,
      });
      setTitle("");
      setFile(null);
      setProgress(0);
      if (inputRef.current) inputRef.current.value = "";
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload failed");
    } finally {
      setUploading(false);
    }
  }

  async function onDelete(video: VideoItem) {
    if (!confirm(`Delete “${video.title}”?`)) return;
    setBusyId(video.id);
    setError("");
    try {
      await deleteVideo(video);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed");
    } finally {
      setBusyId("");
    }
  }

  return (
    <AdminShell
      title="Video library"
      subtitle="Publish clips to Firebase — they appear in the app Videos section."
      actions={
        <span className={`live-pill ${live ? "on" : ""}`}>
          <span className="live-dot" />
          {live ? "Live updates" : "Connecting…"}
        </span>
      }
    >
      <form className="card stack upload-card" onSubmit={onUpload}>
        <div className="upload-head">
          <IllustUpload className="illust upload-illust" />
          <div>
            <h2 style={{ margin: 0, fontSize: 22 }}>Upload a video</h2>
            <p className="muted" style={{ margin: "6px 0 0", lineHeight: 1.5 }}>
              Add a title, choose a file, and publish. Devices sync the new video automatically.
            </p>
          </div>
        </div>
        <label className="stack">
          <span className="field-label">Title</span>
          <input
            className="input"
            placeholder="Video title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </label>
        <label className="stack">
          <span className="field-label">Video file</span>
          <input
            ref={inputRef}
            className="input"
            type="file"
            accept="video/*"
            onChange={(e) => setFile(e.target.files?.[0] || null)}
          />
        </label>
        {file ? (
          <p className="muted" style={{ margin: 0 }}>
            Selected: <strong>{file.name}</strong> ({Math.round(file.size / 1024 / 1024)} MB)
          </p>
        ) : null}
        {uploading ? (
          <div className="progress-wrap">
            <div className="progress-bar" style={{ width: `${progress}%` }} />
            <span className="progress-label">{progress}%</span>
          </div>
        ) : null}
        <button className="btn btn-icon" type="submit" disabled={uploading || !file}>
          <IconUpload size={16} />
          {uploading ? "Uploading…" : "Upload to app"}
        </button>
      </form>

      {error ? <div className="error-text">{error}</div> : null}

      <div className="card stack">
        <div className="row" style={{ justifyContent: "space-between" }}>
          <h2 className="display row" style={{ margin: 0, fontSize: 22, gap: 8 }}>
            <IconVideo size={22} />
            Published videos
          </h2>
          <span className="muted">{videos.length} total</span>
        </div>

        {loading && videos.length === 0 ? (
          <div className="skeleton-stack">
            <div className="skeleton-line" />
            <div className="skeleton-line short" />
          </div>
        ) : null}

        {!loading && videos.length === 0 ? (
          <div className="empty-block">
            <IllustEmptyVideos className="illust" />
            <p className="empty-state">No videos yet. Upload one above to populate the app.</p>
          </div>
        ) : null}

        <div className="video-admin-grid">
          {videos.map((v) => (
            <article key={v.id} className="video-admin-card">
              <div className="video-admin-preview">
                {v.thumbnailUrl ? (
                  <img src={v.thumbnailUrl} alt={v.title} />
                ) : (
                  <video src={v.videoUrl || v.url} muted preload="metadata" />
                )}
              </div>
              <div className="stack" style={{ gap: 6 }}>
                <strong>{v.title}</strong>
                <span className="muted tiny">
                  {formatTime(v.uploadedAt || v.timestamp)} · {v.uploadedBy || "admin"}
                </span>
                <div className="row">
                  <a
                    className="btn secondary btn-icon"
                    href={v.videoUrl || v.url}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <IconExternal size={15} />
                    Open
                  </a>
                  <button
                    className="btn danger btn-icon"
                    disabled={busyId === v.id}
                    onClick={() => onDelete(v)}
                  >
                    <IconTrash size={15} />
                    {busyId === v.id ? "Deleting…" : "Delete"}
                  </button>
                </div>
              </div>
            </article>
          ))}
        </div>
      </div>
    </AdminShell>
  );
}
