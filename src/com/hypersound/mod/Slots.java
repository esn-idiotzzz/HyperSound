package com.hypersound.mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统音效槽位表，按 Xiaomi 15 / HyperOS 4.0 (OS4.0.4.0.XOCCNXM) 实际文件校准：
 * /system/media/audio/ui/ 下的真实文件名见每个槽位的 sample。
 * 匹配前会归一化：取文件名、去扩展名、转小写、去掉非字母数字字符。
 */
public final class Slots {

    public static final class Slot {
        public final String key;
        public final String label;
        public final String group;
        public final String sample;

        Slot(String key, String label, String group, String sample) {
            this.key = key;
            this.label = label;
            this.group = group;
            this.sample = sample;
        }
    }

    private static final List<Slot> LIST = new ArrayList<Slot>();
    private static final Map<String, Slot> BY_KEY = new HashMap<String, Slot>();
    private static final Map<String, Slot> BY_NAME = new HashMap<String, Slot>();

    private static void add(String key, String label, String group, String sample, String... names) {
        Slot slot = new Slot(key, label, group, sample);
        LIST.add(slot);
        BY_KEY.put(key, slot);
        for (String n : names) {
            String k = norm(n);
            if (k != null && k.length() > 0 && !BY_NAME.containsKey(k)) {
                BY_NAME.put(k, slot);
            }
        }
    }

    static {
        // 锁屏与解锁
        add("lock", "锁屏音", "锁屏与解锁", "Lock.ogg",
                "lock", "lockscreen", "locksound", "screenlock", "keyguardlock");
        add("unlock", "解锁音", "锁屏与解锁", "Unlock.ogg",
                "unlock", "unlockscreen", "unlocksound", "screenunlock", "lockscreen_off");

        // 按键与触摸
        add("effect_tick", "触摸 · 滴答", "按键与触摸", "Effect_Tick.ogg",
                "effecttick", "tick", "tock");
        add("key_standard", "按键 · 标准", "按键与触摸", "KeypressStandard.ogg",
                "keypressstandard", "keystandard", "keyclick", "keypress", "keypressnormal");
        add("key_space", "按键 · 空格", "按键与触摸", "KeypressSpacebar.ogg",
                "keypressspacebar", "keyspace", "spacebar");
        add("key_return", "按键 · 回车", "按键与触摸", "KeypressReturn.ogg",
                "keypressreturn", "keyreturn", "keyenter");
        add("key_delete", "按键 · 删除", "按键与触摸", "KeypressDelete.ogg",
                "keypressdelete", "keydelete", "backspace");
        add("key_invalid", "按键 · 无效", "按键与触摸", "KeypressInvalid.ogg",
                "keypressinvalid", "keyinvalid", "keyerror");

        // 充电与电量
        add("charge", "插入充电器", "充电与电量", "charging.ogg",
                "charging", "charge", "charger", "connectpower", "powerconnect");
        add("charge_wireless", "无线充电", "充电与电量", "charge_wireless.ogg",
                "chargewireless", "wirelesscharging", "wirelesscharge", "wirelesschargingstarted");
        add("disconnect", "拔出充电器", "充电与电量", "disconnect.ogg",
                "disconnect", "unplug", "powerdisconnect", "chargedisconnect");
        add("low_power", "低电量", "充电与电量", "LowBattery.ogg",
                "lowbattery", "lowpower", "batterylow", "warning", "warrning",
                "batterywarning", "criticallowbattery", "normallowbattery");

        // 截图与相机
        add("screenshot", "截图", "截图与相机", "screenshot.ogg",
                "screenshot", "screenshots", "screenshotkr", "capture", "shot", "screenshoot");
        add("camera", "相机快门", "截图与相机", "camera_click.ogg",
                "cameraclick", "shutter", "camerasound", "camerashutter");
        add("camera_focus", "相机对焦", "截图与相机", "camera_focus.ogg",
                "camerafocus", "autofocus");
        add("video_record", "开始录像", "截图与相机", "VideoRecord.ogg",
                "videorecord", "recordstart");
        add("video_stop", "结束录像", "截图与相机", "VideoStop.ogg",
                "videostop", "recordstop");

        // 系统提示
        add("delete_global", "删除提示", "系统提示", "Delete.ogg",
                "delete", "systemdelete", "globaldelete", "deletesound");
        add("system_error", "操作失败 / 错误", "系统提示", "SystemError.ogg",
                "systemerror", "errorhint");
        add("system_turn_off", "关机提示", "系统提示", "SystemTurnOff.ogg",
                "systemturnoff", "shutdown", "poweroff", "turnoff");
        add("flashlight", "手电筒开关", "系统提示", "flashlight.ogg",
                "flashlight", "torch");
        add("number_picker", "滚轮调节", "系统提示", "NumberPickerValueChange.ogg",
                "numberpickervaluechange", "numberpicker");
        add("music_shake", "摇一摇切歌", "系统提示", "MusicShake.ogg",
                "musicshake", "shake");

        // 通话与消息
        add("in_call", "通话提示", "通话与消息", "InCallNotification.ogg",
                "incallnotification", "incall");
        add("message_sending", "消息发送中", "通话与消息", "MessageSending.ogg",
                "messagesending", "sendingsound");
        add("message_sent", "消息已发送", "通话与消息", "MessageSent.ogg",
                "messagesent", "messagesendout", "messagesentout");
        add("incoming_message", "新消息", "通话与消息", "IncomingMessage.ogg",
                "incomingmessage", "newmessage");

        // 铃声类（走 MediaPlayer + Settings；系统设置里也能改，这里方便统一替换）
        add("notification", "通知提示音", "铃声与闹钟", "Settings · notification_sound",
                "notification", "notificationsound", "notify");
        add("alarm", "闹钟铃声", "铃声与闹钟", "Settings · alarm_alert",
                "alarm", "alarmsound", "alarmalert", "dynamicalarmspeech");
        add("ringtone", "来电铃声", "铃声与闹钟", "Settings · ringtone",
                "ringtone", "miuiweekringtone");
    }

    public static List<Slot> all() {
        return Collections.unmodifiableList(LIST);
    }

    public static Slot byKey(String key) {
        return BY_KEY.get(key);
    }

    public static Slot match(String nameOrPath) {
        String n = norm(nameOrPath);
        if (n == null || n.length() == 0) {
            return null;
        }
        Slot s = BY_NAME.get(n);
        if (s != null) {
            return s;
        }
        return fuzzy(n);
    }

    /** 命名变体兜底：按关键字族匹配，避免系统改名后漏掉。 */
    private static Slot fuzzy(String n) {
        if (n.contains("wirelesscharg")) {
            return BY_KEY.get("charge_wireless");
        }
        if (n.contains("unplug") || n.contains("disconnect")) {
            return BY_KEY.get("disconnect");
        }
        if (n.contains("charg")) {
            return BY_KEY.get("charge");
        }
        if (n.contains("screenshot") || n.contains("screencapture")) {
            return BY_KEY.get("screenshot");
        }
        if (n.contains("unlock")) {
            return BY_KEY.get("unlock");
        }
        if (n.contains("lock") && !n.contains("clock") && !n.contains("block")) {
            return BY_KEY.get("lock");
        }
        if (n.contains("cameraclick") || n.contains("shutter")) {
            return BY_KEY.get("camera");
        }
        if (n.contains("videorecord")) {
            return BY_KEY.get("video_record");
        }
        if (n.contains("videostop")) {
            return BY_KEY.get("video_stop");
        }
        if (n.contains("lowbatt")) {
            return BY_KEY.get("low_power");
        }
        if (n.contains("systemerror")) {
            return BY_KEY.get("system_error");
        }
        if (n.contains("systemturnoff")) {
            return BY_KEY.get("system_turn_off");
        }
        if (n.contains("incall")) {
            return BY_KEY.get("in_call");
        }
        if (n.contains("incomingmessage")) {
            return BY_KEY.get("incoming_message");
        }
        if (n.contains("messagesending")) {
            return BY_KEY.get("message_sending");
        }
        if (n.contains("messagesent") || n.contains("messagesendout")) {
            return BY_KEY.get("message_sent");
        }
        return null;
    }

    /** 取文件名、去扩展名、小写、只保留字母数字。 */
    public static String norm(String s) {
        if (s == null) {
            return null;
        }
        int slash = s.lastIndexOf('/');
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }
        int q = s.indexOf('?');
        if (q >= 0) {
            s = s.substring(0, q);
        }
        int dot = s.lastIndexOf('.');
        if (dot > 0) {
            s = s.substring(0, dot);
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c = (char) (c + 32);
            }
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
