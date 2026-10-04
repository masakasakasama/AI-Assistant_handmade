# Tatsu Home release requirements

- Android and Web are maintained together. User-facing changes must update both clients where browsers support the behavior. Explain concrete iOS/browser limits in the UI; do not claim background alarms or wake-word support that the browser cannot provide.
- Every Android release also publishes Web with the same version. `tools/build-web.mjs` reads Android `versionName`. Keep the Vercel Git integration enabled and the Web production verification in the APK release workflow. Never publish an APK while Web deployment/version verification is failing.
- Use the shared self-contained `tatsu.glb` for the mascot. Android and Web idle/speaking motions must be clearly visible. Preserve lifecycle pause/resume and real rendering evidence.
- Web command type lists are generated from the audited Android `SwitchBotControlProfiles.kt`. Update profiles and their browser/server tests together; never guess commands for unknown devices. AI comparisons must not execute devices.
- Run relevant backend tests and WebKit/Chromium browser checks for Web changes. Fixtures do not establish physical SwitchBot or actual iPhone acceptance. Keep that distinction in release notes.
- Never put provider keys or owner tokens in Web assets, APKs, release archives, logs, or committed files. Per the user's request, browser credentials persist in an encrypted IndexedDB vault with a non-exportable key and an explicit delete action. Migrate existing sessions and support one-use pairing from Android's saved settings. Android credentials use Keystore.
