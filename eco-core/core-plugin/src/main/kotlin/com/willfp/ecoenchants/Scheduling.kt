package com.willfp.ecoenchants

import com.willfp.eco.core.Eco
import org.bukkit.entity.Entity

/**
 * Run [block] on the region owning this entity: now if the current thread already owns it,
 * which is always the case off Folia, otherwise on the entity's next tick.
 *
 * The block is therefore **asynchronous** relative to this call, and it is not guaranteed to run
 * at all: the entity can be removed or go offline before its next tick. Anything that depends on
 * the block's result (or on it having run) must live inside [block], not after this call.
 */
internal inline fun Entity.runOwned(crossinline block: () -> Unit) {
    if (Eco.get().isOwnedByCurrentRegion(this)) {
        block()
    } else {
        plugin.scheduler.on(this).run { block() }
    }
}
