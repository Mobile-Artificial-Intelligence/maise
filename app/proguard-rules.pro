# Preserve line numbers in stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ONNX Runtime — uses JNI and reflection internally
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
