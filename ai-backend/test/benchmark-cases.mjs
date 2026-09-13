const utterances = {
  ja: ["エアコンを26度にして", "7時にアラーム", "7時のアラームを7時半にして", "今日の天気は？", "ドイツの首都は？", "あれ消して", "この3案をQCDで比較して"],
  en: ["Set the air conditioner to 26 degrees", "Set an alarm for seven", "Move the seven o'clock alarm to seven thirty", "What's the weather today?", "What is the capital of Germany?", "Turn that off", "Compare these three options by quality, cost and delivery"],
  de: ["Stell die Klimaanlage auf 26 Grad", "Stell einen Wecker auf sieben Uhr", "Verschiebe den Wecker von sieben auf halb acht", "Wie ist das Wetter heute?", "Was ist die Hauptstadt von Deutschland?", "Mach das aus", "Vergleiche diese drei Optionen nach Qualität, Kosten und Lieferzeit"]
};
const routes = [["device_action"], ["alarm_action", "clarify"], ["alarm_action", "clarify"], ["weather"], ["simple_chat"], ["clarify"], ["deep_reasoning"]];
export const cases = Object.entries(utterances).flatMap(([language, texts]) => texts.map((text,index) => ({
  id: `${language}-${index+1}`, language, text,
  expectedRoutes: routes[index], chat: index === 4 || index === 6,
  expectedParameters: index === 0 ? {action: "set_ac", temperatureC: 26} : null,
  context: index === 6 ? "A: cost 100, delivery 2 days, quality 7/10. B: cost 150, delivery 1 day, quality 9/10. C: cost 80, delivery 7 days, quality 6/10. Prioritize reliability." : ""
})));
