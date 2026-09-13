# Tatsu Home

Google Home / Nestの代替を狙う、自作AIスマートホーム用Androidアプリ

## 今回のMVP

- SwitchBot OpenAPI v1.1接続
- 登録済みSwitchBotデバイス一覧取得
- ON / OFF操作
- 赤外線エアコンの電源、温度、モード操作
- アラームの作成、繰り返し、ON / OFF、削除
- 再起動、時刻変更、タイムゾーン変更後のアラーム再登録
- Token / SecretはAndroid Keystoreで暗号化保存
- 画面常時点灯
- 700dp以上ではタブレット向け2カラムUI
- スマホでは1カラムUI
- AI Voice PoC用の領域を確保

## 方針

最初はGalaxy S26 Ultraで使う
会話モデル、SwitchBot、アラームを先に検証し、合格後にXVF3800などの音響ハードを追加する

最終的にタブレット常設する場合も同じアプリを使い、
AI、家電、アラーム、天気などを1画面に集約する

## SwitchBot設定

SwitchBotアプリからDeveloper Optionsを開き、Open TokenとSecret Keyを取得する

アプリ右上の「設定」からToken / Secretを登録すると、SwitchBot OpenAPI v1.1でデバイス一覧を取得する

認証情報はリポジトリには保存しない

## Android Studio

- JDK 17
- compileSdk 35
- minSdk 28
- Kotlin + Jetpack Compose

Android Studioでこのリポジトリを開いてGradle Sync後、appを実行する

## 次の実装

1. OpenAI音声モデル比較用Voice Lab
2. 音声からSwitchBot / アラームをTool Calling
3. 天気カード
4. アラーム編集
5. XVF3800 + XIAO ESP32S3音声ストリーム
6. Kiosk / Dedicated Device化
7. 72時間常時稼働試験

## 注意

アラームはAndroid 12以降で「アラームとリマインダー」の正確なアラーム権限がない場合、現在のMVPではinexactへフォールバックする
本番利用前に権限導線を追加する
