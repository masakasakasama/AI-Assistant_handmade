import test from "node:test";
import assert from "node:assert/strict";
import { compareRouters } from "../src/router-compare.mjs";
import { parseJevRouteResponse } from "../src/jev.mjs";

test("parses a Jev Choice route", () => {
  const parsed = parseJevRouteResponse({
    model: "jev-1.13.0",
    answers: {
      route: {
        type: "choice",
        choice: "weather",
        confidence: 0.91,
        probabilities: { weather: 0.91, simple_chat: 0.09 }
      }
    },
    usage: { input_tokens: 42, output_tokens: 0 }
  });
  assert.equal(parsed.route, "weather");
  assert.equal(parsed.confidence, 0.91);
});

test("router comparison keeps Luna and Jev independent", async () => {
  const result = await compareRouters({ text: "明日の天気は？" }, {
    routeIntent: async input => {
      assert.equal(input.benchmarkClassificationOnly, true);
      return { route: "weather", confidence: 0.95, _usage: { input_tokens: 10 } };
    },
    routeIntentJev: async () => ({
      model: "jev-test",
      route: "weather",
      confidence: 0.9,
      probabilities: { weather: 0.9 },
      usage: { input_tokens: 4, output_tokens: 0 }
    })
  });
  assert.equal(result.luna.ok, true);
  assert.equal(result.jev.ok, true);
  assert.equal(result.luna.route, "weather");
  assert.equal(result.jev.route, "weather");
});

test("router comparison reports one provider failure without hiding the other", async () => {
  const result = await compareRouters({ text: "hello" }, {
    routeIntent: async () => ({ route: "simple_chat", confidence: 0.8 }),
    routeIntentJev: async () => { throw new Error("TYPESAFE_API_KEY is not configured"); }
  });
  assert.equal(result.luna.ok, true);
  assert.equal(result.jev.ok, false);
  assert.match(result.jev.error, /TYPESAFE_API_KEY/);
  assert.equal(result.deltaMs, null);
});
