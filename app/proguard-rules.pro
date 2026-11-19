# Warden is open source, so renaming classes hides nothing and only makes crash reports unreadable.
# Shrinking and optimising stay on.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
# Tor is native code called through JNI by these classes.
-keep class org.torproject.jni.** { *; }
-keep class net.freehaven.tor.control.** { *; }
