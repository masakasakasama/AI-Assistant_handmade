import { neon } from '@neondatabase/serverless';

// The stored function owns the transaction and locks both counters together.
export async function claimPostgres(connectionString, dayKey, minuteKey, daily, minute) {
  const sql = neon(connectionString);
  const rows = await sql.query('SELECT tatsu_home_claim($1, $2, $3, $4) AS result',
    [dayKey, minuteKey, daily, minute], {fetchOptions:{signal:AbortSignal.timeout(5000)}});
  return rows[0]?.result;
}
