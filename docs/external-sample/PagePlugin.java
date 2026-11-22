package com.example.page;

import com.ioo.watchnote.plugin.ButtonSlot;
import com.ioo.watchnote.plugin.ButtonSpec;
import com.ioo.watchnote.plugin.HostScreen;
import com.ioo.watchnote.plugin.Permission;
import com.ioo.watchnote.plugin.PluginHost;
import com.ioo.watchnote.plugin.PluginManifest;
import com.ioo.watchnote.plugin.SimplePage;
import com.ioo.watchnote.plugin.WristPlugin;
import java.util.Collections;

/**
 * 页面型外置插件示例（腕记 4.1.21 起）。
 *
 * 和 HelloPlugin 的区别：它不光加一行按钮，还注册了一个**页面**。
 * 页面不自己画 UI——只交"有哪些行"，由宿主用官方组件渲染（SimplePage）：
 * 所以外观、返回键、圆屏边距、主题色都和内置页一模一样，插件也不会画出花花绿绿的风格。
 *
 * 编译见 build-sample-page.ps1（javac + d8，不需要 Kotlin，也不需要 Compose 编译器）。
 */
public final class PagePlugin implements WristPlugin {

    private static final String ID = "com.example.page";

    private final PluginManifest manifest =
            new PluginManifest(ID, "示例页面插件", 2, 1, Collections.singleton(Permission.READ_NOTES));

    @Override
    public PluginManifest getManifest() {
        return manifest;
    }

    @Override
    public void install(PluginHost host) {
        final PluginHost h = host;

        h.getPages().add(
                new SimplePage(ID + ":main", "示例页面")
                        .sub("外置插件 · 页面型")
                        .onAction(actionId -> {
                            if ("inc".equals(actionId)) {
                                int n = h.getData().kv().getInt("clicks", 0) + 1;
                                h.getData().kv().putInt("clicks", n);
                                h.toast("插件计数：" + n);
                                h.invalidate();
                            } else if ("about".equals(actionId)) {
                                // 官方页面只走白名单：插件说"去哪个官方页"，宿主自己压栈
                                h.openHostPage(HostScreen.ABOUT);
                            }
                        })
                        // 4.1.22 起：render 每次宿主重画这一页都会重跑，行里读的是最新数据。
                        // 以前统计行是安装那一刻算好的，点「计数加一」后 toast 变了、页面数字不变。
                        .render(p -> {
                            int clicks = h.getData().kv().getInt("clicks", 0);
                            int notes;
                            try {
                                notes = h.getData().notes().size();
                            } catch (Throwable t) {
                                notes = -1;
                            }
                            p.stat("插件计数", String.valueOf(clicks));
                            p.stat("宿主笔记", notes < 0 ? "读不到" : String.valueOf(notes));
                            p.button("＋", "计数加一", "共 " + clicks + " 次", "inc");
                            p.button("▸", "打开关于页", null, "about");
                        })
        );

        // 设置页底部加一行入口，点一下直接打开上面那个页面
        h.getButtons().add(new ButtonSpec(
                ID + ":entry",
                ButtonSlot.SETTINGS_ROW,
                "▤",
                "示例页面插件",
                () -> {
                    h.openPage(ID + ":main");
                    return kotlin.Unit.INSTANCE;
                },
                null
        ));
    }

    @Override
    public void uninstall() {
    }
}
