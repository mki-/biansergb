# Protocol layer is pure data manipulation used for frame encoding; keep it intact
# so the wire format stays predictable in release builds.
-keep class com.juzhen.ambient.protocol.** { *; }

-keepclassmembers enum com.juzhen.ambient.protocol.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Compose / ViewModel reflection entry points are handled by their own consumer rules.
-dontwarn org.jetbrains.annotations.**
