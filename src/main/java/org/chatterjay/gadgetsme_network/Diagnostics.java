package org.chatterjay.gadgetsme_network;

/**
 * Full-chain diagnostic logging for troubleshooting order/paste issues.
 * Everything is gated behind the common-config {@code debugLog} flag
 * ({@code config/gadgetsme_network-common.toml}) so normal play stays silent.
 * All messages go to the mod's SLF4J logger tagged {@code [GadgetsME/diag]}.
 */
public final class Diagnostics {

    private Diagnostics() {
    }

    public static boolean enabled() {
        try {
            return Config.SPEC.isLoaded() && Config.DEBUG_LOG.get();
        } catch (Exception e) {
            return false; // spec not loaded yet during early startup
        }
    }

    public static void log(String message, Object... args) {
        if (!enabled()) return;
        Gadgetsme_network.LOGGER.info("[GadgetsME/diag] " + message, args);
    }
}
