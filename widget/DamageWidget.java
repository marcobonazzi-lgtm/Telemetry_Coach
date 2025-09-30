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

public class DamageWidget {

    public static TitledPane build(Lap lap) {
        double front = avgLap(lap, Channel.CAR_DAMAGE_FRONT);
        double left  = avgLap(lap, Channel.CAR_DAMAGE_LEFT);
        double rear  = avgLap(lap, Channel.CAR_DAMAGE_REAR);
        double right = avgLap(lap, Channel.CAR_DAMAGE_RIGHT);
        return titledGrid("Danni vettura (media giro)", front, left, rear, right);
    }

    public static TitledPane buildFromLaps(List<Lap> laps) {
        double front = avgLaps(laps, Channel.CAR_DAMAGE_FRONT);
        double left  = avgLaps(laps, Channel.CAR_DAMAGE_LEFT);
        double rear  = avgLaps(laps, Channel.CAR_DAMAGE_REAR);
        double right = avgLaps(laps, Channel.CAR_DAMAGE_RIGHT);
        return titledGrid("Danni vettura (media sessione)", front, left, rear, right);
    }

    // ---- calcolo medie ----
    private static double avgLap(Lap lap, Channel ch){
        double sum=0; int n=0;
        for (Sample s: lap.samples){
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v!=null && !v.isNaN()){ sum+=normalize(v); n++; }
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

    /** porta a scala 0..100 se i dati arrivano 0..1 */
    private static double normalize(double v){
        if (v <= 1.0) return v*100.0;
        return v; // già percentuale
    }

    // ---- UI ----
    private static TitledPane titledGrid(String title, double front, double left, double rear, double right){
        GridPane g = new GridPane();
        g.setHgap(14); g.setVgap(14); g.setPadding(new Insets(6));
        g.add(tile("Front", front), 1, 0);
        g.add(tile("Left",  left ), 0, 1);
        g.add(tile("Right", right), 2, 1);
        g.add(tile("Rear",  rear ), 1, 2);

        TitledPane pane = new TitledPane(title, g);
        pane.setCollapsible(false);
        return pane;
    }

    private static StackPane tile(String name, double pct){
        Rectangle rect = new Rectangle(70, 40);
        rect.setArcWidth(10); rect.setArcHeight(10);
        rect.setFill(colorForDamage(pct)); rect.setStroke(Color.BLACK);
        String txt = Double.isNaN(pct) ? "--" : String.format("%.0f%%", pct);
        return new StackPane(rect, new Label(name + "\n" + txt));
    }

    private static Color colorForDamage(double pct){
        if (Double.isNaN(pct)) return Color.GRAY;
        if (pct < 10) return Color.LIMEGREEN;
        if (pct < 30) return Color.GOLD;
        return Color.CRIMSON;
    }
}
