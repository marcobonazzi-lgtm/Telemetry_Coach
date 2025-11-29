package org.simulator.ui.settings;

public enum AppTheme {
    CLASSIC("Classico (Default)"),
    DARK_BLUE("Scuro (Professional)"),
    LIGHT("Chiaro (High Contrast)"),
    RACING("Racing (Aggressive)");

    private final String label;

    AppTheme(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}