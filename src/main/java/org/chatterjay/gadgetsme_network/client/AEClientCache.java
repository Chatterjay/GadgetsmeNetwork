package org.chatterjay.gadgetsme_network.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache for AE item counts, populated by server responses.
 */
public class AEClientCache {
    private static final Map<ResourceLocation, Integer> aeCounts = new HashMap<>();

    public static void updateCounts(Map<ResourceLocation, Integer> counts) {
        aeCounts.clear();
        aeCounts.putAll(counts);
    }

    public static void clear() {
        aeCounts.clear();
    }

    public static int getCount(ResourceLocation itemId) {
        return aeCounts.getOrDefault(itemId, 0);
    }
}
