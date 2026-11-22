# 保留 app 自身类（Activity/Application 等）
-keep class com.ioo.watchnote.** { *; }
-keepclassmembers class * implements android.os.Parcelable { *; }
# Wear 触感相关可选类，缺失时不报错
-dontwarn com.google.wear.**

# ===== 外置插件 ABI（.wplugin）=====
# 外置插件的 classes.dex 是插件作者用 javac/kotlinc + d8 独立编译的，
# 里面直接写死了宿主运行时类名（kotlin.Unit、kotlin.jvm.functions.Function0 …）。
# 只要 release 版把名字改掉，插件加载时就会：
#   NoClassDefFoundError: Class not found using the boot class loader
# （4.1.19 真机就是 kotlin.Unit 被 R8 改名成 m32 之后炸的）
# 所以 release 版整体关掉「改名」，只保留压缩和优化；插件 ABI 的名字永远稳定。
-dontobfuscate

# 下面这些是插件代码（尤其 Kotlin 写的插件和 @Composable lambda）必须能解析到的类，
# 显式钉死，防止被压缩阶段整类删掉。
-keep class kotlin.Unit { *; }
-keep class kotlin.Result { *; }
-keep class kotlin.Pair { *; }
-keep class kotlin.Triple { *; }
-keep interface kotlin.jvm.functions.** { *; }
-keep class kotlin.jvm.internal.Intrinsics { *; }
-keep class kotlin.jvm.internal.DefaultConstructorMarker { *; }
# @Composable lambda 在插件 dex 里会引用这几个 Compose runtime 入口
-keep class androidx.compose.runtime.Composer { *; }
-keep class androidx.compose.runtime.ComposerKt { *; }
-keep class androidx.compose.runtime.internal.ComposableLambdaKt { *; }