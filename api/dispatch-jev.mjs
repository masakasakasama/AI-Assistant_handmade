import { requestAuthError } from "../ai-backend/src/request-auth.mjs";
import { parseRequestBody, requestBodyError, AUDIO_BODY_LIMIT } from "../ai-backend/src/request-body.mjs";
import { apiError } from "../ai-backend/src/openai.mjs";
import { dispatchJev } from "../ai-backend/src/dispatch-jev.mjs";

export default async function handler(req, res) {
  if (req.method !== "POST") {
    res.status(405).json({ error: "method_not_allowed" });
    return;
  }

  const auth = requestAuthError(req);
  if (auth) return res.status(auth.status).json(auth.body);

  try {
    const body = parseRequestBody(req.body);
    if (typeof body.text !== "string" || !body.text.trim()) {
      res.status(400).json({ error: "text is required" });
      return;
    }

    const result = await dispatchJev({
      text: body.text.trim(),
      context: typeof body.context === "string" ? body.context : "",
      modelProfile: typeof body.modelProfile === "string" ? body.modelProfile : "current",
      answerMode: body.answerMode
    });
    res.setHeader("Cache-Control", "no-store");
    res.status(200).json(result);
  } catch (error) {
    console.error(error);
    const failure = requestBodyError(error) || apiError(error);
    res.status(failure.status).json(failure.body);
  }
}
