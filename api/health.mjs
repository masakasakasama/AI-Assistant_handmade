import { ROUTER_MODEL } from "../ai-backend/src/router.mjs";
import { REASONING_MODEL } from "../ai-backend/src/reasoner.mjs";

export default async function handler(req, res) {
  if (req.method !== "GET") {
    res.status(405).json({ error: "method_not_allowed" });
    return;
  }

  res.setHeader("Cache-Control", "no-store");
  res.status(200).json({
    ok: true,
    routerModel: ROUTER_MODEL,
    reasoningModel: REASONING_MODEL,
    openaiConfigured: Boolean(process.env.OPENAI_API_KEY),
    jevConfigured: Boolean(process.env.JEV_OPENROUTER_API_KEY)
  });
}
