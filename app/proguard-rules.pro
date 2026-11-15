-keep class android.car.** {*;}
-keep class android.app.** {*;}

-dontwarn android.**
-dontwarn com.platform.bdstar.drive.*
-dontwarn com.yuante.api.YuanTeManager

-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

-keep class com.umeng.** { *; }
-keep class com.uc.** { *; }
-keep class com.efs.** { *; }
