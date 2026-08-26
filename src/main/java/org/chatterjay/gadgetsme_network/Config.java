package org.chatterjay.gadgetsme_network;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * Full-chain diagnostic logging (right-click audit, batch ordering, AE2
     * planning/submission, paste-time network pulls, paste execution) intended
     * for troubleshooting. Written to the mod's SLF4J logger at INFO level.
     */
    public static final ModConfigSpec.BooleanValue DEBUG_LOG = BUILDER
            .comment("Enable verbose diagnostic logging of the whole order & paste chain",
                    "(audits, AE2 planning/submission, network pulls, paste execution).",
                    "Intended for troubleshooting - leave off for normal play.",
                    "Log tag: [GadgetsME/diag]")
            .define("debugLog", false);

    static final ModConfigSpec SPEC = BUILDER.build();
}
