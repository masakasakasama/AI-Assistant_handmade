# Tatsu Home

Google Home / Nestの代替を狙う、自作AIスマートホーム用Androidアプリ

## 現在のMVP

- SwitchBot OpenAPI v1.1接続
- 登録済みSwitchBotデバイス一覧取得
- 一般デバイスのON / OFF
- 赤外線エアコンの電源、温度、モード、風量を一括設定
- SwitchBot Token / SecretをAndroid Keystoreで暗号化保存
- アラームの作成、編集、繰り返し、ON / OFF、削除
- Room DBでアラーム永続化
- Exact Alarm権限導線
- 再起動、時刻変更、タイムゾーン変更後のアラーム再登録
- AI / SwitchBot停止時も既存アラームはローカル動作
- 天気常時表示
- 天気キャッシュによるオフライン時の前回値表示
- 天気地点を緯度・経度で変更可能
- ネットワーク復旧時にSwitchBotと天気を自動再同期
- 画面常時点灯
- スマホ1カラム / 700dp以上のタブレット2カラムUI
- Android Homeアプリ候補として登録可能
- Device Owner時にLock Task Modeへ自動移行
- GitHub Releaseの更新を12時間ごとに自動確認
- 新版APKのダウンロードとAndroidインストーラー起動
- CIでDebug APKを自動ビルド
- 固定署名用のRelease workflowを用意済み

## 方針

最初はGalaxy S26 Ultraで使う

AI会話、SwitchBot、アラームを先に検証し、合格後にXVF3800などの音響ハードを追加する

最終的にタブレット常設する場合も同じアプリを使い、AI、家電、アラーム、天気、更新管理を1画面に集約する

## SwitchBot API設定

SwitchBot OpenAPI v1.1を使用する

SwitchBotアプリ V9.0以降では

1. Profile
2. Preferences
3. About
4. App Versionを10回タップ
5. Developer Options
6. Get Token
7. Open TokenとSecret Keyを取得

Tatsu Home右上の「設定」にOpen Token / Secret Keyを入力すると、保存直後にGET /v1.1/devicesで接続確認を兼ねたデバイス同期を行う

認証情報はGitHubには保存せず、端末のAndroid Keystoreに暗号化して保存する

API認証は公式v1.1方式の

- Authorization
- sign
- t
- nonce

を使用し、signはtoken + timestamp + nonceをSecret KeyでHMAC-SHA256してBase64化する

エアコンは公式のsetAllコマンドを使用する

parameter形式

`temperature,mode,fanSpeed,power`

例

`26,2,1,on`

## アラーム

- Room DBに永続化
- AlarmManagerのExact Alarmを使用
- Android 12以降では初回起動時にExact Alarm権限画面を表示
- BOOT_COMPLETEDで再登録
- TIMEZONE_CHANGED / TIME_SETでも再計算
- 日本からドイツへ移動しても端末のローカルTimezoneに追従

## アプリ更新

通常のGalaxy PoCでは

1. GitHub Releaseを12時間ごとに確認
2. 新版があればアプリ画面に表示
3. APKをダウンロード
4. Android標準インストーラーを開く
5. OSが要求する場合のみユーザー確認

最終Dedicated DeviceではDevice Owner化して無人更新を検証する

Release APKは毎回同じ署名鍵が必要

GitHub Actions Secretsに以下を設定する

- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

その後GitHub ActionsのRelease APK workflowを手動実行し、versionを入力すると署名済みAPKとGitHub Releaseを作成する

## Dedicated Device / タブレット常設

アプリはHOME Intentを持つため、AndroidのデフォルトHomeアプリとして選択できる

完全なKiosk運用では初期化した専用端末でDevice Owner化する

例

```bash
adb shell dpm set-device-owner com.tatsu.homehub/.admin.AdminReceiver
```

Device Ownerの場合、アプリ起動時に自身をLock Task許可対象へ登録してKiosk Modeへ入る

## Android Studio

- JDK 17
- compileSdk 35
- minSdk 28
- Kotlin + Jetpack Compose
- Room 2.8.5
- KSP

Android Studioでリポジトリを開いてGradle Sync後、appを実行する

CIもmainへのpushごとにassembleDebugを実行し、Debug APKをArtifactとして保存する

## 未実装 / 実機待ち

- OpenAI音声モデル比較用Voice Lab
- 音声からSwitchBot / アラームへのTool Calling
- GPT音声会話
- XVF3800 + XIAO ESP32S3音声ストリーム
- Wake Word
- AEC / Full Duplex実測
- Device Owner環境での完全無人アップデート実測
- 72時間連続稼働試験
- 実SwitchBotアカウントでのAPI疎通試験
