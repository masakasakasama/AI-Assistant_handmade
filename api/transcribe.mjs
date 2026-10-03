import { claimRequest } from "../ai-backend/src/request-limits.mjs";
import { logRequestFailure } from "../ai-backend/src/safe-logging.mjs";
import { requestAuthError } from "../ai-backend/src/request-auth.mjs";
import { parseRequestBody, requestBodyError, AUDIO_BODY_LIMIT } from "../ai-backend/src/request-body.mjs";
import { transcribe, transcriptionError, decodeAudio } from "../ai-backend/src/transcribe.mjs";

export default async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");
  if (req.method !== "POST") return res.status(405).json({ error: "method_not_allowed" });
  const auth = requestAuthError(req);
  if (auth) return res.status(auth.status).json(auth.body);

  try {
    const body = parseRequestBody(req.body, AUDIO_BODY_LIMIT);
    decodeAudio(body.audioBase64);
    await claimRequest();
    return res.status(200).json(await transcribe(body));
  } catch (error) {
    const failure = requestBodyError(error) || transcriptionError(error);
    return res.status(failure.status).json(failure.body);
  }
}
