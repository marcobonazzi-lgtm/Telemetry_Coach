package org.simulator.ui.time_line_view.Track.Curve;

import org.simulator.ui.time_line_view.Track.TrackGeometry;

import java.util.List;

final class CurveRecognition {
    private CurveRecognition() {}

    static final class Result {
        final List<CurveSegment> curves;
        final double[] sMeters;
        Result(List<CurveSegment> curves, double[] sMeters){
            this.curves = curves; this.sMeters = sMeters;
        }
    }

    static Result detect(TrackGeometry g){
        double[] px = g.px(), py = g.py();
        double[] s = buildS(px, py);
        List<CurveSegment> curves = extract(px, py, s);
        return new Result(curves, s);
    }

    static List<CurveSegment> extract(double[] px, double[] py, double[] s){
        int n = Math.min(Math.min(px.length, py.length), s.length);
        if (n < 5) return java.util.List.of();

        double totalLen = s[n-1]; // Lunghezza totale geometria

        double[] th = new double[n];
        for (int i=1;i<n;i++){
            double dx = px[i]-px[i-1], dy = py[i]-py[i-1];
            th[i] = Math.atan2(dy, dx);
        }
        for (int i=1;i<n;i++){
            double d = th[i] - th[i-1];
            while (d > Math.PI){ th[i] -= 2*Math.PI; d -= 2*Math.PI; }
            while (d < -Math.PI){ th[i] += 2*Math.PI; d += 2*Math.PI; }
        }

        double smoothM = 8.0;
        int W = Math.max(1, (int)Math.round((smoothM / Math.max(1e-3, s[n-1])) * n));
        double[] k = new double[n];
        for (int i=1;i<n;i++){
            double ds = Math.max(1e-6, s[i]-s[i-1]);
            k[i] = (th[i]-th[i-1]) / ds;
        }
        double[] ks = boxcar(k, W);

        double[] abs = new double[n];
        for (int i=0;i<n;i++) abs[i] = Math.abs(ks[i]);
        double thr = percentile(abs, 60);
        thr = clamp(thr, 0.003, 0.08);

        java.util.ArrayList<CurveSegment> out = new java.util.ArrayList<>();
        int idx = 1;
        int i = 0;
        while (i < n){
            while (i < n && Math.abs(ks[i]) < thr) i++;
            if (i >= n) break;
            int start = i;
            int sign = ks[i] >= 0 ? 1 : -1;

            int last = i;
            while (i < n && (ks[i]*sign > 0 || Math.abs(ks[i]) < thr*0.7)) { last = i; i++; }
            int end = last;

            double mergeGapM = 12.0;
            int j = i;
            while (j < n){
                int gapStart = j;
                while (j<n && Math.abs(ks[j]) < thr) j++;
                if (j>=n) break;
                int nextSign = ks[j] >= 0 ? 1 : -1;
                double gapM = s[j] - s[gapStart];
                if (gapM <= mergeGapM && nextSign == sign){
                    while (j<n && (ks[j]*sign > 0 || Math.abs(ks[j])<thr*0.7)){ end = j; j++; }
                    i = j;
                } else {
                    break;
                }
            }

            int apex = start;
            double kmax = 0;
            for (int t = start; t <= end; t++){
                double ak = Math.abs(ks[t]);
                if (ak > kmax){ kmax = ak; apex = t; }
            }

            double minLenM = 35.0;
            if (s[end] - s[start] < minLenM) continue;

            CurveSegment.Dir dir = (sign > 0) ? CurveSegment.Dir.LEFT : CurveSegment.Dir.RIGHT;

            // Calcolo percentuali fallback
            double inPct = s[start] / Math.max(1, totalLen);
            double outPct = s[end] / Math.max(1, totalLen);

            out.add(new CurveSegment(idx++, null,
                    inPct, outPct,
                    start, apex, end,
                    s[start], s[apex], s[end],
                    px[start], py[start], px[apex], py[apex], px[end], py[end],
                    dir, ks[apex]));
        }
        return out;
    }

    private static double[] buildS(double[] px, double[] py){
        int n = Math.min(px.length, py.length);
        double[] s = new double[n];
        s[0] = 0;
        for (int i=1;i<n;i++){
            double dx = px[i]-px[i-1], dy = py[i]-py[i-1];
            s[i] = s[i-1] + Math.hypot(dx, dy);
        }
        return s;
    }

    private static double[] boxcar(double[] a, int w){
        if (w<=1) return a.clone();
        double[] b = new double[a.length];
        int n = a.length;
        for (int i=0;i<n;i++){
            int L = Math.max(0, i-w), R = Math.min(n-1, i+w);
            double sum = 0; int c=0;
            for (int j=L;j<=R;j++){ sum+=a[j]; c++; }
            b[i] = sum / Math.max(1, c);
        }
        return b;
    }

    private static double percentile(double[] a, int p){
        double[] b = a.clone();
        java.util.Arrays.sort(b);
        int i = (int)Math.round((p/100.0) * (b.length-1));
        return b[Math.max(0, Math.min(b.length-1, i))];
    }
    private static double clamp(double v, double lo, double hi){ return Math.max(lo, Math.min(hi, v)); }
}