package com.slabbed.compat;

import com.slabbed.loader.Loader;

/** Version seams. This jar serves one Minecraft version; the constant is read once from the loader. */
public final class MinecraftVersions {
    /** True on Minecraft 26.3 and newer; false on 26.2. */
    public static final boolean AT_LEAST_26_3 = atLeast("26.3");

    private MinecraftVersions() {
    }

    static boolean atLeast(String version) {
        return compare(Loader.minecraftVersion(), version) >= 0;
    }

    /** Numeric dotted comparison; a non-numeric tail (a snapshot suffix) sorts as zero. */
    static int compare(String a, String b) {
        String[] x = a.split("[.\\-+ ]"), y = b.split("[.\\-+ ]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? parse(x[i]) : 0;
            int yi = i < y.length ? parse(y[i]) : 0;
            if (xi != yi) {
                return Integer.compare(xi, yi);
            }
        }
        return 0;
    }

    private static int parse(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
