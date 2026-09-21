# WiFi到着ON（WifiOnArrival）

特定のGPS座標に到着したときにWiFiをオンにするAndroidアプリです。

## 機能

- GPSで現在位置を取得
- 指定座標＋半径内に入ったらWiFiオンを試行
- 位置取得間隔（秒）を設定可能
- GPSを動かす時間帯（開始〜終了）を設定可能
- **ネットワーク通信は一切行いません**（`INTERNET` 権限なし）

## 重要な制限（Androidの仕様）

| Androidバージョン | WiFi自動ON |
|---|---|
| 9 以下 | アプリから直接ON可能 |
| 10 以降 | OS制限により直接ON不可。到着時にWiFi設定パネルを開きます |

## ビルド方法（Android Studio）

1. Android Studio をインストール
2. **File → Open** でこの `WifiOnArrival` フォルダを開く
3. 初回は Gradle 同期を待つ
4. メニュー **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. 生成された APK:
   - `app/build/outputs/apk/debug/app-debug.apk`

コマンドラインの場合:

```bash
./gradlew assembleDebug
# 出力: app/build/outputs/apk/debug/app-debug.apk
```

## インストール時の注意

- 「提供元不明のアプリ」を許可してインストール
- 初回起動時に **位置情報 → 常に許可** を選択
- Android 13+ では通知権限も許可

## 権限一覧（ネットワークなし）

- ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION
- ACCESS_BACKGROUND_LOCATION
- FOREGROUND_SERVICE / FOREGROUND_SERVICE_LOCATION
- POST_NOTIFICATIONS
- CHANGE_WIFI_STATE / ACCESS_WIFI_STATE

`INTERNET` / `ACCESS_NETWORK_STATE` は宣言していません。
