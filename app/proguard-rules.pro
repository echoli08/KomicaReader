# Jsoup
-keep public class org.jsoup.** { public *; }

# OkHttp3
-keepattributes Signature
-keepattributes AnnotationDefault
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Okio
-dontwarn okio.**

# Glide
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-keep @com.bumptech.glide.annotation.GlideModule class *
-keep enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# Models - Prevent stripping serializable fields
-keepclassmembers class com.komica.reader.model.** { *; }
-keep class com.komica.reader.model.** { *; }

# Keep Lifecycle and ViewModel
-keep class androidx.lifecycle.** { *; }