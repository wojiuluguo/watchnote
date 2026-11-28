package com.example.testui;

import com.ioo.watchnote.plugin.ButtonSlot;
import com.ioo.watchnote.plugin.ButtonSpec;
import com.ioo.watchnote.plugin.Pen;
import com.ioo.watchnote.plugin.Permission;
import com.ioo.watchnote.plugin.PluginDependency;
import com.ioo.watchnote.plugin.PluginHost;
import com.ioo.watchnote.plugin.PluginKind;
import com.ioo.watchnote.plugin.PluginManifest;
import com.ioo.watchnote.plugin.UINode;
import com.ioo.watchnote.plugin.UIPage;
import com.ioo.watchnote.plugin.Ui;
import com.ioo.watchnote.plugin.WristPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import kotlin.Unit;

/**
 * 界面全测（0.4.5）：把插件能碰的界面能力一次跑完。
 *
 * 三页：
 * - :tree   布局容器（column/row/box/group/grid/scroll/spacer/divider + weight/height/pad/align/columns）+ 16 种官方组件 + 一块元素画布
 * - :canvas 元素画布：Pen 的每个方法都画一遍，另加 onTap / onDrag / .action() 兜底三条路
 * - :full   整页画布：fullPage() + asMainPage()，直接占指示栏第 6 格
 *
 * 为什么单独写一个插件而不是塞进内置演示：外置插件作者真正会踩的坑
 * （weight 只在 Row/Grid 里生效、chips 是逗号、timeCard 传 "09:30"、scroll 套在宿主滚动页里）
 * 只有从 Java 侧按文档写一遍才测得到。
 */
public final class TestUiPlugin implements WristPlugin {

    public static final String ID = "com.example.testui";

    private PluginHost host;

    private int actions;
    private String last = "（还没点过）";
    private int taps;
    private float tapX = -1f;
    private float tapY = -1f;
    private int drags;
    private float dragX;
    private float dragY;
    private int dial = 35;

    @Override
    public PluginManifest getManifest() {
        return new PluginManifest(ID, "界面全测", 1, 5,
                Collections.<Permission>emptySet(), PluginKind.APP,
                Collections.<String>emptyList(), Collections.<PluginDependency>emptyList());
    }

    @Override
    public void install(PluginHost host) {
        this.host = host;
        host.getPages().add(buildTree());
        host.getPages().add(buildCanvas());
        host.getPages().add(buildFull());

        host.getButtons().add(new ButtonSpec(
                ID + ":tree", ButtonSlot.SETTINGS_ROW, "▤", "界面全测：容器树",
                () -> {
                    host.openPage(ID + ":tree");
                    return Unit.INSTANCE;
                }, null));

        host.getButtons().add(new ButtonSpec(
                ID + ":canvas", ButtonSlot.PAGE_BOTTOM, "✎", "画布三连测",
                () -> {
                    host.openPage(ID + ":canvas");
                    return Unit.INSTANCE;
                }, null));
    }

    @Override
    public void uninstall() {
        host = null;
    }

    // ---------------- 第 1 页：容器树 + 全部官方组件 ----------------

    private UIPage buildTree() {
        UIPage page = new UIPage(ID + ":tree", "容器树");
        page.sub("布局容器 8 种 + 官方组件 16 种 + 元素画布");
        page.onAction((String id) -> {
            actions++;
            last = id;
            host.toast("动作 " + id);
            host.invalidate();
            return Unit.INSTANCE;
        });

        List<Float> barValues = Arrays.asList(0.15f, 0.45f, 0.8f, 0.55f, 1f);

        // 顶上一块画布专门显示「刚才点到了什么」：容器树里的官方组件是安装时定死的，
        // 要看到实时数据就得靠画布（它的 draw 块会在每次 invalidate 后重跑）。
        page.add(Ui.canvas(64, pen -> {
                    pen.clear("card");
                    pen.text("动作 " + actions + " 次", 0.5f, 0.30f, 13f, "text", Pen.CENTER, true);
                    pen.text("最后：" + last, 0.5f, 0.60f, 10f, "muted", Pen.CENTER, false);
                    pen.text("点 " + taps + " 次 / 拖 " + drags + " 次", 0.5f, 0.84f, 9f, "primary", Pen.CENTER, false);
                })
                .onTap((x, y) -> {
                    taps++;
                    tapX = x;
                    tapY = y;
                    host.invalidate();
                })
                .onDrag((dx, dy) -> {
                    drags++;
                    dragX += dx;
                    dragY += dy;
                    host.invalidate();
                }));

        page.add(Ui.note("这一段全是宿主画的：插件只交数据，圆屏边距、字号、主题色都跟官方页一致。"));

        page.add(Ui.section("官方组件 16 种"));
        page.add(Ui.hero("今天完成", "3 / 4", "还差 1 条"));
        page.add(Ui.stat("普通统计行", "值"));
        page.add(Ui.stat("带动作的统计行（点我）", "点", "stat"));
        page.add(Ui.row(8,
                Ui.stat("左 1", "weight").weight(1f),
                Ui.stat("中 2", "weight").weight(1f),
                Ui.stat("右 3", "weight").weight(1f)));
        page.add(Ui.item("●", "列表项主标题", "副标题说明文字", "尾注", "item"));
        page.add(Ui.progress("进度行", 3, 4, "75%"));
        page.add(Ui.ring("环形行", 3, 4));
        page.add(Ui.stepper("步进行（−/+）", 3, 0, 10, 1, "step"));
        page.add(Ui.chips("选项行（逗号分隔）", "甲,乙,丙", 1, "chip"));
        page.add(Ui.colorCard("颜色行", "#3DDC84", "已选", true, "color"));
        page.add(Ui.progress("进度行（0 起点）", 0, 10));
        page.add(Ui.timeCard("09:30", "18:00", "时间行（HH:mm）", "time"));
        page.add(Ui.toggle("开关行", true, "toggle"));
        page.add(Ui.bars("柱状行", barValues, "5 根"));
        page.add(Ui.shape("star", "图形行 star", 44, "#FFC107", "shape"));
        page.add(Ui.shape("hexagon", "图形行 hexagon", 44, "", "shape2"));
        page.add(Ui.empty("空状态行（示例）"));
        page.add(Ui.button("＋", "按钮行（symbol + trailing）", "›", false, "btn1"));
        page.add(Ui.button("两参按钮（默认参数）", "btn2"));
        page.add(Ui.button("危险按钮", "×", "不可用", true, "btn3"));

        page.add(Ui.section("容器 8 种"));
        page.add(Ui.group("group：自带圆角底色和内边距",
                Ui.stat("组内统计行", "ok"),
                Ui.note("分组里可以继续塞任何节点。")));
        page.add(Ui.grid(2,
                Ui.button("格 1", "g1"),
                Ui.button("格 2", "g2"),
                Ui.button("格 3（单独一行）", "g3")));
        page.add(Ui.box(
                Ui.button("左上", "a1").align(0),
                Ui.button("居中", "a2").align(4),
                Ui.button("右下", "a3").align(8)).height(112));
        page.add(Ui.column(2,
                Ui.stat("嵌套 column spacing=2（1）", "1"),
                Ui.stat("嵌套 column spacing=2（2）", "2")));
        page.add(Ui.stat("pad=8 的统计行", "p").pad(8));
        page.add(Ui.divider());
        page.add(Ui.spacer(6));
        page.add(Ui.scroll(
                Ui.item("·", "滚动行 1", "scroll 容器里的第一行", "", "s1"),
                Ui.item("·", "滚动行 2", "scroll 容器里的第二行", "", "s2"),
                Ui.item("·", "滚动行 3", "scroll 容器里的第三行", "", "s3"),
                Ui.item("·", "滚动行 4", "scroll 容器里的第四行", "", "s4"),
                Ui.item("·", "滚动行 5", "scroll 容器里的第五行", "", "s5"),
                Ui.item("·", "滚动行 6", "scroll 容器里的第六行", "", "s6")).height(150));

        return page;
    }

    // ---------------- 第 2 页：元素画布 ----------------

    private UIPage buildCanvas() {
        UIPage page = new UIPage(ID + ":canvas", "元素画布");
        page.sub("Pen 每个方法 + onTap / onDrag / action 兜底");
        page.onAction((String id) -> {
            actions++;
            last = id;
            host.toast("画布动作 " + id);
            host.invalidate();
            return Unit.INSTANCE;
        });

        page.add(Ui.note("画布坐标是 0~1 的比例（左上 0,0），粗细和字号按 dp；颜色可以用 text/muted/primary/danger/card，也可以写 #RRGGBB。"));

        page.add(Ui.canvas(150, this::drawPrimitives));

        page.add(Ui.canvas(72, pen -> {
            pen.clear("card");
            if (tapX >= 0f) {
                pen.line(tapX, 0f, tapX, 1f, "danger", 1f);
                pen.line(0f, tapY, 1f, tapY, "danger", 1f);
                pen.circle(tapX, tapY, 0.10f, "danger", 2f);
            }
            pen.text("点我：" + taps + " 次", 0.5f, 0.28f, 12f, "text", Pen.CENTER, true);
            pen.text(tapX < 0f ? "还没点过" : "x=" + round2(tapX) + "  y=" + round2(tapY),
                    0.5f, 0.58f, 10f, "muted", Pen.CENTER, false);
            pen.text("回调里改完状态要 host.invalidate() 才重画", 0.5f, 0.82f, 8f, "muted", Pen.CENTER, false);
        }).onTap((x, y) -> {
            taps++;
            tapX = x;
            tapY = y;
            host.invalidate();
        }));

        page.add(Ui.canvas(72, pen -> {
            pen.clear("card");
            pen.line(0.10f, 0.62f, 0.90f, 0.62f, "muted", 6f);
            float kx = 0.5f + Math.max(-0.40f, Math.min(0.40f, dragX));
            pen.circle(kx, 0.62f, 0.07f, "primary");
            pen.text("拖我：累计 dx=" + round2(dragX) + " dy=" + round2(dragY), 0.5f, 0.22f, 11f, "text", Pen.CENTER, true);
            pen.text("拖动 " + drags + " 次（onDrag 收到的是增量，不是坐标）", 0.5f, 0.40f, 9f, "muted", Pen.CENTER, false);
        }).onDrag((dx, dy) -> {
            drags++;
            dragX += dx;
            dragY += dy;
            host.invalidate();
        }));

        page.add(Ui.canvas(56, pen -> {
            pen.clear("primary");
            pen.text("没挂 onTap：点我走 .action() 兜底", 0.5f, 0.50f, 11f, "card", Pen.CENTER, true);
        }).action("fallback"));

        page.add(Ui.button("整页画布（指示栏第 6 格）", "full"));

        return page;
    }

    /** 每个 Pen 方法各来一次：实心/描边/圆角/扇形/折线/闭合/三种对齐。 */
    private void drawPrimitives(Pen pen) {
        pen.clear("card");

        pen.rect(0.04f, 0.05f, 0.27f, 0.18f, "primary", 6f);
        pen.rect(0.36f, 0.05f, 0.27f, 0.18f, "muted", 0f, 2f);
        pen.rect(0.69f, 0.05f, 0.27f, 0.18f, "danger", 12f);

        pen.circle(0.14f, 0.37f, 0.09f, "primary");
        pen.circle(0.38f, 0.37f, 0.09f, "muted", 2f);

        pen.line(0.55f, 0.28f, 0.96f, 0.45f, "muted", 2f);
        pen.line(0.55f, 0.45f, 0.96f, 0.28f, "danger", 1f);

        pen.arc(0.19f, 0.66f, 0.11f, 0f, 240f, "primary", 6f);
        pen.arc(0.44f, 0.66f, 0.11f, 0f, 240f, "primary", 0f, true);

        pen.path("muted", 1.5f, false, 0.62f, 0.58f, 0.70f, 0.74f, 0.78f, 0.58f);
        pen.path("primary", 0f, true, 0.86f, 0.74f, 0.96f, 0.58f, 0.76f, 0.58f);

        pen.text("左", 0.06f, 0.92f, 12f, "text", Pen.LEFT, false);
        pen.text("中", 0.50f, 0.92f, 12f, "primary", Pen.CENTER, true);
        pen.text("右", 0.94f, 0.92f, 12f, "danger", Pen.RIGHT, false);
    }

    // ---------------- 第 3 页：整页画布（占指示栏第 6 格） ----------------

    private UIPage buildFull() {
        UIPage page = new UIPage(ID + ":full", "整页画布");
        page.sub("fullPage + asMainPage");
        page.fullPage().asMainPage();
        page.onAction((String id) -> {
            actions++;
            last = id;
            host.invalidate();
            return Unit.INSTANCE;
        });
        page.add(Ui.fillCanvas(this::drawDial)
                .onTap((x, y) -> {
                    dial = Math.max(0, Math.min(100, dial + 5));
                    host.invalidate();
                })
                .onDrag((dx, dy) -> {
                    dial = Math.max(0, Math.min(100, dial + Math.round(dx * 60f)));
                    host.invalidate();
                }));
        return page;
    }

    private void drawDial(Pen pen) {
        pen.clear("card");
        float cx = 0.5f;
        float cy = 0.52f;
        float r = 0.40f;

        pen.circle(cx, cy, r, "muted", 2f);

        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(135.0 + i * 270.0 / 11.0);
            float x1 = cx + (float) (Math.cos(a) * (r - 0.06f));
            float y1 = cy + (float) (Math.sin(a) * (r - 0.06f));
            float x2 = cx + (float) (Math.cos(a) * (r - 0.02f));
            float y2 = cy + (float) (Math.sin(a) * (r - 0.02f));
            pen.line(x1, y1, x2, y2, "muted", 2f);
        }

        pen.arc(cx, cy, r - 0.03f, 135f, 270f * dial / 100f, "primary", 8f);
        pen.text(dial + "%", cx, cy - 0.02f, 34f, "text", Pen.CENTER, true);
        pen.text("点一下 +5，横向拖动也能调", cx, 0.88f, 10f, "muted", Pen.CENTER, false);

        double a = Math.toRadians(135.0 + 270.0 * dial / 100.0);
        float px = cx + (float) (Math.cos(a) * 0.24f);
        float py = cy + (float) (Math.sin(a) * 0.24f);
        pen.path("danger", 0f, true,
                px, py - 0.035f,
                px + 0.025f, py + 0.03f,
                px - 0.03f, py + 0.02f);
    }

    private static String round2(float v) {
        return String.valueOf(Math.round(v * 100f) / 100f);
    }
}
