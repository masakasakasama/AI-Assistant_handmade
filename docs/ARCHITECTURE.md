# Tatsu Home アーキテクチャ

更新: 2026-09-21。実装済み・予定・実測を混同しない。

## 比較する通常経路

```text
A: Luna-first
音声 → STT → Luna
  ├ 家電 / アラーム / 天気 → Android
  ├ simple_chat → Lunaの同一応答
  ├ deep_reasoning → Sol high
  └ clarify → Lunaの同一応答

B: Jev-first
音声 → STT → Jev
  ├ 家電 / アラーム / 天気 → Android
  ├ simple_chat → Luna
  ├ deep_reasoning → Sol high
  └ clarify → ローカル定型確認
```

Jevは自由文を生成しない。1回のSystem One呼び出しでrouteに加え、家電action・goal・既知対象・16〜30℃・アラームaction・既知対象・新旧の時分を閉じたChoiceとして並列判定する。Jevは明示ActionとGoalを区別し、「暑い」は`goal=cooler, action=null, executionMode=resolve`として返す。
AndroidのAction Resolverは現在状態・対象一意性・能力・温度範囲を使ってActionPlanを決め、Policy Engineが実行可否を最終検査し、SwitchBot AdapterだけがAPIを呼ぶ。JevやLunaからDevice Adapterへ直接つながない。

SwitchBotの赤外線エアコンはOpenAPIから現在状態を読めない。状態不明の相対要求は推測操作せずfallback／確認にし、赤外線エアコンへの「暑い」の自動温度変更は行わない。物理デバイスの状態が得られた場合だけ、1℃刻みの相対変更を解決する。
simple_chatはLuna-firstが1回、Jev-firstがJev＋Lunaの2回になるため、Jev-firstが必ず速いわけではない。deep_reasoningと物理系は初段ルーター差がそのまま短縮候補になる。

## 回答比較

Luna-firstとJev-firstへ同じ確定済みSTT文脈を並列送信し、回答本文・route・モデル・端末往復時間・requestIdを個別に表示する。両結果は別々に保持し、一方の失敗や遅延で他方の回答を消さない。比較要求はキャンセル可能で、各経路は60秒で終了する。
比較モードではLuna/Jevの各経路についてAndroid側Action ResolverとPolicyまでdry-runし、同じ機器への重複コマンドを防ぐためSwitchBot／AlarmManager実行層へ入らない。家電・アラームrouteはActionPlanを含む実行前提案として表示する。weatherもAndroidにある同一キャッシュを参照し、比較だけのために追加取得しない。速度差だけから回答品質の優劣を決めない。

各経路で共通`AiPipelineTimings`を使う。サーバー時計とAndroidのmonotonic clockは別の名前空間で診断し、跨いだ絶対時刻の差を計算しない。TTFTはモデル呼び出し全体に重ねて足さず、TTFT後の生成時間だけを排他的区間として扱う。比較モードは音声回答を生成しないためTTS行は`—`。
比較完了後の通常Luna／Jev音声セッションは、新しい世代IDを発行して前の比較要求・TTSを破棄する。古い応答・TTS callbackが新しいセッションを更新しない。

## Conversation Mode

GPT-Liveを任意起動。通常のSTT/LLM/TTSと並行して二重にマイクを消費しない。
Tatsu Backendでセッションを作成し、対応するWebRTC経路を検証する。
Liveの委譲は同じ操作実行層へ。家電状態・アラーム・確認依頼・操作履歴を別管理にしない。
明示終了、無操作終了、セッション時間上限を設け、音声とバックエンド課金を別集計。
Liveを使わなくても通常の読み上げ停止は実装する。

## 操作の権限境界

- LLMは操作案のみ。confidenceは未校正であり実行の根拠にしない。
- AndroidがdeviceId/alarmId、対応機能、範囲、現在状態、要求の有効期限を確認。
- 候補ゼロ／複数なら対象を確認。確認可能な具体ActionPlanは30秒だけ保持し、「はい」後には取得可能な機器状態を再取得して前回状態から変化していないことを確認する。対象不明・操作不明のプランは確認肯定だけで実行しない。
- 発話の途中結果で操作しない。「26度、いや24度」の確定を待つ。
- 最初は一発話一変更。複合指示を黙って一部実行しない。
- 現実装は端末メモリ内で30秒間、同一内容の操作再送を抑止し、確認用ActionPlan IDも一度だけ受け付ける。再起動をまたぐ冪等記録・外部APIの厳密なexactly-onceは未実装。
- HTTPタイムアウトは失敗確定ではない。SwitchBot受付／状態確認済み／成否不明を分ける。
- 停止後や会話切替後に届く古い結果はgenerationIdで破棄。

## Androidとローカルアラーム

RoomとAlarmManagerは単一トランザクションではない。予約状態との照合・修復を設ける。
alarmId維持、繰り返し全体と一回の例外、複数候補、Direct Boot、Exact権限失効を検証。
現在の実装は時分とrepeatMask中心であり、日付付き単発指定や例外管理の完成を主張しない。
現在のExact権限なしの不正確な予約へのフォールバックは、UIで精度低下を知らせるまで本番合格にしない。
Asia/Tokyo / Europe/Berlin、DSTの欠落／重複時刻、現地追従／固定タイムゾーンを仕様化する。
アラームはAndroid内蔵音で鳴動可能にし、Wi-Fiスピーカーのみへ依存しない。
手持ちGalaxyでの成功は中古タブレットの保証ではない。最終端末でも72時間と再起動試験を行う。

## Phase 1音響

XVF3800とXIAO ESP32-S3はI2S、AndroidとはWi-Fi。USB直結は今回の基本案にしない。
WakeNetは16kHz入力に合わせる。Hey Tatsuは学習モデル作成を要する別課題。既存語で先に検証。
Wakeは待機から会話への入口。会話中はWakeなしの発話検出、終了後はローカル待機へ戻る。
再生経路：Android → Wi-Fi → ESP32 → XVF3800の再生入力／AEC参照経路 → 有線スピーカー。
ファームウェア・チャンネル・クロック・参照経路・同時発話時の性能を実測する。
前方バッファ、番号付き音声フレーム、上限付きjitterバッファ、再生位置報告、即時flush、認証を実装。
AECはテレビ音声除去の保証ではなく、Full Duplexや割り込み制御の代替でもない。

## Backend / 障害 / セキュリティ

Vercelは短いdispatch・セッション作成の候補。長時間音声の必須中継点にしない。
Function最大時間・cold start・再接続・保存状態を考慮。プロセスメモリだけへ状態を置かない。
OpenAIキーはBackendのみ。端末認証・失効、日月予算、モデルと出力量制限、Live接続数制限が公開前の必須条件。
現HTTPエンドポイントの認証・レート制限は未実装。APIキーを設定した無防備な公開デプロイは禁止。
SwitchBot秘密情報はKeystore鍵で暗号化。トークンと未編集の音声・プロンプトを公開ログへ残さない。
API停止→手動操作、ネット断→ローカルアラーム、音声端末断→Android発音。
ネット復帰時に古い物理操作を自動再実行しない。天気キャッシュは取得時刻と古さを示す。

## 公式仕様

- https://developers.openai.com/api/docs/models/gpt-5.6-luna
- https://developers.openai.com/api/docs/models/gpt-5.6-sol
- https://developers.openai.com/api/docs/guides/live
- https://developer.android.com/reference/android/speech/RecognizerIntent
- https://developer.android.com/privacy-and-security/direct-boot
- https://vercel.com/docs/functions/websockets
- https://wiki.seeedstudio.com/respeaker_xvf3800_introduction/
