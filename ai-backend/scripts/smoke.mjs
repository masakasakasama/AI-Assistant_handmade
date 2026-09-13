const base = process.env.TATSU_AI_URL || "http://127.0.0.1:8787";

const health = await fetch(base + "/health").then(r => r.json());
console.log("health", health);

if (!process.env.SMOKE_TEXT) process.exit(0);

const dispatch = await fetch(base + "/api/dispatch", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ text: process.env.SMOKE_TEXT })
}).then(async r => ({ status: r.status, body: await r.json() }));

console.log("dispatch", dispatch);
if (dispatch.status >= 400) process.exit(1);
