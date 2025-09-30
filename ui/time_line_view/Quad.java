package org.simulator.ui.time_line_view;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.Locale;

public final class Quad{
    public final VBox root = new VBox(6);
    final Label fl = new Label("-- °C"), fr = new Label("-- °C"), rl = new Label("-- °C"), rr = new Label("-- °C");
    public Quad(String title){
        Label t = new Label(title);
        t.setStyle("-fx-text-fill:#e5e7eb; -fx-font-weight:bold;");
        GridPane g = new GridPane(); g.setHgap(18); g.setVgap(10);
        add(g, "FL", fl, 0,0); add(g,"FR", fr,1,0);
        add(g, "RL", rl, 0,1); add(g,"RR", rr,1,1);
        VBox box = new VBox(6,t,g);
        box.setPadding(new javafx.geometry.Insets(8)); box.setStyle("-fx-background-color:#0f172a; -fx-background-radius:10;");
        root.getChildren().setAll(box);
    }
    static void add(GridPane g, String k, Label v, int c, int r){
        VBox cell = new VBox(new Label(k), v);
        cell.setAlignment(Pos.CENTER); cell.setStyle("-fx-text-fill:#e5e7eb;");
        ((Label)cell.getChildren().get(0)).setStyle("-fx-text-fill:#e5e7eb; -fx-opacity:.8;");
        v.setStyle("-fx-text-fill:#e5e7eb;");
        g.add(cell, c, r);
    }
    public void set(Double vfl, Double vfr, Double vrl, Double vrr, String unit){
        fl.setText(format(vfl, unit)); fr.setText(format(vfr, unit));
        rl.setText(format(vrl, unit)); rr.setText(format(vrr, unit));
    }
    private static String format(Double v, String u){
        return (v==null || v.isNaN()) ? "-- " + u : String.format(Locale.ITALIAN, "%.0f %s", v, u);
    }
}
