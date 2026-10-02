# Tatsu Home

会話と、家の操作をひとつに。Androidを常設ハブにする個人用AIスマートスピーカー。

Google Home / Alexaの全機能コピーではなく、自然な会話、SwitchBot、正確なアラーム管理、天気、日・英・独を優先します。応答速度は最優先の品質条件です。

## v0.4.22

標準回答をmedium＋短い読み上げ向け要約へ変更し、設定で「速く答える／標準／じっくり考える」を選択可能にした。難しい比較質問のサーバー完了時間は各3回の中央値で46.1秒→15.5秒。単独のあいさつはAndroid内で返信する。測定条件と限界は[性能資料](docs/PERFORMANCE.md)参照。

## v0.4.21

Sol highの出力枠を推論込み8,192トークンへ拡大。出力上限による終了通知を正しく処理し、長い比較質問で回答前に終了する問題を修正。時間切れ対策はv0.4.20の内容を継続する。

## v0.4.20

Solの回答生成の待機上限を45秒から90秒へ、AIラボの4経路比較を150秒へ更新。時間切れはモデルと工程を表示し、他の比較結果を維持する。速度改善や品質低下を意味する変更ではない。

## v0.4.19

現行モデルは **GPT-6 Luna ＋ GPT-6.1 Sol high**。6.1 Lunaは公式モデル一覧にないためLunaは6を継続する。AIラボの4経路比較は5.6とこの現行ペアを比較し、実際のモデルIDと応答時間を表示する。速度・品質の改善は実機測定で判定する。

## 開発方針

**Phase 0は手持ちGalaxyで、AIの回答から音声出力・操作結果までを検証する。合格まで音響HW・中古タブレットは買わない。**

- 比較中の2経路：Luna-first（STT → Luna → 必要時Sol）とJev-first（STT → Jev → simple_chatはLuna / deep_reasoningはSol high / 家電・アラーム・天気はAndroid）。
- Jevは文章生成に使わず、route・action・対象・温度・時刻などの閉じた判断を1回で返す。simple_chatだけはJev後にLunaが追加されるため、速度メリットが出るとは限らない。
- GPT-Liveは任意のConversation Mode。通常経路と同じ状態・操作実行層を使う。
- confidenceは診断情報。操作許可には使わず、対象ID・状態・値・有効期限をAndroidで検証する。
- アラームはAndroidで永続化・発火。AI障害とローカル鳴動を分離する。
- Phase 1：XVF3800＋ESP32-S3＋ローカルWake＋Wi-Fi音声。2〜5m・AEC・割り込みは実機判定。

## 実装と検証を区別する

| 項目 | 現状 |
|---|---|
| SwitchBot / Room / AlarmManager / 天気 / 更新 | Android実装あり。故障注入と長期試験は未完了 |
| AIテキスト検証UI / HTTP Backend | 実装あり。AI操作案を表示するだけで、物理操作しない |
| Luna単一呼び出し / Sol high既定 / 区間計測 | 実装・オフラインテストあり。mediumとの実API比較は未測定 |
| Luna vs Jev 初段ルーター比較 | v0.4.6で文字・音声の比較PoCを追加。Luna-firstとJev-firstを個別に音声実行でき、判定比較中は物理操作を実行しない |
| Luna vs Jev 全経路比較 | v0.4.9で回答本文に加え、STTから応答までの共通timings表、差分、実測短縮率、JevのActionPlan dry-run、折りたたみ診断を追加。比較では家電・アラームを実行しない |
| Luna/Jev × GPT-5.6/6の4経路比較 | v0.4.18で同じ入力を4経路へ送り、回答・route・モデルID・サーバー時間・端末往復・同一入力のp50を比較。Jevは分類後のLuna/Solを選択した5.6/6へ切り替える。実機操作なし |
| 性能比較CLI | 日英独21ケース・4経路。初期データセットはスモーク用 |
| Galaxy STT / Android TTS / 音声キャンセル | v0.4.0で縦切り実装。v0.4.2で言語別STT切替・診断表示を追加。v0.4.3でBackend URLを初期設定。v0.4.4で日英独の自動言語検出を既定化。v0.4.5で音声状態と同期するマスコット表示を追加。v0.4.6でLuna-first / Jev-firstの音声比較と初段判定比較を追加。v0.4.8で回答比較・単体音声セッションの後始末を追加。実Galaxy評価中 |
| OpenAI STT比較 / Live | 後続のPhase 0比較対象 |
| Jev家電操作 | v0.4.9でIntent → 決定論的Resolver → Policy Gate → SwitchBot Adapterを実装。IRエアコンは現在状態を取得できないため相対操作を推測実行しない |
| 確認付きActionPlan | 30秒のpending context、確認後の状態再取得、期限切れ・重複実行ガードを実装。SwitchBot実機・障害注入評価は未完了 |
| 外部PCM入力 / AEC / Wake / 2〜5m / 72時間 | 未検証 |

「実装あり」は品質合格を意味しません。速度目標は実測値ではありません。

## 設計資料

- [全体アーキテクチャと責務](docs/ARCHITECTURE.md)
- [Phase 0・Phase 1の実装順序と合格条件](docs/PHASE_0.md)
- [性能比較手順・測定の定義](docs/PERFORMANCE.md)
- [UI設計方針](docs/DESIGN.md)
- [Android・SwitchBot・APKセットアップ](docs/ANDROID_SETUP.md)
- [AI Backend設定](AI_BACKEND_SETUP.md)

## 確認コマンド

```sh
npm ci --ignore-scripts
cd ai-backend
npm test
npm run benchmark:plan
# APIキーを安全に設定した環境でのみ実行。実API料金が発生する。
npm run benchmark
```

Android：JDK 17、Android SDK 35、Gradle 8.11.1で `gradle :app:assembleDebug`。
PRのGitHub ActionsでもAndroidをビルドします。mainの自動デプロイ前に、Backend認証・利用予算制限を完成させてください。

## Local Wake Word

Tatsu Home can keep a wake-word detector running entirely on the Android device.

- Initial phrase: `Hey Jarvis`; Settings → ウェイクワード switches between `Hey Jarvis`, `Alexa`, and `Hey Mycroft`. Changes apply immediately and survive restarts.
- For a different phrase, import a trained openWakeWord-compatible single-class ONNX classifier in the same settings panel. The entered phrase is its display label; typing a label does not train a detector. The app validates the float `[1,16,96]` input, runs one prediction, and checks the output before replacing the saved model. Models up to 10 MB are copied to private app storage, so the source file can then be removed.
- Runtime: openWakeWord-compatible ONNX models through `openwakeword-android`
- Network/API use before wake detection: none
- On detection: the wake detector releases the microphone, then the existing Android speech-recognition flow starts
- During TTS, wake-word interruption can stay active (v0.4.27); after completion or error/cancel, ordinary wake-word listening resumes automatically
- Listening pauses when the activity leaves the foreground and resumes when it returns. Background/screen-off operation is not provided by this foreground implementation.
- Test on the existing Galaxy before choosing dedicated hardware.

The dependency is pinned to upstream tag `0.1.2` (commit `b49f3ab14cf558ac0c2aef73a5e6c83da7a08558`).
The bundled pre-trained wake-word models are CC BY-NC-SA 4.0, so this initial configuration is intended for the personal/non-commercial Tatsu Home deployment. A custom `Tatsu` model can replace the built-in classifier later without changing the app flow.

### Existing USB microphone trial

The user's Sanwa 400-MC011 is a USB-A bus-powered omnidirectional microphone, without a built-in speaker. Try it on the existing Galaxy with a USB-C host/OTG adapter (or a data-capable hub); a charging-only cable will not work. The manufacturer lists Windows/macOS/ChromeOS, not Android, so compatibility with Galaxy and the Android speech-recognition service is unverified. Automatic cloud transcription and wake detection prefer the same connected USB input and verify the actual route. Fixed-language Android SpeechRecognizer input is still controlled by its service. Verify input by muting the 400-MC011 while the phone's microphone is still exposed and checking that recognition stops using that input. USB output may route to the microphone's 3.5 mm jack; if TTS becomes silent, check the output route or attach a speaker/headphones there. For continuous charging, a hub must support both USB data and PD charging with the specific phone/tablet.

Official specifications: https://direct.sanwa.co.jp/ItemPage/400-MC011. Advertised coverage is an environmental estimate, not verified wake-word range. A microphone replacement or ESP32 board is not required to begin this test.

## v0.4.24: Conversation tone and German recognition

Conversational replies match playful/cute wording in Japanese, English, and German while keeping neutral or serious requests appropriate. Standalone playful Japanese greetings also respond locally. Tone never changes device-action validation or makes a proposal sound like a completed operation. Android TTS voice selection is unchanged; this release adjusts reply wording.

Speech recognition now defaults to the system service, which may use the network. Settings → 音声入力 allows selecting the speech language and switching between 標準サービス and 端末内優先. For German, start with Deutsch + 標準サービス. Automatic switching depends on the device's recognizer and installed language models; selecting Deutsch removes automatic language selection from the recognition request.

Fixed an automatic-mode support-check bug that checked only Japanese availability while requesting Japanese/English/German switching. On-device automatic mode now requires all three language models; missing models or an inconclusive support check fall back to the system recognizer. Automatic mode requests quick switching, and retains the last confidently detected language during the app session as the next base language. Low-confidence detections are logged but do not select a new base language. Diagnostics include required/installed languages and the reported switch result. Recognition accuracy and the actual Galaxy provider behavior still require audio/device testing; code-level checks do not measure German word error rate.

## v0.4.25: Automatic multilingual audio transcription

Automatic voice input now records an utterance as 16 kHz mono PCM16 WAV and sends it to `/api/transcribe`. The backend uses `gpt-transcribe` with `languages[]=ja`, `en`, `de`, without a fixed language or Japanese prompt. This replaces the automatic-mode dependence on Android recognition providers accepting language-switch extras. The API returns the transcript in its original language plus detected language codes; the app passes these into the existing conversation/validated-action flow. If detection is uncertain, it is not mislabeled Japanese. Service errors remain errors and do not silently retry through Japanese recognition. Fixed-language modes still use the Android recognizer.

Audio recording begins only after wake detection or explicit voice input, keeps initial samples, stops after 1.2 seconds of silence after speech, and is bounded at 30 seconds. A connected USB input is preferred when Android exposes it; diagnostics identify the routed input. Cancellation stops recording/upload and ignores stale results. Recordings are kept in memory, not saved by the app/backend. Audio is transmitted to OpenAI for this cloud recognition path, requires networking, and incurs audio API charges. Pre-wake detection remains local. Provider data handling follows the configured OpenAI API account policy.

## v0.4.26: Transcription language consistency

An audio benchmark found German transcripts sometimes carrying a Japanese language label. The backend now checks this inconsistency with a local statistical German/English text-language classifier (`franc-min`), without rewriting the transcript or making an extra model call. Correction requires at least three words/twelve letters, no Japanese script, and a clear separation in the classifier's distance scores. Mixed-language labels, short ambiguous fragments, and Japanese text containing Latin product names retain the audio result. Responses preserve `audioLanguages` and state `languageSource` so a text-based correction is distinguishable from an audio-only prediction. This fixes language labeling; it does not claim to improve the speech transcription itself.

Measured results: [音声認識の測定（v0.4.25 → v0.4.26）](docs/STT_ACCURACY.md). Paired German language identification improved from 31/42 to 42/42; an additional 42 unseen German utterances also passed. This is a cloud API benchmark, not a Galaxy microphone or old Android recognizer comparison.

## v0.4.27: USB wake input and wake-word interruption

Wake detection and automatic/cloud transcription now share USB microphone selection. Once a USB input is selected, rejected preference or a different actual input is reported as an error; the app does not silently claim to use USB while recording the phone microphone. Settings → ウェイクワード displays the actual input name/ID and echo-effect status after the first successful read. Fixed-language Android recognition services still control their own microphone; use 自動 for the shared verified input path.

「返答中も呼びかけで割り込む」 defaults on and is saved immediately. During a spoken reply, say the selected wake phrase to stop it and start a new utterance. The old session/generation and pending jobs are canceled; completed physical actions are not undone. This is wake-word interruption, not arbitrary speech/barge-in. Disabling the setting restores listening only after TTS. The app remains foreground-only as before.

Playback detection requires two adjacent scores ≥0.8 (ordinary wake score ≥0.55), excludes the first 500 ms of playback and 350 ms after completion, and suppresses detection throughout a reply containing the selected wake phrase. This conservative suppression can prevent a legitimate interruption of that particular reply; it avoids treating a known self-spoken wake phrase as a user. Built-in capture requests Android platform AEC where available, but availability/enabled status is not proof of echo suppression quality. With a USB array and the phone/tablet's own speaker, XVF3800 does not automatically receive the speaker's playback reference. Effective hardware AEC requires playback through the array's supported output path. Physical false-wake rates and interruption latency remain to be measured on Galaxy/XVF3800.

A locally adapted Apache-2.0 copy of upstream `openwakeword-android` 0.1.2's pipeline enables recorder routing, interruption gating, error propagation, and stale-callback checks. The pinned dependency continues providing the original model assets and ONNX runtime. Upstream source: https://github.com/msnilsen/openwakeword-android/tree/b49f3ab14cf558ac0c2aef73a5e6c83da7a08558 ; license is bundled at `app/src/main/assets/licenses/openwakeword-android-LICENSE.txt`. Pretrained model license remains CC BY-NC-SA 4.0.
