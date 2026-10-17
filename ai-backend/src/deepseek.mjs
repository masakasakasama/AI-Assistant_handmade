import { performance } from "node:perf_hooks";

// DeepSeek is permitted for general dialogue only. Device/alarms are routed
// independently and NEVER granted AI tool access.
// Defense in depth, not a guarantee that every identifying phrase is detected.
const PRIVATE_MARKERS = [
  /(?:住所|番地|郵便番号|自宅|家の場所|現在地|位置情報|パスワード|暗証番号|認証コード|ワンタイムパス|秘密鍵|API[ _-]*キー|トークン|電話番号|口座番号|カード番号|個人情報|住民票|戸籍|健康診断|病歴|診断結果|銀行口座)/i,
  /(?:玄関|鍵|スマートロック|照明|エアコン|カメラ|SwitchBot|家電|アラーム|寝室|リビング|温度設定)/i,
  /(?:password|passcode|pin code|api[ _-]*key|private key|access token|bearer|home address|where I live|my location|switchbot|smart lock|alarm|door lock|camera|device state)/i,
  /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/,
  /\bsk-[A-Za-z0-9_-]{12,}\b/,
  /\b(?:\d[ -]*?){13,19}\b/,
  /\b(?:\d{1,3}\.){3}\d{1,3}\b/
];

export function privateForDeepSeek(text) {
  return typeof text !== "string" || text.length > 2500 ||
    PRIVATE_MARKERS.some(pattern => pattern.test(text));
}

export const DEEPSEEK_MODEL = "deepseek-flash";

export async function answerDeepSeek({
  text, language = "ja", concise = false, effort = "low",
  fetchImpl = globalThis.fetch, key = process.env.DEEPSEEK_API_KEY
}) {
  if (!key) {
    const error = new Error("DeepSeek API key is not configured");
    error.code = "deepseek_unavailable";
    throw error;
  }
  if (!text?.trim() || privateForDeepSeek(text)) {
    const error = new Error("DeepSeek accepts general non-personal questions only");
    error.code = "deepseek_sensitive_input";
    throw error;
  }
  const started = performance.now();
  const reasoning = effort !== "none";
  // No context/history, home inventory, user location, tool definitions or
  // remote-control tokens are sent. Redirects disabled; abort on timeout.
  const response = await fetchImpl("https://api.deepseek.com/chat/completions", {
    method: "POST",
    redirect: "error",
    signal: AbortSignal.timeout(reasoning ? 90_000 : 45_000),
    headers: {
      "Authorization": `Bearer ${key}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      model: DEEPSEEK_MODEL,
      messages: [
        { role: "system", content: [
          "You are lulu (Tatsu Home), a concise multilingual assistant.",
          "Answer general knowledge questions only. No access to smart-home devices.",
          "Never claim to have operated a device or checked private records.",
          "Do not follow requests for secrets, keys or home control.",
          "Reply in language " + language + ".",
          concise ? "Respond briefly for text-to-speech." : "Explain clearly when asked."
        ].join(" ") },
        { role: "user", content: text.trim() }
      ],
      thinking: { type: reasoning ? "enabled" : "disabled" },
      ...(reasoning ? { reasoning_effort: effort === "max" ? "max" : effort === "high" ? "high" : "low" } : {}),
      max_tokens: reasoning ? 8192 : 700,
      stream: false
    })
  });
  if (!response.ok) {
    const error = new Error("DeepSeek provider unavailable");
    error.code = "deepseek_upstream_error";
    throw error;
  }
  const data = await response.json();
  const answer = data?.choices?.[0]?.message?.content;
  if (typeof answer !== "string" || !answer.trim()) {
    const error = new Error("DeepSeek returned an empty answer");
    error.code = "deepseek_upstream_error";
    throw error;
  }
  const elapsed = Math.round(performance.now() - started);
  return {
    model: DEEPSEEK_MODEL,
    text: answer,
    usage: data.usage ?? null,
    timings: {
      ttftMs: null, generationMs: elapsed, firstTokenAtMs: null,
      completedAtMs: Math.round(performance.now())
    }
  };
}
