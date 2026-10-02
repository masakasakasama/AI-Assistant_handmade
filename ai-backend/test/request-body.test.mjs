import test from "node:test";
import assert from "node:assert/strict";
import { Readable } from "node:stream";
import { parseRequestBody, readRequestBody, TEXT_BODY_LIMIT } from "../src/request-body.mjs";
import dispatch from "../../api/dispatch.mjs";
import jev from "../../api/dispatch-jev.mjs";
import compare from "../../api/router-compare.mjs";
import transcribe from "../../api/transcribe.mjs";

test("JSON input rejects malformed and non-object payloads", () => {
  for (const body of ["{", "null", "[]", "1", '"hello"']) {
    assert.throws(() => parseRequestBody(body), error => error.status === 400);
  }
  assert.deepEqual(parseRequestBody(undefined), {});
});

test("UTF-8 body limits count bytes and preserve characters split across chunks", async () => {
  const body = Buffer.from(JSON.stringify({ text: "日本語" }));
  const stream = Readable.from([body.subarray(0, 11), body.subarray(11)]);
  assert.deepEqual(await readRequestBody(stream), { text: "日本語" });
  assert.throws(() => parseRequestBody({ text: "あ".repeat(TEXT_BODY_LIMIT / 2) }),
    error => error.status === 413);
  await assert.rejects(readRequestBody(Readable.from([Buffer.alloc(5), Buffer.alloc(5)]), 9),
    error => error.status === 413);
});

for (const [name, handler] of Object.entries({ dispatch, jev, compare, transcribe })) {
  test(`${name} rejects invalid HTTP input before a provider request`, async () => {
    const previousFetch = globalThis.fetch;
    const previousToken = process.env.AI_BACKEND_TOKEN;
    const previousLog = console.error;
    globalThis.fetch = () => { assert.fail("invalid input reached provider"); };
    console.error = () => {};
    try {
      for (const [body, expected] of [["{", 400], ["null", 400], ["[]", 400],
        [JSON.stringify({ text: "あ".repeat(500_000) }), 413]]) {
        const res = { setHeader() {}, status(code) { this.code = code; return this; },
          json(value) { this.body = value; return this; } };
        process.env.AI_BACKEND_TOKEN = "input-fixture-token";
        await handler({ method: "POST", headers: { authorization: "Bearer input-fixture-token" }, body }, res);
        assert.equal(res.code, expected);
      }
    } finally {
      if (previousToken === undefined) delete process.env.AI_BACKEND_TOKEN;
      else process.env.AI_BACKEND_TOKEN = previousToken;
      globalThis.fetch = previousFetch;
      console.error = previousLog;
    }
  });
}
