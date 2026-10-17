import test from "node:test";
import assert from "node:assert/strict";
import { answerDeepSeek, privateForDeepSeek } from "../src/deepseek.mjs";
import { dispatchJev } from "../src/dispatch-jev.mjs";
import { dispatch } from "../src/dispatch.mjs";
import { resolveModelProfile } from "../src/model-profiles.mjs";

test("DeepSeek profile is opt-in", () => {
  assert.equal(resolveModelProfile("deepseek").simpleModel, "deepseek-flash");
  assert.equal(resolveModelProfile("current").simpleModel, undefined);
});

test("sensitive text is denied", async () => {
  assert.equal(privateForDeepSeek("玄関の鍵を開けて"), true);
  assert.equal(privateForDeepSeek("capital of France"), false);
  await assert.rejects(answerDeepSeek({ text: "エアコンを消して", key: "fake",
    fetchImpl: () => { throw Error("should not be called"); }
  }), { code: "deepseek_sensitive_input" });
});

test("only explicit public question goes to DeepSeek", async () => {
  let body;
  const answer = await answerDeepSeek({ text: "capital of France", language: "en", effort: "none",
    key: "fake", fetchImpl: async (url, opts) => {
      assert.equal(url, "https://api.deepseek.com/chat/completions");
      assert.equal(opts.redirect, "error");
      body = JSON.parse(opts.body);
      return { ok: true, json: async () => ({ choices: [{ message: { content: "Paris" } }] }) };
    }
  });
  assert.equal(body.model, "deepseek-flash");
  assert.deepEqual(body.messages.map(x => x.role), ["system", "user"]);
  assert.equal(body.messages[1].content, "capital of France");
  assert.equal(body.tools, undefined);
  assert.equal(answer.text, "Paris");
});

test("Jev physical route never calls a chat model", async () => {
  const result = await dispatchJev({ text: "エアコンを消して", modelProfile: "deepseek" }, {
    routeIntentJev: async () => ({
      model: "jev-test", route: "device_action", language: "ja", action: "turn_off",
      target: "エアコン", confidence: 0.99
    }),
    answerSimple: async () => { throw Error("unexpected"); },
    reason: async () => { throw Error("unexpected"); }
  });
  assert.equal(result.answer, null);
});

test("both routing modes use selected DeepSeek answer model", async () => {
  const mock = async input => {
    assert.equal(input.model, "deepseek-flash");
    return { model: input.model, text: "result" };
  };
  const jev = await dispatchJev({ text: "capital of France", modelProfile: "deepseek" }, {
    routeIntentJev: async () => ({ model: "jev-test", route: "simple_chat", language: "en" }),
    answerSimple: mock
  });
  const luna = await dispatch({ text: "capital of France", modelProfile: "deepseek" }, {
    routeIntent: async () => ({ route: "simple_chat", language: "en", replyText: "unused" }),
    answerSimple: mock
  });
  assert.equal(jev.answer.model, "deepseek-flash");
  assert.equal(luna.answer.model, "deepseek-flash");
});
