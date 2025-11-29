package org.simulator.importCSVFW;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;


public class CsvImporter {

    public List<Lap> importFile(Path csvPath, Map<String, Channel> userMapping) throws Exception {
        System.out.println("--- INIZIO IMPORTAZIONE CSV ---");
        long startTotal = System.currentTimeMillis();

        try (BufferedReader br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {

            // 1. Header Detection
            String line = br.readLine();
            if (line == null) throw new IllegalArgumentException("File vuoto");

            // Salta metadati iniziali
            int metaLines = 0;
            while (line != null && !isLikelyHeader(line) && metaLines < 50) {
                line = br.readLine();
                metaLines++;
            }
            if (line == null) throw new IllegalArgumentException("Header non trovato");

            // Parsing Header
            String[] header = splitLine(line);
            cleanQuotes(header); // Rimuovi virgolette

            // 2. Unità di misura
            br.mark(2048);
            String unitLine = br.readLine();
            if (unitLine != null) {
                if (!unitLine.contains("s") && !unitLine.contains("m") && !unitLine.contains("%")) {
                    br.reset(); // Non sembra una riga unità, torna indietro
                }
            }

            // 3. Mapping
            Map<Integer, Channel> idx2ch = CsvMappingBuilder.buildIndexMapping(header, userMapping);

            // Preparazione Indici per LapBuilder
            Map<String,Integer> hMap = new HashMap<>();
            for(int i=0; i<header.length; i++) hMap.put(ChannelAliases.norm(header[i]), i);

            int timeIdx = findIdx(hMap, ChannelAliases.TIME_ALIASES);
            int distIdx = findIdx(hMap, ChannelAliases.DIST_ALIASES);
            int lapIdx = findIdx(hMap, ChannelAliases.LAP_ALIASES);
            int lapTimeIdx = findIdx(hMap, ChannelAliases.LAPTIME_ALIASES);

            // 4. Loop di lettura (Misuriamo solo questo)
            long startLoop = System.currentTimeMillis();

            Map<Integer, List<Sample>> byLap = new LinkedHashMap<>();
            int currLap = 1;
            double prevDist = Double.NaN;
            double prevLapT = Double.NaN;
            int rowCount = 0;

            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;

                String[] row = splitLine(line); // Split semplice e sicuro
                if (row.length == 0) continue;

                // Calcolo Giro
                currLap = CsvLapBuilder.computeLap(row, lapIdx, lapTimeIdx, distIdx, currLap, prevDist, prevLapT, false);

                // Cache per logica giro
                Double d = CsvParsers.getDouble(row, distIdx);
                Double lt = CsvParsers.getDouble(row, lapTimeIdx);
                if (!CsvParsers.isNaN(d)) prevDist = d;
                if (!CsvParsers.isNaN(lt)) prevLapT = lt;

                // Creazione Sample
                Sample s = CsvLapBuilder.buildSample(row, timeIdx, distIdx, idx2ch);
                byLap.computeIfAbsent(currLap, k -> new ArrayList<>(1000)).add(s);

                rowCount++;
            }

            long endLoop = System.currentTimeMillis();
            System.out.println("-> Lettura File e Parsing: " + (endLoop - startLoop) + " ms (" + rowCount + " righe)");

            // 5. Costruzione Oggetti Lap
            List<Lap> laps = new ArrayList<>();
            for (Map.Entry<Integer, List<Sample>> e : byLap.entrySet()) {
                laps.add(new Lap(e.getKey(), e.getValue()));
            }

            long endTotal = System.currentTimeMillis();
            System.out.println("-> Tempo Totale Importazione: " + (endTotal - startTotal) + " ms");
            System.out.println("--- FINE IMPORTAZIONE (Ora inizia la UI...) ---");

            return laps;
        }
    }

    // Helpers semplici e robusti
    private String[] splitLine(String line) {
        // Split veloce manuale per evitare regex lenta
        // Se il file è ben formato (virgola), questo è rapidissimo
        List<String> tokens = new ArrayList<>();
        int start = 0;
        boolean inQuote = false;
        int len = line.length();
        for (int i = 0; i < len; i++) {
            char c = line.charAt(i);
            if (c == '\"') inQuote = !inQuote;
            else if (c == ',' && !inQuote) {
                tokens.add(line.substring(start, i));
                start = i + 1;
            }
        }
        tokens.add(line.substring(start));
        return tokens.toArray(new String[0]);
    }

    private boolean isLikelyHeader(String line) {
        String lower = line.toLowerCase();
        return lower.contains("time") && (lower.contains("speed") || lower.contains("rpm") || lower.contains("distance"));
    }

    private void cleanQuotes(String[] row) {
        for(int i=0; i<row.length; i++) {
            String s = row[i].trim();
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
                row[i] = s.substring(1, s.length()-1);
            } else {
                row[i] = s;
            }
        }
    }

    private int findIdx(Map<String,Integer> map, List<String> aliases) {
        for(String a : aliases) if(map.containsKey(a)) return map.get(a);
        for(String a : aliases) for(String key : map.keySet()) if(key.contains(a)) return map.get(key);
        return -1;
    }

}