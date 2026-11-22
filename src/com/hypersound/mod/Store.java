package com.hypersound.mod;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 模块 App 侧的存储：音频文件 + SharedPreferences。 */
public final class Store {

    public static final String PREFS = "hs_cfg";
    private static final String K_VER = "ver";
    private static final String K_APPLIED = "applied";
    private static final String P_FILE = "f_";
    private static final String P_NAME = "n_";

    private Store() {
    }

    /** 设备加密存储：开机解锁前也可用，SystemUI 启动早期就能读到。 */
    static Context de(Context c) {
        try {
            return c.createDeviceProtectedStorageContext();
        } catch (Throwable t) {
            return c;
        }
    }

    public static File dir(Context c) {
        File d = new File(de(c).getFilesDir(), "sounds");
        if (!d.exists()) {
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        }
        return d;
    }

    private static volatile boolean migrated;

    public static SharedPreferences prefs(Context c) {
        SharedPreferences p = de(c).getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!migrated) {
            migrated = true;
            try {
                migrate(c, p);
            } catch (Throwable ignored) {
            }
        }
        return p;
    }

    /** 老版本数据在凭据加密存储里，升级后一次性搬到设备加密存储。 */
    private static void migrate(Context c, SharedPreferences dePrefs) {
        if (dePrefs.getBoolean("migrated", false)) {
            return;
        }
        SharedPreferences ce = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = dePrefs.edit();
        for (java.util.Map.Entry<String, ?> en : ce.getAll().entrySet()) {
            Object v = en.getValue();
            if (v instanceof String) {
                e.putString(en.getKey(), (String) v);
            } else if (v instanceof Integer) {
                e.putInt(en.getKey(), (Integer) v);
            } else if (v instanceof Boolean) {
                e.putBoolean(en.getKey(), (Boolean) v);
            }
        }
        try {
            File from = new File(c.getFilesDir(), "sounds");
            File to = new File(de(c).getFilesDir(), "sounds");
            if (!to.exists()) {
                to.mkdirs();
            }
            File[] fs = from.listFiles();
            if (fs != null) {
                for (File src : fs) {
                    File dst = new File(to, src.getName());
                    if (dst.exists()) {
                        continue;
                    }
                    FileInputStream in = new FileInputStream(src);
                    FileOutputStream out = new FileOutputStream(dst);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    out.close();
                    in.close();
                }
            }
        } catch (Throwable ignored) {
        }
        e.putBoolean("migrated", true).apply();
    }


    /** 外部镜像目录：/sdcard/Android/media/<pkg>/sounds，任何进程都能按路径读。 */
    public static File mirrorDir(Context c) {
        try {
            File[] dirs = c.getExternalMediaDirs();
            if (dirs != null && dirs.length > 0 && dirs[0] != null) {
                File d = new File(dirs[0], "sounds");
                if (!d.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    d.mkdirs();
                }
                return d;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void mirror(Context c, String key, File src) {
        File d = mirrorDir(c);
        if (d == null || src == null || !src.isFile()) {
            return;
        }
        String ext = extOf(src.getName());
        File dest = new File(d, key + ext);
        File part = new File(d, "~" + key + ".part");
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(part);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } catch (Throwable ignored) {
        } finally {
            close(in);
            close(out);
        }
        if (part.isFile() && part.length() > 0) {
            unmirror(c, key);
            if (!part.renameTo(dest)) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
            }
        } else {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
        }
    }

    private static void unmirror(Context c, String key) {
        File d = mirrorDir(c);
        if (d == null) {
            return;
        }
        File[] fs = d.listFiles();
        if (fs == null) {
            return;
        }
        for (File f : fs) {
            if (f.getName().startsWith(key + ".")) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    private static void clearMirror(Context c) {
        File d = mirrorDir(c);
        if (d == null) {
            return;
        }
        File[] fs = d.listFiles();
        if (fs == null) {
            return;
        }
        for (File f : fs) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** 清掉已删除槽位留下的配置和音频文件，避免残留影响。 */
    public static void prune(Context c) {
        try {
            SharedPreferences p = prefs(c);
            java.util.Set<String> valid = new java.util.HashSet<String>();
            for (Slots.Slot s : Slots.all()) {
                valid.add(s.key);
            }
            SharedPreferences.Editor e = p.edit();
            boolean changed = false;
            for (String k : new java.util.ArrayList<String>(p.getAll().keySet())) {
                if (k.startsWith(P_FILE) || k.startsWith(P_NAME)) {
                    if (!valid.contains(k.substring(2))) {
                        e.remove(k);
                        changed = true;
                    }
                }
            }
            if (changed) {
                e.apply();
            }
            java.util.Set<String> keep = new java.util.HashSet<String>();
            for (Slots.Slot s : Slots.all()) {
                File f = file(c, s.key);
                if (f != null) {
                    keep.add(f.getName());
                }
            }
            File[] fs = dir(c).listFiles();
            if (fs != null) {
                for (File f : fs) {
                    if (!keep.contains(f.getName())) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    }
                }
            }
            File md = mirrorDir(c);
            if (md != null) {
                File[] ms = md.listFiles();
                if (ms != null) {
                    for (File f : ms) {
                        String n = f.getName();
                        int dot = n.indexOf('.');
                        String key = dot > 0 ? n.substring(0, dot) : n;
                        if (!valid.contains(key)) {
                            //noinspection ResultOfMethodCallIgnored
                            f.delete();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
    public static int version(Context c) {
        return prefs(c).getInt(K_VER, 0);
    }

    public static void bump(Context c) {
        prefs(c).edit().putInt(K_VER, version(c) + 1).apply();
    }

    /** 上一次成功「应用更改」时的配置版本，用于提示有未应用的改动。 */
    public static int appliedVersion(Context c) {
        return prefs(c).getInt(K_APPLIED, 0);
    }

    public static void setAppliedVersion(Context c, int v) {
        prefs(c).edit().putInt(K_APPLIED, v).apply();
    }

    /** 当前槽位生效的自定义文件，没有则返回 null。 */
    public static File file(Context c, String key) {
        String name = prefs(c).getString(P_FILE + key, null);
        if (name == null) {
            return null;
        }
        File f = new File(dir(c), name);
        return (f.isFile() && f.length() > 0) ? f : null;
    }

    public static String displayName(Context c, String key) {
        return prefs(c).getString(P_NAME + key, "自定义音效");
    }

    public static void clear(Context c, String key) {
        File f = file(c, key);
        if (f != null) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        unmirror(c, key);
        prefs(c).edit().remove(P_FILE + key).remove(P_NAME + key).apply();
        bump(c);
    }

    public static void clearAll(Context c) {
        File[] files = dir(c).listFiles();
        if (files != null) {
            for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        clearMirror(c);
        SharedPreferences.Editor e = prefs(c).edit();
        for (Slots.Slot s : Slots.all()) {
            e.remove(P_FILE + s.key).remove(P_NAME + s.key);
        }
        e.apply();
        bump(c);
    }

    /** 把一个音频 Uri 复制到私有目录并绑定到槽位。 */
    public static boolean setSound(Context c, String key, Uri uri) {
        File tmp = new File(dir(c), key + ".tmp");
        FileOutputStream out = null;
        InputStream in = null;
        try {
            String name = queryName(c, uri);
            String ext = extOf(name);
            in = c.getContentResolver().openInputStream(uri);
            if (in == null) {
                return false;
            }
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            out.close();
            out = null;
            if (tmp.length() <= 0) {
                return false;
            }
            File old = file(c, key);
            File dest = new File(dir(c), key + "_" + System.currentTimeMillis() + ext);
            if (!tmp.renameTo(dest)) {
                return false;
            }
            if (!decodable(dest)) {
                //noinspection ResultOfMethodCallIgnored
                dest.delete();
                return false;
            }
            if (old != null && !old.getAbsolutePath().equals(dest.getAbsolutePath())) {
                //noinspection ResultOfMethodCallIgnored
                old.delete();
            }
            prefs(c).edit()
                    .putString(P_FILE + key, dest.getName())
                    .putString(P_NAME + key, name)
                    .apply();
            mirror(c, key, dest);
            bump(c);
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            close(in);
            close(out);
            if (tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    /** 用 MediaMetadataRetriever 试解码，避免把系统不认识的格式塞给 SoundPool 导致静音。 */
    private static boolean decodable(File f) {
        android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
        try {
            mmr.setDataSource(f.getAbsolutePath());
            String d = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d != null && d.length() > 0;
        } catch (Throwable t) {
            return false;
        } finally {
            try {
                mmr.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void close(java.io.Closeable c) {        if (c != null) {
            try {
                c.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String queryName(Context c, Uri uri) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                String s = cur.getString(0);
                if (s != null && s.length() > 0) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cur != null) {
                try {
                    cur.close();
                } catch (Throwable ignored) {
                }
            }
        }
        String last = uri.getLastPathSegment();
        return last == null ? "sound" : last;
    }

    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            String e = name.substring(dot).toLowerCase(Locale.US);
            if (e.length() <= 6) {
                return e;
            }
        }
        return ".ogg";
    }

    public static String sizeText(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    /** 给注入进程读取的 JSON 配置。 */
    public static String json(Context c) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"ver\":").append(version(c));
        sb.append(",\"slots\":{");
        boolean first = true;
        for (Slots.Slot s : Slots.all()) {
            File f = file(c, s.key);
            if (f == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(s.key).append("\":{\"f\":\"");
            sb.append(esc(f.getName()));
            sb.append("\",\"n\":\"");
            sb.append(esc(displayName(c, s.key)));
            sb.append("\"}");
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                default:
                    if (ch < 0x20) {
                        b.append(' ');
                    } else {
                        b.append(ch);
                    }
            }
        }
        return b.toString();
    }

    private static File logFile(Context c) {
        return new File(de(c).getFilesDir(), "hook.log");
    }

    /** 运行日志最多保留的条数。 */
    public static final int LOG_MAX_LINES = 50;

    public static synchronized void appendLog(Context c, String line) {
        try {
            File f = logFile(c);
            String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            FileOutputStream o = new FileOutputStream(f, true);
            o.write((ts + "  " + line + "\n").getBytes("UTF-8"));
            o.close();
            trimLog(f);
        } catch (Throwable ignored) {
        }
    }

    /** 只保留最近 LOG_MAX_LINES 条，避免日志无限增长。 */
    private static void trimLog(File f) {
        if (!f.isFile() || f.length() <= 256) {
            return;
        }
        java.io.BufferedReader r = null;
        java.util.LinkedList<String> lines = new java.util.LinkedList<String>();
        try {
            r = new java.io.BufferedReader(new java.io.InputStreamReader(new FileInputStream(f), "UTF-8"));
            String s;
            while ((s = r.readLine()) != null) {
                lines.add(s);
                while (lines.size() > LOG_MAX_LINES) {
                    lines.removeFirst();
                }
            }
        } catch (Throwable t) {
            return;
        } finally {
            close(r);
        }
        if (lines.size() <= LOG_MAX_LINES) {
            return;
        }
        File tmp = new File(f.getParentFile(), "hook.log.tmp");
        java.io.BufferedWriter w = null;
        try {
            w = new java.io.BufferedWriter(new java.io.OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"));
            for (String l : lines) {
                w.write(l);
                w.write('\n');
            }
            w.flush();
        } catch (Throwable t) {
            close(w);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        close(w);
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(f);
        }
    }

    public static String readLog(Context c) {
        File f = logFile(c);
        if (!f.exists()) {
            return "";
        }
        try {
            byte[] b = new byte[(int) Math.min(f.length(), 96 * 1024)];
            FileInputStream in = new FileInputStream(f);
            int off = 0;
            while (off < b.length) {
                int n = in.read(b, off, b.length - off);
                if (n <= 0) {
                    break;
                }
                off += n;
            }
            in.close();
            String all = new String(b, 0, off, "UTF-8");
            String[] lines = all.split("\n");
            if (lines.length <= LOG_MAX_LINES) {
                return all;
            }
            StringBuilder sb = new StringBuilder(all.length() / 2);
            for (int i = lines.length - LOG_MAX_LINES; i < lines.length; i++) {
                if (lines[i].length() > 0) {
                    sb.append(lines[i]).append('\n');
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    public static void clearLog(Context c) {
        //noinspection ResultOfMethodCallIgnored
        logFile(c).delete();
    }
}
