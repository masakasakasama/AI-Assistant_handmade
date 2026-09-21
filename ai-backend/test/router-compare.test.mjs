import test from "node:test";
import assert from "node:assert/strict";
import { compareRouters } from "../src/router-compare.mjs";
import { parseJevRouteResponse } from "../src/jev.mjs";

function choice(choice, confidence = 0.9) {
  return { type: "choice", choice, confidence, probabilities: { [choice]: 1 } };
}

test("parses a Jev Choice route", () => {
  const parsed = parseJevRouteResponse({
    model: "jev-1.13.0",
    answers: {
      route: choice("weather", 0.91),
      language: choice("ja"),
      device_action: choice("none"),
      device_goal: choice("none"),
      device_target: choice("none"),
      temperature_c: choice("none"),
      alarm_action: choice("none"),
      alarm_target: choice("none"),
      new_hour: choice("none"),
      new_minute: choice("none"),
      reference_hour: choice("none"),
      reference_minute: choice("none")
    },
    usage: { input_tokens: 42, output_tokens: 0 }
  });
  assert.equal(parsed.route, "weather");
  assert.equal(parsed.language, "ja");
  assert.equal(parsed.confidence, 0.91);
});

test("Jev extracts hot as a cooler goal without inventing a concrete action", () => {
  const parsed = parseJevRouteResponse({
    model: "jev-1.13.0",
    answers: {
      route: choice("device_action", .94), language: choice("ja"),
      device_action: choice("none"), device_goal: choice("cooler"),
      device_target: choice("item_0"), temperature_c: choice("none"),
      alarm_action: choice("none"), alarm_target: choice("none"),
      new_hour: choice("none"), new_minute: choice("none"),
      reference_hour: choice("none"), reference_minute: choice("none")
    }
  }, { deviceMap: new Map([["item_0", { name: "寝室エアコン", targetType: "air_conditioner" }]]) });
  assert.equal(parsed.route, "device_action");
  assert.equal(parsed.goal, "cooler");
  assert.equal(parsed.action, null);
  assert.equal(parsed.executionMode, "resolve");
  assert.equal(parsed.targetType, "air_conditioner");
});

test("explicit air-conditioner setting is an action with structured parameters", () => {
  const parsed = parseJevRouteResponse({
    answers: {
      route: choice("device_action"), language: choice("ja"),
      device_action: choice("set_ac"), device_goal: choice("set"),
      device_target: choice("item_0"), temperature_c: choice("t25"),
      alarm_action: choice("none"), alarm_target: choice("none"),
      new_hour: choice("none"), new_minute: choice("none"),
      reference_hour: choice("none"), reference_minute: choice("none")
    }
  }, { deviceMap: new Map([["item_0", { name: "エアコン", targetType: "air_conditioner" }]]) });
  assert.equal(parsed.action, "set_ac");
  assert.deepEqual(parsed.parameters, { temperature: 25 });
  assert.equal(parsed.executionMode, "execute");
});

test("router comparison keeps Luna and Jev independent", async () => {
  const result = await compareRouters({ text: "明日の天気は？" }, {
    routeIntent: async input => {
      assert.equal(input.benchmarkClassificationOnly, true);
      return { route: "weather", confidence: 0.95, _usage: { input_tokens: 10 } };
    },
    routeIntentJev: async () => ({
      model: "jev-test",
      language: "ja",
      route: "weather",
      confidence: 0.9,
      action: null,
      target: null,
      temperatureC: null,
      timeLocal: null,
      referenceTimeLocal: null,
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
    routeIntentJev: async () => { throw new Error("JEV_OPENROUTER_API_KEY is not configured"); }
  });
  assert.equal(result.luna.ok, true);
  assert.equal(result.jev.ok, false);
  assert.match(result.jev.error, /JEV_OPENROUTER_API_KEY/);
  assert.equal(result.deltaMs, null);
});
