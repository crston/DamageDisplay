package com.gmail.bobason01.util;

import org.bukkit.Location;

public interface DamageDisplayRenderer {
    void display(Location location, int damage, boolean isCritical, int skinIndex);
    void removeAll();
}
