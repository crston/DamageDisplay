// DamageDisplayRenderer.java
package com.gmail.bobason01.util;

import org.bukkit.Location;

public interface DamageDisplayRenderer {
    void display(Location location, int damage, boolean critical, int skinIndex,
                 double offsetX, double offsetY, double offsetZ);

    default void display(Location location, int damage, boolean critical, int skinIndex, double[] offset) {
        double ox = (offset != null && offset.length > 0) ? offset[0] : 0.0;
        double oy = (offset != null && offset.length > 1) ? offset[1] : 0.0;
        double oz = (offset != null && offset.length > 2) ? offset[2] : 0.0;
        display(location, damage, critical, skinIndex, ox, oy, oz);
    }

    void removeAll();
}
