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
import java.util.EnumMap;

public class BrakeThermalWidget {

    private static boolean hasBrakeData(Lap lap){
        if (lap == null || lap.samples == null) return false;
        for (Sample s : lap.samples){
            EnumMap<Channel, Double> v = s.values();
            if (v == null) continue;
            if (hasFinite(v.get(Channel.BRAKE_TEMP_FL))) return true;
            if (hasFinite(v.get(Channel.BRAKE_TEMP_FR))) return true;
            if (hasFinite(v.get(Channel.BRAKE_TEMP_RL))) return true;
            if (hasFinite(v.get(Channel.BRAKE_TEMP_RR))) return true;
        }
        return false;
    }
    private static boolean hasBrakeData(List<Lap> laps){
        if (laps == null) return false;
        for (Lap l : laps) if (hasBrakeData(l)) return true;
        return false;
    }
    private static boolean hasFinite(Double d){
        return d != null && !d.isNaN() && !d.isInfinite();
    }


    public static TitledPane build(Lap lap){
        if (!hasBrakeData(lap)){
            TitledPane tp = titledGrid("Freni (T media giro °C)", Double.NaN, Double.NaN, Double.NaN, Double.NaN);
            tp.setVisible(false); tp.setManaged(false);
            return tp;
        }

        double fl = avgLap(lap, Channel.BRAKE_TEMP_FL);
        double fr = avgLap(lap, Channel.BRAKE_TEMP_FR);
        double rl = avgLap(lap, Channel.BRAKE_TEMP_RL);
        double rr = avgLap(lap, Channel.BRAKE_TEMP_RR);
        return titledGrid("Freni (T media giro °C)", fl, fr, rl, rr);
    }

    public static TitledPane buildFromLaps(List<Lap> laps){
        if (!hasBrakeData(laps)){
            TitledPane tp = titledGrid("Freni (T media giro °C)", Double.NaN, Double.NaN, Double.NaN, Double.NaN);
            tp.setVisible(false); tp.setManaged(false);
            return tp;
        }

        double fl = avgLaps(laps, Channel.BRAKE_TEMP_FL);
        double fr = avgLaps(laps, Channel.BRAKE_TEMP_FR);
        double rl = avgLaps(laps, Channel.BRAKE_TEMP_RL);
        double rr = avgLaps(laps, Channel.BRAKE_TEMP_RR);
        return titledGrid("Freni (T media sessione °C)", fl, fr, rl, rr);
    }

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

    private static StackPane tile(String name, double t){
        Rectangle rect = new Rectangle(60, 80);
        rect.setArcWidth(10); rect.setArcHeight(10);
        rect.setFill(colorForTemp(t)); rect.setStroke(Color.BLACK);
        String txt = Double.isNaN(t) ? "--" : String.format("%.0f°C", t);
        return new StackPane(rect, new Label(name + "\n" + txt));
    }

    private static Color colorForTemp(double t){
        if (Double.isNaN(t)) return Color.GRAY;
        if (t < 150) return Color.LIGHTBLUE;
        if (t < 300) return Color.LIMEGREEN;
        if (t < 450) return Color.GOLD;
        return Color.CRIMSON;
    }
}
