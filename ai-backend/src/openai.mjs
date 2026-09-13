const OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";

function apiKey() {
  const value = process.env.OPENAI_API_KEY;
  if (!value) throw new Error("OPENAI_API_KEY is not configured");
  return value;
}

export async function createResponse(body) {
  const response = await fetch(OPENAI_RESPONSES_URL, {
    method: "POST",
    signal: AbortSignal.timeout(45_000),
    headers: {
      "Authorization": `Bearer ${apiKey()}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });

  const text = await response.text();
  if (!response.ok) {
    throw new Error(`OpenAI HTTP ${response.status}: ${text}`);
  }

  return JSON.parse(text);
}

export function outputText(response) {
  if (response.status === "incomplete") throw new Error("OpenAI response incomplete");
  for (const item of response.output ?? []) {
    if (item.type !== "message") continue;
    for (const content of item.content ?? []) {
      if (content.type === "output_text" && typeof content.text === "string") {
        return content.text;
      }
    }
  }
  throw new Error("OpenAI response did not contain output_text");
}
