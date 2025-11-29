package org.simulator.importCSVFW;

final class CsvParsers {

    static Double getDouble(String[] row, int idx) {
        if (idx < 0 || idx >= row.length) return Double.NaN;
        return parseNumber(row[idx]);
    }

    static Integer getInt(String[] row, int idx) {
        Double d = getDouble(row, idx);
        return isNaN(d) ? null : d.intValue();
    }

    static Double parseNumber(String s) {
        if (s == null || s.isEmpty()) return Double.NaN;
        // Trim manuale per evitare garbage
        int start = 0, end = s.length();
        while(start < end && (s.charAt(start) <= ' ' || s.charAt(start) == '"')) start++;
        while(end > start && (s.charAt(end-1) <= ' ' || s.charAt(end-1) == '"')) end--;

        if (start >= end) return Double.NaN;

        try {
            // Sostituzione virgola al volo solo se c'è
            String clean = s.substring(start, end);
            if (clean.indexOf(',') >= 0) clean = clean.replace(',', '.');
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    static boolean isNaN(Double d) { return d == null || d.isNaN(); }

}