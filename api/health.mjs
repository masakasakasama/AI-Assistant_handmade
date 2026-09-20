export default async function handler(req, res) {
  if (req.method !== "GET") {
    res.status(405).json({ error: "method_not_allowed" });
    return;
  }

  res.setHeader("Cache-Control", "no-store");
  res.status(200).json({
    ok: true,
    routerModel: process.env.ROUTER_MODEL || "gpt-5.6-luna",
    reasoningModel: process.env.REASONING_MODEL || "gpt-5.6-sol",
    openaiConfigured: Boolean(process.env.OPENAI_API_KEY),
    jevConfigured: Boolean(process.env.JEV_OPENROUTER_API_KEY)
  });
}
