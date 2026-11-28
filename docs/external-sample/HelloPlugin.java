package com.example.hello;

import com.ioo.watchnote.plugin.ButtonSlot;
import com.ioo.watchnote.plugin.ButtonSpec;
import com.ioo.watchnote.plugin.PluginHost;
import com.ioo.watchnote.plugin.PluginKind;
import com.ioo.watchnote.plugin.PluginManifest;
import com.ioo.watchnote.plugin.WristPlugin;

import java.util.Collections;

/**
 * 外置插件最小样例（v2.0）：只往设置页加一行按钮，点一下弹提示。
 *
 * 为什么用 Java 写样例：不引 Compose 编译器，一个 javac + d8 就能产出 classes.dex，
 * 方便任何人复现「.wplugin 里到底装了什么」。要画页面的插件用 Kotlin + Compose，
 * 见 插件作者手册；加载机制完全一样。
 */
public final class HelloPlugin implements WristPlugin {

    private final PluginManifest manifest =
            new PluginManifest("com.example.hello", "示例插件", 1, 1, Collections.emptySet(),
                    PluginKind.APP, Collections.emptyList(), Collections.emptyList());

    @Override
    public PluginManifest getManifest() {
        return manifest;
    }

    @Override
    public void install(PluginHost host) {
        host.getButtons().add(new ButtonSpec(
                "com.example.hello:hello",
                ButtonSlot.SETTINGS_ROW,
                "★",
                "示例外置插件",
                () -> {
                    host.toast("外置插件已生效：代码是从 .wplugin 里加载的");
                    return kotlin.Unit.INSTANCE;
                },
                null));
    }

    @Override
    public void uninstall() {
        // 宿主卸载时会按 pluginId 兜底清页面/按钮/数据，这里不用做
    }
}
