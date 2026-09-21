import { performance } from "node:perf_hooks";
import { randomUUID } from "node:crypto";
import { routeIntent, ROUTER_MODEL } from "./router.mjs";
import { reason, REASONING_MODEL } from "./reasoner.mjs";

// Tests inject dependencies; this endpoint never executes physical actions.
export async function dispatch(input, dependencies = {}) {
  const started = performance.now();
  const routed = await (dependencies.routeIntent || routeIntent)(input);
  const routingCompletedAt = performance.now();
  const routerMs = Math.round(routingCompletedAt - started);
  const { _usage, _timings, ...route } = routed;
  let answer = null;
  let answerMs = 0;
  let answerGenerationMs = null;
  let answerTtftMs = null;
  let answerStartWaitMs = null;
  let answerStartedAt = null;
  let answerFirstTokenAt = null;
  let answerCompletedAt = null;
  const calls = [{ model: ROUTER_MODEL, usage: _usage ?? null }];
  if (["simple_chat", "clarify"].includes(route.route)) {
    if (!route.replyText?.trim()) throw new Error("Missing router reply");
    answer = { model: ROUTER_MODEL, text: route.replyText };
  } else if (route.route === "deep_reasoning") {
    const answerStarted = performance.now();
    answerStartedAt = answerStarted;
    answerStartWaitMs = Math.round(answerStarted - routingCompletedAt);
    const response = await (dependencies.reason || reason)({ ...input, language: route.language });
    answerMs = Math.round(performance.now() - answerStarted);
    answerTtftMs = response.timings?.ttftMs ?? null;
    answerGenerationMs = response.timings?.generationMs ?? answerMs;
    answerFirstTokenAt = response.timings?.firstTokenAtMs ?? null;
    answerCompletedAt = response.timings?.completedAtMs ?? null;
    calls.push({ model: response.model, usage: response.usage ?? null });
    answer = { model: response.model, text: response.text };
  }
  if (["simple_chat", "clarify"].includes(route.route)) {
    answerTtftMs = _timings?.ttftMs ?? null;
    answerFirstTokenAt = _timings?.firstTokenAtMs ?? null;
    answerCompletedAt = _timings?.completedAtMs ?? null;
  }
  const responseAssemblyStarted = performance.now();
  const answerGenerationExclusiveMs = answerGenerationMs == null || answerTtftMs == null
    ? null : answerGenerationMs - answerTtftMs;
  const result = { requestId: randomUUID(), routerModel: ROUTER_MODEL, reasoningModel: REASONING_MODEL,
    route, answer, calls, timings: {}, latencyMs: null };
  const finishedAt = performance.now();
  const responseAssemblyMs = Math.round(finishedAt - responseAssemblyStarted);
  const totalMs = Math.round(finishedAt - started);
  const exclusiveParts = [routerMs, answerStartWaitMs, answerGenerationExclusiveMs, responseAssemblyMs].filter(value => value != null);
  const unaccountedMs = totalMs - exclusiveParts.reduce((sum, value) => sum + value, 0);
  result.timings = {
    routerMs, preRoutingWaitMs: null, answerStartWaitMs, answerTtftMs,
    answerGenerationMs: answerGenerationExclusiveMs, answerMs, responseAssemblyMs,
    afterRoutingMs: Math.round(finishedAt - routingCompletedAt), unaccountedMs,
    timingError: answerGenerationExclusiveMs != null && answerGenerationExclusiveMs < 0
      ? "answer generation interval is shorter than TTFT" : unaccountedMs < 0 ? "exclusive intervals exceed totalMs" : null,
    timestamps: {
      server_t2_routing_start: Math.round(started), server_t3_routing_complete: Math.round(routingCompletedAt),
      server_t4_answer_start: answerStartedAt == null ? null : Math.round(answerStartedAt),
      server_t8_first_token: answerFirstTokenAt, server_t9_answer_complete: answerCompletedAt,
      server_t10_response_assembly_start: Math.round(responseAssemblyStarted),
      server_t11_response_assembled: Math.round(finishedAt), server_t13_backend_complete: Math.round(finishedAt)
    }, totalMs
  };
  result.latencyMs = totalMs;
  return result;
}
