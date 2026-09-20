export const JEV_MODEL = process.env.JEV_MODEL || "jev-1.13.0";
export const JEV_ENDPOINT = process.env.JEV_ENDPOINT || "https://api.typesafe.ai/v1/systemone";

const ROUTE_CRITERIA = {
  device_action: "A direct request to control a home or SwitchBot device, including power, mode, or temperature.",
  alarm_action: "A request to create, update, delete, enable, disable, or inspect an alarm.",
  weather: "A direct request for current or forecast weather.",
  simple_chat: "Lightweight conversation or a short factual request that does not require substantial reasoning.",
  deep_reasoning: "A request needing nontrivial reasoning, planning, comparison, explanation, research-like synthesis, or a high-quality long answer.",
  clarify: "An unclear or ambiguous request where the assistant should ask a concise clarification before acting."
};

const LANGUAGE_CRITERIA = {
  ja: "Japanese",
  en: "English",
  de: "German",
  other: "Any other language"
};

const DEVICE_ACTION_CRITERIA = {
  turn_on: "Turn a known device on.",
  turn_off: "Turn a known device off.",
  set_ac: "Set an air conditioner's temperature or operating state.",
  none: "No supported device action is explicitly requested."
};

const ALARM_ACTION_CRITERIA = {
  alarm_create: "Create a new alarm.",
  alarm_update: "Change an existing alarm.",
  alarm_delete: "Delete an existing alarm.",
  none: "No supported alarm action is explicitly requested."
};

function section(context, start, end) {
  const source = typeof context === "string" ? context : "";
  const from = source.indexOf(start);
  if (from < 0) return "";
  const after = source.slice(from + start.length);
  const to = end ? after.indexOf(end) : -1;
  return (to >= 0 ? after.slice(0, to) : after).trim();
}

function knownDevices(context) {
  return section(context, "Known devices:", "Current alarms:")
    .split("\n")
    .map(line => line.trim())
    .filter(line => line.startsWith("- ") && line !== "- none")
    .map(line => {
      const body = line.slice(2);
      const parts = body.split("|").map(x => x.trim());
      return { name: parts[0] || "", description: parts.slice(1).join(" | ") };
    })
    .filter(item => item.name);
}

function knownAlarms(context) {
  return section(context, "Current alarms:", "Recent conversation:")
    .split("\n")
    .map(line => line.trim())
    .filter(line => line.startsWith("- ") && line !== "- none")
    .map(line => {
      const body = line.slice(2);
      const parts = body.split("|").map(x => x.trim());
      return { label: parts[0] || "", time: parts[1] || "", description: parts.slice(2).join(" | ") };
    })
    .filter(item => item.label || item.time);
}

function candidateCriteria(items, label, description) {
  const criteria = { none: `No known ${label} is clearly referenced.` };
  const map = new Map();
  items.slice(0, 254).forEach((item, index) => {
    const key = `item_${index}`;
    criteria[key] = description(item);
    map.set(key, item);
  });
  return { criteria, map };
}

function timeCriteria(max, unit) {
  const criteria = { none: `No ${unit} is specified.` };
  for (let value = 0; value <= max; value++) {
    criteria["v" + value] = `${unit} ${String(value).padStart(2, "0")}`;
  }
  return criteria;
}

function parseChoice(payload, key) {
  const answer = payload?.answers?.[key];
  if (!answer || typeof answer.choice !== "string") {
    throw new Error(`Invalid Jev ${key} response`);
  }
  return answer;
}

function choiceNumber(answer, min, max) {
  const raw = answer?.choice;
  if (typeof raw !== "string" || raw === "none" || !raw.startsWith("v")) return null;
  const value = Number(raw.slice(1));
  return Number.isInteger(value) && value >= min && value <= max ? value : null;
}

function clock(hour, minute) {
  if (hour == null) return null;
  const resolvedMinute = minute ?? 0;
  return `${String(hour).padStart(2, "0")}:${String(resolvedMinute).padStart(2, "0")}`;
}

export function parseJevRouteResponse(payload, metadata = {}) {
  const routeAnswer = parseChoice(payload, "route");
  if (!(routeAnswer.choice in ROUTE_CRITERIA)) throw new Error("Invalid Jev route choice");

  const languageAnswer = parseChoice(payload, "language");
  const language = languageAnswer.choice in LANGUAGE_CRITERIA ? languageAnswer.choice : "other";

  const deviceActionAnswer = parseChoice(payload, "device_action");
  const alarmActionAnswer = parseChoice(payload, "alarm_action");
  const deviceTargetAnswer = parseChoice(payload, "device_target");
  const alarmTargetAnswer = parseChoice(payload, "alarm_target");
  const temperatureAnswer = parseChoice(payload, "temperature_c");
  const newHourAnswer = parseChoice(payload, "new_hour");
  const newMinuteAnswer = parseChoice(payload, "new_minute");
  const referenceHourAnswer = parseChoice(payload, "reference_hour");
  const referenceMinuteAnswer = parseChoice(payload, "reference_minute");

  const route = routeAnswer.choice;
  let action = null;
  let target = null;
  let temperatureC = null;
  let timeLocal = null;
  let referenceTimeLocal = null;

  if (route === "device_action") {
    action = deviceActionAnswer.choice !== "none" ? deviceActionAnswer.choice : null;
    target = metadata.deviceMap?.get(deviceTargetAnswer.choice)?.name ?? null;
    const numericTemperature = temperatureAnswer.choice?.startsWith("t")
      ? Number(temperatureAnswer.choice.slice(1))
      : NaN;
    temperatureC = Number.isFinite(numericTemperature) && numericTemperature >= 16 && numericTemperature <= 30
      ? numericTemperature
      : null;
  }

  if (route === "alarm_action") {
    action = alarmActionAnswer.choice !== "none" ? alarmActionAnswer.choice : null;
    target = metadata.alarmMap?.get(alarmTargetAnswer.choice)?.label ?? null;
    timeLocal = clock(
      choiceNumber(newHourAnswer, 0, 23),
      choiceNumber(newMinuteAnswer, 0, 59)
    );
    referenceTimeLocal = clock(
      choiceNumber(referenceHourAnswer, 0, 23),
      choiceNumber(referenceMinuteAnswer, 0, 59)
    );
  }

  return {
    model: typeof payload.model === "string" ? payload.model : JEV_MODEL,
    language,
    route,
    confidence: Number.isFinite(routeAnswer.confidence) ? routeAnswer.confidence : 0,
    action,
    target,
    temperatureC,
    timeLocal,
    referenceTimeLocal,
    replyText: null,
    probabilities: routeAnswer.probabilities && typeof routeAnswer.probabilities === "object"
      ? routeAnswer.probabilities
      : {},
    usage: payload.usage ?? null
  };
}

export async function routeIntentJev({ text, context = "" }, dependencies = {}) {
  if (typeof text !== "string" || !text.trim()) throw new Error("text is required");
  const apiKey = dependencies.apiKey ?? process.env.TYPESAFE_API_KEY;
  if (!apiKey) throw new Error("TYPESAFE_API_KEY is not configured");

  const devices = knownDevices(context);
  const alarms = knownAlarms(context);
  const deviceCandidates = candidateCriteria(
    devices,
    "device",
    item => `Known device named "${item.name}". ${item.description}`.trim()
  );
  const alarmCandidates = candidateCriteria(
    alarms,
    "alarm",
    item => `Known alarm "${item.label}" at ${item.time}. ${item.description}`.trim()
  );

  const temperatureCriteria = { none: "No air-conditioner temperature is specified." };
  for (let value = 16; value <= 30; value++) {
    temperatureCriteria["t" + value] = `Set temperature to ${value} degrees Celsius.`;
  }

  const fetchImpl = dependencies.fetchImpl ?? globalThis.fetch;
  const response = await fetchImpl(JEV_ENDPOINT, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "Accept": "application/json"
    },
    body: JSON.stringify({
      model: JEV_MODEL,
      state: {
        utterance: text.trim(),
        context: typeof context === "string" ? context : ""
      },
      questions: {
        route: {
          type: "choice",
          instructions: "Choose the single best first routing destination for the user's utterance.",
          criteria: ROUTE_CRITERIA
        },
        language: {
          type: "choice",
          instructions: "Which language is the user's utterance primarily written or spoken in?",
          criteria: LANGUAGE_CRITERIA
        },
        device_action: {
          type: "choice",
          instructions: "If the utterance is a device command, choose the requested action. Otherwise choose none.",
          criteria: DEVICE_ACTION_CRITERIA
        },
        device_target: {
          type: "choice",
          instructions: "If the utterance refers to one known device, choose it. Otherwise choose none.",
          criteria: deviceCandidates.criteria
        },
        temperature_c: {
          type: "choice",
          instructions: "If the utterance explicitly requests an air-conditioner temperature in Celsius, choose it. Otherwise choose none.",
          criteria: temperatureCriteria
        },
        alarm_action: {
          type: "choice",
          instructions: "If the utterance is an alarm command, choose the requested action. Otherwise choose none.",
          criteria: ALARM_ACTION_CRITERIA
        },
        alarm_target: {
          type: "choice",
          instructions: "If the utterance clearly refers to one existing known alarm, choose it. Otherwise choose none.",
          criteria: alarmCandidates.criteria
        },
        new_hour: {
          type: "choice",
          instructions: "For an alarm create or update, choose the requested NEW hour in local 24-hour time. Otherwise choose none.",
          criteria: timeCriteria(23, "hour")
        },
        new_minute: {
          type: "choice",
          instructions: "For an alarm create or update, choose the requested NEW minute. Use 00 when the user gives an exact hour. Otherwise choose none.",
          criteria: timeCriteria(59, "minute")
        },
        reference_hour: {
          type: "choice",
          instructions: "For an alarm update or delete, choose the hour of the EXISTING alarm the user refers to. Otherwise choose none.",
          criteria: timeCriteria(23, "reference hour")
        },
        reference_minute: {
          type: "choice",
          instructions: "For an alarm update or delete, choose the minute of the EXISTING alarm the user refers to. Use 00 when appropriate. Otherwise choose none.",
          criteria: timeCriteria(59, "reference minute")
        }
      }
    })
  });

  if (!response.ok) throw new Error(`Jev HTTP ${response.status}`);
  return parseJevRouteResponse(await response.json(), {
    deviceMap: deviceCandidates.map,
    alarmMap: alarmCandidates.map
  });
}
