# Solへの引き継ぎ：Tatsuマスコット3D設計

設計引き継ぎ時点の記録。実装はSolが担当する。
その後のユーザー指示により実装・公開済み。現行のAndroid描画は末尾のv0.4.30を参照。WebViewに関する契約は旧実装の記録。

## 採用する方向

- 既存PNGの大きな丸い頭、長い垂れ耳、青い楕円の目、ピンクの頬、小さなw口、胸元で合わせた手、渦巻き尻尾を維持。鼻やリアルな動物の顔は足さない。
- 可愛さを優先した白いぬいぐるみ。最終モデルは短い毛並みの法線・控えめなAO・粗さを焼き込んだ軽量GLBを推奨。今回の手続き的な試作は形状・照明・質感の参考であり、完成モデルの品質上限ではない。
- Astraが初期設計と試作を作り、再開後に耳・尻尾・目・布の質感をレビューした。以下の修正版を採用する。実機検証済み・完璧との扱いにはしない。

## 初期草案からの修正（こちらを優先）

- 耳：折れを避けた下向きの曲線。根元相対の中心線は (0,0,0),(.30,-.03,-.02),(.55,-.20,-.04),(.72,-.43,-.02),(.80,-.68,.01),(.79,-.97,.035)。左右反転し、閉じた端と滑らかな法線を持たせる。
- 尻尾：中心(-.86,.77,-.20)、半径.31→.045、角度-.20→-.20+2.35π、管半径.070。内側の巻きを胴体の外に見せ、取っ手に見せない。
- 目：半径(.13,.174,.030)、頭の表面からのオフセット.004。色#269cd2、roughness .38、clearcoat .22 / roughness .35。白いハイライトを保ち、宝石のような突出と光沢を抑える。
- 布：色#f9f5ef、roughness .96、metalness 0、sheen .65、sheenRoughness 1。試作は法線マップのnormalScale(.16,.20)、repeat(2,2)。最終版は等方的な粒ノイズを完成扱いせず、短い繊維らしい凹凸を焼き込む。汚れた色ノイズは足さない。
- 腹の独立したプレート状パーツを外す。手の中心xは±.185に寄せ、胸で合わせる。
- 詳細な修正版の参照コード：`mascot3d-design/reference-study.js`。設計資料であり、アプリにそのまま組み込む実装ではない。依存Three.jsは同梱していない。

## 実装契約

- オフライン描画。Three.js r160相当のWebGL1対応、ローカル素材のみ。通常224dp、狭い端末では縮小。24fps上限、DPR≤1.5、描画辺≤640px、目標35draw calls/35k triangles以内。実機測定で確かめる。
- 待機は小さな呼吸・まばたき、聞き取りは少し身を寄せる、考え中は小さな首傾げ、発話は控えめな口・耳の動き。PREPARING/ANSWER_READY/ERRORもVoicePhaseで分ける。発話の動きは音素リップシンクとは呼ばない。
- ネイティブ側に状態表示・操作・音声を残す。画面外・バックグラウンドで描画停止。初期化失敗・WebGL喪失時は既存PNGへ戻す。外部通信・任意JSブリッジは禁止。入力は列挙型とbooleanだけ。
- 初期化のタイムアウトはonPageFinished未到達でも効かせる。破棄後のAPIは何もしない。画面復帰時に時刻をリセット。低モーション設定では連続動作を停止し、状態のポーズは反映する。

## 確認済みと残る検証

Astraの試作を正面・左右20度・大きめの首傾げ・224px表示で確認した。Sol側でも正面、右20度、首傾げ画像を確認し、目・頬の配置とシルエットが保たれていることを確認した。

確認画像：
- `mascot3d-design/front-study-640.png`
- `mascot3d-design/front-study-224.png`
- `mascot3d-design/left20-study-640.png`
- `mascot3d-design/right20-study-640.png`
- `mascot3d-design/pose-extreme-study-640.png`

画像はスタジオ照明・影付きの形状／質感試作。低負荷の製品版やAndroidの動作を証明するものではない。最終GLBの布の質感、全7状態の動きと遷移、まばたき、ライフサイクル、失敗時のPNG復帰、実機fps・発熱はSolの実装時に検証する。

数値の基礎仕様は `MASCOT_3D_DESIGN.md` を参照。草案と食い違う場合は上の修正版と確認画像を優先する。

## v0.4.28実装と検証

Solが修正版の造形を実際の3Dメッシュとして実装。専用GLBは同梱せず、同じ比率・材質のローカルメッシュを生成する。布の法線マップ、滑らかな耳、渦巻き尻尾、低い目、胸で合わせた手、7状態の動き・まばたき・口の動き付き。最終GLBの推奨は資産制作の方向として残す。

ブラウザで全7状態、停止／再開、不正な状態名の拒否、低モーションでの連続描画停止、WebGL喪失での停止と透明化、破棄後の再起動防止を確認。リソースはローカルCSS・JS・Three.jsのみ。通常22,210 triangles/20draw calls、発話23,426/21。描画640px上限。

Android側はネイティブの状態表示・操作を維持し、ライフサイクル／画面内表示で停止、初期化10秒上限・応答2秒上限・WebGL／描画プロセス喪失でPNG復帰を実装。AndroidビルドはCIで確認する。実機のfps・発熱・WebViewライフサイクルの操作確認は未測定。

## v0.4.30：実際のAI画面の表示修正

v0.4.28とv0.4.29はユーザーのAI画面で旧イラストのままだった。前回の単独WebViewのJavaScript起動テストは、Compose内の実際の画面に3Dが見えることを確認できていなかった。WebViewの合成経路を原因と断定はしない。今回はその表示経路を外し、TextureView上のネイティブFilament/OpenGL描画へ置き換えた。

- `mascot-native/tatsu.glb`：314,408 bytes、全7状態のループ、呼吸・まばたき・耳・口の動き。外部リソースなし。造形とアニメーションの再生成ソースは `../tools/mascot-export/` に保存。
- `NativeMascotView.kt`：24fps上限、状態遷移、画面外／非アクティブ時の停止、再開、破棄。低モーション設定は静止ポーズ。音素リップシンクではない。
- `TatsuMascot3D.kt`：準備中／失敗時のみ同じ3Dモデルのレンダリング画像を表示。AI画面では旧イラストを使わない。失敗時に再試行を表示。
- 実際のMainActivityからAIタブへ移動し、TextureViewの画像に非透明の白い本体と青い目があることをassert。全7状態、停止／再開もAndroidテストで確認。JavaScriptのready通知を合格条件にしない。
- CIは廃止された `swiftshader_indirect` でFXAAシェーダーがコンパイル失敗したため、サポートされる `swangle` を使う。テストを通ったv0.4.30のAPK内のGLBがソースと一致すること、arm64のFilamentライブラリが入っていること、旧WebView資産がないことも確認。

Androidエミュレーターの描画検証はGalaxy実機のfps・発熱・GPU互換性の測定とは区別する。端末固有の性能は未測定。確認画像はGitHub Release v0.4.30の `mascot-ai-window.png` と `mascot-native.png` に保存する。画像保存時はテスト後もAPKを保持し、PNGシグネチャの確認を公開条件にする。

## v0.4.33：待機／発話の見える動き

ユーザーから静止して見えるとの報告を受け、OSのANIMATOR_DURATION_SCALEが0だと連続描画を止めていた処理を外した。AI画面が表示されている間は待機でも描画を続け、ネイティブ側で小さな上下・左右の揺れを加える。発話では揺れを少し大きくし、GLBのOpeningノードの口を周期的に開閉する。音素リップシンクではない。画面外／バックグラウンドの停止は維持。

Androidテストはシステム設定0の状態でIDLEとSPEAKINGを各3枚取得し、非透明部分で色差のあるピクセルが80点を超えることをassertする。描画回数だけを根拠にしない。実際の連続画像mascot-motion.png（上段待機、下段発話、左から時間順）をReleaseに保存し、目視で揺れと発話時の変化も確認。CI 37093764878はsuccess。

### v0.4.34 — visibly stronger native motion

Idle: body bob 0.10 scene units, roll ±5°, yaw ±4°, head yaw ±8° and nod ±5°, independent ears ±12°. Speaking: roll ±8°, head nod ±9°, ears ±16°, substantially larger mouth opening. Native transforms override clip joint poses without frame allocations. Temporary TextureView detach pauses; reattachment resumes without destroying Filament. Permanent release remains AndroidView.onRelease.

Android release gate captures 12 real texture frames per state at 150ms intervals and publishes looping idle/speaking GIFs. Pixel change requirement scales with image area; temporary parent detach/reattach must resume frames. Existing phase, activity resume and actual AI page checks remain.

Auto STT now sends the encrypted owner token just like chat. Public transcription probe returned `backend_auth_not_configured`: deployment requires AI_BACKEND_TOKEN and shared request-limit configuration; app motion changes do not resolve missing server environment variables.
