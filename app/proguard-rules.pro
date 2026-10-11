# ProGuard rules for Instagram Clone
-keep class com.insta.clone.data.model.** { *; }
-keep class com.insta.clone.data.remote.dto.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# CircleImageView
-dontwarn de.hdodenhof.circleimageview.**
-keep class de.hdodenhof.circleimageview.** { *; }

# Retrofit & OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
