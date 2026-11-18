# Hidden framework APIs are accessed by name through reflection.
-keep class android.bluetooth.BluetoothA2dp { *; }
-keep class android.bluetooth.BluetoothCodecConfig { *; }
-keep class android.bluetooth.BluetoothCodecStatus { *; }
-keep class android.bluetooth.BluetoothCodecType { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.arison.codecmanager.**$$serializer { *; }
-keepclassmembers class dev.arison.codecmanager.** {
    *** Companion;
}
-keepclasseswithmembers class dev.arison.codecmanager.** {
    kotlinx.serialization.KSerializer serializer(...);
}
