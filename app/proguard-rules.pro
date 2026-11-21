# Shizuku instantiates the user service by class name in the shell process.
-keep class de.xitee.torchbrightness.curve.DumpService { <init>(...); *; }
-keep class * extends android.os.Binder { *; }
