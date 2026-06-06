# Keep obfuscation conservative: rename implementation code, but do not shrink or optimize
# plugin bytecode that depends on Paper, eco, libreforge, and version-specific NMS bridges.
-dontshrink
-dontoptimize
-dontpreverify
-ignorewarnings
-dontwarn **

-allowaccessmodification
-overloadaggressively
-useuniqueclassmembernames
-adaptclassstrings

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# Bukkit loads this class by the literal name in plugin.yml.
-keep class com.willfp.ecoenchants.EcoEnchantsPlugin { *; }

# eco.yml resolves version-specific proxies from this package by name.
-keep class com.willfp.ecoenchants.proxy.** { *; }
-keep interface com.willfp.ecoenchants.**Proxy { *; }

# Public API used by plugins that compileOnly depend on EcoEnchants.
-keep class com.willfp.ecoenchants.enchant.EcoEnchants { *; }
-keep interface com.willfp.ecoenchants.enchant.EcoEnchant { *; }
-keep interface com.willfp.ecoenchants.enchant.EcoEnchantLike { *; }
-keep class com.willfp.ecoenchants.enchant.EcoEnchantLevel { *; }
-keep class com.willfp.ecoenchants.enchant.VanillaEnchantments { *; }
-keep class com.willfp.ecoenchants.display.EnchantmentFormattingKt { public *; }
-keep class com.willfp.ecoenchants.target.EnchantFinder { *; }
-keep class com.willfp.ecoenchants.target.EnchantmentTargets { *; }
-keep interface com.willfp.ecoenchants.target.EnchantmentTarget { *; }
-keep class com.willfp.ecoenchants.type.EnchantmentTypes { *; }
-keep class com.willfp.ecoenchants.type.EnchantmentType { *; }
-keep class com.willfp.ecoenchants.rarity.EnchantmentRarities { *; }
-keep class com.willfp.ecoenchants.rarity.EnchantmentRarity { *; }

# Bukkit event discovery is annotation based.
-keepclassmembers class * {
    @org.bukkit.event.EventHandler <methods>;
}

# Kotlin/JVM interop points that can be called reflectively by Java plugins.
-keepclassmembers class * {
    @kotlin.jvm.JvmField <fields>;
    @kotlin.jvm.JvmStatic <methods>;
    @kotlin.jvm.JvmOverloads <methods>;
}
