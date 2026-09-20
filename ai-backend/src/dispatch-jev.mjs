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
  const routerMs = Math.round(performance.now() - started);
  const { usage, model: jevModel, probabilities, ...route } = routed;

  let answer = null;
  let answerMs = 0;
  const calls = [{ model: jevModel || JEV_MODEL, usage: usage ?? null }];

  if (route.route === "simple_chat") {
    const answerStarted = performance.now();
    const response = await (dependencies.answerSimple || answerSimple)({
      ...input,
      language: route.language
    });
    answerMs = Math.round(performance.now() - answerStarted);
    calls.push({ model: response.model, usage: response.usage ?? null });
    answer = { model: response.model, text: response.text };
  } else if (route.route === "deep_reasoning") {
    const answerStarted = performance.now();
    const response = await (dependencies.reason || reason)({
      ...input,
      language: route.language,
      effort: "high"
    });
    answerMs = Math.round(performance.now() - answerStarted);
    calls.push({ model: response.model, usage: response.usage ?? null });
    answer = { model: response.model, text: response.text };
  } else if (route.route === "clarify") {
    answer = { model: "local", text: clarification(route.language) };
  }

  return {
    requestId: randomUUID(),
    routerModel: jevModel || JEV_MODEL,
    reasoningModel: REASONING_MODEL,
    route,
    answer,
    calls,
    timings: { routerMs, answerMs },
    latencyMs: Math.round(performance.now() - started),
    routerProbabilities: probabilities ?? null
  };
}
