# 保留 app 自身类（Activity/Application 等）
-keep class com.ioo.watchnote.** { *; }
-keepclassmembers class * implements android.os.Parcelable { *; }
# Wear 触感相关可选类，缺失时不报错
-dontwarn com.google.wear.**
