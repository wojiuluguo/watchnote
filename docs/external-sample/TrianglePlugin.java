package com.example.triangle;

import com.example.shapekit.ShapeKit;
import com.ioo.watchnote.plugin.ButtonSlot;
import com.ioo.watchnote.plugin.ButtonSpec;
import com.ioo.watchnote.plugin.PluginDependency;
import com.ioo.watchnote.plugin.PluginHost;
import com.ioo.watchnote.plugin.PluginKind;
import com.ioo.watchnote.plugin.PluginManifest;
import com.ioo.watchnote.plugin.SimplePage;
import com.ioo.watchnote.plugin.WristPlugin;
import java.util.Collections;

/**
 * 使用前置模块的「图形插件」示例（0.4 起）。
 *
 * 它自己不会算几何、也不认识图形名字：图形名、中文名、下一个图形、面积、说明文字
 * 全部来自前置模块 ShapeKit。宿主本来没有「画图形」这件事——这一版宿主才加了
 * shape 行，而 ShapeKit 把它包成更好用的一组 API，正是「前置模组给更多能力」的例子。
 *
 * 前置模块没装 / 被同名关掉时不会崩：整个页面降级成文字，并直接说清楚缺了什么。
 */
public final class TrianglePlugin implements WristPlugin {

    private static final String ID = "com.example.triangle";
    private static final String LIB = "com.example.shapekit";
    private static final String KEY_SHAPE = "shape";
    private static final int SIZE_DP = 88;

    private final PluginManifest manifest = new PluginManifest(
            ID,
            "前置图形示例",
            1,
            4,
            Collections.emptySet(),
            PluginKind.APP,
            Collections.emptyList(),
            Collections.singletonList(new PluginDependency(LIB, 1)));

    @Override
    public PluginManifest getManifest() {
        return manifest;
    }

    @Override
    public void install(final PluginHost host) {
        // 保底判断：前置模块在、并且符号没被宿主同名关掉，才用它。
        final boolean kit = host.getLibraries().available(LIB, "com.example.shapekit.ShapeKit");

        SimplePage page = new SimplePage(ID + ":page", "前置图形示例")
                .sub(kit ? "图形与算法来自前置模块 " + ShapeKit.version() : "缺少前置模块 shapekit")
                .section("一个 App 里没有的图形");

        page.render(p -> {
            String shape = host.getData().kv().getString(KEY_SHAPE);
            if (shape == null || shape.isEmpty()) {
                shape = ShapeKit.TRIANGLE;
            }
            if (kit) {
                ShapeKit.draw(p, shape, ShapeKit.nameOf(shape), SIZE_DP, "#FF7043", "tap");
                p.note(ShapeKit.geometry(shape, SIZE_DP));
                p.note("点图形或下面的按钮换下一个；名字、面积、顺序都是前置模块算的。");
            } else {
                p.note("前置模块 com.example.shapekit 不可用，这里只能给文字：");
                p.stat("本应画出的图形", ShapeKit.nameOf(shape));
                p.stat("缺的符号", "com.example.shapekit.ShapeKit");
            }
            p.button("⇄", "换下一个图形", kit ? "由前置模块决定顺序" : "前置不可用，仍可切换", "next");
        });

        page.onAction(actionId -> {
            String shape = host.getData().kv().getString(KEY_SHAPE);
            if (shape == null || shape.isEmpty()) {
                shape = ShapeKit.TRIANGLE;
            }
            String nextShape = ShapeKit.next(shape);
            host.getData().kv().putString(KEY_SHAPE, nextShape);
            host.toast(kit ? "换成了" + ShapeKit.nameOf(nextShape) : "前置不可用：" + nextShape);
            host.invalidate();
        });

        host.getPages().add(page);
        host.getButtons().add(new ButtonSpec(
                ID + ":open",
                ButtonSlot.SETTINGS_ROW,
                "▲",
                "前置图形示例",
                () -> {
                    host.openPage(ID + ":page");
                    return kotlin.Unit.INSTANCE;
                },
                null));
    }

    @Override
    public void uninstall() {
    }
}