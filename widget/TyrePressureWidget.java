package org.simulator.widget;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.List;

public class TyrePressureWidget {

    public static TitledPane build(Lap lap) {
        double fl = avgLap(lap, Channel.TIRE_PRESSURE_FL);
        double fr = avgLap(lap, Channel.TIRE_PRESSURE_FR);
        double rl = avgLap(lap, Channel.TIRE_PRESSURE_RL);
        double rr = avgLap(lap, Channel.TIRE_PRESSURE_RR);
        return titledGrid("Pressioni gomme (psi, media giro)", fl, fr, rl, rr);
    }

    public static TitledPane buildFromLaps(List<Lap> laps) {
        double fl = avgLaps(laps, Channel.TIRE_PRESSURE_FL);
        double fr = avgLaps(laps, Channel.TIRE_PRESSURE_FR);
        double rl = avgLaps(laps, Channel.TIRE_PRESSURE_RL);
        double rr = avgLaps(laps, Channel.TIRE_PRESSURE_RR);
        return titledGrid("Pressioni gomme (psi, media sessione)", fl, fr, rl, rr);
    }

    // ---- calcolo medie ----
    private static double avgLap(Lap lap, Channel ch){
        double sum=0; int n=0;
        for (Sample s: lap.samples){
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v!=null && !v.isNaN()){ sum+=v; n++; }
        }
        return n>0 ? sum/n : Double.NaN;
    }
    private static double avgLaps(List<Lap> laps, Channel ch){
        double sum=0; int n=0;
        for (Lap lap: laps){
            double m = avgLap(lap, ch);
            if (!Double.isNaN(m)){ sum+=m; n++; }
        }
        return n>0 ? sum/n : Double.NaN;
    }

    // ---- UI ----
    private static TitledPane titledGrid(String title, double fl, double fr, double rl, double rr){
        GridPane g = new GridPane();
        g.setHgap(20); g.setVgap(20); g.setPadding(new Insets(6));
        g.add(tile("FL", fl), 0, 0);
        g.add(tile("FR", fr), 1, 0);
        g.add(tile("RL", rl), 0, 1);
        g.add(tile("RR", rr), 1, 1);
        TitledPane pane = new TitledPane(title, g);
        pane.setCollapsible(false);
        return pane;
    }

    private static StackPane tile(String name, double psi){
        Rectangle rect = new Rectangle(60, 80);
        rect.setArcWidth(10); rect.setArcHeight(10);
        rect.setFill(colorForPsi(psi)); rect.setStroke(Color.BLACK);
        String txt = Double.isNaN(psi) ? "--" : String.format("%.1f psi", psi);
        return new StackPane(rect, new Label(name + "\n" + txt));
    }

    private static Color colorForPsi(double p){
        if (Double.isNaN(p)) return Color.GRAY;
        if (p < 24) return Color.LIGHTBLUE;
        if (p < 26) return Color.LIMEGREEN;
        if (p <= 28) return Color.GREEN;
        if (p <= 30) return Color.GOLD;
        return Color.CRIMSON;
    }
}
