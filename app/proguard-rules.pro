# Native bridge
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.reel.editor.nativebridge.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.reel.editor.model.** { *** Companion; }
-keepclasseswithmembers class com.reel.editor.model.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.reel.editor.model.**$$serializer { *; }
