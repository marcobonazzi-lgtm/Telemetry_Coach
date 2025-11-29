package org.simulator.importCSVFW;

import org.simulator.canale.Channel;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/**
 * Normalizza il canale frizione:
 * - individua la colonna CLUTCH/CLUTCH_POS via ChannelAliases
 * - rileva scala (0..1 o 0..100)
 * - decide se invertire:
 *      * regola A: >=70% dei campioni sopra metà scala (0.5/50)
 *      * regola B: mediana > 90% della scala (per casi "sempre 100%")
 * - applica inversione in-place
 */
public final class ClutchChannelNormalizer {

    private ClutchChannelNormalizer() {}

    /** Se >= 70% dei campioni sono sopra metà scala, invertiamo. */
    public static final double INVERT_THRESHOLD_RATIO = 0.70;
    /** Soglia mediana per forzare inversione (es. 100% costante): 90% della scala. */
    public static final double MEDIAN_FORCE_INVERT_RATIO = 0.90;

    /** Tolleranze per distinguere scale. */
    public static final double EPS_ONE = 1.05;
    public static final double EPS_HUNDRED = 105.0;

    public enum Scale { ZERO_TO_ONE, ZERO_TO_100, UNKNOWN }

    public static final class Detection {
        public final int index;             // indice colonna, -1 se assente
        public final Scale scale;           // scala rilevata
        public final boolean shouldInvert;  // true se conviene invertire
        public Detection(int index, Scale scale, boolean shouldInvert) {
            this.index = index; this.scale = scale; this.shouldInvert = shouldInvert;
        }
    }

    /** Trova l'indice della colonna frizione usando ChannelAliases. */
    public static int findClutchIndex(String[] header) {
        Objects.requireNonNull(header, "header");
        Map<String, Channel> alias = ChannelAliases.aliasMap();
        for (int i = 0; i < header.length; i++) {
            String norm = ChannelAliases.norm(header[i]);
            Channel ch = alias.get(norm);
            if (ch == Channel.CLUTCH || ch == Channel.CLUTCH_POS) {
                return i;
            }
        }
        return -1;
    }

    /** Analizza alcune righe per capire scala e se invertire. **/
    public static Detection analyze(List<List<String>> sampleRows, String[] header, int maxSamples) {
        int idx = findClutchIndex(header);
        if (idx < 0) return new Detection(-1, Scale.UNKNOWN, false);

        List<Double> vals = new ArrayList<>(maxSamples);
        for (List<String> row : sampleRows) {
            if (idx < row.size()) {
                Double v = parse(row.get(idx));
                if (v != null) {
                    vals.add(v);
                    if (vals.size() >= maxSamples) break;
                }
            }
        }
        if (vals.isEmpty()) return new Detection(idx, Scale.UNKNOWN, false);

        double max = Collections.max(vals);
        Scale scale = (max <= EPS_ONE) ? Scale.ZERO_TO_ONE
                : (max <= EPS_HUNDRED) ? Scale.ZERO_TO_100
                : Scale.UNKNOWN;

        double mid = (scale == Scale.ZERO_TO_ONE) ? 0.5 : 50.0;
        int above = 0;
        for (double v : vals) if (v > mid) above++;
        double ratio = vals.isEmpty() ? 0.0 : (above / (double) vals.size());

        // mediana per casi "sempre alto"
        Collections.sort(vals);
        double median = vals.get(vals.size() / 2);
        double scaleMax = (scale == Scale.ZERO_TO_ONE) ? 1.0 : 100.0;

        boolean forceMedianInvert = (median >= MEDIAN_FORCE_INVERT_RATIO * scaleMax);

        // >>> NEW: fallback "estremo costante" (quasi tutti sotto 5% o sopra 95%)
        double nearZeroThr = 0.05 * scaleMax;
        double nearHundredThr = 0.95 * scaleMax;

        int nearZero = 0, nearHundred = 0;
        for (double v : vals) {
            if (v <= nearZeroThr) nearZero++;
            if (v >= nearHundredThr) nearHundred++;
        }
        boolean forceExtremeInvert = (nearZero >= 0.9 * vals.size())  // ~90% molto vicino a 0
                || (nearHundred >= 0.9 * vals.size()); // ~90% molto vicino a 100

        boolean shouldInvert = (ratio >= INVERT_THRESHOLD_RATIO) || forceMedianInvert || forceExtremeInvert;
        return new Detection(idx, scale, shouldInvert);
    }


    /** Inverte il valore frizione in-place: 0..1 -> 1-v, 0..100 -> 100-v. */
    public static void invertRowValue(List<String> row, Detection det, DecimalFormat df) {
        if (det == null || det.index < 0 || det.scale == Scale.UNKNOWN) return;
        int idx = det.index;
        if (idx >= row.size()) return;

        Double v = parse(row.get(idx));
        if (v == null) return;

        double out = (det.scale == Scale.ZERO_TO_ONE) ? (1.0 - v) : (100.0 - v);
        row.set(idx, df != null ? df.format(out) : Double.toString(out));
    }

    /** Formatter numerico US, nessun separatore migliaia. */
    public static DecimalFormat dfClutch() {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("0.###", sym);
        df.setGroupingUsed(false);
        return df;
    }

    // --------- helpers ---------

    /**
     * Parser robusto:
     * - rimuove doppi apici
     * - trim
     * - rimuove simbolo '%' e spazi
     * - accetta ',' come separatore decimale
     */
    private static Double parse(String raw) {
        if (raw == null) return null;
        String s = unquote(raw).trim();
        if (s.isEmpty()) return null;

        // elimina eventuale percentuale o testo residuo (es. "100 %", "100%")
        if (s.endsWith("%")) s = s.substring(0, s.length() - 1).trim();
        // rimuovi eventuali spazi interni tipo "100 %"
        if (s.endsWith(" %")) s = s.substring(0, s.length() - 2).trim();

        // se restano caratteri non numerici (rari), prova a tenere solo [0-9,.-]
        if (!s.matches("[-+]?\\d*[\\.,]?\\d+")) {
            s = s.replaceAll("[^0-9,\\.\\-+Ee]", "");
        }

        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String unquote(String s) {
        if (s == null) return "";
        String t = s;
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1);
        }
        return t.replace("\"\"", "\"");
    }
}
