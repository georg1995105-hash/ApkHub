# Keep Kotlinx Serialization generated classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.apkhub.app.**$$serializer { *; }
-keepclassmembers class com.apkhub.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.apkhub.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
