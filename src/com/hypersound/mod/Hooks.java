package com.hypersound.mod;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.provider.Settings;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 实际的 Hook 实现：在“系统界面 / 系统框架”等进程里，把系统要加载的音效替换成模块 App 里的文件。
 * 全部逻辑都做了兜底：任何异常都会退回系统原始行为。
 */
public final class Hooks {

    private static final String TAG = "HyperSound";

    /** App 会把音效额外镜像到这里（无需权限、任何进程都能按路径读）。 */
    private static final String MIRROR_DIR =
            "/storage/emulated/0/Android/media/com.hypersound.mod/sounds";

    private static XposedModule module;
    private static String proc = "?";
    private static volatile Context appCtx;
    private static int ctxTries;
    private static boolean installed;

    private static long lastPush;
    private static final ThreadLocal<Boolean> REPLACING = new ThreadLocal<Boolean>();
    private static final Set<String> once = Collections.synchronizedSet(new HashSet<String>());

    private static volatile Map<String, File> mirrorCache;
    private static volatile long mirrorAt;

    /** 需要注入的进程。 */
    private static final String[] TARGETS = {
            "com.android.systemui",
            "com.miui.home",
            "com.android.settings",
            "com.android.phone",
            "com.android.dialer",
            "com.android.contacts",
            "com.android.mms",
            "com.miui.screenshot",
            "com.android.camera",
            "com.android.incallui",
    };

    private Hooks() {
    }

    public static boolean isTarget(String pkg) {
        if (pkg == null) {
            return false;
        }
        for (String t : TARGETS) {
            if (t.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized void install(XposedModule m, String process, ClassLoader cl) {
        if (installed) {
            return;
        }
        module = m;
        proc = process;
        try {
            int loads = hookSoundPool();
            int mps = hookMediaPlayer();
            hookAppContext();
            installed = true;
            log("installed in " + process + " (SoundPool.load=" + loads + ", MediaPlayer=" + mps + ")");
            status("installed in " + process);
        } catch (Throwable t) {
            log("install error: " + t);
        }
    }

    private static int hookSoundPool() {
        int n = 0;
        Method[] methods = SoundPool.class.getDeclaredMethods();
        for (Method m : methods) {
            if (!"load".equals(m.getName())) {
                continue;
            }
            try {
                module.hook(m)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(new LoadHook());
                n++;
            } catch (Throwable t) {
                log("hook " + m + " failed: " + t);
            }
        }
        return n;
    }

    private static int hookMediaPlayer() {
        int n = 0;
        Method[] methods = MediaPlayer.class.getDeclaredMethods();
        for (Method m : methods) {
            if (!"setDataSource".equals(m.getName())) {
                continue;
            }
            try {
                module.hook(m)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(new PlayerHook());
                n++;
            } catch (Throwable t) {
                log("hook " + m + " failed: " + t);
            }
        }
        return n;
    }

    /** 尽早拿到 Application 的 Context，供没有 Context 参数的 load(String) 使用。 */
    private static void hookAppContext() {
        try {
            Method m = android.app.Application.class.getDeclaredMethod("attach", Context.class);
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            try {
                                Object a = chain.getArg(0);
                                if (a instanceof Context) {
                                    appCtx = (Context) a;
                                }
                            } catch (Throwable ignored) {
                            }
                            return chain.proceed();
                        }
                    });
        } catch (Throwable t) {
            log("hook Application.attach failed: " + t);
        }
    }

    /** SoundPool.load 的所有重载共用一个拦截器。 */
    private static final class LoadHook implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            if (Boolean.TRUE.equals(REPLACING.get())) {
                return chain.proceed();
            }
            try {
                Object a0 = chain.getArg(0);
                Slots.Slot slot = null;
                if (a0 instanceof String) {
                    String path = (String) a0;
                    slot = Slots.match(path);
                    if (slot == null && path.length() > 0 && path.indexOf("/audio/") >= 0) {
                        miss(path);
                    }
                } else if (a0 instanceof Context) {
                    Context c = (Context) a0;
                    if (appCtx == null) {
                        appCtx = c;
                    }
                    Object a1 = chain.getArgs().size() > 1 ? chain.getArg(1) : null;
                    if (a1 instanceof Integer) {
                        slot = matchResource(c, (Integer) a1);
                    } else if (a1 instanceof Uri) {
                        slot = matchUri(c, (Uri) a1);
                    }
                }
                if (slot != null) {
                    Object r = replaceLoad(slot, chain.getThisObject());
                    if (r != null) {
                        return r;
                    }
                }
            } catch (Throwable t) {
                logOnce("load err: " + t);
            }
            return chain.proceed();
        }
    }

    /** MediaPlayer.setDataSource 的所有重载共用一个拦截器。 */
    private static final class PlayerHook implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            if (Boolean.TRUE.equals(REPLACING.get())) {
                return chain.proceed();
            }
            try {
                Object a0 = chain.getArg(0);
                Slots.Slot slot = null;
                if (a0 instanceof String) {
                    String path = (String) a0;
                    slot = Slots.match(path);
                    if (slot == null && path.indexOf("/audio/") >= 0) {
                        miss(path);
                    }
                } else if (a0 instanceof Context) {
                    Context c = (Context) a0;
                    if (appCtx == null) {
                        appCtx = c;
                    }
                    Object a1 = chain.getArgs().size() > 1 ? chain.getArg(1) : null;
                    if (a1 instanceof Uri) {
                        slot = matchUri(c, (Uri) a1);
                        if (slot == null) {
                            miss(a1.toString());
                        }
                    }
                }
                if (slot != null) {
                    if (replacePlayer(slot, chain.getThisObject())) {
                        return null;
                    }
                }
            } catch (Throwable t) {
                logOnce("media err: " + t);
            }
            return chain.proceed();
        }
    }

    private static Slots.Slot matchResource(Context c, int resId) {
        try {
            String name = c.getResources().getResourceEntryName(resId);
            return Slots.match(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 铃声/通知/闹钟走 Settings 里的 content:// URI，名字里没线索，需要比对设置值。 */
    private static Slots.Slot matchUri(Context c, Uri uri) {
        Slots.Slot slot = Slots.match(uri.getLastPathSegment());
        if (slot == null) {
            slot = Slots.match(uri.getPath());
        }
        if (slot != null) {
            return slot;
        }
        String s = uri.toString();
        try {
            android.content.ContentResolver cr = c.getContentResolver();
            String ring = Settings.System.getString(cr, Settings.System.RINGTONE);
            if (ring != null && ring.equals(s)) {
                return Slots.byKey("ringtone");
            }
            String notify = Settings.System.getString(cr, "notification_sound");
            if (notify != null && notify.equals(s)) {
                return Slots.byKey("notification");
            }
            String alarm = Settings.System.getString(cr, "alarm_alert");
            if (alarm != null && alarm.equals(s)) {
                return Slots.byKey("alarm");
            }
            // 充电提示音（system_server Notifier 用 Settings.Global 里的 URI 播放）
            String chg = Settings.Global.getString(cr, "charging_started_sound");
            if (chg != null && chg.equals(s)) {
                return Slots.byKey("charge");
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object replaceLoad(Slots.Slot slot, Object pool) {
        Context c = context();
        if (c != null) {
            AssetFileDescriptor afd = null;
            try {
                if (!Cfg.get(c).has(slot.key)) {
                    seen(slot);
                    return null;
                }
                if (Cfg.get(c).has(slot.key)) {
                    afd = c.getContentResolver().openAssetFileDescriptor(Cfg.uriSound(slot.key), "r");
                    if (afd != null) {
                        int id;
                        REPLACING.set(Boolean.TRUE);
                        try {
                            id = ((SoundPool) pool).load(afd, 1);
                        } finally {
                            REPLACING.remove();
                        }
                        if (id > 0) {
                            push("替换 " + slot.key + " → id=" + id + " @" + proc);
                            return Integer.valueOf(id);
                        }
                        log("load(" + slot.key + ") returned " + id);
                    }
                } else {
                    return null;
                }
            } catch (Throwable t) {
                logOnce("provider fail " + slot.key + ": " + t);
            } finally {
                if (afd != null) {
                    try {
                        afd.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        // 兜底：直接读镜像文件（system_server 等拿不到 Context 时也能用）
        File m = mirrorFile(slot.key);
        if (m != null) {
            try {
                int id;
                REPLACING.set(Boolean.TRUE);
                try {
                    id = ((SoundPool) pool).load(m.getAbsolutePath(), 1);
                } finally {
                    REPLACING.remove();
                }
                if (id > 0) {
                    push("替换 " + slot.key + " (path) → id=" + id + " @" + proc);
                    return Integer.valueOf(id);
                }
            } catch (Throwable t) {
                logOnce("path load fail " + slot.key + ": " + t);
            }
        }
        return null;
    }

    private static boolean replacePlayer(Slots.Slot slot, Object mp) {
        Context c = context();
        if (c != null) {
            AssetFileDescriptor afd = null;
            try {
                if (!Cfg.get(c).has(slot.key)) {
                    seen(slot);
                    return false;
                }
                if (Cfg.get(c).has(slot.key)) {
                    afd = c.getContentResolver().openAssetFileDescriptor(Cfg.uriSound(slot.key), "r");
                    if (afd != null) {
                        REPLACING.set(Boolean.TRUE);
                        try {
                            ((MediaPlayer) mp).setDataSource(afd.getFileDescriptor(),
                                    afd.getStartOffset(), afd.getLength());
                        } finally {
                            REPLACING.remove();
                        }
                        push("替换 " + slot.key + " (MediaPlayer) @" + proc);
                        return true;
                    }
                } else {
                    return false;
                }
            } catch (Throwable t) {
                logOnce("provider mp fail " + slot.key + ": " + t);
            } finally {
                if (afd != null) {
                    try {
                        afd.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        File m = mirrorFile(slot.key);
        if (m != null) {
            try {
                REPLACING.set(Boolean.TRUE);
                try {
                    ((MediaPlayer) mp).setDataSource(m.getAbsolutePath());
                } finally {
                    REPLACING.remove();
                }
                push("替换 " + slot.key + " (MediaPlayer/path) @" + proc);
                return true;
            } catch (Throwable t) {
                logOnce("path mp fail " + slot.key + ": " + t);
            }
        }
        return false;
    }

    private static File mirrorFile(String key) {
        long now = System.currentTimeMillis();
        Map<String, File> m = mirrorCache;
        if (m == null || now - mirrorAt > 1500) {
            HashMap<String, File> map = new HashMap<String, File>();
            File[] fs = new File(MIRROR_DIR).listFiles();
            if (fs != null) {
                for (File f : fs) {
                    String n = f.getName();
                    int dot = n.indexOf('.');
                    if (dot > 0) {
                        map.put(n.substring(0, dot), f);
                    }
                }
            }
            m = map;
            mirrorCache = map;
            mirrorAt = now;
        }
        File f = m.get(key);
        return (f != null && f.isFile() && f.length() > 0 && f.canRead()) ? f : null;
    }

    /** 拿到一个可用的 Context：优先系统上下文，其次当前 Application。 */
    static Context context() {
        Context c = appCtx;
        if (c != null) {
            return c;
        }
        synchronized (Hooks.class) {
            c = appCtx;
            if (c != null) {
                return c;
            }
            if (ctxTries >= 3) {
                return null;
            }
            ctxTries++;
            try {
                Class<?> at = Class.forName("android.app.ActivityThread");
                Object thread = callStatic(at, "currentActivityThread");
                if (thread != null) {
                    Object sys = call(thread, at, "getSystemContext");
                    if (sys instanceof Context) {
                        appCtx = (Context) sys;
                        return appCtx;
                    }
                    Object app = call(thread, at, "getApplication");
                    if (app instanceof Context) {
                        appCtx = (Context) app;
                        return appCtx;
                    }
                }
            } catch (Throwable t) {
                logOnce("context fail: " + t);
            }
        }
        return null;
    }

    private static Object callStatic(Class<?> cls, String name) throws Exception {
        Method m = cls.getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(null);
    }

    private static Object call(Object obj, Class<?> cls, String name) throws Exception {
        Method m = cls.getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(obj);
    }

    static void log(String msg) {
        try {
            if (module != null) {
                module.log(4, TAG, "[" + proc + "] " + msg);
            }
        } catch (Throwable ignored) {
        }
    }

    static void logOnce(String msg) {
        if (once.size() > 120) {
            return;
        }
        if (once.add(msg)) {
            log(msg);
        }
    }

    /** 把关键事件回写到 App 的日志区，方便在手机上排查。 */
    private static void push(String msg) {
        log(msg);
        long now = System.currentTimeMillis();
        if (now - lastPush < 500) {
            return;
        }
        lastPush = now;
        if ("android".equals(proc)) {
            return;
        }
        Context c = appCtx != null ? appCtx : context();
        if (c == null) {
            return;
        }
        try {
            ContentValues v = new ContentValues();
            v.put("line", "[" + proc + "] " + msg);
            c.getContentResolver().insert(Uri.parse("content://com.hypersound.mod.sounds/log"), v);
        } catch (Throwable ignored) {
        }
    }

    /** 注入状态：拿不到 appCtx 时也尽量回写一次，App 用它显示作用域是否生效。 */
    private static void status(String msg) {
        log(msg);
        Context c = appCtx != null ? appCtx : context();
        if (c == null) {
            return;
        }
        try {
            ContentValues v = new ContentValues();
            v.put("line", "\u25cf " + msg);
            c.getContentResolver().insert(Uri.parse("content://com.hypersound.mod.sounds/log"), v);
        } catch (Throwable ignored) {
        }
    }

    private static boolean looksAudio(String name) {
        String s = name.toLowerCase();
        return s.endsWith(".ogg") || s.endsWith(".mp3") || s.endsWith(".wav") || s.endsWith(".m4a") || s.endsWith(".flac");
    }

    /** 音效被系统请求且识别出槽位，但用户还没设置自定义音：记一条，便于自检覆盖率。 */
    private static void seen(Slots.Slot slot) {
        if (once.size() > 200 || !once.add("seen:" + slot.key)) {
            return;
        }
        log("seen " + slot.key);
        Context c = appCtx != null ? appCtx : context();
        if (c == null) {
            return;
        }
        try {
            ContentValues v = new ContentValues();
            v.put("line", "seen " + slot.key + "  @" + proc);
            c.getContentResolver().insert(Uri.parse("content://com.hypersound.mod.sounds/log"), v);
        } catch (Throwable ignored) {
        }
    }
    /** 系统请求了但没匹配到槽位的音效名，回写到 App 日志（去重）。 */
    private static void miss(String path) {
        String base = path;
        int slash = path.lastIndexOf(47);
        if (slash >= 0) {
            base = path.substring(slash + 1);
        }
        if (!looksAudio(base)) {
            return;
        }
        if (once.size() > 200 || !once.add("miss:" + base)) {
            return;
        }
        log("miss: " + path);
        Context c = appCtx;
        if (c == null) {
            return;
        }
        try {
            ContentValues v = new ContentValues();
            v.put("line", "miss: " + base + "  @" + proc);
            c.getContentResolver().insert(Uri.parse("content://com.hypersound.mod.sounds/log"), v);
        } catch (Throwable ignored) {
        }
    }
}
