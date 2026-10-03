import { readFile, writeFile } from 'node:fs/promises';
export function extractTypes(source) {
  return Object.fromEntries(['standardPowerTypes','simpleVacuumTypes','wetVacuumTypes','advancedVacuumTypes'].map(name=>{
    const list = new RegExp(`private val ${name} = setOf\\(([\\s\\S]*?)\\)\\s*(?=\\n\\s*(?:private val|fun ))`).exec(source)?.[1];
    if(!list)throw new Error(`Missing Android command types: ${name}`);
    return [name,[...list.matchAll(/"([^"\n]+)"/g)].map(match=>match[1])];
  }));
}
const source=await readFile('app/src/main/java/com/tatsu/homehub/model/SwitchBotControlProfiles.kt','utf8');
await writeFile('ai-backend/src/switchbot-types.mjs','// Generated from Android SwitchBotControlProfiles.kt by tools/sync-switchbot-profiles.mjs.\nexport default '+JSON.stringify(extractTypes(source),null,2)+';\n');
