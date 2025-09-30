package org.simulator.ui.time_line_view;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;

final class ReadoutBox {
    private final VBox root = new VBox(8);
    private final Label xL = new Label("-");
    private final Label tLapL = new Label("--.--");
    private final Row r1 = new Row(); private final Row r2 = new Row(); private final Row r3 = new Row();

    ReadoutBox(){
        Label title = new Label("Valori");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));
        GridPane g = new GridPane(); g.setHgap(8); g.setVgap(6);

        int r=0;
        g.add(new Label("X:"), 0, r); g.add(xL, 1, r++);
        g.add(new Label("Time lap:"), 0, r); g.add(tLapL, 1, r++);

        addRow(g, r++, r1);
        addRow(g, r++, r2);
        addRow(g, r++, r3);

        TitledPane tp = new TitledPane("Lettura puntuale", g);
        tp.setCollapsible(false);
        root.getChildren().addAll(title, tp);
        root.setPadding(new Insets(10));
        root.setMaxWidth(260);
    }

    private static void addRow(GridPane g, int r, Row row){
        g.add(row.k, 0, r); g.add(row.v, 1, r);
    }

    void setX(String s){ xL.setText(s); }

    void setTimeLap(Double sec){
        tLapL.setText(formatLapTime(sec));
    }

    void set1(String k, String v){ r1.set(k, v); }
    void set2(String k, String v){ r2.set(k, v); }
    void set3(String k, String v){ r3.set(k, v); }
    Node getRoot(){ return root; }

    private static String formatLapTime(Double seconds) {
        if (seconds == null || Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            return "—";
        }
        int totalMillis = (int) Math.round(seconds * 1000.0);
        int minutes = totalMillis / 60000;
        int secs = (totalMillis % 60000) / 1000;
        int millis = totalMillis % 1000;

        if (millis == 0) {
            return String.format(Locale.getDefault(), "%d:%02d", minutes, secs);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d.%03d", minutes, secs, millis);
        }
    }

    private static final class Row {
        final Label k = new Label("");
        final Label v = new Label("");
        void set(String kk, String vv) { k.setText(kk); v.setText(vv); }
    }
}
