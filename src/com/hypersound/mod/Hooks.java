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
 * Hook 实现：在 SystemUI / 安全中心 / 短信 / 截图等进程里，把系统加载的音效换成模块 App 里的文件。
 * 所有逻辑都做了兜底，任何异常都会退回系统原始行为。
 */
public final class Hooks {

    private static final String TAG = "HyperSound";

    /** App 额外镜像的目录，拿不到 Context 时按路径加载。 */
    private static final String MIRROR_DIR =
            "/storage/emulated/0/Android/media/com.hypersound.mod/sounds";

    private static XposedModule module;
    private static String proc = "?";
    private static volatile Context appCtx;
    private static boolean installed;

    private static long lastPush;
    private static final Set<String> once = Collections.synchronizedSet(new HashSet<String>());
    private static final ThreadLocal<Boolean> REPLACING = new ThreadLocal<Boolean>();

    private static volatile Map<String, File> mirrorCache;
    /** 出错过多时自动停用，避免极端情况下拖垮宿主进程。 */
    private static volatile boolean disabled;
    private static int failCount;
    private static volatile long mirrorAt;

    private Hooks() {
    }

    public static synchronized void install(XposedModule m, String process, ClassLoader cl) {
        if (installed || "com.hypersound.mod".equals(process)) {
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
        for (Method m : SoundPool.class.getDeclaredMethods()) {
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
        for (Method m : MediaPlayer.class.getDeclaredMethods()) {
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

    private static final class LoadHook implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            if (disabled || Boolean.TRUE.equals(REPLACING.get())) {
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
                noteFail(t, "load");
            }
            return chain.proceed();
        }
    }

    private static final class PlayerHook implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            if (disabled || Boolean.TRUE.equals(REPLACING.get())) {
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
                noteFail(t, "media");
            }
            return chain.proceed();
        }
    }

    private static Slots.Slot matchResource(Context c, int resId) {
        try {
            return Slots.match(c.getResources().getResourceEntryName(resId));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 铃声/通知/闹钟走 Settings 里的 content:// URI，需要比对设置值。 */
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

    /** 拿到一个可用的 Context：优先 Application，其次系统上下文。 */
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

    static void noteFail(Throwable t, String where) {
        logOnce(where + " err: " + t);
        if (++failCount > 15 && !disabled) {
            disabled = true;
            log("too many errors, hooks disabled in " + proc);
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

    /** 注入状态：回写到 App 日志。 */
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

    /** 系统请求了但没匹配到槽位的音效名。 */
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

    /** 识别出槽位但还没设置自定义音。 */
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

    private static boolean looksAudio(String name) {
        String s = name.toLowerCase();
        return s.endsWith(".ogg") || s.endsWith(".mp3") || s.endsWith(".wav")
                || s.endsWith(".m4a") || s.endsWith(".flac");
    }

    /** 关键事件回写 App 日志区。 */
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
}
