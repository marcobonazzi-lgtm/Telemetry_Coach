package org.simulator.analisi_base.lap_analysis;

import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.simulator.analisi_base.lap_analysis.SampleMath.speedKmh;
import static org.simulator.analisi_base.lap_analysis.SampleMath.steeringDeg;

public final class CornerDetector {

    private static final int    MIN_SECTION_LEN_SAMPLES   = 8;
    private static final double MIN_DROP_KMH              = 7.5;
    private static final int    MINIMA_MARGIN_SAMPLES     = 4;
    private static final int    SMOOTH_WINDOW_SAMPLES     = 11;
    private static final int    MEDIAN_WIN_SAMPLES        = 5;
    private static final int    FALLBACK_MIN_CURVES       = 6;

    private static final double STEER_GATE_ON_DEG         = 3.5;
    private static final double STEER_GATE_OFF_DEG        = 2.0;
    private static final int    STEER_DEBOUNCE_ON         = 3;
    private static final int    STEER_DEBOUNCE_OFF        = 3;
    private static final int    MIN_STEER_DUR_SAMPLES     = 6;

    private static final double WARMUP_SPEED_MIN_KMH      = 40.0;
    private static final int    WARMUP_STABLE_SAMPLES     = 20;
    private static final double PIT_ENTRY_SPEED_MAX_KMH   = 35.0;
    private static final int    PIT_STABLE_SAMPLES        = 20;

    private static double speedUpFracFromApexKmh(double vApexKmh) {
        double f = 0.18;
        if (vApexKmh < 60)      f = 0.10;
        else if (vApexKmh < 100) f = 0.12;
        else if (vApexKmh < 140) f = 0.15;
        return Math.max(0.08, f);
    }

    public static final class CornerSegment {
        public final int id;
        public final double xStart, xApex, xEnd;
        public CornerSegment(int id, double xStart, double xApex, double xEnd) {
            this.id = id; this.xStart = xStart; this.xApex = xApex; this.xEnd = xEnd;
        }
    }

    public List<CornerSegment> detect(Lap lap) {
        List<CornerSegment> out = new ArrayList<>();
        if (lap == null || lap.samples == null || lap.samples.isEmpty()) return out;

        final int n = lap.samples.size();
        final boolean hasSteer = hasSteering(lap);
        if (n < 3) return out;

        double[] X    = new double[n];
        double[] Vkmh = new double[n];
        double[] Vms  = new double[n];
        double[] Sabs = new double[n];

        for (int i = 0; i < n; i++) {
            Sample sm = lap.samples.get(i);
            X[i]    = sm.distance();
            double v = speedKmh(sm);
            Vkmh[i] = v;
            Vms[i]  = v / 3.6;
            Sabs[i] = Math.abs(steeringDeg(sm));
        }

        Vkmh = SavGol.median(Vkmh, MEDIAN_WIN_SAMPLES);
        Vkmh = SavGol.smooth(Vkmh, SMOOTH_WINDOW_SAMPLES);
        Vms  = SavGol.smooth(Vms,  SMOOTH_WINDOW_SAMPLES);
        Sabs = SavGol.median(Sabs, MEDIAN_WIN_SAMPLES);

        int startIdx = findWarmupStart(Vkmh, WARMUP_SPEED_MIN_KMH, WARMUP_STABLE_SAMPLES);
        int endIdx   = findPitEntryStart(Vkmh, PIT_ENTRY_SPEED_MAX_KMH, PIT_STABLE_SAMPLES);
        if (endIdx <= startIdx) { startIdx = 0; endIdx = n - 1; }

        List<Integer> minima = findLocalMinimaPlateau(Vms, MINIMA_MARGIN_SAMPLES, startIdx, endIdx);
        minima.sort(Comparator.comparingDouble(i -> X[i]));

        int nextId = 1;
        for (int m : minima) {
            double vMin_ms  = Vms[m];
            double vMin_kmh = vMin_ms * 3.6;
            if (!Double.isFinite(vMin_ms)) continue;

            double frac    = speedUpFracFromApexKmh(vMin_kmh);
            double thrV_ms = vMin_ms * (1.0 + frac);

            double thrOn  = hasSteer ? STEER_GATE_ON_DEG  : Double.POSITIVE_INFINITY;
            double thrOff = hasSteer ? STEER_GATE_OFF_DEG : Double.POSITIVE_INFINITY;

            int i0 = searchBackHysteretic(Vms, Sabs, m, thrV_ms, thrOn, thrOff, startIdx);
            int i1 = searchFwdHysteretic (Vms, Sabs, m, thrV_ms, thrOn, thrOff, endIdx);
            if (i1 - i0 < MIN_SECTION_LEN_SAMPLES) continue;

            double dropKmh = (max(Vms, i0, i1) - vMin_ms) * 3.6;
            if (dropKmh < MIN_DROP_KMH) continue;

            if (hasSteer && !enoughSteerDuration(Sabs, i0, i1)) continue;

            out.add(new CornerSegment(nextId++, X[i0], X[m], X[i1]));
        }

        out = mergeOverlaps(out);
        out.sort(Comparator.comparingDouble(c -> c.xStart));
        out = pruneTooShort(out, 8.0);
        for (int i = 0; i < out.size(); i++) {
            CornerSegment c = out.get(i);
            out.set(i, new CornerSegment(i + 1, c.xStart, c.xApex, c.xEnd));
        }

        if (out.size() < FALLBACK_MIN_CURVES) {
            List<CornerSegment> fb = detectBySpeedMinima(lap, 9, 80.0);
            if (fb.size() > out.size()) out = fb;
        }

        return out;
    }

    public List<CornerSegment> detectBySpeedMinima(Lap lap, int smoothWindow, double minGapMeters) {
        List<CornerSegment> out = new ArrayList<>();
        if (lap == null || lap.samples == null || lap.samples.size() < 3) return out;

        int n = lap.samples.size();
        double[] X = new double[n];
        double[] Vkmh = new double[n];

        for (int i = 0; i < n; i++) {
            Sample sm = lap.samples.get(i);
            X[i]    = sm.distance();
            Vkmh[i] = speedKmh(sm);
        }

        Vkmh = SavGol.median(Vkmh, Math.max(3, 5));
        Vkmh = SavGol.smooth(Vkmh, Math.max(5, smoothWindow));

        List<Integer> mins = findLocalMinimaPlateauOnKmh(Vkmh, 3, 0, n - 1);
        mins.sort(Comparator.comparingDouble(i -> X[i]));

        List<Integer> filtered = new ArrayList<>();
        double lastApexX = Double.NEGATIVE_INFINITY;
        for (int idx : mins) {
            double xApex = X[idx];
            if (xApex - lastApexX >= minGapMeters) {
                filtered.add(idx);
                lastApexX = xApex;
            }
        }

        int id = 1;
        for (int m : filtered) {
            int i0 = Math.max(0, m - 8);
            int i1 = Math.min(n - 1, m + 8);
            out.add(new CornerSegment(id++, X[i0], X[m], X[i1]));
        }

        out = mergeOverlaps(out);
        out.sort(Comparator.comparingDouble(c -> c.xStart));
        return pruneTooShort(out, 8.0);
    }

    private static boolean hasSteering(Lap lap) {
        for (Sample s : lap.samples) {
            double st = steeringDeg(s);
            if (!Double.isNaN(st) && !Double.isInfinite(st)) return true;
        }
        return false;
    }

    private static int findWarmupStart(double[] vKmh, double thr, int stable) {
        int run = 0;
        for (int i = 0; i < vKmh.length; i++) {
            if (vKmh[i] >= thr) {
                if (++run >= stable) return i - stable + 1;
            } else run = 0;
        }
        return 0;
    }

    private static int findPitEntryStart(double[] vKmh, double thr, int stable) {
        int run = 0;
        for (int i = vKmh.length - 1; i >= 0; i--) {
            if (vKmh[i] <= thr) {
                if (++run >= stable) return i;
            } else run = 0;
        }
        return vKmh.length - 1;
    }

    private static int searchBackHysteretic(double[] v_ms, double[] s_deg, int i, double thrV_ms,
                                            double thrOn, double thrOff, int startIdx) {
        int consecOn = 0, consecOff = 0;
        boolean inCorner = false;
        while (i > startIdx) {
            boolean speedLow = v_ms[i] < thrV_ms;
            boolean steerOn  = s_deg[i] > thrOn;
            boolean steerOff = s_deg[i] < thrOff;

            if (steerOn) { consecOn++; consecOff = 0; }
            else if (steerOff) { consecOff++; consecOn = 0; }
            else { consecOn = consecOff = 0; }

            if (!inCorner && (speedLow || consecOn >= STEER_DEBOUNCE_ON)) inCorner = true;
            if (inCorner && !speedLow && consecOff >= STEER_DEBOUNCE_OFF) break;

            i--;
        }
        return Math.max(i, startIdx);
    }

    private static int searchFwdHysteretic(double[] v_ms, double[] s_deg, int i, double thrV_ms,
                                           double thrOn, double thrOff, int endIdx) {
        int consecOn = 0, consecOff = 0;
        boolean inCorner = false;
        while (i < endIdx) {
            boolean speedLow = v_ms[i] < thrV_ms;
            boolean steerOn  = s_deg[i] > thrOn;
            boolean steerOff = s_deg[i] < thrOff;

            if (steerOn) { consecOn++; consecOff = 0; }
            else if (steerOff) { consecOff++; consecOn = 0; }
            else { consecOn = consecOff = 0; }

            if (!inCorner && (speedLow || consecOn >= STEER_DEBOUNCE_ON)) inCorner = true;
            if (inCorner && !speedLow && consecOff >= STEER_DEBOUNCE_OFF) break;

            i++;
        }
        return Math.min(i, endIdx);
    }

    private static boolean enoughSteerDuration(double[] s_deg, int i0, int i1) {
        int cnt = 0;
        for (int i = i0; i <= i1; i++) if (s_deg[i] > STEER_GATE_ON_DEG) cnt++;
        return cnt >= MIN_STEER_DUR_SAMPLES;
    }

    private static List<Integer> findLocalMinimaPlateau(double[] a_ms, int margin, int lo, int hi) {
        List<Integer> out = new ArrayList<>();
        lo = Math.max(lo, margin);
        hi = Math.min(hi, a_ms.length - 1 - margin);
        for (int i = lo; i <= hi; i++) {
            if (a_ms[i] <= a_ms[i - 1] && a_ms[i] <= a_ms[i + 1]) {
                if (!out.isEmpty() && i - out.get(out.size() - 1) <= 2) {
                    int j = out.get(out.size() - 1);
                    if (a_ms[i] < a_ms[j]) out.set(out.size() - 1, i);
                } else {
                    out.add(i);
                }
            }
        }
        return out;
    }

    private static List<Integer> findLocalMinimaPlateauOnKmh(double[] a_kmh, int margin, int lo, int hi) {
        List<Integer> out = new ArrayList<>();
        lo = Math.max(lo, margin);
        hi = Math.min(hi, a_kmh.length - 1 - margin);
        for (int i = lo; i <= hi; i++) {
            if (a_kmh[i] <= a_kmh[i - 1] && a_kmh[i] <= a_kmh[i + 1]) {
                if (!out.isEmpty() && i - out.get(out.size() - 1) <= 2) {
                    int j = out.get(out.size() - 1);
                    if (a_kmh[i] < a_kmh[j]) out.set(out.size() - 1, i);
                } else {
                    out.add(i);
                }
            }
        }
        return out;
    }

    private static double max(double[] a, int lo, int hi) {
        double m = Double.NEGATIVE_INFINITY;
        for (int i = lo; i <= hi; i++) m = Math.max(m, a[i]);
        return m;
    }

    private static List<CornerSegment> pruneTooShort(List<CornerSegment> in, double minLenMeters) {
        List<CornerSegment> out = new ArrayList<>();
        for (CornerSegment c : in) {
            if (c.xEnd - c.xStart >= minLenMeters) out.add(c);
        }
        return out;
    }

    private static List<CornerSegment> mergeOverlaps(List<CornerSegment> in) {
        if (in.size() < 2) return in;
        in.sort(Comparator.comparingDouble(c -> c.xStart));
        List<CornerSegment> out = new ArrayList<>();
        CornerSegment cur = in.get(0);
        for (int k = 1; k < in.size(); k++) {
            CornerSegment n = in.get(k);
            if (n.xStart <= cur.xEnd) {
                cur = new CornerSegment(cur.id, cur.xStart, (cur.xApex + n.xApex) / 2.0, Math.max(cur.xEnd, n.xEnd));
            } else {
                out.add(cur);
                cur = n;
            }
        }
        out.add(cur);
        return out;
    }

    static final class SavGol {
        static double[] smooth(double[] y, int window) {
            int n = y.length, hw = Math.max(1, window / 2);
            double[] out = new double[n];
            for (int i = 0; i < n; i++) {
                int lo = Math.max(0, i - hw), hi = Math.min(n - 1, i + hw);
                double s = 0; int c = 0;
                for (int j = lo; j <= hi; j++) { s += y[j]; c++; }
                out[i] = s / c;
            }
            return out;
        }
        static double[] median(double[] y, int window) {
            int n = y.length, hw = Math.max(1, window / 2);
            double[] out = new double[n];
            double[] buf = new double[2 * hw + 1];
            for (int i = 0; i < n; i++) {
                int lo = Math.max(0, i - hw), hi = Math.min(n - 1, i + hw);
                int c = 0;
                for (int j = lo; j <= hi; j++) buf[c++] = y[j];
                java.util.Arrays.sort(buf, 0, c);
                out[i] = buf[c / 2];
            }
            return out;
        }
    }

}
