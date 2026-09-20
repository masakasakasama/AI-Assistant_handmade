import { performance } from "node:perf_hooks";
import { randomUUID } from "node:crypto";
import { routeIntent, ROUTER_MODEL } from "./router.mjs";
import { routeIntentJev, JEV_MODEL } from "./jev.mjs";

async function measured(name, model, fn) {
  const started = performance.now();
  try {
    const result = await fn();
    const { _usage, usage, model: returnedModel, ...decision } = result;
    return {
      ok: true,
      provider: name,
      model: returnedModel || model,
      ...decision,
      usage: usage ?? _usage ?? null,
      latencyMs: Math.round(performance.now() - started)
    };
  } catch (error) {
    return {
      ok: false,
      provider: name,
      model,
      error: error instanceof Error ? error.message : String(error),
      latencyMs: Math.round(performance.now() - started)
    };
  }
}

export async function compareRouters(input, dependencies = {}) {
  const lunaFn = dependencies.routeIntent || routeIntent;
  const jevFn = dependencies.routeIntentJev || routeIntentJev;

  const [luna, jev] = await Promise.all([
    measured("luna", ROUTER_MODEL, () => lunaFn({ ...input, benchmarkClassificationOnly: true })),
    measured("jev", JEV_MODEL, () => jevFn(input))
  ]);

  const deltaMs = luna.ok && jev.ok ? luna.latencyMs - jev.latencyMs : null;
  return {
    requestId: randomUUID(),
    luna,
    jev,
    deltaMs,
    faster: deltaMs == null ? null : deltaMs > 0 ? "jev" : deltaMs < 0 ? "luna" : "tie"
  };
}
