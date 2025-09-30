package org.simulator.importCSVFW;

final class CsvParsers {
    private static boolean LIKELY_NUMERIC_CHAR(char c){return (c>='0'&&c<='9')||c=='-'||c=='+'||c=='.'||c==','||c=='e'||c=='E';}
    private static final java.util.regex.Pattern UNITISH_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z%°/\\[\\]\\.]+$");

    static Double getDouble(String[] row, int idx) {
        if (idx < 0 || idx >= row.length) return Double.NaN;
        return parseNumber(row[idx]);
    }

    static Integer getInt(String[] row, int idx) {
        if (idx < 0 || idx >= row.length) return null;
        try {
            String s = row[idx];
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) return null;
            s = s.replace(',', '.');
            Double d = Double.parseDouble(s);
            return d.intValue();
        } catch (Exception e) {
            return null;
        }
    }

    static Double parseNumber(String s) {
        if (s == null) return Double.NaN;
        String t = s.trim();
        if (t.isEmpty()) return Double.NaN;
        boolean maybe=true; for(int i=0;i<t.length();i++){if(!LIKELY_NUMERIC_CHAR(t.charAt(i))){maybe=false;break;}}
        if(!maybe) return Double.NaN;
        if (t.indexOf(',')>=0) t=t.replace(',', '.');
        try { return Double.parseDouble(t); } catch(Exception e){ return Double.NaN; }
    }


    static boolean isNaN(Double d) { return d == null || d.isNaN(); }

    static boolean rowIsEmpty(String[] row) {
        if (row == null) return true;
        for (String s : row) if (s != null && !s.trim().isEmpty()) return false;
        return true;
    }

    /** Euristica riga "unità" (Time=s, Distance=m, %, deg, °C, ...). */
    static boolean looksLikeUnitsRow(String[] row) {
        if (row == null) return false;
        int tokens = 0, unitish = 0;
        for (String s : row) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            tokens++;
            if (t.length() <= 6 && UNITISH_PATTERN.matcher(t).matches()) unitish++;
        }
        return tokens > 0 && unitish >= Math.max(4, (int) Math.ceil(tokens * 0.6));
    }
}
