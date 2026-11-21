package com.hypersound.mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统音效槽位表，按 Xiaomi 15 / HyperOS 4.0 真机实际播放的文件校准。
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
        add("lock", "锁屏音", "锁屏与解锁", "Lock.ogg",
                "lock", "lockscreen", "locksound", "screenlock", "keyguardlock");
        add("unlock", "解锁音", "锁屏与解锁", "Unlock.ogg",
                "unlock", "unlockscreen", "unlocksound", "screenunlock", "lockscreen_off");

        add("charge", "插入充电器", "充电与电量", "charging.ogg",
                "charging", "charge", "charger", "connectpower", "powerconnect");
        add("charge_wireless", "无线充电", "充电与电量", "charge_wireless.ogg",
                "chargewireless", "wirelesscharging", "wirelesscharge", "wirelesschargingstarted");
        add("disconnect", "拔出充电器", "充电与电量", "disconnect.ogg",
                "disconnect", "unplug", "powerdisconnect", "chargedisconnect");
        add("low_power", "低电量", "充电与电量", "LowBattery.ogg",
                "lowbattery", "lowpower", "batterylow", "warning", "warrning");

        add("screenshot", "截图", "截图", "screenshot.ogg",
                "screenshot", "screenshots", "screenshotkr", "capture", "shot");

        add("message_sent", "消息已发送", "消息", "MessageSent.ogg",
                "messagesent", "messagesendout", "messagesentout");
        add("incoming_message", "新消息", "消息", "IncomingMessage.ogg",
                "incomingmessage", "newmessage");

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
        if (n.contains("lowbatt")) {
            return BY_KEY.get("low_power");
        }
        if (n.contains("incomingmessage")) {
            return BY_KEY.get("incoming_message");
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
