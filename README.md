# HyperSound 澎湃音效

面向 **小米澎湃OS 4（HyperOS 4）** 的 LSPosed 系统音效自定义模块。
桌面打开即可替换系统音效，支持试听与恢复默认。

- 包名：`com.hypersound.mod`，模块名 `HyperSound`，版本 4.0
- 框架：LSPosed（libxposed API 101）
- 校准机型：Xiaomi 15（`24129PN74C`），HyperOS `OS4.0.4.0.XOCCNXM`，API 37
- 只保留真机上**已确认有播放者**的 11 个音效

## 支持的音效（11 项）

| 分组 | 音效 | 系统文件 | 播放进程 |
|---|---|---|---|
| 锁屏与解锁 | 锁屏音 | `Lock.ogg` | com.android.systemui |
| | 解锁音 | `Unlock.ogg` | com.android.systemui |
| 充电与电量 | 插入充电器 | `charging.ogg` | com.miui.securitycenter |
| | 无线充电 | `charge_wireless.ogg` | com.miui.securitycenter |
| | 拔出充电器 | `disconnect.ogg` | com.miui.securitycenter |
| | 低电量 | `LowBattery.ogg` | 系统（该机此文件为 0 字节） |
| 截图 | 截图 | `screenshot.ogg` | com.miui.screenshot |
| 消息 | 消息已发送 | `MessageSent.ogg` | com.android.mms |
| | 新消息 | `IncomingMessage.ogg` | com.android.mms |

## 推荐作用域（5 个）

```
android                     低电量等系统级音效
com.android.systemui        锁屏 / 解锁 / 通知
com.miui.securitycenter     充电 / 无线充 / 拔出
com.miui.screenshot         截图
com.android.mms             消息
com.android.deskclock       闹钟
com.android.incallui        来电铃声
```

## 已移除的槽位（真机上无播放者或无法区分）

- 按键与触摸整组（与系统「触摸提示音」共用 `Effect_Tick.ogg`）
- 相机快门 / 对焦 / 录像（澎湃OS 相机统一走 `playSoundEffect`，无法按文件区分）
- 系统提示组（删除提示、系统错误、关机、手电筒、滚轮、摇一摇）
- 通话提示（由 SystemUI 的来电界面播放，场景不可控）
- 消息发送中（短信 App 实际不走该文件）
- 来电铃声 / 闹钟（系统设置里可直接改，模块不重复做）


## 使用

1. 安装 `HyperSound-4.0.apk`
2. LSPosed → HyperSound → 启用，勾选上面 5 个作用域
3. 重启手机（首次必需）
4. 打开 App → 选音效 → 选文件 → **应用更改**
   - 应用更改会自动重启相关进程（系统界面、安全中心、截图、短信）并刷新系统音效，无需手动打开这些 App

## 排错（右上角「日志」）

- `● installed in <进程>`：该进程已注入
- `替换 xxx`：替换成功
- `seen xxx`：系统请求了、模块识别了，但你还没给这一项设音效
- `miss: xxx.ogg`：系统请求了但模块不认识这个文件名

> 只有设置过的槽位才会替换，未设置的保持系统原声。

## 构建

需要 JDK 17+、Android SDK `platform-35`、`build-tools-35`、libxposed API 的 `classes.jar`。

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

产物输出到项目旁的 `output（hypersound）/`；仓库不含签名密钥，构建时自动生成。
