# CODEX_STATE

Status: blocked
Goal: GalaxyでPhase 0を検証し、音声会話・家電操作の品質と応答時間を確かめる。

## Done
- df8dd99までの応答時間境界・対象名保持・操作確認修正を確認。7fcfd94で失敗していたBackend回帰を含む最新AI backend・Web/browser・Android buildの3 CIがsuccessとなり、以前のCI障害を解除。今回テスト自体は再実行していない。
- 最新fd37d8f（v0.4.36）のホームmascot/TextureView復帰修正を確認。最新headのRelease APK・Android buildはGitHub CIでsuccess。追加実装は保存Nextの実DB/実機検証に不要。
- v0.4.28のローカル3Dマスコット実装と設計引き継ぎを確認した。
- HTTP JSON入力を共通化。UTF-8バイト数で制限し、分割された日本語入力を保持する。
- 不正JSON・null/配列・過大入力を400/413でAPI呼び出し前に拒否する。ローカルHTTP/Vercelの4経路へ適用。

- v0.4.31: local HTTP/Vercel 4 POST経路へshared owner Bearer gateを追加。未設定503・不正/旧token401を本文解析/provider呼出し前に拒否。healthはpublicのまま。
- 端末設定のtokenは既存Keystore AES/GCMへ保存。空欄維持/明示削除/各requestで再読込。HTTPS＋loopbackのみ送信、redirect追従停止、401/403は再設定案内。provider keyをAPKへ入れていない。

- 共有Redis RESTのatomic EVALでUTC日/固定1分窓の受付上限を追加。設定不足/不明/障害503、超過429+retryAfterSeconds。全4 local HTTP/serverless POSTへprovider前に適用。auth rotationではカウンタをresetしない。
- 例外・cause・provider本文・入力/headerをログへ出さず固定eventのみ記録。未知API/音声エラー・比較provider失敗を固定文言へ秘匿。timeout工程/待機時間は維持。
- 模擬共有storeで日/分境界・拒否非加算・並行claims・設定/障害拒否を検証。4 serverlessの超過時provider未呼出しとlocal HTTP未設定拒否を確認。
- 最新mainのv0.4.32/v0.4.33家電対応・native3D更新を保持。今回Androidコード変更なし。

- 最新d43e30eのNeon対応を確認。上限管理の関連6テストが成功。公開health HTTP200と未認証dispatch HTTP401を確認した。認証設定未設定503の以前の状態は現在のdispatch probeでは再現しない。

## Current
- df8dd99のBackend/Web/Android CIは合格。最新AGENTSの暗号化IndexedDB保存・Android pairing方針を維持。runtime223にDB/owner資格情報なし、adb端末なし。公開health200/未認証dispatch401。残る実DB境界・Galaxy受入は未完了。
- fd37d8fまでの外部変更は描画/UI/リリース関連で、保存Nextの実DB境界・Galaxy受入を解決する新しい証跡はない。runtime191は接続済みだがDB/owner資格情報なし、adb接続端末なし。公開health200/未認証dispatch401は維持。
- Redisに加えてNeonのatomic上限管理が実装され、最新setupには本番Neon12並行要求・日次上限・拒否非加算の実測記録がある。今回の司令塔は関連fixture6/6と公開health/未認証拒否を独立確認した。DB資格情報がなく実DB境界/障害注入は再検証できず、Galaxy/Phase0C受入は未完了。

## Next
- 実Deployment/共有storeへの接続が利用可能になったら、設定されているRedis EVALまたはNeon SQLの複数Function/process共有・期限境界・store障害のfail-closedを検証する。最新setupのNeon実測記録を使い、同じ確認を不要に繰り返さない。
- Galaxyでowner token保存/再起動/失効/再入力と429/503表示を確認し、Phase0CのWAN/LAN断・Backend停止・72時間受入を進める。

## Blockers
- この司令塔に実DB接続文字列・owner token・Deployment設定接続がない。最新setupのNeon実測記録とは別に、期限境界/store障害・provider側課金制限の実受入は未確認。fixture成功を実DB/課金上限の合格に置き換えない。
- Galaxy実機・USBマイクは未接続。端末Keystore persistenceと実API費用/応答測定、故障注入は未検証。

## Verification
- df8dd99 latest-head review: AI backend CI37198990171, Android CI37198990179, Web/browser CI37198990200 success; prior Jev simple_chat CI failure resolved. Runtime223 current with no configured bindings; scoped DB/owner credentials absent; adb devices empty. No physical/real-store acceptance claimed
- Public health HTTP200 and unauthenticated dispatch HTTP401. Real DB/physical Galaxy probes cannot run; no broad tests or provider requests repeated
- fd37d8f latest-head read-only review: Release APK/Android build CI success; runtime191 current with secrets/runtime variables/outbound identities empty; scoped DB/owner credentials and local config absent; adb devices empty
- Public health HTTP200 and unauthenticated dispatch HTTP401. Real DB/physical Galaxy probes cannot run; no broad tests or provider requests repeated
- Latest d43e30e request-limits.test.mjs: 6/6 passed (synthetic Redis/database claims); locked Backend dependencies installed without scripts; no source/dependency changes
- Public GET /api/health HTTP200; unauthenticated empty POST /api/dispatch HTTP401 unauthorized, before request parsing/claims/providers
- Runtime revision164 has no configured secret bindings; scoped DB/owner environment variables and local config files absent. Real DB tests not rerun
- npm test --workspace ai-backend: 67/67 passed; synthetic stores/gateways only
- npm run benchmark:plan --workspace ai-backend: passed; no provider API called
- git diff --check passed; Backendにbuild/lint scriptなし; Android変更なしのためGradle再実行なし
- Redis EVAL/Neon期限境界・障害注入、実Galaxy、課金上限はこの司令塔では未検証。公開Backendはhealth/未認証拒否だけを確認

Updated at: 2026-10-04T12:34:59.600535+00:00

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

## 全機種コマンド監査・キャラ動作（2026-10-03、v0.4.33）
- SwitchBot公式84資料のControl CommandsとDevice Listを確認。docs/SWITCHBOT_COMMAND_AUDIT.mdに型・コマンド・パラメーター・未対応の扱いを記録。機器一覧とコマンド表の型名差（Curtain3/K11+/K20 Plus Pro）にも対応。
- SwitchBotControlProfilesがUI・手動・音声の共通の根拠。掃除機の世代別start/stopとstartClean/pause、Blind Tilt、Botモードを分ける。startCleanのparameterは文字列ではなくJSONObjectとしてシリアライズする。センサー／鍵／Hub／未知機種／チャンネル未指定Relay 2PM／IR Othersは汎用ON/OFFを送らない。Bot pressはOFFなし、音声は押下の確認が必要。
- キャラはOSアニメーション倍率0でも待機時に小さく揺れ、発話時は口・体を動かす。画面外停止は維持。IDLE/SPEAKINGの実画像3枚ずつで色差ピクセルをassertし、連続画像を目視確認済み。
- unit tests、署名APK build、Android送信JSON実型2テスト、ネイティブ描画・停止復帰・実AI画面・待機発話画像差分テストがsuccess。CI 37093764878。v0.4.33公開、mascot-motion.png添付。実家電にはコマンドを送っていないため物理動作は未確認。

## Web版・継続リリース（2026-10-03、v0.4.34）
- 新規Web/PWAクライアントを実装。同じGLBと明確な待機/発話アニメーション、日英独PCM入力、Luna/Jev会話、比較、天気、設定、SwitchBot機種別操作と音声操作の確認、ブラウザー内アラーム。
- Androidの機種別タイプ一覧からWebサーバー用リストを生成。SwitchBot proxyはowner認証・共有利用制限・fresh inventory検証・固定provider URL・署名・業務statusCode検査を行い、未知機種や不正対象を拒否する。比較は操作しない。
- WebのversionはAndroid gradleから、commitはVercel Gitメタデータから生成。既存main Git連携を利用してVercelで公開。WebKit/Chromium CIと本番version/commit検査を追加し、今後のAPK releaseもWeb検証・ZIP添付を必須化した。AGENTS.mdにも両クライアントを同時に維持する要件を記載。
- オーナー/SwitchBot認証はブラウザーsessionStorageのみ。永続設定・アラームはこのブラウザー内だけ。Webはボタンで録音開始、バックグラウンドalarm/常時wake/Android USB選択/端末間同期は非対応で画面に明示。
- Backend fixture 74件、PCM単体2件成功。主要9シナリオはWebKit/Chromium両方で成功（実GLBピクセル変化、TTSイベント連動、認証失敗、設定保持、家電確認、比較非実行、音声経路、拒否後復帰、更新案内、アラーム保存）。旧cache削除/資産保存とChrome offline再読込は別途確認。WebKitのoffline模擬navigationはSWの前で失敗するため実iPhoneofflineは未合格。
- 音声/HTTP fixtureはsyntheticで実iPhoneマイク・TTS・SwitchBot実機・provider品質の合格ではない。公開BackendのAI_BACKEND_TOKEN未設定503とRedis設定の実受入は残る。
- アプリ内アップデーターはAPKをcacheDir/updatesに保存するのでMy Files検索に出ない。Galaxyのアプリ設定からキャッシュ削除で除去できる。Android自動cleanupはこのWeb変更に含めていない。
