package com.gmail.bobason01.util;

import org.bukkit.Location;

public interface DamageDisplayRenderer {
    void display(Location loc, int damage, boolean isCritical, int skinIndex, double[] offset);
    void removeAll();
}
