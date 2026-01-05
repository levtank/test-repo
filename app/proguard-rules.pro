# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools.

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.example.aialarmclock.**$$serializer { *; }
-keepclassmembers class com.example.aialarmclock.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.aialarmclock.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities
-keep class com.example.aialarmclock.data.local.entities.** { *; }
