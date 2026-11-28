package com.example.testdata;

import com.ioo.watchnote.Note;
import com.ioo.watchnote.plugin.ButtonSlot;
import com.ioo.watchnote.plugin.ButtonSpec;
import com.ioo.watchnote.plugin.HostScreen;
import com.ioo.watchnote.plugin.HttpResult;
import com.ioo.watchnote.plugin.KeyValueStore;
import com.ioo.watchnote.plugin.LibraryApi;
import com.ioo.watchnote.plugin.NetApi;
import com.ioo.watchnote.plugin.Pen;
import com.ioo.watchnote.plugin.Permission;
import com.ioo.watchnote.plugin.PluginDependency;
import com.ioo.watchnote.plugin.PluginHost;
import com.ioo.watchnote.plugin.PluginKind;
import com.ioo.watchnote.plugin.PluginManifest;
import com.ioo.watchnote.plugin.SimplePage;
import com.ioo.watchnote.plugin.UINode;
import com.ioo.watchnote.plugin.UIPage;
import com.ioo.watchnote.plugin.Ui;
import com.ioo.watchnote.plugin.WristPlugin;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

/**
 * 数据全测（0.4.5）：把插件除了「画界面」以外的每一条边都跑一遍。
 *
 * 两页 + 三个按钮插槽：
 * - :panel  SimplePage（老 19 种行全用上），并且用 render() 每帧重算，验证「数据一变页面就变」
 * - :net    NetApi：get / post / request 三条主路 + no_permission 到 too_frequent 六条拦截路径
 * 按钮：SETTINGS_ROW（开面板）、PAGE_BOTTOM（开联网页）、CARD_ACTION（点笔记卡片的按钮，读那条笔记）
 * 还测：kv 读写、notes()/tags()、事件总线订阅与反注册、前置模块查询、host.openHostPage / openPage、invalidate。
 *
 * 注意：清单里声明了 WRITE_NOTES，但这一版写笔记接口还没开（安装页会显示「写笔记（未开放）」），
 * 所以这里只验证「权限声明 → 安装页展示 → 宿主不因此拒绝加载」这条链，不假装能写。
 */
public final class TestDataPlugin implements WristPlugin {

    public static final String ID = "com.example.testdata";

    private PluginHost host;
    private KeyValueStore kv;

    private int actions;
    private String lastAction = "（还没点过）";
    private int noteSaves;
    private int pageShows;
    private String lastSaved = "";

    private Function0<Unit> offNoteSaved;
    private Function0<Unit> offPageShown;

    private String netLabel = "还没发过请求";
    private String netCode = "—";
    private String netError = "点下面的按钮开始";
    private String netBody = "";
    private int netCalls;
    private boolean netOk;

    @Override
    public PluginManifest getManifest() {
        return new PluginManifest(ID, "数据全测", 1, 5,
                EnumSet.of(Permission.READ_NOTES, Permission.WRITE_NOTES, Permission.READ_SETTINGS, Permission.NET),
                PluginKind.APP,
                Collections.<String>emptyList(),
                Collections.<PluginDependency>emptyList());
    }

    @Override
    public void install(PluginHost host) {
        this.host = host;
        this.kv = host.getData().kv();

        host.getPages().add(buildPanel());
        host.getPages().add(buildNet());

        host.getButtons().add(new ButtonSpec(
                ID + ":panel", ButtonSlot.SETTINGS_ROW, "▦", "数据全测：面板",
                () -> {
                    host.openPage(ID + ":panel");
                    return Unit.INSTANCE;
                }, null));

        host.getButtons().add(new ButtonSpec(
                ID + ":netbtn", ButtonSlot.PAGE_BOTTOM, "🌐", "联网测试",
                () -> {
                    host.openPage(ID + ":net");
                    return Unit.INSTANCE;
                }, null));

        host.getButtons().add(new ButtonSpec(
                ID + ":card", ButtonSlot.CARD_ACTION, "✎", "测试按钮",
                () -> Unit.INSTANCE,
                note -> {
                    String text = note == null ? "" : note.getText();
                    host.toast("卡片按钮拿到笔记：" + shorten(text, 18));
                    return Unit.INSTANCE;
                }));

        offNoteSaved = host.getEvents().onNoteSaved(note -> {
            noteSaves++;
            lastSaved = note == null ? "" : shorten(note.getText(), 14);
            host.invalidate();
            return Unit.INSTANCE;
        });

        offPageShown = host.getEvents().onPageShown(pageId -> {
            pageShows++;
            host.invalidate();
            return Unit.INSTANCE;
        });
    }

    @Override
    public void uninstall() {
        if (offNoteSaved != null) {
            offNoteSaved.invoke();
            offNoteSaved = null;
        }
        if (offPageShown != null) {
            offPageShown.invoke();
            offPageShown = null;
        }
        host = null;
        kv = null;
    }

    // ---------------- 第 1 页：19 种行 + 动态重算 ----------------

    private SimplePage buildPanel() {
        SimplePage page = new SimplePage(ID + ":panel", "数据全测");
        page.sub("19 种行 + 动态重算 + 数据/事件/前置模块");
        page.onAction((String id) -> {
            actions++;
            lastAction = id;
            handlePanelAction(id);
            host.invalidate();
        });
        page.render(p -> {
            List<Note> notes = notesQuietly();
            List<String> tags = tagsQuietly();
            int total = notes.size();
            int done = 0;
            for (Note n : notes) {
                if (n.getDone()) {
                    done++;
                }
            }
            Note first = notes.isEmpty() ? null : notes.get(0);
            String firstText = first == null ? "（还没有笔记）" : shorten(first.getText(), 16);
            String firstTag = first == null ? "" : first.getTag();

            LibraryApi lib = host.getLibraries();
            String uikit = lib.installed("com.example.uikit")
                    ? "已装 v" + lib.version("com.example.uikit")
                    : "没装（点示例装一个）";
            String uikitSym = lib.available("com.example.uikit", "com.example.uikit.FrontKit") ? "可用" : "不可用";
            String shapeBlocked = lib.blocked("com.example.shapekit").isEmpty()
                    ? "没有"
                    : String.valueOf(lib.blocked("com.example.shapekit").size()) + " 个";

            p.section("数据源（READ_NOTES）");
            p.stat("笔记条数", String.valueOf(total));
            p.stat("已完成", done + " / " + total);
            p.stat("标签数", String.valueOf(tags.size()));
            p.note("读到的标签：" + (tags.isEmpty() ? "（空）" : joinCsv(tags)));

            p.section("官方行（19 种）");
            p.hero("笔记总数", String.valueOf(total), "已完成 " + done);
            p.item("●", "第一条笔记", firstText, firstTag, "open1");
            p.progress("完成度", done, total);
            p.ring("未完成", total - done, total);
            p.stepper("步进行（kv 计数）", kv.getInt("n", 0), 0, 20, 1, "step");
            p.chips("选项行（| 分隔）", "甲|乙|丙", kv.getInt("chip", 0), "chip");
            p.colorCard("颜色行", "#3DDC84", "已选", true, "color");
            p.timeCard(todayAt(9, 30), todayAt(18, 0), "今天 09:30 → 18:00", "time");
            p.toggle("开关行", kv.getInt("sw", 0) == 1, "toggle");
            p.empty("空状态行（示例）");
            p.bars("柱状行（, 分隔）", "0.2,0.5,0.9,0.4", "4 根");
            p.shape("star", "图形行 star", 48, "#FFC107", "shape");
            p.shape("diamond", "图形行 diamond", 48, "", "shape2");
            p.divider();
            p.space(4);

            p.section("事件与宿主");
            p.stat("笔记保存事件", noteSaves + " 次" + (lastSaved.isEmpty() ? "" : "（" + lastSaved + "）"));
            p.stat("页面打开事件", pageShows + " 次");
            p.stat("动作总次数", String.valueOf(actions));
            p.stat("最后动作", lastAction);
            p.stat("kv 键", kv.keys().isEmpty() ? "（空）" : joinCsv(new ArrayList<>(kv.keys())));

            p.section("前置模块（libraries）");
            p.stat("uikit", uikit);
            p.stat("uikit FrontKit", uikitSym);
            p.stat("shapekit 被挡符号", shapeBlocked);

            p.section("宿主页面与动作");
            p.button("＋", "kv 加一（invalidate）", String.valueOf(kv.getInt("clicks", 0)), "inc");
            p.button("⌫", "清空 kv", null, "clear");
            p.button("i", "打开「关于」", null, "about");
            p.button("⌘", "打开「插件」页", null, "plugins");
            p.button("🌐", "打开联网页", null, "net");
            p.button("★", "弹一句 toast", null, "toast");
        });
        return page;
    }

    private void handlePanelAction(String id) {
        if (id.equals("inc")) {
            kv.putInt("clicks", kv.getInt("clicks", 0) + 1);
            host.toast("clicks=" + kv.getInt("clicks", 0));
        } else if (id.equals("clear")) {
            kv.clear();
            host.toast("kv 清空了");
        } else if (id.equals("about")) {
            host.openHostPage(HostScreen.ABOUT);
        } else if (id.equals("plugins")) {
            host.openHostPage(HostScreen.PLUGIN_MANAGER);
        } else if (id.equals("net")) {
            host.openPage(ID + ":net");
        } else if (id.equals("toast")) {
            host.toast("这是插件弹的 toast");
        } else if (id.equals("open1")) {
            List<Note> notes = notesQuietly();
            host.toast(notes.isEmpty() ? "还没有笔记" : shorten(notes.get(0).getText(), 24));
        } else if (id.equals("color")) {
            host.toast("颜色行被点了");
        } else if (id.equals("time")) {
            host.toast("时间行被点了");
        } else if (id.startsWith("shape")) {
            host.toast("图形行 " + id);
        } else if (id.startsWith("chip:")) {
            kv.putInt("chip", parseInt(id.substring(5), 0));
            host.toast("选中第 " + kv.getInt("chip", 0) + " 项");
        } else if (id.startsWith("step:+")) {
            kv.putInt("n", kv.getInt("n", 0) + 1);
            host.toast("n=" + kv.getInt("n", 0));
        } else if (id.startsWith("step:-")) {
            kv.putInt("n", kv.getInt("n", 0) - 1);
            host.toast("n=" + kv.getInt("n", 0));
        } else if (id.startsWith("toggle:")) {
            kv.putInt("sw", id.endsWith(":on") ? 1 : 0);
            host.toast("开关 " + (id.endsWith(":on") ? "开" : "关"));
        } else {
            host.toast("面板动作 " + id);
        }
    }

    // ---------------- 第 2 页：联网 ----------------

    private UIPage buildNet() {
        UIPage page = new UIPage(ID + ":net", "联网渠道");
        page.sub("get / post / request + 6 条拦截路径");
        page.onAction((String id) -> {
            send(id);
            return Unit.INSTANCE;
        });

        page.add(Ui.canvas(104, pen -> {
            pen.clear("card");
            pen.text(netLabel, 0.5f, 0.14f, 9f, "muted", Pen.CENTER, false);
            pen.text(netCode, 0.5f, 0.34f, 24f, netOk ? "primary" : "danger", Pen.CENTER, true);
            pen.text(netError, 0.5f, 0.55f, 10f, "text", Pen.CENTER, false);
            pen.text(netBody, 0.5f, 0.72f, 9f, "muted", Pen.CENTER, false);
            pen.text("请求 " + netCalls + " 次", 0.5f, 0.88f, 9f, "muted", Pen.CENTER, false);
        }));

        page.add(Ui.note("只允许 http/https、不许本机和内网、响应最多 256KB、超时 5~30 秒、每分 30 次、同时 2 个。只验证「拦得住」和「通得了」两件事。"));
        page.add(Ui.section("能通的"));
        page.add(Ui.button("GET https://example.com", "get"));
        page.add(Ui.button("POST https://httpbin.org/post", "post"));
        page.add(Ui.button("request() 带自定义头", "req"));
        page.add(Ui.section("该被拦的"));
        page.add(Ui.button("本机 http://127.0.0.1 → blocked", "local"));
        page.add(Ui.button("内网 http://192.168.1.1 → blocked", "lan"));
        page.add(Ui.button("ftp:// → bad_url", "ftp"));
        page.add(Ui.button("假域名 → dns", "dns"));
        page.add(Ui.button("关掉联网再发 → blocked", "off"));
        page.add(Ui.section("限速与复位"));
        page.add(Ui.button("连发 35 次（之后 1 分钟内都会 too_frequent）", "rate"));
        page.add(Ui.button("清空显示", "reset"));
        return page;
    }

    private void send(String kind) {
        NetApi api = host.getNet();
        switch (kind) {
            case "get":
                netLabel = "GET example.com";
                api.get("https://example.com/", 10000, this::onHttp);
                break;
            case "post":
                netLabel = "POST httpbin.org/post";
                api.post("https://httpbin.org/post", "{\"a\":1}", "application/json", 10000, this::onHttp);
                break;
            case "req":
                netLabel = "REQUEST GET + 自定义头";
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("X-WristNote-Test", "1");
                api.request("GET", "https://example.com/", headers, null, 10000, this::onHttp);
                break;
            case "local":
                netLabel = "GET 127.0.0.1（应 blocked）";
                api.get("http://127.0.0.1:8080/", 10000, this::onHttp);
                break;
            case "lan":
                netLabel = "GET 192.168.1.1（应 blocked）";
                api.get("http://192.168.1.1/", 10000, this::onHttp);
                break;
            case "ftp":
                netLabel = "GET ftp://（应 bad_url）";
                api.get("ftp://example.com/", 10000, this::onHttp);
                break;
            case "dns":
                netLabel = "GET 假域名（应 dns）";
                api.get("https://no-such-host.invalid/", 10000, this::onHttp);
                break;
            case "off":
                netLabel = "关掉联网后 GET（应 blocked）";
                api.setEnabled(false);
                api.get("https://example.com/", 10000, this::onHttp);
                api.setEnabled(true);
                break;
            case "rate":
                netLabel = "连发 35 次（测限速）";
                for (int i = 0; i < 35; i++) {
                    api.get("https://example.com/?i=" + i, 10000, this::onHttp);
                }
                break;
            case "reset":
                netLabel = "还没发过请求";
                netCode = "—";
                netError = "点下面的按钮开始";
                netBody = "";
                netCalls = 0;
                netOk = false;
                host.invalidate();
                break;
            default:
                host.toast("未知动作 " + kind);
                break;
        }
    }

    private Unit onHttp(HttpResult result) {
        netCalls++;
        netOk = result.getOk();
        netCode = String.valueOf(result.getCode());
        String error = result.getError();
        netError = error == null ? "成功" : error;
        String body = result.getBody() == null ? "" : result.getBody();
        netBody = error == null ? body.length() + " 字符" : "body 忽略";
        host.invalidate();
        return Unit.INSTANCE;
    }

    // ---------------- 工具 ----------------

    private List<Note> notesQuietly() {
        try {
            return host.getData().notes();
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private List<String> tagsQuietly() {
        try {
            return host.getData().tags();
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private static long todayAt(int hour, int minute) {
        return LocalDate.now()
                .atTime(LocalTime.of(hour, minute))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.replace('\n', ' ').trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    private static String joinCsv(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(v);
        }
        return sb.toString();
    }
}
