# Androidセットアップ

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

## K10シリーズの掃除操作

K10+ / K10+ Proは電源コマンドでは動かない。v0.4.32から家電画面では「掃除開始／停止」を表示し、掃除開始は `start`、停止は `stop` を送る。parameterは `default`、commandTypeは `command`。音声経路のON/OFF操作にも同じ機種別変換を適用する。

照明・ボット・赤外線エアコンのON/OFFは従来の `turnOn / turnOff` を維持する。停止は充電台に戻す操作ではない。

仕様の参照: [SwitchBot公式 K10+ Pro API](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/robot-vacuum/mini-robot-vacuum-k10-pro.md)。コマンド変換は回帰テストで確認し、実機の掃除開始／停止は端末側で確認する。

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


仕様上の対応と無人復旧の実測合格は別です。現状と残課題は[Phase 0計画](PHASE_0.md)を参照してください。
