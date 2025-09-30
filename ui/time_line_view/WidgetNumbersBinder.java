package org.simulator.ui.time_line_view;

import javafx.scene.Node;
import javafx.scene.control.Label;

import java.util.Set;

/** Copia lo stile dei numeri dai label dentro il widget Pedali. */
public final class WidgetNumbersBinder {
    private WidgetNumbersBinder() {}

    public static void bindToPedalsFont(Node pedalsRoot, WidgetValueOverlay... overlays) {
        if (pedalsRoot == null || overlays == null || overlays.length == 0) return;

        Label sample = findSampleLabel(pedalsRoot);
        if (sample == null) return;

        for (WidgetValueOverlay o : overlays) {
            if (o != null) o.applyFrom(sample);
        }
    }

    /** Cerca una Label "di valore" nel widget pedali. */
    private static Label findSampleLabel(Node root) {
        try {
            Set<Node> labels = root.lookupAll(".label");
            Label best = null;
            for (Node n : labels) {
                if (n instanceof Label l) {
                    String t = l.getText();
                    // Preferisci una label con percentuale o una delle parole chiave
                    if (t != null && (t.contains("%")
                            || t.toLowerCase().contains("throttle")
                            || t.toLowerCase().contains("brake")
                            || t.toLowerCase().contains("clutch"))) {
                        return l;
                    }
                    if (best == null) best = l;
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
