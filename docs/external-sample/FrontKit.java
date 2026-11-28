package com.example.uikit;

import com.ioo.watchnote.plugin.PluginHost;
import com.ioo.watchnote.plugin.PluginKind;
import com.ioo.watchnote.plugin.PluginManifest;
import com.ioo.watchnote.plugin.SimplePage;
import com.ioo.watchnote.plugin.WristPlugin;
import java.util.Collections;

/**
 * 前置模块（kind=lib）示例，插件模块 0.3 起。
 *
 * 它没有页面、没有按钮、不会出现在「插件页面」里；只把自己的类交给依赖它的插件用。
 * 依赖方在清单里写 requires: ["com.example.uikit>=1"]，宿主加载时会把这份 dex
 * 排到依赖方 dex 的前面，所以下面这些静态方法可以直接调。
 *
 * 这就是「懒得写的小模块」：算法（fmtMins / percent）和官方 UI 排版助手（bigStat / circle）
 * 都可以塞进前置模块，用的人不用再抄一遍，也不用改宿主。
 */
public final class FrontKit {

    private FrontKit() {
    }

    /** 分钟数说成人话：95 -> "1 小时 35 分"。 */
    public static String fmtMins(int mins) {
        if (mins <= 0) {
            return "0 分";
        }
        int hour = mins / 60;
        int min = mins % 60;
        if (hour <= 0) {
            return min + " 分";
        }
        if (min == 0) {
            return hour + " 小时";
        }
        return hour + " 小时 " + min + " 分";
    }

    /** 百分比，0~100；总数是 0 时返回 0。 */
    public static int percent(long done, long total) {
        if (total <= 0L) {
            return 0;
        }
        long p = done * 100L / total;
        if (p < 0L) {
            return 0;
        }
        if (p > 100L) {
            return 100;
        }
        return (int) p;
    }

    /** 官方 UI 排版助手：一行大数字（还是由宿主渲染，插件不画界面）。 */
    public static void bigStat(SimplePage page, String label, String value) {
        page.hero(label, value, null);
    }

    /** 官方 UI 排版助手：一个环形进度。 */
    public static void circle(SimplePage page, String label, long done, long total) {
        page.ring(label, done, total);
    }

    /** 前置模块版本，依赖方可以按它开关功能。 */
    public static String version() {
        return "uikit 1.0";
    }
}

/** 前置模块入口：install 里什么都不注册（也没有页面可以注册）。 */
final class FrontKitPlugin implements WristPlugin {

    private final PluginManifest manifest = new PluginManifest(
            "com.example.uikit",
            "前置 UI 工具",
            1,
            3,
            Collections.emptySet(),
            PluginKind.LIB,
            Collections.singletonList("com.example.uikit.FrontKit"),
            Collections.emptyList());

    @Override
    public PluginManifest getManifest() {
        return manifest;
    }

    @Override
    public void install(PluginHost host) {
        // 前置模块不注册页面/按钮；能力就是自己的类本身。
    }

    @Override
    public void uninstall() {
    }
}
