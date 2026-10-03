# Tatsu Home Web

URL: https://ai-assistant-handmade.vercel.app/

Safari (iOS 16.4+) / modern Chrome: same 3D GLB, clearly visible idle/speaking motion, Luna/Jev text conversation, multilingual PCM16 audio input, system speech output, weather, SwitchBot manual controls and confirmed voice commands, browser-local alarms, comparison mode and settings.

Open Safari's Share menu → Add to Home Screen. No APK download is necessary. The PWA caches only the current Web assets (approximately 1 MB before HTTP compression), removes older caches after an update, and never caches API requests. Navigation checks the network first. An open page checks `version.json` every minute and offers an update button without interrupting a conversation.

Enter Backend owner token in Settings; SwitchBot Token/Secret are needed only for device controls. Tokens are kept in sessionStorage, not persistent localStorage. Backend provider keys remain server-only. `/api/switchbot` authenticates and applies the same shared request limit, refreshes provider inventory before commands, and selects audited commands server-side. SwitchBot's acceptance is not a physical state observation, especially for IR devices. Conversation/voice features still depend on the existing Backend authentication and Redis deployment configuration.

Alarms/settings in this browser are separate from Android. Web alarms ring only while the page is visible and audio is unlocked by the Add button. iOS may suspend a hidden/locked page, so Web alarms are not a substitute for Android AlarmManager. Web voice input is started with a button; continuous wake-word capture and Android USB microphone selection are not available. Text remains available if microphone permission is denied. Voice recording is cancelled on page hide. Auto input sends Japanese/English/German candidates without forcing Japanese; fixed input sends a validated language hint.

## Build and release

`npm ci --ignore-scripts`, `npm run build:web`, `npm run dev:web`.
`npx playwright install --with-deps webkit chromium`, `npm run test:web`.
`npm test --workspace ai-backend` includes provider-command rejection, profile parity and fixed/auto transcription tests.

Vercel's existing main-branch Git integration builds `dist/` with `vercel.json`; API functions remain rooted in `api/`. The version comes from `app/build.gradle.kts`, commit from Vercel's Git metadata. `web.yml` verifies mobile WebKit/Chromium and the deployed version/commit. Every new APK release also builds/tests Web, waits for its production version/commit, and attaches `tatsu-home-web-VERSION.zip` to the GitHub release. No additional Vercel deployment token is required for the Git integration.

Browser tests use synthetic HTTP/audio fixtures and verify real WebGL pixels, user confirmation, denial recovery, persistence, comparison non-execution and update prompts. They do not prove actual iPhone microphones/TTS, live provider accuracy or real device control.

Webだけを更新した場合も、ブラウザー・本番version検査後に、既存の同じ番号のGitHub Releaseへ最新版Web ZIPとWebKit/Chromiumの画面画像を自動添付する。新しい番号のReleaseがまだなければAPK公開ジョブがZIPを添付する。

Chromeのオフライン画面再読込と、両ブラウザーの旧キャッシュ削除・必要資産の保存を検証する。Playwright WebKitのオフライン指定はService Workerの前でナビゲーションが失敗するため、iPhone実機のオフライン再読込は未検証。オンラインでのWebKit画面・会話・音声PCM処理・設定・操作確認は別に検証する。
