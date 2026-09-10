# Riverbank has no reflection-driven code and no JavaScript bridge, so the
# default optimised rules are enough. These two lines only keep crash traces
# readable for users who send them to us by hand.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# AndroidX Preference inflates fragments by class name from XML.
-keep class androidx.preference.** { *; }
-keep class org.riverbank.shop.SettingsActivity$SettingsFragment { *; }
