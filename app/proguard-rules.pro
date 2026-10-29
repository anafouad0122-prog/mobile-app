-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# kotlinx.serialization keeps generated serializers reachable through companions.
-keepclassmembers class com.fouad.cuttingnotebook.** {
    *** Companion;
}
-keepclasseswithmembers class com.fouad.cuttingnotebook.** {
    kotlinx.serialization.KSerializer serializer(...);
}
