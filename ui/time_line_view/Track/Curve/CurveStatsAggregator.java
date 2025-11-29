package org.simulator.ui.time_line_view.Track.Curve;

import org.simulator.canale.Channel;
import java.util.*;

final class CurveStatsAggregator {
    // Storico cumulativo: Salviamo le velocità MINIME (Apex) per ogni curva
    private final Map<Integer, List<Double>> speedsByCurve = new HashMap<>();
    private final Map<Integer, List<Double>> timesByCurve = new HashMap<>();

    void reset() {
        speedsByCurve.clear();
        timesByCurve.clear();
    }

    void addLapSpeeds(java.util.List<? extends java.util.Map<Channel, Double>> samples,
                      double[] xAxis, java.util.List<CurveSegment> curves) {

        if (samples == null || samples.isEmpty() || curves == null || curves.isEmpty()) return;

        // Cache veloce
        double[] speeds = new double[samples.size()];
        for (int i=0; i<samples.size(); i++) {
            Double s = samples.get(i).get(Channel.SPEED);
            speeds[i] = (s == null) ? Double.NaN : s;
        }

        double totalGeoLen = 0;
        for(CurveSegment cs : curves) if(cs.sEnd() > totalGeoLen) totalGeoLen = cs.sEnd();
        if (totalGeoLen <= 1.0) totalGeoLen = 1.0;

        int nSamples = samples.size();

        for (CurveSegment c : curves) {
            double pctStart = c.inPct();
            double pctEnd   = c.outPct();

            if (pctStart <= 0 && pctEnd <= 0) {
                pctStart = c.sStart() / totalGeoLen;
                pctEnd   = c.sEnd()   / totalGeoLen;
            }

            int idxStart = (int)(pctStart * (nSamples - 1));
            int idxEnd   = (int)(pctEnd   * (nSamples - 1));

            idxStart = Math.max(0, Math.min(nSamples - 1, idxStart));
            idxEnd   = Math.max(idxStart, Math.min(nSamples - 1, idxEnd));

            if (idxEnd >= idxStart) {
                // --- LOGICA MODIFICATA: Cerca la velocità MINIMA (Apex) ---
                double minSpeed = Double.MAX_VALUE;
                boolean found = false;
                double sumSpeed = 0;
                int cnt = 0;

                for (int i = idxStart; i <= idxEnd; i++) {
                    double v = speeds[i];
                    if (!Double.isNaN(v)) {
                        if (v < minSpeed) minSpeed = v;
                        sumSpeed += v;
                        cnt++;
                        found = true;
                    }
                }

                // Salviamo la velocità minima (molto più utile per l'analisi di curva)
                if (found) {
                    speedsByCurve.computeIfAbsent(c.index(), k->new ArrayList<>()).add(minSpeed);
                }

                // Per il TEMPO usiamo ancora la velocità media (perché T = Spazio/V_media)
                double timeSec = Double.NaN;
                double avgSpeed = (cnt > 0) ? sumSpeed / cnt : Double.NaN;
                if (!Double.isNaN(avgSpeed) && avgSpeed > 1.0) {
                    double dist = c.length();
                    double speedMs = avgSpeed / 3.6;
                    timeSec = dist / speedMs;
                }
                if (!Double.isNaN(timeSec)) {
                    timesByCurve.computeIfAbsent(c.index(), k->new ArrayList<>()).add(timeSec);
                }
            }
        }
    }

    double deltaForCurve(int idx, double currentVal){
        java.util.List<Double> list = speedsByCurve.get(idx);
        if (list == null || list.isEmpty() || Double.isNaN(currentVal)) return 0.0;

        // Per la velocità minima: PIÙ ALTA è meglio (hai portato più velocità in curva)
        double best = list.stream().filter(d->!Double.isNaN(d)).mapToDouble(v->v).max().orElse(currentVal);
        double worst= list.stream().filter(d->!Double.isNaN(d)).mapToDouble(v->v).min().orElse(currentVal);

        if (Math.abs(best - worst) < 0.1) return 0.0;

        // Mappa: Se sei vicino al Best -> Verde (+1), se sei vicino al Worst -> Rosso (-1)
        double t = (currentVal - worst) / (best - worst);
        return t * 2.0 - 1.0;
    }

    CurveStat getCurveStats(int curveIndex, double currentSpeed, int currentGear) {
        List<Double> speeds = speedsByCurve.getOrDefault(curveIndex, List.of());
        List<Double> times = timesByCurve.getOrDefault(curveIndex, List.of());

        // Best Speed = La più alta delle velocità minime registrate (Apex più veloce)
        double bestSpeed = speeds.stream().filter(d->!Double.isNaN(d)).mapToDouble(v->v).max().orElse(Double.NaN);
        // Best Time = Il tempo più basso
        double bestTime = times.stream().filter(d->!Double.isNaN(d)).mapToDouble(v->v).min().orElse(Double.NaN);

        double currentTime = (!times.isEmpty()) ? times.get(times.size()-1) : Double.NaN;

        return new CurveStat(currentSpeed, bestSpeed, currentTime, bestTime, currentGear);
    }

    public static class CurveStat {
        public final double curSpeed, bestSpeed, curTime, bestTime;
        public final int gear;
        public CurveStat(double cs, double bs, double ct, double bt, int g) {
            this.curSpeed = cs; this.bestSpeed = bs;
            this.curTime = ct; this.bestTime = bt;
            this.gear = g;
        }
    }
}