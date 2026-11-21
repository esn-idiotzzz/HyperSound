package com.hypersound.mod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 1001;
    private static final int MODE_ALL = 0;
    private static final int MODE_MODIFIED = 1;

    private final List<Object> items = new ArrayList<Object>();
    private ListView list;
    private TextView tabAll;
    private TextView tabModified;
    private TextView empty;
    private Adapter adapter;
    private MediaPlayer player;
    private String pendingKey;
    private int mode = MODE_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        list = (ListView) findViewById(R.id.list);
        tabAll = (TextView) findViewById(R.id.tab_all);
        tabModified = (TextView) findViewById(R.id.tab_modified);
        empty = (TextView) findViewById(R.id.tv_empty);

        findViewById(R.id.btn_apply).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doApply();
            }
        });
        findViewById(R.id.btn_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmReset();
            }
        });
        findViewById(R.id.btn_log).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLog();
            }
        });

        tabAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_ALL);
            }
        });
        tabModified.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_MODIFIED);
            }
        });

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object o = items.get(position);
                if (o instanceof Slots.Slot) {
                    pick(((Slots.Slot) o).key);
                }
            }
        });

        rebuild();
    }

    private void setMode(int m) {
        mode = m;
        rebuild();
    }

    private void rebuild() {
        items.clear();
        int total = 0;
        int modified = 0;
        String group = null;
        for (Slots.Slot s : Slots.all()) {
            total++;
            boolean has = Store.file(this, s.key) != null;
            if (has) {
                modified++;
            }
            if (mode == MODE_MODIFIED && !has) {
                continue;
            }
            if (!s.group.equals(group)) {
                group = s.group;
                items.add(group);
            }
            items.add(s);
        }

        tabAll.setText(getString(R.string.tab_all) + " " + total);
        tabModified.setText(getString(R.string.tab_modified) + " " + modified);
        styleTab(tabAll, mode == MODE_ALL);
        styleTab(tabModified, mode == MODE_MODIFIED);

        boolean blank = items.isEmpty();
        empty.setVisibility(blank ? View.VISIBLE : View.GONE);
        list.setVisibility(blank ? View.GONE : View.VISIBLE);

        if (adapter == null) {
            adapter = new Adapter();
            list.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    private void styleTab(TextView tv, boolean on) {
        tv.setBackgroundResource(on ? R.drawable.bg_tab_on : R.drawable.bg_tab_off);
        tv.setTextColor(on ? 0xFF08131F : 0xFF8A96A8);
    }

    private void pick(String key) {
        pendingKey = key;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"audio/*", "application/ogg", "application/octet-stream"});
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Throwable t) {
            toast("没有找到可用的文件选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null || pendingKey == null) {
            return;
        }
        if (Store.setSound(this, pendingKey, uri)) {
            File f = Store.file(this, pendingKey);
            long len = f == null ? 0 : f.length();
            if (len > 2 * 1024 * 1024) {
                toast("已设置（" + Store.sizeText(len) + "），提示音建议 1MB 以内");
            } else {
                toast("已设置，记得点「应用更改」");
            }
            rebuild();
        } else {
            toast("这个音频无法解码，换一个试试（推荐 ogg / wav / mp3）");
        }
    }

    private void preview(String key) {
        File f = Store.file(this, key);
        if (f == null) {
            toast("还没有设置自定义音效");
            return;
        }
        stopPreview();
        try {
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopPreview();
                }
            });
            player.prepare();
            player.start();
        } catch (Throwable t) {
            toast("播放失败：" + t.getMessage());
            stopPreview();
        }
    }

    private void stopPreview() {
        if (player != null) {
            try {
                player.stop();
            } catch (Throwable ignored) {
            }
            try {
                player.release();
            } catch (Throwable ignored) {
            }
            player = null;
        }
    }

    private void clearOne(String key) {
        Store.clear(this, key);
        rebuild();
        toast("已恢复默认");
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("全部恢复默认")
                .setMessage("删除所有自定义音效，全部回到系统默认？")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Store.clearAll(MainActivity.this);
                        rebuild();
                        toast("已全部恢复默认");
                    }
                })
                .show();
    }

    private void doApply() {
        toast("正在重启系统界面…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = killSystemUi();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            toast("系统界面已重启，锁屏/解锁等音效已生效");
                        } else {
                            showApplyHelp();
                        }
                    }
                });
            }
        }).start();
    }

    private boolean killSystemUi() {
        String[] suPaths = {"/system/bin/su", "/system/xbin/su", "/debug_ramdisk/su", "/sbin/su", "su"};
        String[] cmds = {
                "killall com.android.systemui",
                "pkill -f com.android.systemui",
                "kill $(pidof com.android.systemui)",
        };
        for (String su : suPaths) {
            for (String cmd : cmds) {
                try {
                    Process p = new ProcessBuilder(su, "-c", cmd).redirectErrorStream(true).start();
                    if (p.waitFor() == 0) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private void showApplyHelp() {
        new AlertDialog.Builder(this)
                .setTitle("没能自动重启")
                .setMessage("没有拿到 root，或系统不允许结束系统界面。\n\n"
                        + "可以手动让改动生效：\n"
                        + "· 重启手机；或\n"
                        + "· 在 LSPosed 里停用再启用本模块后重启系统界面；或\n"
                        + "· 用 root 终端执行：killall com.android.systemui\n\n"
                        + "注意：按键、触摸等由「系统框架」播放的音效，必须重启手机才会生效。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showLog() {
        String log = Store.readLog(this);
        if (log.length() == 0) {
            log = "暂无日志。\n\n如果音效没有生效：\n"
                    + "1. 确认 LSPosed 里已启用模块并勾选作用域；\n"
                    + "2. 确认已点「应用更改」或重启过手机；\n"
                    + "3. 重启后再次查看这里，会记录替换过程。";
        }
        TextView tv = new TextView(this);
        tv.setText(log);
        tv.setTextSize(10f);
        tv.setTextColor(0xFFCBD5E1);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        int pad = (int) (getResources().getDisplayMetrics().density * 16);
        tv.setPadding(pad, pad, pad, pad);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("运行日志")
                .setView(sv)
                .setNegativeButton("关闭", null)
                .setNeutralButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Store.clearLog(MainActivity.this);
                    }
                })
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        stopPreview();
        super.onDestroy();
    }

    private final class Adapter extends BaseAdapter {

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof String ? 0 : 1;
        }

        @Override
        public boolean isEnabled(int position) {
            return !(items.get(position) instanceof String);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object o = items.get(position);
            if (o instanceof String) {
                TextView tv = (TextView) (convertView != null
                        ? convertView
                        : getLayoutInflater().inflate(R.layout.item_header, parent, false));
                tv.setText((String) o);
                return tv;
            }

            View v = convertView != null
                    ? convertView
                    : getLayoutInflater().inflate(R.layout.item_slot, parent, false);
            final Slots.Slot slot = (Slots.Slot) o;

            ((TextView) v.findViewById(R.id.tv_title)).setText(slot.label);

            TextView sub = (TextView) v.findViewById(R.id.tv_sub);
            File f = Store.file(MainActivity.this, slot.key);
            if (f != null) {
                sub.setText(Store.displayName(MainActivity.this, slot.key)
                        + " · " + Store.sizeText(f.length()));
                sub.setTextColor(0xFF4DA3FF);
            } else {
                sub.setText(getString(R.string.default_sound) + " · " + slot.sample);
                sub.setTextColor(0xFF8A96A8);
            }

            TextView pickBtn = (TextView) v.findViewById(R.id.btn_pick);
            pickBtn.setBackgroundResource(f != null
                    ? R.drawable.bg_pill
                    : R.drawable.bg_pill_accent);
            pickBtn.setTextColor(f != null ? 0xFF8A96A8 : 0xFF08131F);

            v.findViewById(R.id.btn_pick).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    pick(slot.key);
                }
            });
            v.findViewById(R.id.btn_play).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    preview(slot.key);
                }
            });
            v.findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    clearOne(slot.key);
                }
            });
            return v;
        }
    }
}
