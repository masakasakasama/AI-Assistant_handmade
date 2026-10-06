import { performance } from "node:perf_hooks";
import { randomUUID } from "node:crypto";
import { routeIntentOpenAIDecisions, OPENAI_DECISIONS_MODEL } from "./openai-decisions.mjs";
import { routeIntentJevClassification, JEV_MODEL } from "./jev.mjs";

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
      error: "provider_request_failed",
      latencyMs: Math.round(performance.now() - started)
    };
  }
}

export async function compareRouters(input, dependencies = {}) {
  const lunaFn = dependencies.routeIntentOpenAIDecisions || routeIntentOpenAIDecisions;
  const jevFn = dependencies.routeIntentJevClassification || routeIntentJevClassification;

  const [luna, jev] = await Promise.all([
    measured("openai_decisions", OPENAI_DECISIONS_MODEL, () => lunaFn(input)),
    measured("jev_decisions", JEV_MODEL, () => jevFn(input))
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
