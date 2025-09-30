package org.simulator.importCSVFW;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVParser;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.io.FileReader;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Facade pubblico (API invariata). */
public class CsvImporter {

    /** Importa un CSV MoTeC/ACTI e restituisce la lista di Lap separati. */
    public List<Lap> importFile(Path csvPath, Map<String, Channel> userMapping) throws Exception {
        long T0=System.nanoTime();
        try (CSVReader reader = new CSVReaderBuilder(new FileReader(csvPath.toFile())).build()) {

            // 1) Header canali
            String[] header = HeaderDetector.findHeaderRow(reader);
            if (header == null) {
                throw new IllegalArgumentException("Intestazione canali non trovata in: " + csvPath);
            }

            // 2) Salta eventuale riga unità
            String[] row = reader.readNext();
            if (!CsvParsers.looksLikeUnitsRow(row)) {
                // non era la riga unità: la tratteremo come prima riga di dati
            } else {
                row = reader.readNext();
            }

            // 3) Mapping colonna -> Channel
            Map<Integer, Channel> idx2ch = CsvMappingBuilder.buildIndexMapping(header, userMapping);

            // 3.b) Mappa rapida header normalizzati
            Map<String,Integer> normIndex = buildHeaderIndex(header);
            long T1=System.nanoTime();

            // 4) Indici chiave
            int timeIdx    = findWithAliases(normIndex, ChannelAliases.TIME_ALIASES);
            int distIdx    = findWithAliases(normIndex, ChannelAliases.DIST_ALIASES);
            int lapIdx     = findWithAliases(normIndex, ChannelAliases.LAP_ALIASES);
            int lapTimeIdx = findWithAliases(normIndex, ChannelAliases.LAPTIME_ALIASES);

            // 5) Loop righe, split per giro
            Map<Integer, List<Sample>> byLap = new LinkedHashMap<>();
            int currLap = 1;
            double prevDist = Double.NaN;
            double prevLapT = Double.NaN;

            // prima riga (se presente)
            if (row != null && !CsvParsers.rowIsEmpty(row)) {
                currLap  = CsvLapBuilder.computeLap(row, lapIdx, lapTimeIdx, distIdx, currLap, prevDist, prevLapT, true);
                prevDist = CsvParsers.getDouble(row, distIdx);
                prevLapT = CsvParsers.getDouble(row, lapTimeIdx);
                byLap.computeIfAbsent(currLap, k -> new ArrayList<>())
                        .add(CsvLapBuilder.buildSample(row, timeIdx, distIdx, idx2ch));
            }

            while ((row = reader.readNext()) != null) {
                if (CsvParsers.rowIsEmpty(row)) continue;

                currLap  = CsvLapBuilder.computeLap(row, lapIdx, lapTimeIdx, distIdx, currLap, prevDist, prevLapT, false);
                Double d  = CsvParsers.getDouble(row, distIdx);
                Double lt = CsvParsers.getDouble(row, lapTimeIdx);
                if (!CsvParsers.isNaN(d))  prevDist = d;
                if (!CsvParsers.isNaN(lt)) prevLapT = lt;

                byLap.computeIfAbsent(currLap, k -> new ArrayList<>())
                        .add(CsvLapBuilder.buildSample(row, timeIdx, distIdx, idx2ch));
            }

            // 6) Converte in lista ordinata (log identico)
            List<Lap> laps = new ArrayList<>();
            for (Map.Entry<Integer, List<Sample>> e : byLap.entrySet()) {
                laps.add(new Lap(e.getKey(), e.getValue()));
            }
            for (Lap l : laps){ long T2 =System.nanoTime();
            System.err.println("[CSV] header+mapping: "+((T1-T0)/1_000_000)+" ms, parse+build: "+((T2-T1)/1_000_000)+" ms, total: "+((T2-T0)/1_000_000)+" ms");
            return laps;}
        }
        return null;
    }

    /** Watcher su cartella per nuovi CSV (API invariata). */

private static char detectDelimiter(java.nio.file.Path csvPath) throws java.io.IOException {
    try (var br = java.nio.file.Files.newBufferedReader(csvPath, java.nio.charset.StandardCharsets.UTF_8)) {
        String line;
        for (int i=0; i<10 && (line = br.readLine()) != null; i++) {
            if (line == null || line.isBlank()) continue;
            int c = count(line, ','), s = count(line, ';'), t = count(line, '\t'), p = count(line, '|');
            int max = Math.max(Math.max(c,s), Math.max(t,p));
            if (max == 0) continue;
            if (max == c) return ','; if (max == s) return ';'; if (max == t) return '\t'; return '|';
        }
    }
    return ',';
}
private static int count(String s, char ch){int n=0; for(int i=0;i<s.length();i++) if(s.charAt(i)==ch) n++; return n;}

private static java.util.Map<String,Integer> buildHeaderIndex(String[] header){
    java.util.Map<String,Integer> m = new java.util.HashMap<>(header.length*2);
    for(int i=0;i<header.length;i++){
        String h = org.simulator.importCSVFW.ChannelAliases.norm(header[i]);
        if(!h.isEmpty() && !m.containsKey(h)) m.put(h,i);
    }
    return m;
}
private static int findWithAliases(java.util.Map<String,Integer> normIndex, java.util.List<String> aliases){
    for(String a: aliases){ Integer idx = normIndex.get(a); if(idx!=null) return idx; }
    for(String a: aliases){ if(a.isEmpty()) continue; for(var e: normIndex.entrySet()){ if(e.getKey().contains(a)) return e.getValue(); } }
    return -1;
}

    public static void watchFolder(Path folder, Consumer<Path> onNewFile) throws Exception {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            folder.register(ws, StandardWatchEventKinds.ENTRY_CREATE);
            for (;;) {
                WatchKey key = ws.take();
                for (WatchEvent<?> ev : key.pollEvents()) {
                    if (ev.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        Path p = folder.resolve((Path) ev.context());
                        if (p.toString().toLowerCase(Locale.ROOT).endsWith(".csv")) onNewFile.accept(p);
                    }
                }
                key.reset();
            }
        }
    }
}
