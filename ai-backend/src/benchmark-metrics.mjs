export function percentile(values, fraction) {
  if (!values.length) return null;
  const sorted = [...values].sort((a,b) => a-b);
  return sorted[Math.max(0, Math.ceil(sorted.length * fraction) - 1)];
}
export function summarize(rows) {
  const successful = rows.filter(r => r.ok);
  const measuredRoutes = successful.filter(r => r.routeCorrect !== null && r.routeCorrect !== undefined);
  const measuredParameters = successful.filter(r => r.parametersCorrect !== null && r.parametersCorrect !== undefined);
  return {
    samples: rows.length, failures: rows.length - successful.length,
    p50Ms: percentile(successful.map(r => r.elapsedMs), .5),
    p95Ms: percentile(successful.map(r => r.elapsedMs), .95),
    failedElapsedMs: rows.filter(r => !r.ok).map(r => r.elapsedMs),
    routeAccuracy: measuredRoutes.length ? measuredRoutes.filter(r => r.routeCorrect).length / measuredRoutes.length : null,
    parameterAccuracy: measuredParameters.length ? measuredParameters.filter(r => r.parametersCorrect).length / measuredParameters.length : null,
    firstInProcessMs: rows.filter(r => r.firstInProcess).map(r => r.elapsedMs)
  };
}
