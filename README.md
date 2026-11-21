# HyperSound 澎湃音效

面向 **小米澎湃OS 4（HyperOS 4）** 的 LSPosed 系统音效自定义模块。
桌面直接打开 App，逐个音效选择文件、试听、恢复默认。

- 包名：`com.hypersound.mod`，模块名 `HyperSound`
- 框架：LSPosed（libxposed API 101）
- **校准机型**：Xiaomi 15（`24129PN74C`），HyperOS `OS4.0.4.0.XOCCNXM`，API 37
- 只保留真机上**确认有播放者**的 14 个音效

## 支持的音效（14 项）

| 分组 | 音效 | 系统文件 | 播放进程 |
|---|---|---|---|
| 锁屏与解锁 | 锁屏音 | `Lock.ogg` | com.android.systemui |
| | 解锁音 | `Unlock.ogg` | com.android.systemui |
| 充电与电量 | 插入充电器 | `charging.ogg` | com.miui.securitycenter |
| | 无线充电 | `charge_wireless.ogg` | com.miui.securitycenter |
| | 拔出充电器 | `disconnect.ogg` | com.miui.securitycenter |
| | 低电量 | `LowBattery.ogg` | 系统（该文件在真机上为 0 字节） |
| 截图 | 截图 | `screenshot.ogg` | com.miui.screenshot |
| 通话与消息 | 通话提示 | `InCallNotification.ogg` | com.android.systemui |
| | 消息发送中 | `MessageSending.ogg` | com.android.mms |
| | 消息已发送 | `MessageSent.ogg` | com.android.mms |
| | 新消息 | `IncomingMessage.ogg` | com.android.mms |
| 铃声与闹钟 | 通知提示音 | `Settings.System · notification_sound` | 各 App + MediaPlayer |
| | 闹钟铃声 | `Settings.System · alarm_alert` | 各 App + MediaPlayer |
| | 来电铃声 | `Settings.System · ringtone` | 各 App + MediaPlayer |

匹配规则：把「文件名 / 资源名」归一化（去路径、去扩展名、转小写、去非字母数字）后比对，
并带关键字族兜底（`charg*`、`*unplug*`、`*disconnect*` 等），系统改名也能命中。

## 已移除的槽位（真机上没有播放者 / 无法区分）

- **系统提示组**：删除提示、系统错误、关机提示、手电筒、滚轮调节、摇一摇切歌
- **相机相关**：相机快门、对焦、开始/结束录像 —— 澎湃OS 相机统一走
  `AudioManager.playSoundEffect(0)`（系统点击音），无法按文件区分，且与触摸音共用
- **按键与触摸组**：触摸滴答、按键标准/空格/回车/删除/无效 —— 与系统「触摸提示音」共用
  `Effect_Tick.ogg`，改它会同时影响所有点击音，故整组移除

## 安装与使用

1. 安装 `HyperSound-3.0.apk`（普通安装）。
2. LSPosed → 模块 → **HyperSound** → 启用，勾选作用域（推荐 7 个）：
   `android`、`com.android.systemui`、`com.miui.securitycenter`、`com.miui.screenshot`、
   `com.android.mms`、`com.android.camera`、`com.android.settings`
3. 重启手机（首次必需）。
4. 打开「澎湃音效」→ 选音效 → 选文件 → 「应用更改」。
   **应用更改会自动重启相关进程**（系统界面、安全中心、截图、相机、短信、设置等），
   并让 AudioService 重新加载音效，不需要你手动打开这些 App。

## 排错

点右上角「日志」：

- `● installed in <进程>` —— 该进程已注入
- `替换 xxx` —— 已替换成功
- `seen xxx` —— 系统请求了该音效、模块也识别了，但你还没给它设音效
- `miss: xxx.ogg` —— 系统请求了但模块不认识这个文件名

> 只有「设置过」的槽位才会替换；没设置的保持系统原声。

## 构建

需要 JDK 17+、Android SDK `platform-35` 与 `build-tools-35`、libxposed API 的 `classes.jar`。

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

产物默认输出到项目旁的 `output（hypersound）/`。仓库不含签名密钥，构建时会自动生成。
