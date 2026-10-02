import { requestAuthError } from "./request-auth.mjs";
import { readRequestBody, requestBodyError, AUDIO_BODY_LIMIT } from "./request-body.mjs";
import { DEFAULT_ANSWER_MODE } from "./answer-mode.mjs";
import { apiError } from "./openai.mjs";
import http from "node:http";
import { dispatch } from "./dispatch.mjs";
import { compareRouters } from "./router-compare.mjs";
import { dispatchJev } from "./dispatch-jev.mjs";
import { ROUTER_MODEL } from "./router.mjs";
import { REASONING_MODEL } from "./reasoner.mjs";
import { transcribe, transcriptionError } from "./transcribe.mjs";

const port = Number(process.env.PORT || 8787);

function json(res, status, payload) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store"
  });
  res.end(JSON.stringify(payload));
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === "GET" && ["/health", "/api/health"].includes(req.url)) {
      return json(res, 200, {
        ok: true,
        routerModel: ROUTER_MODEL,
        reasoningModel: REASONING_MODEL,
        answerMode: DEFAULT_ANSWER_MODE,
        openaiConfigured: Boolean(process.env.OPENAI_API_KEY),
        jevConfigured: Boolean(process.env.JEV_OPENROUTER_API_KEY)
      });
    }

    if (req.method === "POST" && ["/api/dispatch", "/api/dispatch-jev", "/api/router-compare", "/api/transcribe"].includes(req.url)) {
      const auth = requestAuthError(req);
      if (auth) return json(res, auth.status, auth.body);
    }

    if (req.method === "POST" && req.url === "/api/dispatch-jev") {
      const body = await readRequestBody(req);
      if (typeof body.text !== "string" || !body.text.trim()) {
        return json(res, 400, { error: "text is required" });
      }
      const result = await dispatchJev({
        text: body.text.trim(),
        context: typeof body.context === "string" ? body.context : "",
        modelProfile: typeof body.modelProfile === "string" ? body.modelProfile : "current",
      answerMode: body.answerMode
      });
      return json(res, 200, result);
    }

    if (req.method === "POST" && req.url === "/api/transcribe") {
      try {
        return json(res, 200, await transcribe(await readRequestBody(req, AUDIO_BODY_LIMIT)));
      } catch (error) {
        const failure = requestBodyError(error) || transcriptionError(error);
        return json(res, failure.status, failure.body);
      }
    }

    if (req.method === "POST" && req.url === "/api/router-compare") {
      const body = await readRequestBody(req);
      if (typeof body.text !== "string" || !body.text.trim()) {
        return json(res, 400, { error: "text is required" });
      }
      const result = await compareRouters({
        text: body.text.trim(),
        context: typeof body.context === "string" ? body.context : ""
      });
      return json(res, 200, result);
    }

    if (req.method === "POST" && req.url === "/api/dispatch") {
      const body = await readRequestBody(req);
      if (typeof body.text !== "string" || !body.text.trim()) {
        return json(res, 400, { error: "text is required" });
      }

      const result = await dispatch({
        text: body.text.trim(),
        context: typeof body.context === "string" ? body.context : "",
        modelProfile: typeof body.modelProfile === "string" ? body.modelProfile : "current",
      answerMode: body.answerMode
      });
      return json(res, 200, result);
    }

    return json(res, 404, { error: "not_found" });
  } catch (error) {
    console.error(error);
    const failure = requestBodyError(error) || apiError(error);
    return json(res, failure.status, failure.body);
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Tatsu Home AI backend listening on :${port}`);
});
