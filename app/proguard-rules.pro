# Preserve line numbers in stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ONNX Runtime — uses JNI and reflection internally
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Vendored g2p — small code volume; blanket keep avoids reflection surprises
-keep class com.danemadsen.maise.g2p.** { *; }

# kotlinx-serialization — standard rules for the g2p package
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.danemadsen.maise.g2p.**$$serializer { *; }
-keepclassmembers class com.danemadsen.maise.g2p.** {
    *** Companion;
}
-keepclasseswithmembers class com.danemadsen.maise.g2p.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# g2p dependencies
-dontwarn org.slf4j.**
-dontwarn com.ibm.icu.**
-dontwarn org.apache.opennlp.**
