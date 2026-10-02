export const TEXT_BODY_LIMIT = 64 * 1024;
export const AUDIO_BODY_LIMIT = 1_300_000;

function invalid(status, code) {
  const error = new Error(code);
  error.status = status;
  error.code = code;
  return error;
}

export function parseRequestBody(body, limit = TEXT_BODY_LIMIT) {
  const raw = Buffer.isBuffer(body) ? body.toString("utf8") :
    typeof body === "string" ? body : JSON.stringify(body ?? {});
  if (Buffer.byteLength(raw, "utf8") > limit) throw invalid(413, "request_body_too_large");
  let value;
  try { value = JSON.parse(raw || "{}"); }
  catch { throw invalid(400, "invalid_json"); }
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw invalid(400, "json_object_required");
  }
  return value;
}

export async function readRequestBody(req, limit = TEXT_BODY_LIMIT) {
  const chunks = [];
  let bytes = 0;
  for await (const chunk of req) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    bytes += buffer.length;
    if (bytes > limit) throw invalid(413, "request_body_too_large");
    chunks.push(buffer);
  }
  return parseRequestBody(Buffer.concat(chunks), limit);
}

export function requestBodyError(error) {
  return ["request_body_too_large", "invalid_json", "json_object_required"].includes(error?.code)
    ? { status: error.status, body: { error: error.code } } : null;
}
