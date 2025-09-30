package org.simulator.widget;

import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.List;

public class FuelWidget {

    public static TitledPane build(Lap lap){
        double start = firstNonNaN(lap, Channel.FUEL_LEVEL);
        double end   = lastNonNaN(lap, Channel.FUEL_LEVEL);
        double used  = (!Double.isNaN(start) && !Double.isNaN(end)) ? Math.max(0, start - end) : Double.NaN;
        VBox box = new VBox(6,
                label("Fuel start", start),
                label("Fuel end", end),
                label("Usato nel giro", used)
        );
        TitledPane tp = new TitledPane("Carburante (giro)", box);
        tp.setCollapsible(false);
        return tp;
    }


    // ---- helpers ----
    private static Label label(String k, double v){
        return new Label(k + ": " + (Double.isNaN(v) ? "n/d" : String.format("%.2f", v)));
    }
    private static double firstNonNaN(Lap lap, Channel ch){
        for (Sample s: lap.samples){
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v!=null && !v.isNaN()) return v;
        }
        return Double.NaN;
    }
    private static double lastNonNaN(Lap lap, Channel ch){
        for (int i=lap.samples.size()-1; i>=0; i--){
            Double v = lap.samples.get(i).values().getOrDefault(ch, Double.NaN);
            if (v!=null && !v.isNaN()) return v;
        }
        return Double.NaN;
    }
}
