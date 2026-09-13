import { appendFile, mkdir, writeFile } from "node:fs/promises";
import { performance } from "node:perf_hooks";
import { dispatch } from "../src/dispatch.mjs";
import { routeIntent } from "../src/router.mjs";
import { reason } from "../src/reasoner.mjs";
import { answerSimple } from "../src/simple.mjs";
import { cases } from "../test/benchmark-cases.mjs";
import { summarize } from "../src/benchmark-metrics.mjs";

// Local CLI only. Never exposes model selection or physical execution over HTTP.
const dryRun = process.argv.includes("--dry-run");
const rounds = Number(process.env.BENCHMARK_ROUNDS || 3);
if (!Number.isInteger(rounds) || rounds < 1 || rounds > 20) throw new Error("rounds must be 1..20");
if (!dryRun && !process.env.OPENAI_API_KEY) throw new Error("OPENAI_API_KEY required; use --dry-run to inspect the plan");
const profiles = ["routed", "legacy-two-call", "sol-medium", "sol-high"];
const plan = [];
for (let round = 0; round < rounds; round++) {
  for (const entry of cases) {
    // Rotate profile order to reduce time-of-run bias; first calls remain marked.
    const rotated = [...profiles.slice(round % 4), ...profiles.slice(0, round % 4)];
    for (const profile of rotated) {
      if (profile !== "routed" && !entry.chat) continue;
      plan.push({ round, profile, entry });
    }
  }
}
if (dryRun) {
  console.log(JSON.stringify({ paidCalls: false, cases: cases.length, rounds,
    samples: plan.length, profiles, note: "No fabricated timings. Real runs are billed." }, null, 2));
  process.exit(0);
}
const directory = `benchmark-results/${new Date().toISOString().replaceAll(":", "-")}`;
await mkdir(directory, { recursive: true });
const rows = [];
const seen = new Set();
for (const { round, profile, entry } of plan) {
  const started = performance.now();
  const sample = { caseId: entry.id, language: entry.language, round, profile,
    firstInProcess: !seen.has(profile), measuredAt: new Date().toISOString() };
  seen.add(profile);
  try {
    const input = { text: entry.text, context: entry.context || "", language: entry.language };
    let result;
    if (profile === "routed") result = await dispatch(input);
    else if (profile === "legacy-two-call") {
      const route = await routeIntent({ ...input, benchmarkClassificationOnly: true });
      const answer = route.route === "deep_reasoning" ? await reason(input) : await answerSimple(input);
      result = { route, answer, calls: [
        { model: process.env.ROUTER_MODEL || "gpt-5.6-luna", usage: route._usage },
        { model: answer.model, usage: answer.usage }
      ] };
    } else {
      const answer = await reason({ ...input, effort: profile === "sol-high" ? "high" : "medium" });
      result = { answer, calls: [{model: answer.model, usage: answer.usage}] };
    }
    sample.ok = true;
    sample.routeCorrect = result.route ? entry.expectedRoutes.includes(result.route.route) : null;
    sample.parametersCorrect = result.route && entry.expectedParameters
      ? Object.entries(entry.expectedParameters).every(([key, value]) => result.route[key] === value) : null;
    sample.result = result;
  } catch (error) {
    sample.ok = false;
    sample.error = error.name; // Do not persist upstream error bodies or credentials.
  }
  sample.elapsedMs = Math.round(performance.now() - started);
  rows.push(sample);
  await appendFile(`${directory}/samples.jsonl`, JSON.stringify(sample) + "\n");
  console.log(`${entry.id} ${profile} ${sample.ok ? sample.elapsedMs + "ms" : "FAILED"}`);
}
const summary = Object.fromEntries(profiles.map(profile => [profile, summarize(rows.filter(r => r.profile === profile))]));
await writeFile(`${directory}/summary.json`, JSON.stringify({
  measured: true, scope: "text backend completion, NOT speech onset or physical action",
  generatedAt: new Date().toISOString(), summary,
  quality: "Review answers blind; route correctness is not answer quality. Small samples do not prove p95."
}, null, 2));
console.log(directory);
