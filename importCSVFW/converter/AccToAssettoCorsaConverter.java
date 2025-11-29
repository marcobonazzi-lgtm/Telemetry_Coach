package org.simulator.importCSVFW.converter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * ACC -> CSV "tipo AC"
 * - Copia 12 righe meta 1:1.
 * - Header: rinomina secondo AccToAcHeaderMapping (ordine invariato) e
 *           AGGIUNGE "Session Lap Count" subito dopo "Lap Beacon".
 * - Unità: se esiste una riga unità dopo l'header, viene proiettata.
 *          Se non esiste, viene GENERATA una riga unità di soli "" (uno per colonna).
 * - Dati: proiettati e QUOTATI.
 *   Valori mancanti -> "".
 * - Lap logic: legge "Lap Beacon" (0/1). Su fronte 0→1:
 *     Session Lap Count += 1 ; Lap Time = 0.
 *   Altrimenti: Lap Time = max(0, Time - lapStart).
 */
public final class AccToAssettoCorsaConverter {

    private final HeaderMapping mapping;

    public AccToAssettoCorsaConverter(HeaderMapping mapping) {
        this.mapping = mapping;
    }

    // --- utils ---
    private static String normUpper(String s) {
        if (s == null) return "";
        return s.replace("\"","").trim().toUpperCase(Locale.ROOT);
    }
    private static String dequoteTrim(String s) {
        return s == null ? "" : s.replace("\"","").trim();
    }
    private static String quote(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    private static boolean isMostlyNonNumeric(List<String> fields) {
        int nonNum = 0, tot = 0;
        for (String f : fields) {
            tot++;
            String t = dequoteTrim(f);
            if (t.isEmpty()) { nonNum++; continue; }
            try { Double.parseDouble(t); } catch (NumberFormatException ex) { nonNum++; }
        }
        // riga unità tipicamente è quasi tutta non-numerica
        return tot > 0 && nonNum >= (int)Math.ceil(tot * 0.7);
    }
    private static Double toDouble(String s) {
        String t = dequoteTrim(s);
        if (t.isEmpty()) return null;
        try { return Double.valueOf(t); } catch (NumberFormatException e) { return null; }
    }
    private static int indexOfCI(List<String> headers, String name) {
        for (int i = 0; i < headers.size(); i++) if (name.equalsIgnoreCase(headers.get(i))) return i;
        return -1;
    }

    public void convert(Path in, Path out, boolean stripExtras) throws IOException {
        final Map<String, String> baseMap = mapping.map();
        final Map<String, String> mapUpper = new HashMap<>(baseMap.size());
        for (Map.Entry<String, String> e : baseMap.entrySet()) {
            mapUpper.put(normUpper(e.getKey()), e.getValue());
        }

        try (BufferedReader br = Files.newBufferedReader(in, StandardCharsets.UTF_8);
             BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            // 1) prime 12 righe meta
            final int META_LINES = 12;
            for (int i = 0; i < META_LINES; i++) {
                String line = br.readLine();
                if (line == null) return;
                bw.write(line);
                bw.write("\r\n");
            }

            // 2) header (salta vuote)
            String headerLine;
            while (true) {
                headerLine = br.readLine();
                if (headerLine == null) return;
                if (!headerLine.trim().isEmpty()) break;
            }
            List<String> inHeadersRaw = CsvHeader.splitHeader(headerLine);

            // mapping: exact-case prima, poi fallback case-insensitive
            List<Integer> keepIdx = new ArrayList<>();
            List<String> outHeaders = new ArrayList<>();
            boolean sawLapBeaconOut = false;

            for (int i = 0; i < inHeadersRaw.size(); i++) {
                String raw = inHeadersRaw.get(i);
                String clean = dequoteTrim(raw);

                String mapped = baseMap.get(clean);
                if (mapped == null) mapped = mapUpper.get(normUpper(clean));

                boolean isLapBeacon = "LAP_BEACON".equalsIgnoreCase(clean);

                if (mapped != null) {
                    keepIdx.add(i);
                    outHeaders.add(mapped);
                    if ("Lap Beacon".equalsIgnoreCase(mapped)) sawLapBeaconOut = true;
                } else if (isLapBeacon) {
                    keepIdx.add(i);
                    outHeaders.add("Lap Beacon");
                    sawLapBeaconOut = true;
                } else if (!stripExtras) {
                    keepIdx.add(i);
                    outHeaders.add(clean);
                }
            }

            if (keepIdx.isEmpty())
                throw new IOException("Nessuna colonna mappata dall'header: " + inHeadersRaw);

            // Inserisci "Session Lap Count" subito dopo "Lap Beacon"
            int idxLapBeacon = indexOfCI(outHeaders, "Lap Beacon");
            int insertAt = (idxLapBeacon >= 0) ? idxLapBeacon + 1 : outHeaders.size();
            outHeaders.add(insertAt, "Session Lap Count");

            // Scrivi header quotato
            List<String> quotedHeader = new ArrayList<>(outHeaders.size());
            for (String h : outHeaders) quotedHeader.add(quote(h));
            bw.write(String.join(",", quotedHeader));
            bw.write("\r\n");

            // 3) riga unità: se presente, proiettala; altrimenti sintetizza "" per ogni colonna
            br.mark(64 * 1024); // mark per poter fare reset se non è unità
            String maybeUnits = br.readLine();
            List<String> unitsProjected;
            if (maybeUnits != null && !maybeUnits.trim().isEmpty()) {
                List<String> unitsRaw = CsvHeader.splitHeader(maybeUnits);
                if (isMostlyNonNumeric(unitsRaw)) {
                    // proietta unità dei campi mantenuti
                    List<String> units = new ArrayList<>();
                    for (int idx : keepIdx) {
                        String u = (idx < unitsRaw.size()) ? dequoteTrim(unitsRaw.get(idx)) : "";
                        units.add(u);
                    }
                    // inserisci "" per "Session Lap Count"
                    units.add(insertAt, "");
                    unitsProjected = units;
                } else {
                    // non è una riga unità: ripristina per trattarla come primo dato
                    br.reset();
                    unitsProjected = null;
                }
            } else {
                unitsProjected = null;
            }

            if (unitsProjected == null) {
                // genera una riga unità di soli ""
                unitsProjected = new ArrayList<>(outHeaders.size());
                for (int i = 0; i < outHeaders.size(); i++) unitsProjected.add("");
            }
            // scrivi riga unità quotata
            List<String> quotedUnits = new ArrayList<>(unitsProjected.size());
            for (String u : unitsProjected) quotedUnits.add(quote(u));
            bw.write(String.join(",", quotedUnits));
            bw.write("\r\n");

            // 4) dati + lap logic
            int idxTimeOut   = indexOfCI(outHeaders, "Time");     // assoluto (da ACC: TIME)
            int idxLapTimeOut= indexOfCI(outHeaders, "Lap Time"); // verrà riscritto
            int idxLapCountOut = indexOfCI(outHeaders, "Session Lap Count");
            idxLapBeacon = indexOfCI(outHeaders, "Lap Beacon");

            double lastBeacon = 0.0;
            Double lapStartTime = null;  // riferimento per Lap Time
            int sessionLapCount = 0;

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                List<String> rowIn = CsvHeader.splitHeader(line);

                // proiezione sui campi mantenuti
                List<String> rowOut = new ArrayList<>(outHeaders.size());
                for (int i = 0; i < outHeaders.size(); i++) rowOut.add(""); // placeholder

                // riempi proiezione per colonne mappate
                int outCursor = 0;
                for (int h = 0; h < outHeaders.size(); h++) {
                    if (h == insertAt) continue; // slot riservato a Session Lap Count
                }
                // compila i campi mantenuti rispettando l'ordine originale (prima di inserire SLC abbiamo salvato insertAt)
                for (int oi = 0, iiPos = 0; oi < outHeaders.size(); oi++) {
                    if (oi == insertAt) continue;
                    // trova la posizione di input corrispondente
                    int ii = keepIdx.get(iiPos++);
                    String v = (ii < rowIn.size()) ? dequoteTrim(rowIn.get(ii)) : "";
                    rowOut.set(oi, v);
                    if (iiPos >= keepIdx.size()) break;
                }

                // Lap logic (serve Time e Lap Beacon)
                Double timeVal   = (idxTimeOut    >= 0) ? toDouble(rowOut.get(idxTimeOut))    : null;
                Double beaconVal = (idxLapBeacon  >= 0) ? toDouble(rowOut.get(idxLapBeacon))  : null;

                if (timeVal != null && beaconVal != null) {
                    boolean rising = (lastBeacon < 0.5) && (beaconVal >= 0.5);

                    if (rising) {
                        lapStartTime = timeVal;
                        sessionLapCount += 1;

                        if (idxLapTimeOut >= 0) rowOut.set(idxLapTimeOut, "0");
                        if (idxLapCountOut >= 0) rowOut.set(idxLapCountOut, Integer.toString(sessionLapCount));
                    } else {
                        if (lapStartTime == null) lapStartTime = timeVal; // prima del primo taglio
                        if (idxLapTimeOut >= 0) {
                            double lt = Math.max(0.0, timeVal - lapStartTime);
                            // numerico “pulito”
                            String s = Double.toString(lt);
                            if (s.contains(".")) {
                                while (s.endsWith("0")) s = s.substring(0, s.length()-1);
                                if (s.endsWith(".")) s = s.substring(0, s.length()-1);
                            }
                            rowOut.set(idxLapTimeOut, s);
                        }
                        if (idxLapCountOut >= 0) rowOut.set(idxLapCountOut, Integer.toString(sessionLapCount));
                    }

                    lastBeacon = beaconVal;
                } else {
                    // se mancano, lascia "" in Lap Time / Session Lap Count
                    if (idxLapTimeOut >= 0 && (rowOut.get(idxLapTimeOut) == null || rowOut.get(idxLapTimeOut).isEmpty()))
                        rowOut.set(idxLapTimeOut, "");
                    if (idxLapCountOut >= 0 && (rowOut.get(idxLapCountOut) == null || rowOut.get(idxLapCountOut).isEmpty()))
                        rowOut.set(idxLapCountOut, "");
                }

                // QUOTING su ogni campo
                for (int i = 0; i < rowOut.size(); i++) rowOut.set(i, quote(rowOut.get(i)));
                bw.write(String.join(",", rowOut));
                bw.write("\r\n");
            }
        }
    }
}
