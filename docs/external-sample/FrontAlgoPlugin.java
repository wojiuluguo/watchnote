package com.example.frontalgo;

import com.example.uikit.FrontKit;
import com.ioo.watchnote.Note;
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
import java.util.List;

/**
 * 调用前置模块的「算法插件」示例（0.3 起）。
 *
 * 前置模块不一定跟 UI 有关：这里把「时间怎么念」「百分比怎么算」交给 FrontKit，
 * 插件自己只负责取数据、拼一行行结果。两个插件共用同一份算法，改一处就够了。
 */
public final class FrontAlgoPlugin implements WristPlugin {

    private static final String ID = "com.example.frontalgo";
    private static final String LIB = "com.example.uikit";

    private final PluginManifest manifest = new PluginManifest(
            ID,
            "前置算法示例",
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
        final boolean kit = host.getLibraries().available(LIB, "com.example.uikit.FrontKit");

        SimplePage page = new SimplePage(ID + ":page", "前置算法示例").render(p -> {
            List<Note> notes = host.getData().notes();
            int chars = 0;
            for (Note note : notes) {
                chars += note.getText() == null ? 0 : note.getText().length();
            }
            int minutes = Math.max(1, chars / 120);
            p.section("用前置模块算出来的");
            if (kit) {
                p.hero("写作时长", FrontKit.fmtMins(minutes), null);
                p.progress("进度", (long) minutes, 60L);
                p.note("算法来自 " + FrontKit.version() + "：字数 -> 分钟 -> 人话。");
            } else {
                p.hero("写作时长", minutes + " 分钟", null);
                p.note("前置模块不可用，退回了最简单的写法。");
            }
            p.stat("笔记", String.valueOf(notes.size()));
            p.stat("字数", String.valueOf(chars));
        });

        host.getPages().add(page);
        host.getButtons().add(new ButtonSpec(
                ID + ":open",
                ButtonSlot.SETTINGS_ROW,
                "≈",
                "前置算法示例",
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
