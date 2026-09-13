import { dispatch } from "../ai-backend/src/dispatch.mjs";

export default async function handler(req, res) {
  if (req.method !== "POST") {
    res.status(405).json({ error: "method_not_allowed" });
    return;
  }

  try {
    const body = typeof req.body === "string" ? JSON.parse(req.body) : (req.body || {});
    if (typeof body.text !== "string" || !body.text.trim()) {
      res.status(400).json({ error: "text is required" });
      return;
    }

    const result = await dispatch({
      text: body.text.trim(),
      context: typeof body.context === "string" ? body.context : ""
    });
    res.setHeader("Cache-Control", "no-store");
    res.status(200).json(result);
  } catch (error) {
    console.error(error);
    res.status(500).json({
      error: "internal_error",
      message: error instanceof Error ? error.message : String(error)
    });
  }
}
