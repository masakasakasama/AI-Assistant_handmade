# Tatsu Home

会話と、家の操作をひとつに。Androidを常設ハブにする個人用AIスマートスピーカー。

Google Home / Alexaの全機能コピーではなく、自然な会話、SwitchBot、正確なアラーム管理、天気、日・英・独を優先します。応答速度は最優先の品質条件です。

## 開発方針

**Phase 0は手持ちGalaxyで、AIの回答から音声出力・操作結果までを検証する。合格まで音響HW・中古タブレットは買わない。**

- 通常経路：STT → Luna 1回 → 必要時のみSol → TTS。通常の読み上げも中断可能にする。
- 簡単な回答は分類と同じLuna呼び出しで生成。深い質問のSolはhighを既定に維持し、mediumは速度・費用比較用に評価する。
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
| 性能比較CLI | 日英独21ケース・4経路。初期データセットはスモーク用 |
| Galaxy STT / Android TTS / 音声キャンセル | v0.4.0で縦切り実装。v0.4.2で音声言語選択・言語別STT切替・セッション無効化・診断表示を追加。実Galaxy評価前 |
| OpenAI STT比較 / Live | 後続のPhase 0比較対象 |
| 音声→家電／アラームの安全な実行 | 次のPhase 0実装対象 |
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
cd ai-backend
npm test
npm run benchmark:plan
# APIキーを安全に設定した環境でのみ実行。実API料金が発生する。
npm run benchmark
```

Android：JDK 17、Android SDK 35、Gradle 8.11.1で `gradle :app:assembleDebug`。
PRのGitHub ActionsでもAndroidをビルドします。mainの自動デプロイ前に、Backend認証・利用予算制限を完成させてください。
