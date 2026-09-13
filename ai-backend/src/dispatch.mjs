import { performance } from "node:perf_hooks";
import { randomUUID } from "node:crypto";
import { routeIntent, ROUTER_MODEL } from "./router.mjs";
import { reason, REASONING_MODEL } from "./reasoner.mjs";

// Tests inject dependencies; this endpoint never executes physical actions.
export async function dispatch(input, dependencies = {}) {
  const started = performance.now();
  const routed = await (dependencies.routeIntent || routeIntent)(input);
  const routerMs = Math.round(performance.now() - started);
  const { _usage, ...route } = routed;
  let answer = null;
  let answerMs = 0;
  const calls = [{ model: ROUTER_MODEL, usage: _usage ?? null }];
  if (["simple_chat", "clarify"].includes(route.route)) {
    if (!route.replyText?.trim()) throw new Error("Missing router reply");
    answer = { model: ROUTER_MODEL, text: route.replyText };
  } else if (route.route === "deep_reasoning") {
    const answerStarted = performance.now();
    const response = await (dependencies.reason || reason)({ ...input, language: route.language });
    answerMs = Math.round(performance.now() - answerStarted);
    calls.push({ model: response.model, usage: response.usage ?? null });
    answer = { model: response.model, text: response.text };
  }
  return { requestId: randomUUID(), routerModel: ROUTER_MODEL, reasoningModel: REASONING_MODEL,
    route, answer, calls, timings: { routerMs, answerMs },
    latencyMs: Math.round(performance.now() - started) };
}
