# CODEX_STATE

Status: in_progress
Goal: GalaxyでPhase 0を検証し、音声会話・家電操作の品質と応答時間を確かめる。

## Done
- v0.4.28のローカル3Dマスコット実装と設計引き継ぎを確認した。
- HTTP JSON入力を共通化。UTF-8バイト数で制限し、分割された日本語入力を保持する。
- 不正JSON・null/配列・過大入力を400/413でAPI呼び出し前に拒否する。ローカルHTTP/Vercelの4経路へ適用。

- v0.4.31: local HTTP/Vercel 4 POST経路へshared owner Bearer gateを追加。未設定503・不正/旧token401を本文解析/provider呼出し前に拒否。healthはpublicのまま。
- 端末設定のtokenは既存Keystore AES/GCMへ保存。空欄維持/明示削除/各requestで再読込。HTTPS＋loopbackのみ送信、redirect追従停止、401/403は再設定案内。provider keyをAPKへ入れていない。

## Current
- この司令塔の担当はBackend認証と端末設定/HTTP整合。native3D資産・描画実装は変更なし。
- 隔離認証/rotation/本文解析前拒否、Android unit/debug APKが成功。実デプロイ/実Galaxyの認証受入は未検証。

## Next
- 利用上限・レート制限・秘匿ログを実装し、Phase0Cの公開前条件を隔離fixtureで検証する。
- BackendのAI_BACKEND_TOKENを設定/redeploy後、Galaxyで保存/再起動/失効/再入力を確認する。

## Blockers
- Backend owner token/OpenAI/Jev設定と実デプロイ受入が未実施。実API測定なし。
- Galaxy実機・USBマイクは未接続。端末Keystore persistence、USB/wake/fps/発熱は実機合格としない。

## Verification
- npm test --workspace ai-backend: 61/61 passed; local HTTP/serverless auth/rotation tests use no provider calls
- Gradle8.11.1/JDK17/SDK35 :app:testDebugUnitTest :app:assembleDebug passed; JUnit36/36, debug APK generated
- MavenCentral HTTP429を環境専用GCS mirror/JitPack initで回避。repository依存設定は変更なし
- git diff --check passed

Updated at: 2026-10-02T23:21:58.608023+00:00

## 3D表示修正完了（2026-10-02、v0.4.30）
- ユーザー実画面でv0.4.28/v0.4.29が旧イラストだった問題を受け、AI画面のWebViewをネイティブFilament/TextureView＋自己完結GLBへ置き換えた。旧WebView資産は削除。旧イラストへ戻す処理もAI画面から外した。
- 全7状態と停止／再開をAndroidエミュレーターで確認。実MainActivityのAIタブでTextureViewの白い本体・青い目のピクセルをassert。ウィンドウ全体のPixelCopy画像を取得し、3Dが見えることを目視確認した。
- 成功CI: Release 37047641278、画像再取得 37048272248。v0.4.30公開APK内のGLBがソースと一致し、arm64 Filament JNIが入り、旧WebView資産がないことを確認。
- GitHub Releaseに実際のAndroid画像 `mascot-ai-window.png` / `mascot-native.png` を添付。APK 141,838,635 bytes。今後の画像回収はテスト後にAPKをアンインストールせず、PNGシグネチャもassertする。
- 再生成ソースはtools/mascot-export、経緯・現行契約はdocs/MASCOT_3D_HANDOFF.md末尾。Galaxy実機のfps・発熱は未測定。上記Backend作業とPhase 0の残課題は維持。
