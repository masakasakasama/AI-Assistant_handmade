# CODEX_STATE

Status: blocked
Goal: GalaxyでPhase 0を検証し、音声会話・家電操作の品質と応答時間を確かめる。

## Done
- v0.4.28のローカル3Dマスコット実装と設計引き継ぎを確認した。
- HTTP JSON入力を共通化。UTF-8バイト数で制限し、分割された日本語入力を保持する。
- 不正JSON・null/配列・過大入力を400/413でAPI呼び出し前に拒否する。ローカルHTTP/Vercelの4経路へ適用。

- v0.4.31: local HTTP/Vercel 4 POST経路へshared owner Bearer gateを追加。未設定503・不正/旧token401を本文解析/provider呼出し前に拒否。healthはpublicのまま。
- 端末設定のtokenは既存Keystore AES/GCMへ保存。空欄維持/明示削除/各requestで再読込。HTTPS＋loopbackのみ送信、redirect追従停止、401/403は再設定案内。provider keyをAPKへ入れていない。

- 共有Redis RESTのatomic EVALでUTC日/固定1分窓の受付上限を追加。設定不足/不明/障害503、超過429+retryAfterSeconds。全4 local HTTP/serverless POSTへprovider前に適用。auth rotationではカウンタをresetしない。
- 例外・cause・provider本文・入力/headerをログへ出さず固定eventのみ記録。未知API/音声エラー・比較provider失敗を固定文言へ秘匿。timeout工程/待機時間は維持。
- 模擬共有storeで日/分境界・拒否非加算・並行claims・設定/障害拒否を検証。4 serverlessの超過時provider未呼出しとlocal HTTP未設定拒否を確認。
- 最新mainのv0.4.32/v0.4.33家電対応・native3D更新を保持。今回Androidコード変更なし。

## Current
- Backend上限・rate limit・秘匿ログの隔離fixtureが成功。日/分request件数の制限で、tokens/円の請求上限ではない。Phase0C実環境受入は未完了。

## Next
- 共有Redis RESTと日/分受付上限をDeployment環境へ設定/redeployできる接続が利用可能になったら、実EVAL・複数Function/process・期限境界・store障害のfail-closedを検証する。
- Galaxyでowner token保存/再起動/失効/再入力と429/503表示を確認し、Phase0CのWAN/LAN断・Backend停止・72時間受入を進める。

## Blockers
- 実Redis/Deployment環境設定・provider側課金制限の実受入は未実施。模擬storeの並行claimsは本番Redis原子性の合格を意味しない。
- Galaxy実機・USBマイクは未接続。端末Keystore persistenceと実API費用/応答測定、故障注入は未検証。

## Verification
- npm test --workspace ai-backend: 67/67 passed; synthetic stores/gateways only
- npm run benchmark:plan --workspace ai-backend: passed; no provider API called
- git diff --check passed; Backendにbuild/lint scriptなし; Android変更なしのためGradle再実行なし
- 実Redis EVAL、実デプロイ、実Galaxy、課金上限は未検証

Updated at: 2026-10-03T03:49:57.747124+00:00

## 3D表示修正完了（2026-10-02、v0.4.30）
- ユーザー実画面でv0.4.28/v0.4.29が旧イラストだった問題を受け、AI画面のWebViewをネイティブFilament/TextureView＋自己完結GLBへ置き換えた。旧WebView資産は削除。旧イラストへ戻す処理もAI画面から外した。
- 全7状態と停止／再開をAndroidエミュレーターで確認。実MainActivityのAIタブでTextureViewの白い本体・青い目のピクセルをassert。ウィンドウ全体のPixelCopy画像を取得し、3Dが見えることを目視確認した。
- 成功CI: Release 37047641278、画像再取得 37048272248。v0.4.30公開APK内のGLBがソースと一致し、arm64 Filament JNIが入り、旧WebView資産がないことを確認。
- GitHub Releaseに実際のAndroid画像 `mascot-ai-window.png` / `mascot-native.png` を添付。APK 141,838,635 bytes。今後の画像回収はテスト後にAPKをアンインストールせず、PNGシグネチャもassertする。
- 再生成ソースはtools/mascot-export、経緯・現行契約はdocs/MASCOT_3D_HANDOFF.md末尾。Galaxy実機のfps・発熱は未測定。上記Backend作業とPhase 0の残課題は維持。

## K10+ Pro操作修正（2026-10-03、v0.4.32）
- 家電画面のONでunknown commandになる原因は、K10+ Proへ汎用turnOnを送っていたこと。公式APIに沿ってK10+ / K10+ Proをstart / stopへ振り分けた。UIは掃除開始／停止、成功表示はコマンド受付を示す。
- 手動操作と音声のSwitchBotActionAdapterは同じSwitchBotClient.setPowerを使用。照明・Bot・赤外線ACのturnOn / turnOffを維持し、Hub拒否はResult失敗で返す。
- SwitchBotPowerCommandTestの4テストと既存unit tests、署名APKビルド、Android実AI画面の描画チェックが成功。CI 37086681373、v0.4.32公開済み。実掃除機には接続しておらず、実機の掃除開始／停止は未確認。
- edb910eのBackend owner-token対応をFF統合して保持。最新SDK/API/authの課題は上記記録を参照。
