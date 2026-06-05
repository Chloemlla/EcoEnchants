---
title: "API"
sidebar_position: 8
---

EcoEnchants exposes plugin APIs that you can build against from your own plugin. This page shows how to add it as a dependency.

## Backend contract

Closed-source commercial builds use the [`/api/ecoenchants` backend contract](commercialization-and-license-api) for required online license verification.

Secure remote operations, controlled file management, redacted exports, and disaster-recovery workflows are specified in the [`/api/ecoenchants/v1` secure RPC operations contract](secure-rpc-operations-api).

## Adding the dependency

1. Add the Auxilor repository and the EcoEnchants dependency to your `build.gradle.kts`:

   ```kotlin
   repositories {
       maven("https://repo.auxilor.io/repository/maven-public/")
   }

   dependencies {
       compileOnly("com.willfp:EcoEnchants:<version>")
   }
   ```

The latest version available on the repo can be found [here](https://github.com/Auxilor/EcoEnchants/tags)

<hr/>

## Where to go next

- **The framework:** EcoEnchants is built on [eco](https://github.com/Auxilor/eco), where most shared APIs live.
- **Backend contract:** required online license verification is documented in the [`/api/ecoenchants` backend contract](commercialization-and-license-api).
- **Secure operations:** remote maintenance and disaster-recovery requirements are documented in the [`/api/ecoenchants/v1` secure RPC operations contract](secure-rpc-operations-api).
- **Make an enchantment from config:** the [How to Make an Enchantment](how-to-make-a-custom-enchant) guide.
