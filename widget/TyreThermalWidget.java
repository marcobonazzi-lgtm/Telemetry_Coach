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

public class TyreThermalWidget {

    /** Widget per un singolo giro (usa le temperature 'Middle'). */
    public static TitledPane build(Lap lap) {
        double fl = avgLap(lap, Channel.TIRE_TEMP_MIDDLE_FL);
        double fr = avgLap(lap, Channel.TIRE_TEMP_MIDDLE_FR);
        double rl = avgLap(lap, Channel.TIRE_TEMP_MIDDLE_RL);
        double rr = avgLap(lap, Channel.TIRE_TEMP_MIDDLE_RR);
        return titledGrid("Pneumatici (T media giro)", fl, fr, rl, rr);
    }

    /** Widget per la media su più giri. */
    public static TitledPane buildFromLaps(List<Lap> laps) {
        double fl = avgLaps(laps, Channel.TIRE_TEMP_MIDDLE_FL);
        double fr = avgLaps(laps, Channel.TIRE_TEMP_MIDDLE_FR);
        double rl = avgLaps(laps, Channel.TIRE_TEMP_MIDDLE_RL);
        double rr = avgLaps(laps, Channel.TIRE_TEMP_MIDDLE_RR);
        return titledGrid("Pneumatici (media sessione)", fl, fr, rl, rr);
    }

    // ---------- helpers di calcolo (nessuna dipendenza esterna) ----------

    /** media di un canale su tutti i sample di un giro */
    private static double avgLap(Lap lap, Channel ch){
        double sum=0; int n=0;
        for (Sample s: lap.samples){
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v!=null && !v.isNaN()){ sum+=v; n++; }
        }
        return n>0 ? sum/n : Double.NaN;
    }

    /** media di un canale su tutti i giri (media semplice delle medie per giro) */
    private static double avgLaps(List<Lap> laps, Channel ch){
        double sum=0; int n=0;
        for (Lap lap: laps){
            double m = avgLap(lap, ch);
            if (!Double.isNaN(m)){ sum+=m; n++; }
        }
        return n>0 ? sum/n : Double.NaN;
    }

    // ---------- UI helpers ----------

    private static TitledPane titledGrid(String title, double fl, double fr, double rl, double rr){
        GridPane g = new GridPane();
        g.setHgap(20);
        g.setVgap(20);
        g.setPadding(new Insets(6));
        g.add(tyre("FL", fl), 0, 0);
        g.add(tyre("FR", fr), 1, 0);
        g.add(tyre("RL", rl), 0, 1);
        g.add(tyre("RR", rr), 1, 1);

        TitledPane pane = new TitledPane(title, g);
        pane.setCollapsible(false);
        return pane;
    }

    private static StackPane tyre(String name, double temp) {
        Color col = colorForTemp(temp);

        Rectangle rect = new Rectangle(60, 80);
        rect.setArcWidth(10);
        rect.setArcHeight(10);
        rect.setFill(col);
        rect.setStroke(Color.BLACK);

        Label lbl = new Label(name + "\n" + (Double.isNaN(temp) ? "--" : String.format("%.1f°C", temp)));
        lbl.setTextFill(Color.BLACK);

        return new StackPane(rect, lbl);
    }

    /** mappa temperatura → colore (freddo→caldo) */
    private static Color colorForTemp(double t) {
        if (Double.isNaN(t)) return Color.GRAY;
        if (t < 60)  return Color.LIGHTBLUE;
        if (t < 80)  return Color.LIGHTGREEN;
        if (t < 100) return Color.YELLOW;
        if (t < 120) return Color.ORANGE;
        return Color.RED;
    }
}
