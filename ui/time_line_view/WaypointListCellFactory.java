package org.simulator.ui.time_line_view;

import javafx.scene.control.ListCell;
import org.simulator.ui.asix_pack.AxisChoice;

import java.util.function.Supplier;

/** Celle per ComboBox<Double> dei waypoint con formattazione coerente all'asse. */
public final class WaypointListCellFactory {
    private WaypointListCellFactory() {}

    public static ListCell<Double> forAxis(Supplier<AxisChoice> axisSupplier) {
        return new ListCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                AxisChoice axis = axisSupplier.get();
                setText(formatX(item, axis));
            }
        };
    }

    private static String formatX(double x, AxisChoice axis) {
        if (axis == null) return String.format(java.util.Locale.ITALIAN, "%.3f", x);
        if (axis.useDist)     return String.format(java.util.Locale.ITALIAN, "%.1f m", x);
        if (axis.useLapTime)  return String.format(java.util.Locale.ITALIAN, "%.3f s (lap)", x);
        if (axis.useAbsTime)  return String.format(java.util.Locale.ITALIAN, "%.3f s (abs)", x);
        return String.format(java.util.Locale.ITALIAN, "%.3f", x);
    }
}
