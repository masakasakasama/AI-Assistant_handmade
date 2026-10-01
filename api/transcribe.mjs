import { transcribe, transcriptionError } from "../ai-backend/src/transcribe.mjs";

export default async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");
  if (req.method !== "POST") return res.status(405).json({ error: "method_not_allowed" });
  try {
    const body = typeof req.body === "string" ? JSON.parse(req.body) : (req.body || {});
    return res.status(200).json(await transcribe(body));
  } catch (error) {
    const failure = transcriptionError(error);
    return res.status(failure.status).json(failure.body);
  }
}
