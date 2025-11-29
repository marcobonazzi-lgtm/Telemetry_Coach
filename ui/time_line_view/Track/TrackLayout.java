package org.simulator.ui.time_line_view.Track;

import java.nio.file.Path;

public record TrackLayout(String name, Path folder) {
    @Override
    public String toString() {
        // Rende il nome leggibile nel menu a tendina (es. "layout_gp" -> "Layout Gp")
        if (name == null) return "Default";
        return name.replace("ks_", "").replace("_", " ").trim();
    }
}