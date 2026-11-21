package com.hypersound.mod;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 把模块 App 的音效文件和配置暴露给注入进程（系统界面 / 系统框架）。
 * 只按槽位名查表，不接受任意路径。
 */
public class SoundProvider extends ContentProvider {

    public static final String AUTH = "com.hypersound.mod.sounds";
    public static final Uri URI_CONFIG = Uri.parse("content://" + AUTH + "/config");
    public static final Uri URI_LOG = Uri.parse("content://" + AUTH + "/log");

    public static Uri uriSound(String key) {
        return Uri.parse("content://" + AUTH + "/f/" + key);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String path = uri.getPath();
        if ("/config".equals(path)) {
            MatrixCursor c = new MatrixCursor(new String[]{"json"});
            c.addRow(new Object[]{Store.json(getContext())});
            return c;
        }
        if ("/log".equals(path)) {
            MatrixCursor c = new MatrixCursor(new String[]{"log"});
            c.addRow(new Object[]{Store.readLog(getContext())});
            return c;
        }
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "audio/*";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (values != null && "/log".equals(uri.getPath())) {
            String line = values.getAsString("line");
            if (line != null) {
                Store.appendLog(getContext(), line);
            }
        }
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        long len = f.length();
        if (len < 0) {
            len = AssetFileDescriptor.UNKNOWN_LENGTH;
        }
        return new AssetFileDescriptor(pfd, 0, len);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null || !path.startsWith("/f/")) {
            throw new FileNotFoundException(String.valueOf(path));
        }
        String key = path.substring(3);
        File f = Store.file(getContext(), key);
        if (f == null || !f.isFile()) {
            throw new FileNotFoundException(key);
        }
        return f;
    }
}
