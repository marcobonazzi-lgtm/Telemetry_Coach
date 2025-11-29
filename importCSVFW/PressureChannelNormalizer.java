package org.simulator.importCSVFW;

import org.simulator.canale.Channel;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/**
 * Normalizza/convertisce i canali di pressione gomme usando Channel/ChannelAliases.
 * Regola: se unit row contain kPa -> converti a psi.
 * In assenza di unit row: se la mediana > 60 => consideriamo kPa -> converti a psi.
 */
public final class PressureChannelNormalizer {

    private PressureChannelNormalizer() {}

    public static final double KPA_TO_PSI = 0.1450377377;
    public static final double THR_KPA_MEDIAN = 60.0;

    public static List<Integer> findPressureIndexes(String[] header) {
        Objects.requireNonNull(header, "header");
        Map<String, Channel> alias = ChannelAliases.aliasMap();
        List<Integer> idx = new ArrayList<>(4);
        for (int i = 0; i < header.length; i++) {
            String norm = ChannelAliases.norm(header[i]);
            Channel ch = alias.get(norm);
            if (ch == Channel.TIRE_PRESSURE_FL || ch == Channel.TIRE_PRESSURE_FR
                    || ch == Channel.TIRE_PRESSURE_RL || ch == Channel.TIRE_PRESSURE_RR) {
                idx.add(i);
            }
        }
        return idx;
    }

    public static boolean unitsRowIsKpa(List<String> unitsRow, List<Integer> pressureIdx) {
        if (unitsRow == null) return false;
        for (int i : pressureIdx) {
            if (i < unitsRow.size()) {
                String v = unquote(unitsRow.get(i)).trim();
                if (v.equalsIgnoreCase("kPa")) return true;
            }
        }
        return false;
    }

    public static void convertUnitsRowToPsi(List<String> unitsRow, List<Integer> pressureIdx) {
        if (unitsRow == null) return;
        for (int i : pressureIdx) {
            if (i < unitsRow.size()) {
                String v = unquote(unitsRow.get(i)).trim();
                if (v.equalsIgnoreCase("kPa")) unitsRow.set(i, "psi");
            }
        }
    }

    public static boolean shouldConvertBySampling(List<List<String>> sampleRows,
                                                  List<Integer> pressureIdx,
                                                  int maxSamples) {
        List<Double> samples = new ArrayList<>(maxSamples);
        outer:
        for (List<String> row : sampleRows) {
            for (int i : pressureIdx) {
                if (i < row.size()) {
                    Double v = parseNum(row.get(i));
                    if (v != null) {
                        samples.add(v);
                        if (samples.size() >= maxSamples) break outer;
                    }
                }
            }
        }
        if (samples.isEmpty()) return false;
        Collections.sort(samples);
        double median = samples.get(samples.size() / 2);
        return median > THR_KPA_MEDIAN;
    }

    public static void convertRowKpaToPsi(List<String> row, List<Integer> pressureIdx, DecimalFormat df) {
        for (int i : pressureIdx) {
            if (i < row.size()) {
                Double v = parseNum(row.get(i));
                if (v != null) {
                    double psi = v * KPA_TO_PSI;
                    row.set(i, (df != null ? df.format(psi) : Double.toString(psi)));
                }
            }
        }
    }

    // ---- helpers ----
    private static String unquote(String s) {
        if (s == null) return "";
        String t = s;
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1);
        }
        return t.replace("\"\"", "\"");
    }

    private static Double parseNum(String raw) {
        if (raw == null) return null;
        String s = unquote(raw).trim();
        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static DecimalFormat dfPressure() {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("0.##", sym);
        df.setGroupingUsed(false);
        return df;
    }
}
