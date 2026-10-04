import {explicitCommands,alarmDate} from './command-context.mjs';
import { deviceClarification,validateDeviceRoute } from './device-command.mjs';
import { resolveAnswerMode } from "./answer-mode.mjs";
import { performance } from "node:perf_hooks";
import { randomUUID } from "node:crypto";
import { routeIntentJev, JEV_MODEL } from "./jev.mjs";
import { answerSimple } from "./simple.mjs";
import { reason } from "./reasoner.mjs";
import { resolveModelProfile } from "./model-profiles.mjs";

// Jev is the first decision layer. It never generates prose.
// simple_chat -> Luna, deep_reasoning -> Sol high.
// weather/device/alarm stay structured for Android to handle.
export async function dispatchJev(input, dependencies = {}) {
  const answerMode = resolveAnswerMode(input.answerMode);
  const modelProfile = resolveModelProfile(input.modelProfile);
  if (!modelProfile) throw new Error("Unsupported modelProfile");
  const now = dependencies.now || (() => performance.now());
  const started = now();
  const extracted = explicitCommands(input) || await (dependencies.routeIntentJev || routeIntentJev)(input);
  const routed = alarmDate(validateDeviceRoute(extracted,input),input);
  const routingCompletedAt = now();
  const routerMs = Math.round(routingCompletedAt) - Math.round(started);
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
    const answerStarted = now();
    answerStartedAt = answerStarted;
    answerStartWaitMs = Math.round(answerStarted) - Math.round(routingCompletedAt);
    const response = await (dependencies.answerSimple || answerSimple)({
      ...input,
      language: route.language,
      model: modelProfile.routerModel
    });
    answerMs = Math.round(now()) - Math.round(answerStarted);
    answerTtftMs = response.timings?.ttftMs ?? null;
    answerGenerationMs = response.timings?.generationMs ?? answerMs;
    answerFirstTokenAt = response.timings?.firstTokenAtMs ?? null;
    answerCompletedAt = response.timings?.completedAtMs ?? null;
    calls.push({ model: response.model, usage: response.usage ?? null });
    answer = { model: response.model, text: response.text, effort: route.route === "deep_reasoning" ? answerMode.effort : "none" };
  } else if (route.route === "deep_reasoning") {
    const answerStarted = now();
    answerStartedAt = answerStarted;
    answerStartWaitMs = Math.round(answerStarted) - Math.round(routingCompletedAt);
    const response = await (dependencies.reason || reason)({
      ...input,
      language: route.language,
      effort: answerMode.effort,
      concise: answerMode.concise,
      model: modelProfile.reasoningModel
    });
    answerMs = Math.round(now()) - Math.round(answerStarted);
    answerTtftMs = response.timings?.ttftMs ?? null;
    answerGenerationMs = response.timings?.generationMs ?? answerMs;
    answerFirstTokenAt = response.timings?.firstTokenAtMs ?? null;
    answerCompletedAt = response.timings?.completedAtMs ?? null;
    calls.push({ model: response.model, usage: response.usage ?? null });
    answer = { model: response.model, text: response.text, effort: route.route === "deep_reasoning" ? answerMode.effort : "none" };
  } else if (route.route === "clarify") {
    answer = { model: "local", text: route.replyText || deviceClarification(route,input.text) };
  }

  const responseAssemblyStarted = now();
  const responseAssemblyMs = Math.round(now()) - Math.round(responseAssemblyStarted);
  const answerGenerationExclusiveMs = answerGenerationMs == null || answerTtftMs == null
    ? null : answerGenerationMs - answerTtftMs;
  const finishedAt = now();
  const totalMs = Math.round(finishedAt) - Math.round(started);
  const exclusiveParts = [routerMs, answerStartWaitMs, answerTtftMs, answerGenerationExclusiveMs, responseAssemblyMs]
    .filter(value => value != null);
  const unaccountedMs = totalMs - exclusiveParts.reduce((sum, value) => sum + value, 0);
  const timingError = answerGenerationExclusiveMs != null && answerGenerationExclusiveMs < 0
    ? "answer generation interval is shorter than TTFT" : unaccountedMs < 0 ? "exclusive intervals exceed totalMs" : null;
  const result = {
    requestId: randomUUID(),
    routerModel: jevModel || JEV_MODEL,
    reasoningModel: modelProfile.reasoningModel,
    route,
    rawIntent: {
      goal: route.goal ?? null,
      target: route.target ?? null,
      targetType: route.targetType ?? null,
      action: route.action ?? null,
      parameters: route.parameters ?? {},
      executionMode: route.executionMode ?? "none",
      confidence: route.confidence ?? null,
      evidence: route.evidence ?? null,
      classificationConfidence: route.classificationConfidence ?? null,
      routerProbabilities: probabilities ?? null
    },
    answer,
    answerMode: answerMode.name,
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
      afterRoutingMs: Math.round(finishedAt) - Math.round(routingCompletedAt),
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
    latencyMs: Math.round(now()) - Math.round(started),
    routerProbabilities: probabilities ?? null
  };
  const assembledAt = now();
  const assembledTotalMs = Math.round(assembledAt) - Math.round(started);
  const measuredAssemblyMs = Math.round(assembledAt) - Math.round(responseAssemblyStarted);
  const exclusivePartsMeasured = [routerMs, answerStartWaitMs, answerTtftMs, answerGenerationExclusiveMs, measuredAssemblyMs]
    .filter(value => value != null);
  const measuredUnaccountedMs = assembledTotalMs - exclusivePartsMeasured.reduce((sum, value) => sum + value, 0);
  result.timings.totalMs = assembledTotalMs;
  result.timings.latencyMs = assembledTotalMs;
  result.timings.responseAssemblyMs = measuredAssemblyMs;
  result.timings.afterRoutingMs = Math.round(assembledAt) - Math.round(routingCompletedAt);
  result.timings.unaccountedMs = measuredUnaccountedMs;
  result.timings.timingError = answerGenerationExclusiveMs != null && answerGenerationExclusiveMs < 0
    ? "answer generation interval is shorter than TTFT" : measuredUnaccountedMs < 0 ? "exclusive intervals exceed totalMs" : null;
  result.timings.timestamps.server_t11_response_assembled = Math.round(assembledAt);
  result.timings.timestamps.server_t13_backend_complete = Math.round(assembledAt);
  result.latencyMs = assembledTotalMs;
  return result;
}
