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
 * 页面型外置插件示例（腕记插件模块 0.2 起）。
 *
 * 和 HelloPlugin 的区别：它不光加一行按钮，还注册了一个**页面**。
 * 页面不自己画 UI——只交"有哪些行"，由宿主用官方组件渲染（SimplePage）：
 * 所以外观、返回键、圆屏边距、主题色都和内置页一模一样，插件也不会画出花花绿绿的风格。
 *
 * 0.2 起 SimplePage 把整套官方 UI 都开成了行方法：section / note / hero / item / progress /
 * ring / stepper / chips / colorCard / timeCard / toggle / divider / space / empty / bars。
 * 下面这一页就把它们全部用了一遍，作为作者手册的活例子。
 *
 * 带参数的行，回调 id 后面接冒号：
 *   chips   选中第 2 个 -> "freq:2"
 *   stepper 点 +       -> "cups:+1"   （- 是 "cups:-1"）
 *   toggle  打开       -> "sound:on"  （关是 "sound:off"）
 *
 * 编译见 build-sample-page.ps1（javac + d8，不需要 Kotlin，也不需要 Compose 编译器）。
 */
public final class PagePlugin implements WristPlugin {

    private static final String ID = "com.example.page";

    // version 3：示例升级；minApi 2：它用了 0.2 才有的官方 UI 行，老宿主（api 1）要拒绝安装，
    // 而不是装上后 NoSuchMethodError。
    private final PluginManifest manifest =
            new PluginManifest(ID, "示例页面插件", 3, 2, Collections.singleton(Permission.READ_NOTES));

    @Override
    public PluginManifest getManifest() {
        return manifest;
    }

    @Override
    public void install(PluginHost host) {
        final PluginHost h = host;

        h.getPages().add(
                new SimplePage(ID + ":main", "示例页面")
                        .sub("外置插件 · 页面型 · 官方 UI")
                        .onAction(actionId -> {
                            if ("inc".equals(actionId)) {
                                int n = h.getData().kv().getInt("clicks", 0) + 1;
                                h.getData().kv().putInt("clicks", n);
                                h.toast("插件计数：" + n);
                                h.invalidate();
                            } else if ("about".equals(actionId)) {
                                // 官方页面只走白名单：插件说"去哪个官方页"，宿主自己压栈
                                h.openHostPage(HostScreen.ABOUT);
                            } else if ("notes".equals(actionId)) {
                                h.toast("两行列表行也能点");
                            } else if ("color".equals(actionId)) {
                                h.toast("颜色行：这是插件自己的主题色");
                            } else if ("time".equals(actionId)) {
                                h.toast("时间卡行也能点");
                            } else if (actionId.startsWith("cups:")) {
                                int delta = actionId.endsWith(":+1") ? 1 : -1;
                                int cups = h.getData().kv().getInt("cups", 3) + delta;
                                h.getData().kv().putInt("cups", Math.max(0, Math.min(20, cups)));
                                h.toast("杯数：" + h.getData().kv().getInt("cups", 3));
                                h.invalidate();
                            } else if (actionId.startsWith("freq:")) {
                                h.getData().kv().putInt("freq", Integer.parseInt(actionId.substring(5)));
                                h.toast("提醒频率改成第 " + h.getData().kv().getInt("freq", 1) + " 档");
                                h.invalidate();
                            } else if (actionId.startsWith("sound:")) {
                                h.getData().kv().putInt("sound", actionId.endsWith(":on") ? 1 : 0);
                                h.invalidate();
                            }
                        })
                        // render 每次宿主重画这一页都会重跑，行里读的是最新数据（4.1.22 起）。
                        // 0.2 起 renderer 里也能写所有新行：下面这一页就是每次按最新计数重算的。
                        .render(p -> {
                            int clicks = h.getData().kv().getInt("clicks", 0);
                            int cups = h.getData().kv().getInt("cups", 3);
                            int freq = h.getData().kv().getInt("freq", 1);
                            boolean sound = h.getData().kv().getInt("sound", 1) != 0;
                            int notes;
                            try {
                                notes = h.getData().notes().size();
                            } catch (Throwable t) {
                                notes = -1;
                            }
                            long now = System.currentTimeMillis();

                            p.section("主指标");
                            p.hero("插件计数", String.valueOf(clicks), "点下面的按钮加一");
                            p.progress("今日进度", clicks, 10);
                            p.ring("目标", clicks, 10);

                            p.section("可交互的行");
                            p.stepper("杯数", cups, 0, 20, 1, "cups");
                            p.chips("提醒频率", "低|中|高", freq, "freq");
                            p.toggle("提示音", sound, "sound");

                            p.section("官方样式");
                            p.item("▸", "宿主笔记", notes < 0 ? "读不到" : String.valueOf(notes), "查看", "notes");
                            p.colorCard("主题色", "#7C4DFF", "使用中", true, "color");
                            p.timeCard(now, now + 3600_000L, "改时间", "time");
                            p.bars("最近 5 次", "1,5,3,8,2", "峰值 8");
                            p.note("下面这些行都是宿主画的：插件只交数据，画不出别的风格。");
                            p.divider();
                            p.space(4);
                            p.empty("空状态样式（这里只是演示）");

                            p.section("按钮");
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
