# Release builds currently ship without minification (isMinifyEnabled = false).
# Keep rules for when R8 is enabled later:
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
