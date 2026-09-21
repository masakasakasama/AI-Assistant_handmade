import { performance } from "node:perf_hooks";
import { randomUUID } from "node:crypto";
import { routeIntentJev, JEV_MODEL } from "./jev.mjs";
import { answerSimple } from "./simple.mjs";
import { reason, REASONING_MODEL } from "./reasoner.mjs";

function clarification(language) {
  if (language === "de") return "Bitte sag genauer, was ich tun soll.";
  if (language === "en") return "Please be more specific about what you want me to do.";
  return "もう少し具体的に指示してください";
}

// Jev is the first decision layer. It never generates prose.
// simple_chat -> Luna, deep_reasoning -> Sol high.
// weather/device/alarm stay structured for Android to handle.
export async function dispatchJev(input, dependencies = {}) {
  const started = performance.now();
  const routed = await (dependencies.routeIntentJev || routeIntentJev)(input);
  const routingCompletedAt = performance.now();
  const routerMs = Math.round(routingCompletedAt - started);
  const { usage, model: jevModel, probabilities, ...route } = routed;

  let answer = null;
  let answerMs = 0;
  let answerGenerationMs = null;
  let answerTtftMs = null;
  let answerStartWaitMs = null;
  let answerStartedAt = null;
  let answerFirstTokenAt = null;
  let answerCompletedAt = null;
  const calls = [{ model: jevModel || JEV_MODEL, usage: usage ?? null }];

  if (route.route === "simple_chat") {
    const answerStarted = performance.now();
    answerStartedAt = answerStarted;
    answerStartWaitMs = Math.round(answerStarted - routingCompletedAt);
    const response = await (dependencies.answerSimple || answerSimple)({
      ...input,
      language: route.language
    });
    answerMs = Math.round(performance.now() - answerStarted);
    answerTtftMs = response.timings?.ttftMs ?? null;
    answerGenerationMs = response.timings?.generationMs ?? answerMs;
    answerFirstTokenAt = response.timings?.firstTokenAtMs ?? null;
    answerCompletedAt = response.timings?.completedAtMs ?? null;
    calls.push({ model: response.model, usage: response.usage ?? null });
    answer = { model: response.model, text: response.text };
  } else if (route.route === "deep_reasoning") {
    const answerStarted = performance.now();
    answerStartedAt = answerStarted;
    answerStartWaitMs = Math.round(answerStarted - routingCompletedAt);
    const response = await (dependencies.reason || reason)({
      ...input,
      language: route.language,
      effort: "high"
    });
    answerMs = Math.round(performance.now() - answerStarted);
    answerTtftMs = response.timings?.ttftMs ?? null;
    answerGenerationMs = response.timings?.generationMs ?? answerMs;
    answerFirstTokenAt = response.timings?.firstTokenAtMs ?? null;
    answerCompletedAt = response.timings?.completedAtMs ?? null;
    calls.push({ model: response.model, usage: response.usage ?? null });
    answer = { model: response.model, text: response.text };
  } else if (route.route === "clarify") {
    answer = { model: "local", text: clarification(route.language) };
  }

  const responseAssemblyStarted = performance.now();
  const responseAssemblyMs = Math.round(performance.now() - responseAssemblyStarted);
  const answerGenerationExclusiveMs = answerGenerationMs == null || answerTtftMs == null
    ? null : answerGenerationMs - answerTtftMs;
  const finishedAt = performance.now();
  const totalMs = Math.round(finishedAt - started);
  const exclusiveParts = [routerMs, answerStartWaitMs, answerTtftMs, answerGenerationExclusiveMs, responseAssemblyMs]
    .filter(value => value != null);
  const unaccountedMs = totalMs - exclusiveParts.reduce((sum, value) => sum + value, 0);
  const timingError = answerGenerationExclusiveMs != null && answerGenerationExclusiveMs < 0
    ? "answer generation interval is shorter than TTFT" : unaccountedMs < 0 ? "exclusive intervals exceed totalMs" : null;
  const result = {
    requestId: randomUUID(),
    routerModel: jevModel || JEV_MODEL,
    reasoningModel: REASONING_MODEL,
    route,
    rawIntent: {
      goal: route.goal ?? null,
      target: route.target ?? null,
      targetType: route.targetType ?? null,
      action: route.action ?? null,
      parameters: route.parameters ?? {},
      executionMode: route.executionMode ?? "none",
      confidence: route.confidence ?? null,
      routerProbabilities: probabilities ?? null
    },
    answer,
    calls,
    timings: {
      totalMs,
      routerMs,
      preRoutingWaitMs: null,
      answerStartWaitMs,
      answerTtftMs,
      answerGenerationMs: answerGenerationExclusiveMs,
      answerMs,
      responseAssemblyMs,
      afterRoutingMs: Math.round(finishedAt - routingCompletedAt),
      unaccountedMs,
      timingError,
      timestamps: {
        server_t2_routing_start: Math.round(started),
        server_t3_routing_complete: Math.round(routingCompletedAt),
        server_t4_answer_start: answerStartedAt == null ? null : Math.round(answerStartedAt),
        server_t8_first_token: answerFirstTokenAt,
        server_t9_answer_complete: answerCompletedAt,
        server_t10_response_assembly_start: Math.round(responseAssemblyStarted),
        server_t13_backend_complete: Math.round(finishedAt)
      }
    },
    latencyMs: Math.round(performance.now() - started),
    routerProbabilities: probabilities ?? null
  };
  const assembledAt = performance.now();
  const assembledTotalMs = Math.round(assembledAt - started);
  const measuredAssemblyMs = Math.round(assembledAt - responseAssemblyStarted);
  const exclusivePartsMeasured = [routerMs, answerStartWaitMs, answerTtftMs, answerGenerationExclusiveMs, measuredAssemblyMs]
    .filter(value => value != null);
  const measuredUnaccountedMs = assembledTotalMs - exclusivePartsMeasured.reduce((sum, value) => sum + value, 0);
  result.timings.totalMs = assembledTotalMs;
  result.timings.latencyMs = assembledTotalMs;
  result.timings.responseAssemblyMs = measuredAssemblyMs;
  result.timings.afterRoutingMs = Math.round(assembledAt - routingCompletedAt);
  result.timings.unaccountedMs = measuredUnaccountedMs;
  result.timings.timingError = answerGenerationExclusiveMs != null && answerGenerationExclusiveMs < 0
    ? "answer generation interval is shorter than TTFT" : measuredUnaccountedMs < 0 ? "exclusive intervals exceed totalMs" : null;
  result.timings.timestamps.server_t11_response_assembled = Math.round(assembledAt);
  result.timings.timestamps.server_t13_backend_complete = Math.round(assembledAt);
  result.latencyMs = assembledTotalMs;
  return result;
}
