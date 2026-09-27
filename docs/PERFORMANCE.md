# 応答性能の比較

**応答性能はクリティカル。平均だけで合格にしない。現在、実API比較結果は未測定。**

現行PoCの既定モデルはGPT-6 Luna（route/simple_chat）とGPT-6 Sol（deep_reasoning、high）。`ROUTER_MODEL` / `REASONING_MODEL`がデプロイ環境で上書きされていないことを`/api/health`で確認してから比較する。AndroidのLuna / Jev回答比較はGPT-6 Luna経路とJev経路の同一音声認識文を比較し、各カードに実際に返ったモデルIDと総時間を表示する。

AIラボの「5.6 / 6比較」はGPT-5.6 Luna/SolとGPT-6 Luna/Solを同じ入力で並列実行し、同じ推論設定で通常dispatch全体を比較する。サーバー総時間、Android端末往復、route、回答モデル、回答本文を各試行で表示し、同じ入力での完了試行からサーバー総時間差の中央値を出す。差は`GPT-6 − GPT-5.6`で、負値はGPT-6が速い。ボタン1回につきAPIを2回呼ぶ。比較中、家電やアラームは実行しない。STT / TTSを含む音声全体の速度は別途測る。

## 比較する経路

| 経路 | 意図 |
|---|---|
| luna-first | GPT-6 Luna 1回で初段判定。simple_chatは同一呼び出し、deep_reasoningだけGPT-6 Sol high |
| jev-first | Jev 1回でrouteと閉じたパラメータを判定。simple_chatはGPT-6 Luna、deep_reasoningはGPT-6 Sol high、物理系はAndroid |
| sol-medium | チャットケースをSolへ直結し、ルーターの待ち時間と品質差を評価 |
| sol-high | 同じチャットケースをhighで比較 |

初段比較UIは同じSTT最終文をLuna分類とJev分類へ並列送信し、routerMsの差を見る。物理操作は比較中に実行しない。
ソル直結の結果を物理操作用に使わない。Liveと音声STT/TTSは別のPhase 0B試験。
分類の正解と回答品質は別。AIの自己申告confidenceを正解確率として使わない。

## CLI

```sh
cd ai-backend
npm test
npm run benchmark:plan
# 実APIキーを安全な環境変数へ設定してから。実行すると課金される。
npm run benchmark
```

既定は21ケース×3巡（チャットだけ追加3経路）=117サンプル。
BENCHMARK_ROUNDSは1〜20。初期3巡はスモークであり、信頼できるp95を推定する十分な標本ではない。
集計は経路ごと。日本語・英語・ドイツ語・用途別の分析にはsamples.jsonlを使う。
失敗時間を捨てず別表示。firstInProcessは「その経路のプロセス内初回」でありVercel cold startの証明ではない。
benchmark-results/へsamples.jsonlとsummary.jsonを保存し、Git管理から除外する。
回答とusageを記録するため、共有前に個人情報を点検する。

実モデル比較はこのCLIで実施。HTTP経路はAndroid UIの端末往復時間を別測定。
CLIはバックエンド完了時間のみで、音声応答開始・物理動作開始・Vercelネットワーク時間を含まない。

## 実装済みの計測点

| 記号 | イベント | 時計 |
|---|---|---|
| t0 | 発話終了／Android SpeechRecognizerの終端 | Android monotonic |
| t1 | STT最終結果 | Android monotonic |
| t2 | Backend dispatch開始 | Android monotonic |
| t3 | Intent routing完了 | Backend monotonic |
| t4 | 後段処理開始 | Android monotonic |
| t5 / t6 | Device state取得開始／完了 | Android monotonic |
| t7 | Action Resolver完了 | Android monotonic |
| t8 | Policy完了 | Android monotonic |
| t9 / t10 | Device Adapter開始／結果受信 | Android monotonic |
| t8 (server) / t9 (server) | LLM first token／生成完了 | Backend monotonic |
| t10 / t11 | 回答組み立て開始／完了 | AndroidまたはBackend monotonic |
| t11 / t12 / t13 | TTS要求／再生開始／再生完了 | Android monotonic |

各レスポンスの共通timingsにはSTT、判定前後待機、ルーティング、状態取得、Resolver、Policy、Device実行、回答開始待ち、TTFT、TTFT後生成、回答組み立て、TTS開始待ち、TTS準備、判定後合計、総時間、未配賦を格納する。比較画面は同じ列定義でLuna / Jev / `Jev - Luna`を表示する。`—`は工程なし／未計測、`0ms`は実測0。

排他的区間だけを足し、親区間を重ねない。TTFTは回答生成全体と重なるので、生成時間は呼出全体からTTFTを差し引いた区間として記録する。負値や合計超過は丸めて隠さず`timingError`へ出す。

AndroidとBackendのclock originは異なる。timestampは`client_*`と`server_*`に分け、requestIdで照合する。比較のdevice_actionはstate → Resolver → Policyまでdry-runし、機器実行時間は実行しないので`—`。同様に比較画面はTTSを実行しないためTTS時間も`—`。通常音声セッションでは実際のTTS lifecycleを測る。

比較画面の1回分は端末での実測だが、1回だけで性能結論を出さない。同一文・同一端末で複数回比較し、将来はmedian / p50 / p95へ集計する。オフラインテストの時間は実API性能として扱わない。

## 初期の合格目標（実測値ではない）

| 項目 | 目標 |
|---|---|
| 明確な文字指示のIntent＋全引数 | 言語別98%以上 |
| 曖昧・否定・訂正・再送 | 固定評価セットで意図しない変更と重複0件 |
| 通常会話 t7−t0 | 中央値1.5秒以内、p95 3秒以内 |
| 単純な家電の物理動作 t7−t0 | p95 2秒以内。受付成功と区別 |
| ローカル検出→再生停止 | p95 250ms以内 |
| アラーム | 対象ID・保存内容・予約内容の一致。故障時もローカル発音 |

守れない場合はまずSTT終端・不要なLLM二重呼出し・TTS全量待ち・音声バッファを確認。simple_chatではJev-firstが直列で1呼び出し増える点を必ず分離して見る。
モデルの変更、上位モデルへの昇格率、発話長は同じ条件で比較。失敗率が高い経路を速度だけで採用しない。

## 費用・品質

usageのinput/output/reasoning/cached tokensを実請求単価で集計。Liveセッション時間、STT、TTS、Vercelも加算。
CLIはusageを記録するが、料金の自動換算はしない。単価を固定して誤った月額を出さない。
操作1回、成功した回答1回、会話10分、1日分で比較する。
回答品質はモデル名を伏せ、正しさ・会話文脈・言語維持・簡潔さを評価。
最終的にGalaxyと移住先回線、常設端末で再測定し、日付・OS・モデル・設定を残す。
