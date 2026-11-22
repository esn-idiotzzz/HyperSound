package com.hypersound.mod;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** 注入进程侧的配置快照（从模块 App 的 ContentProvider 读取，缓存 3 秒）。 */
final class Cfg {

    private static final String AUTH = "com.hypersound.mod.sounds";
    private static final Uri URI_CONFIG = Uri.parse("content://" + AUTH + "/config");
    private static final long TTL = 1500L;

    private static volatile Cfg cached;

    final Map<String, String> files;
    private final long at;

    private Cfg(Map<String, String> files) {
        this.files = files;
        this.at = System.currentTimeMillis();
    }

    static Uri uriSound(String key) {
        return Uri.parse("content://" + AUTH + "/f/" + key);
    }

    boolean has(String key) {
        return files.containsKey(key);
    }

    static Cfg get(Context c) {
        Cfg s = cached;
        if (s != null && System.currentTimeMillis() - s.at < TTL) {
            return s;
        }
        return load(c);
    }

    private static synchronized Cfg load(Context c) {
        Cfg s = cached;
        if (s != null && System.currentTimeMillis() - s.at < TTL) {
            return s;
        }
        android.database.Cursor cur = null;
        try {
            cur = c.getContentResolver().query(URI_CONFIG, null, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                String json = cur.getString(0);
                JSONObject o = new JSONObject(json);
                HashMap<String, String> map = new HashMap<String, String>();
                JSONObject slots = o.optJSONObject("slots");
                if (slots != null) {
                    Iterator<String> it = slots.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        JSONObject e = slots.optJSONObject(k);
                        if (e == null) {
                            continue;
                        }
                        String f = e.optString("f", null);
                        if (f != null && f.length() > 0) {
                            map.put(k, f);
                        }
                    }
                }
                s = new Cfg(map);
                if (!map.isEmpty()) {
                    cached = s;
                }
                return s;
            }
        } catch (Throwable t) {
            Hooks.logOnce("cfg error: " + t);
            // 读取失败不缓存，下一次事件再试，避免刚改完音效却一直用空配置。
            return new Cfg(new HashMap<String, String>());
        } finally {
            if (cur != null) {
                try {
                    cur.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return new Cfg(new HashMap<String, String>());
    }
}
