<!--
SPDX-FileCopyrightText: 2026 AcideFluorhydrique
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 最後一根蘿蔔

[![Build APK](https://github.com/AcideFluorhydrique/carrot/actions/workflows/build.yml/badge.svg)](https://github.com/AcideFluorhydrique/carrot/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

一款小巧、完全自足的路線型塔防遊戲。饞嘴的傢伙沿著菜園小徑前進，而田裡只剩最後一根蘿蔔。
放塔、升級，守住這條防線。

*[English README](README.md)*

**不索取任何權限，沒有廣告、沒有追蹤器、不連網、不夾帶任何素材檔。**

---

## 這個專案比較特別的地方

這個 repo 裡幾乎沒有素材檔案。你看到和聽到的一切都是 App 自己生出來的：

- **音訊在執行時合成。** 首次啟動時，App 用振盪器與雜訊合成 16 個音效與一段八小節的
  chiptune 循環，寫成 WAV 存進快取再播放。repo 裡沒有任何一個 `.wav` 或 `.ogg`。
- **畫面是畫出來的，不是載入的。** 地圖、防禦塔、HUD、選單全部是 Canvas 繪圖指令，
  單位用系統表情符號，啟動圖示是向量圖。

重點不是為了精簡而精簡 —— 而是這樣就沒有任何第三方內容需要授權、標示出處，或是弄錯。

## 玩法

**五個章節、二十個關卡**，每章有自己的配色與自己要解決的新問題。選好章節後沿著一條蜿蜒小徑
往前推，走過的每一站都保留當時的星等。關卡依序解鎖，星數看你剩下多少蘿蔔。

**八種防禦塔**，隨進度陸續解鎖，每一種都剛好出現在剋制它的敵人之前：

| 防禦塔 | 造價 | 定位 |
|---|---:|---|
| 🏹 箭塔 | 50 | 單體速射 |
| ❄️ 冰塔 | 60 | 對單一目標的強力減速 |
| 💣 炸彈塔 | 85 | 範圍爆破 |
| 🌙 月亮 | 75 | 大範圍輕度減速，一次抓住一整群 |
| ☠️ 毒塔 | 95 | 持續傷害，無視護甲 |
| 🚀 火箭 | 120 | 直線飛行，貫穿沿途所有目標 |
| ⚡ 電塔 | 130 | 在鄰近敵人之間跳躍的連鎖閃電 |
| ☀️ 太陽 | 110 | 以自身為圓心的環狀脈衝 |

每座塔可升三級、賣出退回 65%，還能指定優先打路徑最前、血量最強或距離最近的敵人。
**每關只開放其中四到五座塔**，讓同一批工具能組出不一樣的題目。

**五種敵人** —— 普通的蘿蔔賊、脆皮但飛快的飛毛腿、數量驚人的蟲群、硬扛直接傷害的鐵甲獸，
以及一旦突破就會狠咬蘿蔔一口的菜園霸主。

**可摧毀的障礙物。** 地圖上散落著石頭、大樹、蘑菇、冰塊與寶箱。點一下指定集火，你的塔
**只會在射程內沒有敵人時**去啃它，所以清障永遠不會害你漏怪。打掉會給金幣，還空出一格
可以蓋塔。太陽、月亮和火箭會自己掃到障礙物，不用特別指定。

另外還有：1x/2x/3x 變速、提前催下一波換獎勵金、拖曳定位放塔並在落塔前看清射程、
以及自動存檔，打到一半可以隨時放下。

## 建置

**需求：** JDK 17，以及 Android SDK（compileSdk 34）。

Gradle wrapper 有提交進 repo，所以你不需要另外安裝 Gradle：

```bash
./gradlew assembleDebug
```

APK 會產生在 `app/build/outputs/apk/debug/app-debug.apk`。如果你本機已經有 Gradle 8.2 以上，
直接 `gradle assembleDebug` 也可以 —— CI 就是這樣跑的。

請用 **JDK 17**，不要用更新的版本：Gradle 8.2 只支援跑在 Java 19 以下。
把 wrapper 升到 Gradle 8.5 以上就沒有這個限制。

> **關於 wrapper JAR。** CI 會拿它跟 Gradle 官方公布的校驗碼比對，所以它必須是官方發行版。
> 如果你要重新產生，請用官方的 Gradle 發行版執行 `wrapper` 任務 ——
> Linux 發行版套件庫裡的 `gradle` 常常是重新打包過的舊版本，校驗碼對不上，建置會被擋下來。

## 專案結構

全部的程式碼都在 `app/src/main/java/io/github/acidefluorhydrique/carrot/`。沒有依賴注入、
沒有架構框架，除了 AndroidX core 與 appcompat 之外沒有任何第三方函式庫 ——
就是一個 `SurfaceView` 加一條遊戲執行緒，還有一堆單純的類別。

| 檔案 | 職責 |
|---|---|
| `GameView`、`GameThread` | 遊戲迴圈、畫面切換、觸控處理 |
| `GameLevel`、`Chapter` | 關卡與章節資料、難度曲線、主題配色 |
| `Tower`、`TowerManager`、`Bullet`、`PiercingShot` | 防禦塔、選敵、投射物 |
| `Enemy`、`EnemyManager`、`EnemyKind` | 敵人、波次、生成 |
| `Obstacle`、`ObstacleManager` | 可摧毀的障礙物 |
| `SoundEngine` | 執行時音訊合成 |
| `Effects` | 粒子、飄字、螢幕震動 |
| `MenuRenderer`、`HudRenderer`、`Widgets` | 所有 UI 繪製 |
| `GameSave` | 存檔 |
| `Strings`、`LocaleManager`、`Ui` | 在地化與解析度無關的尺寸 |

關卡資料是用腳本產生的：套用平滑的難度曲線，逐關驗證路徑的連通性、是否自我交叉、
可建塔格數是否足夠，然後輸出 Kotlin。直接手改 `GameLevel.kt` 也行，
但如果想重新調整曲線，重新產生會輕鬆很多。

## 翻譯

介面內建英文、正體中文與簡體中文，可在遊戲內切換，與系統語言無關。

要新增語言，把 `app/src/main/res/values/strings.xml` 複製到帶語系限定詞的新目錄再翻譯即可。
中文使用書寫系統限定詞，這樣香港與澳門才會正確回退：

```
app/src/main/res/values/               英文（預設）
app/src/main/res/values-b+zh+Hant/     正體中文
app/src/main/res/values-b+zh+Hans/     簡體中文
```

有兩件事一定要顧到：**所有語系的字串鍵必須完全一致**，以及**位置式格式參數
（`%1$d`、`%2$s` …）必須對得上** —— 翻譯漏掉或改了編號會在執行時直接崩潰。
最後把你的語系加進 `LocaleManager.options`，它才會出現在遊戲內的語言選單裡。

## 授權

GPL-3.0-or-later，詳見 [LICENSE](LICENSE)。原始碼檔案都帶有 SPDX 標頭。

## 與任何作品無關

這是一款原創遊戲。它從啟發它的路線型塔防類型借用的只有類型本身，
與任何商業作品沒有關聯、未經其背書，也不是從中衍生而來。
