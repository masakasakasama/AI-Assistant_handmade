import { routeIntent, ROUTER_MODEL } from "./router.mjs";
import { reason, REASONING_MODEL } from "./reasoner.mjs";
import { answerSimple } from "./simple.mjs";

export async function dispatch(input) {
  const started = Date.now();
  const route = await routeIntent(input);

  const result = {
    routerModel: ROUTER_MODEL,
    reasoningModel: REASONING_MODEL,
    route,
    answer: null,
    latencyMs: 0
  };

  if (route.route === "simple_chat") {
    result.answer = await answerSimple({
      ...input,
      language: route.language
    });
  }

  if (route.route === "deep_reasoning") {
    result.answer = await reason({
      ...input,
      language: route.language
    });
  }

  result.latencyMs = Date.now() - started;
  return result;
}
