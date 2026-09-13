import test from "node:test";
import assert from "node:assert/strict";
import { percentile, summarize } from "../src/benchmark-metrics.mjs";
test("empty results are unknown, not zero latency", () => {
  assert.equal(percentile([], .95), null);
  assert.equal(summarize([]).p50Ms, null);
});
test("slow failures are retained separately and never masquerade as fast success", () => {
  const result = summarize([{ok:true,elapsedMs:100,routeCorrect:true}, {ok:true,elapsedMs:200,routeCorrect:false}, {ok:false,elapsedMs:45000}]);
  assert.equal(result.p95Ms, 200); assert.equal(result.failures, 1);
  assert.deepEqual(result.failedElapsedMs,[45000]); assert.equal(result.routeAccuracy,.5);
});
