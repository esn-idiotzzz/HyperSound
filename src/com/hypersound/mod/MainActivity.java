package com.hypersound.mod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
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
    private TextView btnApply;
    private TextView tvStatus;
    private Adapter adapter;
    private MediaPlayer player;
    private String pendingKey;
    private int mode = MODE_ALL;
    private boolean importing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        list = (ListView) findViewById(R.id.list);
        tabAll = (TextView) findViewById(R.id.tab_all);
        tabModified = (TextView) findViewById(R.id.tab_modified);
        empty = (TextView) findViewById(R.id.tv_empty);
        btnApply = (TextView) findViewById(R.id.btn_apply);
        tvStatus = (TextView) findViewById(R.id.tv_status);
        tvStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInjectionDialog();
            }
        });

        btnApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doApply();
            }
        });
        findViewById(R.id.btn_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmResetAll();
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

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            rebuild();
        }
    }

    private void setMode(int m) {
        if (mode == m) {
            return;
        }
        mode = m;
        rebuild();
        if (list != null) {
            list.setSelection(0);
        }
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

        boolean pending = Store.version(this) != Store.appliedVersion(this);
        btnApply.setText(getString(pending ? R.string.apply_pending : R.string.apply));
        btnApply.setAlpha(pending ? 1f : 0.82f);

        updateInjectionStatus();

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

    private static final String[] KEY_PKGS = {
            "com.android.systemui",
            "android",
            "com.miui.securitycenter",
            "com.android.camera",
            "com.android.mms",
            "com.android.incallui",
            "com.android.settings",
            "com.miui.home",
    };

    private static final String[] KEY_LABELS = {
            "系统界面",
            "系统框架",
            "安全中心（充电音）",
            "相机",
            "短信",
            "通话界面",
            "设置",
            "桌面",
    };

    private void updateInjectionStatus() {
        String log = Store.readLog(this);
        int ok = 0;
        for (String p : KEY_PKGS) {
            if (log.indexOf("installed in " + p) >= 0) {
                ok++;
            }
        }
        boolean hasSysUi = log.indexOf("installed in com.android.systemui") >= 0;
        if (!hasSysUi) {
            tvStatus.setText("未检测到注入：请在 LSPosed 勾选「系统界面」后重启");
            tvStatus.setTextColor(0xFFFF8A80);
        } else if (ok == KEY_PKGS.length) {
            tvStatus.setText("注入状态：8/8 个关键进程已生效 · 点击查看");
            tvStatus.setTextColor(0xFF6FD08C);
        } else {
            tvStatus.setText("注入状态：" + ok + "/8 个关键进程 · 点击查看缺哪些");
            tvStatus.setTextColor(0xFFFFB86B);
        }
    }

    private void showInjectionDialog() {
        String log = Store.readLog(this);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < KEY_PKGS.length; i++) {
            boolean ok = log.indexOf("installed in " + KEY_PKGS[i]) >= 0;
            sb.append(ok ? "\u2713 " : "\u2717 ").append(KEY_LABELS[i]);
            if (!ok) {
                sb.append("   （").append(KEY_PKGS[i]).append("）");
            }
            sb.append('\n');
        }
        sb.append("\n✗ 表示该进程还没被注入：在 LSPosed 作用域里勾选对应应用，重启后生效。");
        sb.append("\n充电/无线充/拔出提示音由「安全中心」播放，必须勾选它。");
        new AlertDialog.Builder(this)
                .setTitle("注入状态")
                .setMessage(sb.toString())
                .setPositiveButton("知道了", null)
                .show();
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
        if (requestCode != REQ_PICK) {
            return;
        }
        final String key = pendingKey;
        pendingKey = null;
        if (resultCode != RESULT_OK || data == null || key == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        importSound(key, uri);
    }

    /** 拷贝 + 试解码放到后台线程，避免大文件卡 UI。 */
    private void importSound(final String key, final Uri uri) {
        if (importing) {
            return;
        }
        importing = true;
        toast(getString(R.string.importing));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = Store.setSound(MainActivity.this, key, uri);
                File f = ok ? Store.file(MainActivity.this, key) : null;
                final long len = f == null ? 0 : f.length();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        importing = false;
                        if (!ok) {
                            toast("这个音频无法解码，换一个试试（推荐 ogg / wav / mp3）");
                            return;
                        }
                        if (len > 2 * 1024 * 1024) {
                            toast("已设置（" + Store.sizeText(len) + "），提示音建议 1MB 以内");
                        } else {
                            toast("已设置，记得点「应用更改」");
                        }
                        rebuild();
                    }
                });
            }
        }).start();
    }

    private void preview(String key) {
        if (Store.file(this, key) == null) {
            toast(getString(R.string.no_custom));
            return;
        }
        File file = Store.file(this, key);
        if (file == null) {
            toast(getString(R.string.no_custom));
            return;
        }
        stopPreview();
        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
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

    private void confirmResetOne(final Slots.Slot slot) {
        if (Store.file(this, slot.key) == null) {
            toast(getString(R.string.no_custom));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("恢复「" + slot.label + "」")
                .setMessage("删除该项的自定义音频，恢复系统默认？")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复默认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Store.clear(MainActivity.this, slot.key);
                        rebuild();
                        toast("已恢复默认");
                    }
                })
                .show();
    }

    private void confirmResetAll() {
        new AlertDialog.Builder(this)
                .setTitle("全部恢复默认")
                .setMessage("删除所有自定义音效（" + countModified() + " 项），全部回到系统默认？")
                .setNegativeButton("取消", null)
                .setPositiveButton("全部恢复", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Store.clearAll(MainActivity.this);
                        rebuild();
                        toast("已全部恢复默认");
                    }
                })
                .show();
    }

    private int countModified() {
        int n = 0;
        for (Slots.Slot s : Slots.all()) {
            if (Store.file(this, s.key) != null) {
                n++;
            }
        }
        return n;
    }

    private void doApply() {
        boolean pending = Store.version(this) != Store.appliedVersion(this);
        if (!pending) {
            new AlertDialog.Builder(this)
                    .setTitle("没有未应用的更改")
                    .setMessage("仍要重启系统界面吗？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("重启", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            restartSystemUi();
                        }
                    })
                    .show();
            return;
        }
        restartSystemUi();
    }

    private void restartSystemUi() {
        toast("正在重启系统界面…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = killSystemUi();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            Store.setAppliedVersion(MainActivity.this, Store.version(MainActivity.this));
                            rebuild();
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
                        sleep(800);
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
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
            boolean custom = f != null;
            if (custom) {
                sub.setText(Store.displayName(MainActivity.this, slot.key)
                        + " · " + Store.sizeText(f.length()));
                sub.setTextColor(0xFF4DA3FF);
            } else {
                sub.setText(getString(R.string.default_sound) + " · " + slot.sample);
                sub.setTextColor(0xFF8A96A8);
            }

            TextView pickBtn = (TextView) v.findViewById(R.id.btn_pick);
            pickBtn.setText(getString(custom ? R.string.change : R.string.pick));
            pickBtn.setBackgroundResource(custom ? R.drawable.bg_pill : R.drawable.bg_pill_accent);
            pickBtn.setTextColor(custom ? 0xFF8A96A8 : 0xFF08131F);

            View play = v.findViewById(R.id.btn_play);
            View reset = v.findViewById(R.id.btn_clear);
            play.setAlpha(custom ? 1f : 0.3f);
            reset.setAlpha(custom ? 1f : 0.3f);

            pickBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    pick(slot.key);
                }
            });
            play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    preview(slot.key);
                }
            });
            reset.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    confirmResetOne(slot);
                }
            });
            return v;
        }
    }
}
