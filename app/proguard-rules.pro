# SipTimer has no reflection-based code of its own.
# Room, DataStore, Compose and AndroidX ship their own consumer rules.
# Receivers and the Application class are referenced from the manifest (AGP keeps them).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
