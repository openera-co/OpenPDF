# Native bridge
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.reel.editor.nativebridge.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.reel.editor.model.** { *** Companion; }
-keepclasseswithmembers class com.reel.editor.model.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.reel.editor.model.**$$serializer { *; }

# Media3 guards Android 12+ classes (LogSessionId and others) behind SDK checks. Optimizing its
# code can move those references where older devices resolve them and crash, so Media3 may be
# shrunk and renamed but not optimized.
-keep,allowobfuscation,allowshrinking class androidx.media3.** { *; }

# LiteRT (TensorFlow Lite) looks up its Java classes from native code.
-keep class org.tensorflow.lite.** { *; }
