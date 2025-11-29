package org.simulator.ui.export;

import org.knowm.xchart.*;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.markers.None;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

final class ChartGenerator {

    private ChartGenerator() {}

    // --- 1. Velocità vs Distanza (Overlay) ---
    static File speedOverlay(File dir, List<Lap> laps) throws IOException {
        XYChart chart = new XYChart(900, 500);
        chart.setTitle("Velocità vs Distanza (overlay)");
        chart.setXAxisTitle("Distanza [m]");
        chart.setYAxisTitle("Velocità [km/h]");

        for (Lap l : laps){
            double[] x = series(l, Channel.DISTANCE);
            double[] y = series(l, Channel.SPEED);
            if (x.length == 0 || y.length == 0) continue;

            var s = chart.addSeries("Lap " + l.index, x, y);
            s.setMarker(new None());
        }

        File out = new File(dir, "speed_overlay.png");
        BitmapEncoder.saveBitmap(chart, out.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);
        return out;
    }

    // --- 2. Throttle/Brake per singolo giro ---
    static File throttleBrake(File dir, Lap lap) throws IOException {
        XYChart chart = new XYChart(900, 500);
        chart.setTitle("Throttle/Brake vs Distanza – Lap " + lap.index);
        chart.setXAxisTitle("Distanza [m]");
        chart.setYAxisTitle("%");

        double[] x = series(lap, Channel.DISTANCE);
        double[] th = series(lap, Channel.THROTTLE);
        double[] br = series(lap, Channel.BRAKE);

        var s1 = chart.addSeries("Throttle", x, th); s1.setMarker(new None());
        var s2 = chart.addSeries("Brake", x, br);    s2.setMarker(new None());

        File out = new File(dir, "throttle_brake_lap"+lap.index+".png");
        BitmapEncoder.saveBitmap(chart, out.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);
        return out;
    }

    // --- 3. G-G Diagram (Scatter Plot) ---
    static File ggDiagram(File dir, List<Lap> laps) throws IOException {
        XYChart chart = new XYChart(700, 700);
        chart.setTitle("G-G Diagram (Gy vs Gx)");
        chart.setXAxisTitle("Longitudinale Gx");
        chart.setYAxisTitle("Laterale Gy");

        for (Lap l : laps){
            double[] gx = series(l, Channel.CG_ACCEL_LONGITUDINAL);
            double[] gy = series(l, Channel.CG_ACCEL_LATERAL);
            if (gx.length == 0 || gy.length == 0) continue;

            var s = chart.addSeries("Lap " + l.index, gx, gy);
            s.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        }

        File out = new File(dir, "gg_diagram.png");
        BitmapEncoder.saveBitmap(chart, out.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);
        return out;
    }

    // --- 4. Temperature Freni (Istogramma) ---
    static File brakeTemps(File dir, List<Lap> laps) throws IOException {
        CategoryChart chart = new CategoryChart(900, 500);
        chart.setTitle("Brake Temps – media per lap");
        chart.setXAxisTitle("Lap");
        chart.setYAxisTitle("°C");

        List<String> cats = new ArrayList<>();
        List<Double> fl = new ArrayList<>(), fr = new ArrayList<>(), rl = new ArrayList<>(), rr = new ArrayList<>();

        for (Lap l : laps){
            cats.add(Integer.toString(l.index));
            fl.add(mean(l, Channel.BRAKE_TEMP_FL));
            fr.add(mean(l, Channel.BRAKE_TEMP_FR));
            rl.add(mean(l, Channel.BRAKE_TEMP_RL));
            rr.add(mean(l, Channel.BRAKE_TEMP_RR));
        }

        chart.addSeries("FL", cats, fl);
        chart.addSeries("FR", cats, fr);
        chart.addSeries("RL", cats, rl);
        chart.addSeries("RR", cats, rr);

        File out = new File(dir, "brake_temps.png");
        BitmapEncoder.saveBitmap(chart, out.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);
        return out;
    }

    // --- 5. Temperature Gomme (Istogramma) ---
    static File tireTemps(File dir, List<Lap> laps) throws IOException {
        CategoryChart chart = new CategoryChart(900, 500);
        chart.setTitle("Tire Temps – media per lap");
        chart.setXAxisTitle("Lap");
        chart.setYAxisTitle("°C");

        List<String> cats = new ArrayList<>();
        List<Double> fl = new ArrayList<>(), fr = new ArrayList<>(), rl = new ArrayList<>(), rr = new ArrayList<>();

        for (Lap l : laps){
            cats.add(Integer.toString(l.index));
            fl.add(meanTriplet(l, Channel.TIRE_TEMP_INNER_FL, Channel.TIRE_TEMP_MIDDLE_FL, Channel.TIRE_TEMP_OUTER_FL));
            fr.add(meanTriplet(l, Channel.TIRE_TEMP_INNER_FR, Channel.TIRE_TEMP_MIDDLE_FR, Channel.TIRE_TEMP_OUTER_FR));
            rl.add(meanTriplet(l, Channel.TIRE_TEMP_INNER_RL, Channel.TIRE_TEMP_MIDDLE_RL, Channel.TIRE_TEMP_OUTER_RL));
            rr.add(meanTriplet(l, Channel.TIRE_TEMP_INNER_RR, Channel.TIRE_TEMP_MIDDLE_RR, Channel.TIRE_TEMP_OUTER_RR));
        }

        chart.addSeries("FL", cats, fl);
        chart.addSeries("FR", cats, fr);
        chart.addSeries("RL", cats, rl);
        chart.addSeries("RR", cats, rr);

        File out = new File(dir, "tire_temps.png");
        BitmapEncoder.saveBitmap(chart, out.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);
        return out;
    }

    // --- 6. Delta Time vs Distanza (FIXED: usa Lap.isComplete) ---
    static File deltaTime(File dir, List<Lap> laps) throws IOException {
        if (laps == null || laps.size() < 2) return null;

        // FIX: Trova il giro di riferimento (Best Lap) usando la logica nativa di Lap
        // Questo esclude automaticamente outlap, giri incompleti o non validi
        Lap ref = laps.stream()
                .filter(l -> l.isComplete(laps))
                .min(Comparator.comparingDouble(Lap::lapTimeSafe))
                .orElse(null);

        // Fallback: se nessun giro è valido (es. tutti outlap), prendiamo il primo disponibile
        if (ref == null) ref = laps.get(0);

        XYChart chart = new XYChart(900, 500);
        chart.setTitle("Delta Time vs Distanza (Ref: Lap " + ref.index + ")");
        chart.setXAxisTitle("Distanza [m]");
        chart.setYAxisTitle("Delta [s]");

        // Dati Reference
        double[] refDist = series(ref, Channel.DISTANCE);
        double[] refTime = series(ref, Channel.TIME);
        if (refDist.length == 0 || refTime.length == 0) return null;
        double refStartTime = refTime[0];

        boolean hasSeries = false;
        for (Lap l : laps) {
            if (l == ref) continue;

            // FIX: Filtra anche i giri da confrontare usando la logica nativa
            if (!l.isComplete(laps)) continue;

            double[] lDist = series(l, Channel.DISTANCE);
            double[] lTime = series(l, Channel.TIME);
            if (lDist.length == 0 || lTime.length == 0) continue;
            double lStartTime = lTime[0];

            List<Double> xData = new ArrayList<>();
            List<Double> yData = new ArrayList<>();
            double step = 10.0; // campionamento ogni 10m
            double maxDist = Math.min(lDist[lDist.length-1], refDist[refDist.length-1]);

            for (double d = 0; d < maxDist; d += step) {
                double tRef = interpolateTime(refDist, refTime, d) - refStartTime;
                double tLap = interpolateTime(lDist, lTime, d) - lStartTime;

                // Delta positivo = Lento, Negativo = Veloce
                double delta = tLap - tRef;

                xData.add(d);
                yData.add(delta);
            }

            if (!xData.isEmpty()) {
                var s = chart.addSeries("Lap " + l.index, xData, yData);
                s.setMarker(new None());
                s.setLineWidth(1.5f);
                hasSeries = true;
            }
        }

        if (!hasSeries) return null;

        // Linea dello zero
        var zeroSeries = chart.addSeries("Ref (Lap " + ref.index + ")",
                new double[]{0, refDist[refDist.length-1]},
                new double[]{0, 0});
        zeroSeries.setMarker(new None());
        zeroSeries.setLineColor(Color.BLACK);
        zeroSeries.setLineStyle(SeriesLines.DASH_DASH);

        File out = new File(dir, "delta_time.png");
        BitmapEncoder.saveBitmap(chart, out.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);
        return out;
    }

    // ------- HELPERS -------

    private static double interpolateTime(double[] dists, double[] times, double targetDist) {
        int idx = Arrays.binarySearch(dists, targetDist);
        if (idx >= 0) return times[idx];

        int insertionPoint = -idx - 1;
        if (insertionPoint == 0) return times[0];
        if (insertionPoint >= dists.length) return times[times.length - 1];

        double d1 = dists[insertionPoint - 1];
        double d2 = dists[insertionPoint];
        double t1 = times[insertionPoint - 1];
        double t2 = times[insertionPoint];

        double fraction = (targetDist - d1) / (d2 - d1);
        return t1 + fraction * (t2 - t1);
    }

    private static double[] series(Lap l, Channel c){
        var s = l.samples;
        if (s == null) return new double[0];
        return s.stream().mapToDouble(sm -> {
            var v = sm.values()==null?null:sm.values().get(c);
            return v==null?Double.NaN:v;
        }).filter(d -> !Double.isNaN(d)).toArray();
    }

    private static double mean(Lap l, Channel c){
        var s = l.samples; if (s==null || s.isEmpty()) return Double.NaN;
        return s.stream().mapToDouble(sm -> {
            var v = sm.values()==null?null:sm.values().get(c);
            return v==null?Double.NaN:v;
        }).filter(d -> !Double.isNaN(d)).average().orElse(Double.NaN);
    }

    private static double meanTriplet(Lap l, Channel a, Channel b, Channel c){
        return avg(Arrays.asList(mean(l,a), mean(l,b), mean(l,c)));
    }

    private static double avg(List<Double> d){
        return d.stream().filter(x -> !Double.isNaN(x)).mapToDouble(x->x).average().orElse(Double.NaN);
    }
}