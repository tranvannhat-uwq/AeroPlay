# Add project specific ProGuard rules here.
## Rules for NewPipeExtractor (Mozilla Rhino JS engine)
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.annotation.**
