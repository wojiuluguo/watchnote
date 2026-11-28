package com.example.frontui;

import com.example.uikit.FrontKit;
import com.ioo.watchnote.plugin.ButtonSlot;
import com.ioo.watchnote.plugin.ButtonSpec;
import com.ioo.watchnote.plugin.Permission;
import com.ioo.watchnote.plugin.PluginDependency;
import com.ioo.watchnote.plugin.PluginHost;
import com.ioo.watchnote.plugin.PluginKind;
import com.ioo.watchnote.plugin.PluginManifest;
import com.ioo.watchnote.plugin.SimplePage;
import com.ioo.watchnote.plugin.WristPlugin;
import java.util.Collections;

/**
 * 调用前置模块的「UI 插件」示例（0.3 起）。
 *
 * 它自己不想写排版：大数字和环形进度都调前置模块 FrontKit 的助手方法。
 * 页面仍然只用 SimplePage 的声明式行，画还是宿主画——前置模块只加便利方法，不加画布。
 */
public final class FrontUiPlugin implements WristPlugin {

    private static final String ID = "com.example.frontui";
    private static final String LIB = "com.example.uikit";

    private final PluginManifest manifest = new PluginManifest(
            ID,
            "前置 UI 示例",
            1,
            3,
            Collections.singleton(Permission.READ_NOTES),
            PluginKind.APP,
            Collections.emptyList(),
            Collections.singletonList(new PluginDependency(LIB, 1)));

    @Override
    public PluginManifest getManifest() {
        return manifest;
    }

    @Override
    public void install(PluginHost host) {
        // 保底写法：前置模块在、符号没被宿主同名关掉，才用它的排版助手；否则退回官方组件。
        final boolean kit = host.getLibraries().available(LIB, "com.example.uikit.FrontKit");

        SimplePage page = new SimplePage(ID + ":page", "前置 UI 示例")
                .sub(kit ? "排版来自前置模块 " + FrontKit.version() : "前置模块不可用，已退回官方组件")
                .section("概览");
        if (kit) {
            FrontKit.bigStat(page, "本周记录", "7 条");
            FrontKit.circle(page, "目标完成度", 3L, 5L);
        } else {
            page.hero("本周记录", "7 条");
            page.ring("目标完成度", 3L, 5L);
        }
        page.note(kit ? "上面两行由前置模块的助手方法排版，宿主渲染。" : "这两行是官方组件直接写的。")
                .button("↻", "刷新", "点一下让页面按最新数据重画", "refresh")
                .onAction(actionId -> host.invalidate());

        host.getPages().add(page);
        host.getButtons().add(new ButtonSpec(
                ID + ":open",
                ButtonSlot.SETTINGS_ROW,
                "◐",
                "前置 UI 示例",
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
