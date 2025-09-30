package org.simulator.importCSVFW;

import com.opencsv.CSVReader;

class HeaderDetector {

    static String[] findHeaderRow(CSVReader reader) throws Exception {
        String[] row;
        while ((row = reader.readNext()) != null) {
            if (CsvParsers.rowIsEmpty(row)) continue;
            if (looksLikeChannelHeader(row)) return row;
        }
        return null;
    }

    private static boolean looksLikeChannelHeader(String[] row) {
        int nonEmpty = 0, hits = 0;
        for (String cell : row) {
            String h = ChannelAliases.norm(cell);
            if (!h.isEmpty()) {
                nonEmpty++;
                if (ChannelAliases.HEADER_CANDIDATES.contains(h)) hits++;
                if (ChannelAliases.PARTIAL_KEYS.stream().anyMatch(h::contains)) hits++;
            }
        }
        return nonEmpty >= 5 && hits >= 3;
    }
}
