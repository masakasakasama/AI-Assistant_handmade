export const JEV_MODEL = process.env.JEV_MODEL || "jev-1.13.0";
export const JEV_ENDPOINT = process.env.JEV_ENDPOINT || "https://api.typesafe.ai/v1/systemone";

const ROUTE_CRITERIA = {
  device_action: "A direct request to control a home or SwitchBot device, including power, mode, or temperature.",
  alarm_action: "A request to create, update, delete, enable, disable, or inspect an alarm.",
  weather: "A direct request for current or forecast weather.",
  simple_chat: "Lightweight conversation or a short factual request that does not require substantial reasoning.",
  deep_reasoning: "A request needing nontrivial reasoning, planning, comparison, explanation, research-like synthesis, or a high-quality long answer.",
  clarify: "An unclear or ambiguous request where the assistant should ask a concise clarification before acting."
};

export function parseJevRouteResponse(payload) {
  const answer = payload?.answers?.route;
  if (!answer || answer.type !== "choice") throw new Error("Invalid Jev route response");
  if (!(answer.choice in ROUTE_CRITERIA)) throw new Error("Invalid Jev route choice");
  if (!Number.isFinite(answer.confidence)) throw new Error("Invalid Jev route confidence");
  return {
    model: typeof payload.model === "string" ? payload.model : JEV_MODEL,
    route: answer.choice,
    confidence: answer.confidence,
    probabilities: answer.probabilities && typeof answer.probabilities === "object"
      ? answer.probabilities
      : {},
    usage: payload.usage ?? null
  };
}

export async function routeIntentJev({ text }, dependencies = {}) {
  if (typeof text !== "string" || !text.trim()) throw new Error("text is required");
  const apiKey = dependencies.apiKey ?? process.env.TYPESAFE_API_KEY;
  if (!apiKey) throw new Error("TYPESAFE_API_KEY is not configured");

  const fetchImpl = dependencies.fetchImpl ?? globalThis.fetch;
  const response = await fetchImpl(JEV_ENDPOINT, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "Accept": "application/json"
    },
    body: JSON.stringify({
      model: JEV_MODEL,
      state: text.trim(),
      questions: {
        route: {
          type: "choice",
          instructions: "Choose the single best first routing destination for this personal smart-home voice assistant request.",
          criteria: ROUTE_CRITERIA
        }
      }
    })
  });

  if (!response.ok) throw new Error(`Jev HTTP ${response.status}`);
  return parseJevRouteResponse(await response.json());
}
