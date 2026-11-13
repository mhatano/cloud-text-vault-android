# Add project specific ProGuard rules here.
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.vault.model.**$$serializer { *; }
-keepclassmembers class com.example.vault.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.vault.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
