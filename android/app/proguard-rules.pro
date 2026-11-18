# FlareTune Android R8 / ProGuard rules.
# Compose, Lifecycle, Media3, OkHttp, and Coil ship their own consumer rules inside AAR/JAR artifacts.

# Preserve readable line numbers in release crash stack traces without leaking developer file paths.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
