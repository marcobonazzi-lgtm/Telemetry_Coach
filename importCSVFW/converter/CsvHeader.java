package org.simulator.importCSVFW.converter;

import java.util.ArrayList;
import java.util.List;

/** Utility minimale per split/join righe CSV con virgolette. */
final class CsvHeader {
    private CsvHeader() {}

    static List<String> splitHeader(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    cur.append('\"'); i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    static String joinHeader(List<String> cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            String v = cols.get(i);
            boolean needQuotes = v.contains(",") || v.contains("\"") || v.contains("\n")
                    || v.startsWith(" ") || v.endsWith(" ");
            if (needQuotes) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
            if (i < cols.size() - 1) sb.append(',');
        }
        return sb.toString();
    }
}
