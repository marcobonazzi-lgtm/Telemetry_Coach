package org.simulator.ui;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.ui.asix_pack.AxisChoice;

import java.util.ArrayList;
import java.util.List;

final class ChartRenderers {

    private ChartRenderers() {}

    static void renderPedalForce(LineChart<Number,Number> chart,
                                 Lap refLap, Lap cmpLap, AxisChoice axis) {
        chart.getData().clear();
        ((NumberAxis) chart.getYAxis()).setLabel("Force (N)");
        ((NumberAxis) chart.getXAxis()).setLabel(axis.label);

        int added = 0;
        added += addForceSeriesIfPresent(chart, refLap, axis, Channel.BRAKE_FORCE,    "Brake force (rif)");
        added += addForceSeriesIfPresent(chart, refLap, axis, Channel.THROTTLE_FORCE, "Throttle force (rif)");
        added += addForceSeriesIfPresent(chart, refLap, axis, Channel.CLUTCH_FORCE,   "Clutch force (rif)");
        if (added == 0) {
            added += addForceSeriesIfPresent(chart, refLap, axis, Channel.PEDAL_FORCE, "Pedal force (rif)");
        }

        if (cmpLap != null) {
            addForceSeriesIfPresent(chart, cmpLap, axis, Channel.BRAKE_FORCE,    "Brake force (ghost)");
            addForceSeriesIfPresent(chart, cmpLap, axis, Channel.THROTTLE_FORCE, "Throttle force (ghost)");
            addForceSeriesIfPresent(chart, cmpLap, axis, Channel.CLUTCH_FORCE,   "Clutch force (ghost)");
            if (added == 0) {
                addForceSeriesIfPresent(chart, cmpLap, axis, Channel.PEDAL_FORCE, "Pedal force (ghost)");
            }
        }
    }

    static void renderSeatForce(LineChart<Number,Number> chart,
                                Lap refLap, Lap cmpLap, AxisChoice axis) {
        chart.getData().clear();
        ((NumberAxis) chart.getYAxis()).setLabel("Force (N)");
        ((NumberAxis) chart.getXAxis()).setLabel(axis.label);

        int added = 0;
        added += addForceSeriesIfPresent(chart, refLap, axis, Channel.SEAT_FORCE_LEFT,  "Seat L (rif)");
        added += addForceSeriesIfPresent(chart, refLap, axis, Channel.SEAT_FORCE_RIGHT, "Seat R (rif)");
        added += addForceSeriesIfPresent(chart, refLap, axis, Channel.SEAT_FORCE_REAR,  "Seat POST (rif)");
        if (added == 0) {
            added += addForceSeriesIfPresent(chart, refLap, axis, Channel.SEAT_FORCE, "Seat (rif)");
        }

        if (cmpLap != null) {
            addForceSeriesIfPresent(chart, cmpLap, axis, Channel.SEAT_FORCE_LEFT,  "Seat L (ghost)");
            addForceSeriesIfPresent(chart, cmpLap, axis, Channel.SEAT_FORCE_RIGHT, "Seat R (ghost)");
            addForceSeriesIfPresent(chart, cmpLap, axis, Channel.SEAT_FORCE_REAR,  "Seat POST (ghost)");
            if (added == 0) {
                addForceSeriesIfPresent(chart, cmpLap, axis, Channel.SEAT_FORCE, "Seat (ghost)");
            }
        }
    }


    // ---- helper locali ----
    private static int addForceSeriesIfPresent(LineChart<Number,Number> chart,
                                               Lap lap,
                                               AxisChoice axis,
                                               Channel ch,
                                               String name) {
        if (lap == null) return 0;
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        ChartDataUtils.extractXY(lap, axis, ch, xs, ys);
        if (ys.isEmpty()) return 0;

        XYChart.Series<Number,Number> s = new XYChart.Series<>();
        s.setName(name);
        for (int i = 0; i < xs.size(); i++) {
            s.getData().add(new XYChart.Data<>(xs.get(i), ys.get(i)));
        }
        chart.getData().add(s);
        return 1;
    }
}
