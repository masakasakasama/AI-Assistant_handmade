import test from "node:test";
import assert from "node:assert/strict";
import health from "../../api/health.mjs";
import { ROUTER_MODEL } from "../src/router.mjs";
import { DEFAULT_ANSWER_MODE } from "../src/answer-mode.mjs";
import { REASONING_MODEL } from "../src/reasoner.mjs";

test("Vercel health reports the same model configuration used by dispatch", async () => {
  const response = {
    statusCode: 0,
    headers: {},
    body: null,
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(body) { this.body = body; return this; }
  };

  await health({ method: "GET" }, response);

  assert.equal(response.statusCode, 200);
  assert.equal(response.body.routerModel, ROUTER_MODEL);
  assert.equal(response.body.reasoningModel, REASONING_MODEL);
  assert.equal(response.body.answerMode, DEFAULT_ANSWER_MODE);
});
