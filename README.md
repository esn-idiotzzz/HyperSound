# HyperSound 澎湃音效

面向 **小米澎湃OS 4（HyperOS 4）** 的 LSPosed 系统音效自定义模块。
桌面直接打开 App，逐个音效选择文件、试听、恢复默认。

- 包名：`com.hypersound.mod`，模块名 `HyperSound`
- 框架：LSPosed（libxposed API 101）
- **校准机型**：Xiaomi 15（`24129PN74C`，代号 `dada`），HyperOS `OS4.0.4.0.XOCCNXM`，API 37
- 槽位全部按该机 `/system/media/audio/ui/` 的真实文件名核对

## 功能

- **30 个音效槽位**，覆盖锁屏/解锁、按键与触摸、充电与电量、截图与相机、系统提示、通话与消息、铃声与闹钟
- 顶部两个视图：**全部** / **已修改**（带数量），只看改过的音效
- 每个槽位：选择音频 → 试听 → 重置
- 音效替换默认启用、**不可关闭**（App 内没有总开关），要停用只能卸载模块\n- 有未应用的改动时，「应用更改」按钮会带上圆点提示
- 修改后「应用更改」自动重启系统界面；系统级音效（按键/触摸）需重启手机\n- 单项「恢复」和「全部恢复」都会弹二次确认；导入音频在后台线程完成，不卡界面

## 支持的音效（30 项）

| 分组 | 音效 | 对应系统文件 |
|---|---|---|
| 锁屏与解锁 | 锁屏音 / 解锁音 | `Lock.ogg` / `Unlock.ogg` |
| 按键与触摸 | 触摸·滴答 | `Effect_Tick.ogg` |
| | 按键·标准 / 空格 / 回车 / 删除 / 无效 | `KeypressStandard.ogg`、`KeypressSpacebar.ogg`、`KeypressReturn.ogg`、`KeypressDelete.ogg`、`KeypressInvalid.ogg` |
| 充电与电量 | 插入充电器 | `charging.ogg` |
| | 无线充电 | `charge_wireless.ogg` |
| | 拔出充电器 | `disconnect.ogg` |
| | 低电量 | `LowBattery.ogg`（该机此文件为 0 字节，替换后才有声音） |
| 截图与相机 | 截图 | `screenshot.ogg`、`screenshot_kr.ogg` |
| | 相机快门 | `camera_click.ogg` |
| | 相机对焦 | `camera_focus.ogg` |
| | 开始录像 / 结束录像 | `VideoRecord.ogg` / `VideoStop.ogg` |
| 系统提示 | 删除提示 | `Delete.ogg`、`SystemDelete.ogg` |
| | 操作失败 / 错误 | `SystemError.ogg` |
| | 关机提示 | `SystemTurnOff.ogg` |
| | 手电筒开关 | `flashlight.ogg` |
| | 滚轮调节 | `NumberPickerValueChange.ogg` |
| | 摇一摇切歌 | `MusicShake.ogg` |
| 通话与消息 | 通话提示 | `InCallNotification.ogg` |
| | 消息发送中 | `MessageSending.ogg` |
| | 消息已发送 | `MessageSent.ogg`、`MessageSendOut.ogg` |
| | 新消息 | `IncomingMessage.ogg` |
| 铃声与闹钟 | 通知提示音 / 闹钟 / 来电铃声 | 读 `Settings.System` 的 `notification_sound`、`alarm_alert`、`ringtone` |

匹配规则：把「文件名 / 资源名」归一化（去路径、去扩展名、转小写、去非字母数字）后精确比对。
`/system/media/audio/ui/Lock.ogg`、`/product/media/audio/ui/Lock.ogg`、`file:///.../Lock.ogg`、资源名 `lock` 都能命中。

## 安装与使用

1. 安装 `HyperSound-1.4.apk`（普通安装，不要转成系统应用）。
2. LSPosed → 模块 → **HyperSound** → 启用，勾选作用域：
   - 必选：**系统界面**（`com.android.systemui`，真机上是 `MiuiSystemUI`）
   - 要改按键/触摸/充电音：**系统框架**（`android`）——充电音由 system_server 的 Notifier 播放（`playChargingStartedFeedback`），只勾系统界面不会生效
   - 可选：`com.miui.home`、设置、电话、短信、相机、`com.miui.screenshot`、`com.android.incallui`
3. 重启手机（首次必需）。
4. 打开「澎湃音效」→ 选音效 → 选文件 → 试听 → **应用更改**。

## 工作原理

- **SystemUI**：`MiuiSystemUI.apk` 里存在 `file:///system/media/audio/ui/` 前缀、`SoundPool`、`mSoundPoolMap`，
  说明它用 SoundPool 按路径加载 UI 音效。
- **system_server**：`services.jar` 里存在 `SOUND_EFFECTS_PATH`、`Effect_Tick.ogg`、`MSG_LOAD_SOUND_EFFECTS`，
  即 AudioService 的 SoundPoolPlayer 按 `/system/media/audio/ui/Effect_Tick.ogg` 加载按键/触摸音。
- 模块 Hook 了 `SoundPool.load` 的**全部重载**（`String` / `Context+int` / `Context+Uri` / `AssetFileDescriptor`）
  和 `MediaPlayer.setDataSource` 的全部重载，命中槽位就换成你的音频。
- 音频传递两条路：
  1. `ContentProvider`（`content://com.hypersound.mod.sounds/f/<slot>`）→ 直接给 SoundPool 一个 `AssetFileDescriptor`；
  2. 兜底：App 同时把音频镜像到 `/sdcard/Android/media/com.hypersound.mod/sounds/<slot>.<ext>`，
     Hook 拿不到 Context 时直接按路径加载。
- 所有 Hook 都带 try/catch + `ExceptionMode.PROTECTIVE`，出错即回退系统原行为。

## 排错

App 右上角「日志」会记录注入结果与替换动作：

- `installed in com.android.systemui (SoundPool.load=5, MediaPlayer=...)` —— 注入成功
- `替换 lock → id=3 @com.android.systemui` —— 走 ContentProvider 成功
- `替换 lock (path) → id=3 @android` —— 走镜像路径成功
- `miss: /system/media/audio/ui/xxx.ogg` —— 命中了没覆盖的文件名

| 现象 | 处理 |
|---|---|
| 完全没生效 | 确认模块已启用、勾了「系统界面」、点过「应用更改」或重启过手机 |
| 锁屏音生效、按键/充电音没变 | 勾「系统框架」并**重启手机** |\n| 不知道作用域有没有生效 | App 顶部状态行会显示「系统界面 ✓ / 系统框架 ✓」，未注入会给出提示 |
| 某个音效变无声 | 换 ogg/wav；或点「重置」 |
| 日志无 `installed` | 该进程没被注入：作用域没勾 / LSPosed 未重启 |

## 构建

需要 JDK 17+、Android SDK `platform-35` 与 `build-tools-35`、libxposed API 的 `classes.jar`。

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

流程：`aapt2 compile/link → javac → d8 → 打包 dex 与 META-INF/xposed → zipalign → apksigner(v1+v2+v3)`，
产物默认输出到项目旁的 `output（hypersound）/`。

> 仓库里**不包含签名密钥** `hypersound.jks`。自行构建时 `build.ps1` 会在项目根目录生成一个（密码 `hypersound`），
> 用自己生成的密钥签名即可；要与本仓库 Release 的 APK 签名一致才需要原始密钥。

## 目录

```
hypersound/
├─ AndroidManifest.xml
├─ build.ps1                构建脚本
├─ meta/META-INF/xposed/    module.prop / scope.list / java_init.list
├─ res/                     布局、颜色、自适应图标
└─ src/com/hypersound/mod/
   ├─ ModuleMain.java       LSPosed 入口
   ├─ Hooks.java            SoundPool / MediaPlayer Hook + 双通道取音频
   ├─ Cfg.java              注入侧配置快照
   ├─ Slots.java            30 个槽位（真机文件名校准）
   ├─ SoundProvider.java    给注入进程提供音频与配置
   ├─ Store.java            App 侧存储 + 外部镜像
   └─ MainActivity.java     桌面 UI
```
