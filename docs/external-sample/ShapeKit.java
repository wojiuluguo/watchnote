package com.example.shapekit;

import com.ioo.watchnote.plugin.PluginHost;
import com.ioo.watchnote.plugin.PluginKind;
import com.ioo.watchnote.plugin.PluginManifest;
import com.ioo.watchnote.plugin.SimplePage;
import com.ioo.watchnote.plugin.WristPlugin;
import java.util.Collections;

/**
 * 前置模块（kind=lib）示例：图形套件，插件模块 0.4 起。
 *
 * 它没有任何页面、按钮、设置行；装上去在界面上「看不见」。
 * 它把两样东西交给别的插件：
 *
 * 1. UI 能力：draw() / row()——往官方声明式页面里放图形行（三角形、五角星、菱形、六边形）。
 *    图形仍然由宿主 Canvas 画，前置模块只负责「写哪一行、多大、什么颜色」。
 * 2. 算法能力：area() / geometry() / sides() / next()——几何计算和图形序列，
 *    用的人不用抄一遍，也不用改宿主。
 *
 * 依赖方清单里写 requires: ["com.example.shapekit>=1"]，宿主会把这份 dex 排到
 * 依赖方 dex 前面，所以下面这些静态方法可以直接调。
 */
public final class ShapeKit {

    public static final String TRIANGLE = "triangle";
    public static final String STAR = "star";
    public static final String DIAMOND = "diamond";
    public static final String HEXAGON = "hexagon";

    /** 宿主认识的四个名字，顺序就是「下一个图形」的顺序。 */
    private static final String[] ALL = {TRIANGLE, STAR, DIAMOND, HEXAGON};

    private ShapeKit() {
    }

    /** 当前可用的全部图形名。 */
    public static String[] all() {
        return ALL.clone();
    }

    /** 图形的中文名；不认识的名字按三角形处理（和宿主一致）。 */
    public static String nameOf(String shape) {
        if (STAR.equals(shape)) {
            return "五角星";
        }
        if (DIAMOND.equals(shape)) {
            return "菱形";
        }
        if (HEXAGON.equals(shape)) {
            return "六边形";
        }
        return "三角形";
    }

    /** 下一个图形，用来做「点一下换一个」：triangle -> star -> diamond -> hexagon -> triangle。 */
    public static String next(String shape) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].equals(shape)) {
                return ALL[(i + 1) % ALL.length];
            }
        }
        return TRIANGLE;
    }

    /** 边数：三角形 3、菱形 4、六边形 6、五角星 10 条边（10 个角）。 */
    public static int sides(String shape) {
        if (STAR.equals(shape)) {
            return 10;
        }
        if (DIAMOND.equals(shape)) {
            return 4;
        }
        if (HEXAGON.equals(shape)) {
            return 6;
        }
        return 3;
    }

    /**
     * 算法示例：按「外接边长 sizeDp」估算面积（单位 dp²，四舍五入）。
     *
     * 系数来自各自的正多边形面积公式，五角星取经验值：
     * 正三角形 √3/4 ≈ 0.4330，正方形斜放（菱形）0.5，
     * 正六边形 3√3/2 ÷ 4 ≈ 0.6495，五角星约 0.3964。
     */
    public static int area(String shape, int sizeDp) {
        int size = sizeDp;
        if (size < 1) {
            size = 1;
        }
        double k;
        if (STAR.equals(shape)) {
            k = 0.3964d;
        } else if (DIAMOND.equals(shape)) {
            k = 0.5d;
        } else if (HEXAGON.equals(shape)) {
            k = 0.6495d;
        } else {
            k = 0.4330d;
        }
        return (int) Math.round(k * size * size);
    }

    /** 一句人类可读的几何说明，直接塞进页面 note 行。 */
    public static String geometry(String shape, int sizeDp) {
        return nameOf(shape) + " · 边长 " + sizeDp + " · " + sides(shape) + " 条边 · 面积约 "
                + area(shape, sizeDp) + " dp²";
    }

    /** 往页面里放一行图形（最简单的用法，颜色交给宿主主题）。 */
    public static void draw(SimplePage page, String shape, int sizeDp) {
        page.shape(shape, nameOf(shape), sizeDp, "");
    }

    /** 带颜色和点按 id 的图形行：点了宿主回调 "id"。 */
    public static void draw(SimplePage page, String shape, String label, int sizeDp, String colorHex, String actionId) {
        page.shape(shape, label, sizeDp, colorHex, actionId);
    }

    /** 一排小图形（每个 40dp），用来看四个图形的区别。 */
    public static void row(SimplePage page, String... shapes) {
        String[] list = (shapes == null || shapes.length == 0) ? ALL : shapes;
        for (int i = 0; i < list.length; i++) {
            String s = list[i];
            page.shape(s, nameOf(s), 40, "");
        }
    }

    /** 前置模块版本，依赖方可以按它开关功能。 */
    public static String version() {
        return "shapekit 1.0";
    }
}

/** 前置模块入口：install 里什么都不注册（也没有页面可以注册）。 */
final class ShapeKitPlugin implements WristPlugin {

    private final PluginManifest manifest = new PluginManifest(
            "com.example.shapekit",
            "前置图形套件",
            1,
            4,
            Collections.emptySet(),
            PluginKind.LIB,
            Collections.singletonList("com.example.shapekit.ShapeKit"),
            Collections.emptyList());

    @Override
    public PluginManifest getManifest() {
        return manifest;
    }

    @Override
    public void install(PluginHost host) {
        // 前置模块不注册页面/按钮；能力就是 ShapeKit 这些静态方法本身。
    }

    @Override
    public void uninstall() {
    }
}
