package org.simulator.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Fx {

    private Fx() {}

    /** Griglia chiave→valore per statistiche (accetta anche Number generici). */
    public static Node buildStatsGrid(Map<String, ? extends Number> stats) {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);

        int r = 0;
        for (var e : stats.entrySet()) {
            Label k = new Label(e.getKey() + ":");
            Label v = new Label(formatNumber(e.getValue()));
            k.getStyleClass().add("stat-key");
            v.getStyleClass().add("stat-val");
            grid.add(k, 0, r);
            grid.add(v, 1, r);
            r++;
        }
        return grid;
    }

    /** Lista sola-lettura di note del coach. */
    public static Node buildNotesList(List<String> notes) {
        List<String> items = (notes == null) ? Collections.emptyList() : notes;
        ListView<String> lv = new ListView<>();
        lv.getItems().addAll(items);
        lv.setFocusTraversable(false);
        lv.setMouseTransparent(true); // rende la listview non interattiva
        // altezza “furba” in base al numero di elementi
        lv.setPrefHeight(Math.min(300, Math.max(120, items.size() * 28)));
        VBox box = new VBox(lv);
        VBox.setVgrow(lv, Priority.ALWAYS);
        return box;
    }

    private static String formatNumber(Number n) {
        if (n == null) return "-";
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) return "-";
        double ad = Math.abs(d);
        if (ad >= 100 || ad < 0.01) return String.format("%.3g", d);
        if (ad >= 10) return String.format("%.1f", d);
        return String.format("%.2f", d);
    }
}
