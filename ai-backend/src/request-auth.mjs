import { createHash, timingSafeEqual } from "node:crypto";

export function requestAuthError(req) {
  const expected = process.env.AI_BACKEND_TOKEN?.trim();
  if (!expected) return { status: 503, body: { error: "backend_auth_not_configured" } };
  const header = req.headers?.authorization;
  const match = typeof header === "string" ? /^Bearer ([^\s]+)$/i.exec(header) : null;
  const digest = value => createHash("sha256").update(value).digest();
  if (!match || !timingSafeEqual(digest(match[1]), digest(expected))) {
    return { status: 401, body: { error: "unauthorized" } };
  }
  return null;
}
