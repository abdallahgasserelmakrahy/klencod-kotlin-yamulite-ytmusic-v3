# kotlinx.serialization — preserve generated $serializer classes and companions
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.pdv.yamulite.**$$serializer { *; }
-keepclassmembers class dev.pdv.yamulite.** {
    *** Companion;
}
-keepclasseswithmembers class dev.pdv.yamulite.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit — keep annotated interface methods
-keepclasseswithmembers interface dev.pdv.yamulite.** {
    @retrofit2.http.* <methods>;
}
-keepattributes Signature, Exceptions

# OkHttp — suppress noisy warnings from platform detection
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# NewPipeExtractor (YouTube Music) — Rhino is used to decode YouTube's stream signatures
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn com.google.re2j.**
