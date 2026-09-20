import http from "node:http";
import { dispatch } from "./dispatch.mjs";
import { compareRouters } from "./router-compare.mjs";
import { dispatchJev } from "./dispatch-jev.mjs";

const port = Number(process.env.PORT || 8787);

function json(res, status, payload) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store"
  });
  res.end(JSON.stringify(payload));
}

async function readJson(req) {
  let body = "";
  for await (const chunk of req) {
    body += chunk;
    if (body.length > 64 * 1024) {
      throw new Error("Request body too large");
    }
  }
  return body ? JSON.parse(body) : {};
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === "GET" && ["/health", "/api/health"].includes(req.url)) {
      return json(res, 200, {
        ok: true,
        routerModel: process.env.ROUTER_MODEL || "gpt-5.6-luna",
        reasoningModel: process.env.REASONING_MODEL || "gpt-5.6-sol",
        openaiConfigured: Boolean(process.env.OPENAI_API_KEY),
        jevConfigured: Boolean(process.env.JEV_OPENROUTER_API_KEY)
      });
    }

    if (req.method === "POST" && req.url === "/api/dispatch-jev") {
      const body = await readJson(req);
      if (typeof body.text !== "string" || !body.text.trim()) {
        return json(res, 400, { error: "text is required" });
      }
      const result = await dispatchJev({
        text: body.text.trim(),
        context: typeof body.context === "string" ? body.context : ""
      });
      return json(res, 200, result);
    }

    if (req.method === "POST" && req.url === "/api/router-compare") {
      const body = await readJson(req);
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
      const body = await readJson(req);
      if (typeof body.text !== "string" || !body.text.trim()) {
        return json(res, 400, { error: "text is required" });
      }

      const result = await dispatch({
        text: body.text.trim(),
        context: typeof body.context === "string" ? body.context : ""
      });
      return json(res, 200, result);
    }

    return json(res, 404, { error: "not_found" });
  } catch (error) {
    console.error(error);
    return json(res, 500, {
      error: "internal_error",
      message: error instanceof Error ? error.message : String(error)
    });
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Tatsu Home AI backend listening on :${port}`);
});
