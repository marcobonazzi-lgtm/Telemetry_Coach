package org.simulator.ui.time_line_view.Track.AC;

import org.simulator.ui.time_line_view.Track.Curve.CurveSegment;
import org.simulator.ui.time_line_view.Track.TrackGeometry;

import java.util.ArrayList;
import java.util.List;

/** Proietta sezioni INI su Geometria calcolando la distanza reale in metri sulla linea. */
public final class AcCornerProjector {
    private AcCornerProjector(){}

    public static List<CurveSegment> project(TrackGeometry geom, List<AcSection> secs) {
        double[] px = geom.px();
        double[] py = geom.py();
        int n = Math.min(px.length, py.length);
        if (n < 5) return List.of();

        // 1. Ricostruiamo la "Distanza Geometrica Reale" punto per punto
        double[] dist = new double[n];
        dist[0] = 0;
        for (int i = 1; i < n; i++) {
            dist[i] = dist[i-1] + Math.hypot(px[i] - px[i-1], py[i] - py[i-1]);
        }
        double totalLength = dist[n-1];

        // Gestione loop chiuso (AI Line)
        double closingGap = Math.hypot(px[0] - px[n-1], py[0] - py[n-1]);
        if (closingGap < 100.0) totalLength += closingGap;

        List<CurveSegment> out = new ArrayList<>();
        int idx = 1;

        for (AcSection sec : secs) {
            if (!sec.isCorner()) continue;

            // Qui avviene la magia dell'allineamento:
            // Convertiamo la % del file INI in METRI REALI sulla linea geometrica
            double startM = sec.inFrac * totalLength;
            double endM   = sec.outFrac * totalLength;

            // Punto centrale per il pallino (Apex)
            double midFrac = (sec.inFrac + sec.outFrac) * 0.5;
            double midM = midFrac * totalLength;

            // Troviamo l'indice esatto nell'array della geometria che corrisponde a quei metri
            int iStart = findIndex(dist, startM);
            int iEnd   = findIndex(dist, endM);
            int iApex  = findIndex(dist, midM);

            // Calcolo curvatura e direzione
            double kApex = calculateKappa(px, py, iApex);
            CurveSegment.Dir dir = (kApex >= 0) ? CurveSegment.Dir.LEFT : CurveSegment.Dir.RIGHT;

            out.add(new CurveSegment(idx++, sec.name,
                    sec.inFrac, sec.outFrac, // Conserviamo le percentuali per i DATI
                    iStart, iApex, iEnd, // Usiamo gli indici calcolati per la GRAFICA
                    dist[iStart], dist[iApex], (iEnd < iStart ? totalLength + dist[iEnd] : dist[iEnd]),
                    px[iStart], py[iStart], px[iApex], py[iApex], px[iEnd], py[iEnd],
                    dir, kApex));
        }
        return out;
    }

    // Trova l'indice dell'array più vicino alla distanza d
    private static int findIndex(double[] dist, double d) {
        int lo = 0, hi = dist.length - 1;
        if (d >= dist[hi]) return hi;
        if (d <= dist[0]) return 0;

        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (dist[mid] < d) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private static double calculateKappa(double[] x, double[] y, int i) {
        int n = x.length;
        int p = (i - 5 + n) % n;
        int next = (i + 5) % n;
        return (x[i] - x[p]) * (y[next] - y[i]) - (y[i] - y[p]) * (x[next] - x[i]);
    }
}