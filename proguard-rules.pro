# Keep obfuscation conservative: rename implementation code, but do not shrink or optimize
# plugin bytecode that depends on Paper, eco, libreforge, and version-specific NMS bridges.
-dontshrink
-dontoptimize
-dontpreverify
-ignorewarnings
-dontwarn **
-dontnote net.kyori.ansi.**
-dontnote com.willfp.ecoenchants.ReflectionUtilKt
-dontnote com.willfp.ecoenchants.proxy.**

-allowaccessmodification
-overloadaggressively
-useuniqueclassmembernames
-adaptclassstrings

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# Bukkit loads this class by the literal name in plugin.yml.
-keep class com.willfp.ecoenchants.EcoEnchantsPlugin { *; }

# eco.yml resolves version-specific proxies from this package by name.
-keep,includedescriptorclasses class com.willfp.ecoenchants.proxy.** { *; }
-keep,includedescriptorclasses interface com.willfp.ecoenchants.**Proxy { *; }

# The relocated libreforge loader uses its own config/category model.
-keep,includedescriptorclasses class com.willfp.ecoenchants.libreforge.loader.** { *; }

# Public API used by plugins that compileOnly depend on EcoEnchants.
-keep,includedescriptorclasses class com.willfp.ecoenchants.enchant.EcoEnchants { *; }
-keep,includedescriptorclasses interface com.willfp.ecoenchants.enchant.EcoEnchant { *; }
-keep,includedescriptorclasses interface com.willfp.ecoenchants.enchant.EcoEnchantLike { *; }
-keep,includedescriptorclasses class com.willfp.ecoenchants.enchant.EcoEnchantLevel { *; }
-keep,includedescriptorclasses class com.willfp.ecoenchants.enchant.VanillaEnchantmentsKt { *; }
-keep,includedescriptorclasses class com.willfp.ecoenchants.enchant.VanillaEnchantmentData { *; }
-keep,includedescriptorclasses interface com.willfp.ecoenchants.enchant.EcoCraftEnchantmentManagerProxy { *; }
-keep,includedescriptorclasses class com.willfp.ecoenchants.display.EnchantmentFormattingKt { public *; }
-keep,includedescriptorclasses class com.willfp.ecoenchants.target.EnchantFinder { *; }
-keep,includedescriptorclasses class com.willfp.ecoenchants.target.EnchantmentTargets { *; }
-keep,includedescriptorclasses interface com.willfp.ecoenchants.target.EnchantmentTarget { *; }
-keep,includedescriptorclasses class com.willfp.ecoenchants.type.EnchantmentTypes { *; }
-keep,includedescriptorclasses class com.willfp.ecoenchants.type.EnchantmentType { *; }
-keep,includedescriptorclasses class com.willfp.ecoenchants.rarity.EnchantmentRarities { *; }
-keep,includedescriptorclasses class com.willfp.ecoenchants.rarity.EnchantmentRarity { *; }

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
