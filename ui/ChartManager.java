package org.simulator.ui;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import org.simulator.canale.Lap;
import org.simulator.ui.asix_pack.AxisChoice;
import org.simulator.ui.ChartInteractions;

import java.util.ArrayList;
import java.util.List;

public class ChartManager {

    public LineChart<Number,Number> buildChart(String title, String xTitle, String yTitle) {
        NumberAxis xAxis = new NumberAxis(); xAxis.setLabel(xTitle);
        NumberAxis yAxis = new NumberAxis(); yAxis.setLabel(yTitle);
        LineChart<Number,Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setTitle(title);
        ChartInteractions.applyDataBoundsFromSeries(chart);

        // registro il grafico per lo styling dinamico
        ChartStyles.register(chart);
        return chart;
    }


    public void renderChart(LineChart<Number,Number> chart,
                            ChartPane.PlotType pt,
                            Lap refLap,
                            Lap cmpLap,
                            AxisChoice axis) {

        if (refLap == null || axis == null) { chart.getData().clear(); return; }

        switch (pt) {
            case PEDAL_FORCE -> {
                ChartRenderers.renderPedalForce(chart, refLap, cmpLap, axis);
                ((NumberAxis) chart.getXAxis()).setLabel(axis.label);
                ((NumberAxis) chart.getYAxis()).setLabel("Force (N)");
                ChartInteractions.applyDataBoundsFromSeries(chart);
                ChartStyles.reapply(chart);
                return;
            }
            case SEAT_FORCE -> {
                ChartRenderers.renderSeatForce(chart, refLap, cmpLap, axis);
                ((NumberAxis) chart.getXAxis()).setLabel(axis.label);
                ((NumberAxis) chart.getYAxis()).setLabel("Force (N)");
                ChartInteractions.applyDataBoundsFromSeries(chart);
                ChartStyles.reapply(chart);
                return;
            }
            default -> { /* continua sotto */ }
        }

        SeriesBundle ref = SeriesBundle.extract(refLap, axis);
        SeriesBundle cmp = (cmpLap != null) ? SeriesBundle.extract(cmpLap, axis) : null;

        List<XYChart.Series<Number,Number>> series = new ArrayList<>();
        MinMax mm = new MinMax();

        String yLabel = switch (pt) {
            case SPEED_DIST     -> "Speed [km/h]";
            case THR_BRAKE_DIST -> "%";
            case STEERING_DIST  -> "Angle [deg]";
            case RPM_TIME       -> "RPM";
            case FFB_FORCE      -> "FFB (unità CSV)";

            default             -> "Y";
        };

        switch (pt) {
            case SPEED_DIST -> {
                addSeries(series, "Speed (rif)", ref.x, ref.speed, mm);
                if (cmp != null) addSeries(series, "Speed (ghost)", cmp.x, cmp.speed, mm);
            }
            case THR_BRAKE_DIST -> {
                List<Double> thR = normalizePercentList(ref.throttle);
                List<Double> brR = normalizePercentList(ref.brake);
                addSeries(series, "Throttle (rif)", ref.x, thR, mm);
                addSeries(series, "Brake (rif)",    ref.x, brR, mm);

                if (hasAnyFinite(ref.clutch)) {
                    List<Double> clR = invert01List(normalize01List(ref.clutch));
                    addSeries(series, "Clutch (rif)", ref.x, clR, mm);
                }
                if (cmp != null) {
                    List<Double> thC = normalizePercentList(cmp.throttle);
                    List<Double> brC = normalizePercentList(cmp.brake);
                    addSeries(series, "Throttle (ghost)", cmp.x, thC, mm);
                    addSeries(series, "Brake (ghost)",    cmp.x, brC, mm);
                    if (hasAnyFinite(cmp.clutch)) {
                        List<Double> clC = invert01List(normalize01List(cmp.clutch));
                        addSeries(series, "Clutch (ghost)", cmp.x, clC, mm);
                    }
                }
            }
            case STEERING_DIST -> {
                addSeries(series, "Steering (rif)", ref.x, ref.steering, mm);
                if (cmp != null) addSeries(series, "Steering (ghost)", cmp.x, cmp.steering, mm);
            }
            case RPM_TIME -> {
                addSeries(series, "RPM (rif)", ref.x, ref.rpm, mm);
                if (cmp != null) addSeries(series, "RPM (ghost)", cmp.x, cmp.rpm, mm);
            }
            case FFB_FORCE -> {
                addSeries(series, "FFB (rif)", ref.x, ref.ffb, mm);
                if (cmp != null) addSeries(series, "FFB (ghost)", cmp.x, cmp.ffb, mm);
            }

            default -> {}
        }

        chart.getData().setAll(series);

        // applica gli stili (palette + ghost + legenda)
        chart.getData().setAll(series);
        javafx.application.Platform.runLater(() -> org.simulator.ui.ChartStyles.reapply(chart));


        ((NumberAxis) chart.getXAxis()).setLabel(axis.label);
        ((NumberAxis) chart.getYAxis()).setLabel(yLabel);

        if (mm.valid()){
            double padX = 0.02 * (mm.xMax - mm.xMin);
            double padY = 0.02 * (mm.yMax - mm.yMin);
            ChartInteractions.setDataBounds(chart,
                    mm.xMin - padX, mm.xMax + padX,
                    mm.yMin - padY, mm.yMax + padY);
        } else {
            ChartInteractions.applyDataBoundsFromSeries(chart);
        }
    }

    public void renderDelta(LineChart<Number,Number> chart,
                            Lap refLap, Lap cmpLap, AxisChoice axis) {
        if (refLap == null || cmpLap == null || axis == null) {
            chart.getData().clear();
            return;
        }
        var dt = org.simulator.ui.DeltaTimeService.delta(
                SeriesBundle.extract(refLap, axis),
                SeriesBundle.extract(cmpLap, axis),
                axis
        );

        List<XYChart.Series<Number,Number>> s = new ArrayList<>();
        addSeries(s, "Δ Lap Time cumul.", dt.x, dt.deltaTime);
        chart.getData().setAll(s);

        // anche qui aggiorniamo stile/legenda
        chart.getData().setAll(s);
        javafx.application.Platform.runLater(() -> org.simulator.ui.ChartStyles.reapply(chart));


        ChartInteractions.applyDataBoundsFromSeries(chart);
        ((NumberAxis)chart.getXAxis()).setLabel(axis.label);
        ((NumberAxis)chart.getYAxis()).setLabel("Δt [s]");
    }

    // ===== helper interni =====
    private void addSeries(List<XYChart.Series<Number,Number>> col,
                           String name, List<Double> x, List<Double> y) {
        addSeries(col, name, x, y, null);
    }

    private static final class MinMax {
        double xMin = Double.POSITIVE_INFINITY, xMax = Double.NEGATIVE_INFINITY;
        double yMin = Double.POSITIVE_INFINITY, yMax = Double.NEGATIVE_INFINITY;
        void add(List<Double> x, List<Double> y){
            int n = Math.min(x.size(), y.size());
            for (int i=0;i<n;i++){
                Double xv = x.get(i), yv = y.get(i);
                if (xv==null || yv==null || xv.isNaN() || yv.isNaN() || xv.isInfinite() || yv.isInfinite()) continue;
                double xd = xv, yd = yv;
                if (xd < xMin) xMin = xd; if (xd > xMax) xMax = xd;
                if (yd < yMin) yMin = yd; if (yd > yMax) yMax = yd;
            }
        }
        boolean valid(){ return (xMax > xMin) && (yMax > yMin); }
    }

    private void addSeries(List<XYChart.Series<Number,Number>> col,
                           String name, List<Double> x, List<Double> y,
                           MinMax mm) {
        XYChart.Series<Number,Number> s = new XYChart.Series<>();
        s.setName(name);
        int n = Math.min(x.size(), y.size());
        for (int i=0;i<n;i++) {
            Double xv = x.get(i), yv = y.get(i);
            if (xv == null || yv == null || xv.isNaN() || yv.isNaN()) continue;
            s.getData().add(new XYChart.Data<>(xv, yv));
        }
        col.add(s);
        if (mm != null) mm.add(x, y);
    }

    private static boolean hasAnyFinite(List<Double> a){
        if (a == null) return false;
        for (Double d : a) if (d != null && !d.isNaN() && !d.isInfinite()) return true;
        return false;
    }

    private static List<Double> normalizePercentList(List<Double> in){
        List<Double> out = new ArrayList<>(in.size());
        for (Double v : in){
            if (v == null || v.isNaN() || v.isInfinite()) { out.add(Double.NaN); continue; }
            double x = v;
            if (x <= 1.0001)    x *= 100.0;
            else if (x > 100.001 && x <= 10000.0) x /= 100.0;
            out.add(Math.max(0.0, Math.min(100.0, x)));
        }
        return out;
    }
    private static List<Double> normalize01List(List<Double> in) {
        List<Double> out = new ArrayList<>(in.size());
        for (Double v : in) out.add(normalize01(v));
        return out;
    }
    private static List<Double> invert01List(List<Double> in01){
        List<Double> out = new ArrayList<>(in01.size());
        for (Double v : in01) {
            double x = (v==null||v.isNaN()) ? 0.0 : Math.max(0.0, Math.min(1.0, v));
            out.add(1.0 - x);
        }
        return out;
    }
    private static double normalize01(Double v){
        if (v == null || v.isNaN() || v.isInfinite()) return 0.0;
        double x = v;
        if (x <= 1.0001) return Math.max(0.0, Math.min(1.0, x));
        if (x > 100.001 && x <= 10000.0) x /= 100.0;
        return Math.max(0.0, Math.min(1.0, x / 100.0));
    }
}
