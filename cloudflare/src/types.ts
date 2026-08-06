export interface Env {
  DB: D1Database;
  GALLERY?: R2Bucket;
  JWT_SECRET: string;
  ADMIN_EMAIL: string;
  ADMIN_PASSWORD: string;
  GOOGLE_CLIENT_ID: string;
}

export type JwtRole = "user" | "admin";

export interface JwtPayload {
  sub: string;
  email: string;
  role: JwtRole;
  exp: number;
}

export interface AuthContext {
  userId: string;
  email: string;
  role: JwtRole;
}
