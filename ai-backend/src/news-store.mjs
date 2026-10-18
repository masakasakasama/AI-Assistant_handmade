import { neon } from '@neondatabase/serverless';

const CREATE_TABLE = `
CREATE TABLE IF NOT EXISTS public.tatsu_home_news_batches (
  batch_id text PRIMARY KEY,
  captured_at timestamptz NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now(),
  headline_count integer NOT NULL,
  digest text NOT NULL,
  alerts jsonb NOT NULL
)`;

function fail(code, status = 400) {
  const error = new Error(code);
  error.code = code;
  error.status = status;
  return error;
}
export function validateNewsBatch(payload) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) throw fail('invalid_news_batch');
  const batchId = payload.batchId;
  const digest = payload.digest;
  const capturedAt = payload.capturedAt;
  const count = payload.headlineCount;
  if (typeof batchId !== 'string' || !/^[a-f0-9]{64}$/.test(batchId)) throw fail('invalid_news_batch_id');
  if (typeof digest !== 'string' || digest.length > 1000) throw fail('invalid_news_digest');
  if (typeof count !== 'number' || !Number.isSafeInteger(count) || count < 1 || count > 48) throw fail('invalid_news_count');
  if (typeof capturedAt !== 'string' || !/^\d{4}-\d{2}-\d{2}T/.test(capturedAt) ||
      !Number.isFinite(Date.parse(capturedAt))) throw fail('invalid_news_time');
  if (!Array.isArray(payload.alerts) || payload.alerts.length > 5) throw fail('invalid_news_alerts');
  const alerts = payload.alerts.map((alert) => {
    if (!alert || typeof alert !== 'object' || Array.isArray(alert)) throw fail('invalid_news_alert');
    const { score, source, summary, url } = alert;
    if (!Number.isSafeInteger(score) || score < 75 || score > 100) throw fail('invalid_news_score');
    if (typeof source !== 'string' || source.length > 80 ||
        !/^[a-z0-9-]+$/.test(source)) throw fail('invalid_news_source');
    if (typeof summary !== 'string' || summary.length < 1 || summary.length > 200) throw fail('invalid_news_summary');
    if (typeof url !== 'string' || url.length > 1800) throw fail('invalid_news_url');
    let parsed;
    try { parsed = new URL(url); } catch { throw fail('invalid_news_url'); }
    if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') throw fail('invalid_news_url');
    return { score, source, summary, url };
  });
  if (JSON.stringify(alerts).length > 12000) throw fail('news_payload_too_large');
  return { batchId, capturedAt, headlineCount: count, digest, alerts };
}

function sqlClient(env = process.env) {
  const raw = env.AI_LIMIT_DATABASE_URL;
  if (!raw) throw fail('news_storage_not_configured', 503);
  let url;
  try { url = new URL(raw); } catch { throw fail('news_storage_not_configured', 503); }
  if (!['postgres:', 'postgresql:'].includes(url.protocol) ||
      !url.hostname.endsWith('.neon.tech') || !url.username || !url.password) {
    throw fail('news_storage_not_configured', 503);
  }
  return neon(raw);
}

async function prepare(sql) {
  // Reuse Tatsu Home's already configured Neon database; no extra service/credential.
  // Idempotent initialization also works after serverless cold starts.
  await sql.query(CREATE_TABLE, [], { fetchOptions: { signal: AbortSignal.timeout(5000) } });
}

export async function publishNews(payload, env = process.env) {
  const batch = validateNewsBatch(payload);
  const sql = sqlClient(env);
  await prepare(sql);
  await sql.query(
    'INSERT INTO public.tatsu_home_news_batches (batch_id,captured_at,headline_count,digest,alerts) VALUES ($1,$2,$3,$4,$5::jsonb) ON CONFLICT (batch_id) DO NOTHING',
    [batch.batchId, batch.capturedAt, batch.headlineCount, batch.digest, JSON.stringify(batch.alerts)],
    { fetchOptions: { signal: AbortSignal.timeout(5000) } }
  );
  // Retain at most one month. Never expose raw feed or credentials.
  await sql.query(
    'DELETE FROM public.tatsu_home_news_batches WHERE received_at < now() - interval \'30 days\'',
    [], { fetchOptions: { signal: AbortSignal.timeout(5000) } }
  );
  return { accepted: true, batchId: batch.batchId };
}

export async function listNews(env = process.env) {
  const sql = sqlClient(env);
  await prepare(sql);
  const rows = await sql.query(
    'SELECT batch_id AS id, captured_at, headline_count, digest, alerts FROM public.tatsu_home_news_batches ORDER BY captured_at DESC, batch_id DESC LIMIT 20',
    [], { fetchOptions: { signal: AbortSignal.timeout(5000) } }
  );
  return { items: rows.map((r) => ({
    id: r.id, capturedAt: new Date(r.captured_at).toISOString(),
    headlineCount: r.headline_count, digest: r.digest, alerts: r.alerts
  })) };
}

export function newsApiError(error) {
  if (typeof error?.code === 'string' && error.code.startsWith('invalid_news') ||
      error?.code === 'news_payload_too_large') {
    return { status: error.status || 400, body: { error: error.code } };
  }
  if (error?.code === 'news_storage_not_configured') return { status: 503, body: { error: error.code } };
  return { status: 503, body: { error: 'news_storage_unavailable' } };
}
