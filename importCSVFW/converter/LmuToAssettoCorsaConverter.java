package org.simulator.importCSVFW.converter;

import org.simulator.importCSVFW.ChannelAliases;
import org.simulator.importCSVFW.ClutchChannelNormalizer;
import org.simulator.importCSVFW.PressureChannelNormalizer;
import org.simulator.canale.Channel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.*;

/**
 * LMU -> CSV "tipo AC"
 * - 12 righe metadati copiate come sono
 * - header rinominato (LMU->AC) mantenendo l'ordine
 * - rimozione opzionale colonne non mappate (stripExtras)
 * - PRESSIONI: kPa->psi se unit=kPa o se mediana>60
 * - FRIZIONE: inversione automatica se prevalgono valori > metà scala
 * - STERZO: garantita UNA SOLA colonna "Steer Angle" nel risultato (duplicati rimossi)
 */
public final class LmuToAssettoCorsaConverter {

    private static final int METADATA_LINES = 12;

    private final HeaderMapping headerMapping;

    public LmuToAssettoCorsaConverter(HeaderMapping headerMapping) {
        this.headerMapping = Objects.requireNonNull(headerMapping, "headerMapping is null");
    }

    public void convert(Path in, Path out, boolean stripExtras) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(in, StandardCharsets.UTF_8);
             BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            // 1) Metadati
            for (int i = 0; i < METADATA_LINES; i++) {
                String meta = br.readLine();
                if (meta == null) return;
                bw.write(meta);
                bw.newLine();
            }

            // 2) Righe vuote fino all'header
            List<String> preservedEmpty = new ArrayList<>();
            String headerLine;
            while (true) {
                headerLine = br.readLine();
                if (headerLine == null) {
                    for (String s : preservedEmpty) { bw.write(s); bw.newLine(); }
                    return;
                }
                if (headerLine.trim().isEmpty()) preservedEmpty.add(headerLine);
                else break;
            }
            for (String s : preservedEmpty) { bw.write(s); bw.newLine(); }

            // 3) Rinomina header e prepara keepIndexes
            List<String> headersLMU = CsvHeader.splitHeader(headerLine);
            Map<String, String> map = headerMapping.map();

            List<String> headersOut = new ArrayList<>(headersLMU.size());
            List<Integer> keepIdx = new ArrayList<>();
            boolean steerKept = false; // per garantire UN solo "Steer Angle"

            for (int i = 0; i < headersLMU.size(); i++) {
                String original = headersLMU.get(i).trim();
                boolean mapped = map.containsKey(original);
                String targetName = mapped ? map.get(original) : original;

                // se stripExtras e non mappata -> skip
                if (stripExtras && !mapped) continue;

                // dedup sterzo: se questo header (LMU o target) è un alias di STEER_ANGLE e ne abbiamo già uno -> SKIP
                if (isSteerAlias(original) || isSteerAlias(targetName)) {
                    if (steerKept) continue; // rimuovi duplicati di sterzo
                    // manteniamo questo come unico "Steer Angle"
                    headersOut.add("Steer Angle");
                    keepIdx.add(i);
                    steerKept = true;
                    continue;
                }

                headersOut.add(targetName);
                keepIdx.add(i);
            }

            // scrivi header out
            bw.write(CsvHeader.joinHeader(headersOut));
            bw.newLine();

            // 4) PRESSIONI — detection unit/sampling
            String[] headerOutArr = headersOut.toArray(new String[0]);
            List<Integer> pressureCols = PressureChannelNormalizer.findPressureIndexes(headerOutArr);
            boolean convertPressures = false;

            // prova unit row
            List<String> unitsRow = null;
            br.mark(1 << 20);
            String maybeUnits = br.readLine();
            if (maybeUnits != null) {
                List<String> unitsRaw = CsvHeader.splitHeader(maybeUnits);
                // proj units sui keepIdx
                unitsRow = project(unitsRaw, keepIdx);
                if (PressureChannelNormalizer.unitsRowIsKpa(unitsRow, pressureCols)) {
                    convertPressures = true;
                    PressureChannelNormalizer.convertUnitsRowToPsi(unitsRow, pressureCols);
                    bw.write(CsvHeader.joinHeader(unitsRow));
                    bw.newLine();
                } else {
                    br.reset();
                    unitsRow = null;
                }
            } else {
                br.reset();
            }

            // sampling se non c'è unit row
            if (!convertPressures && !pressureCols.isEmpty()) {
                br.mark(1 << 22);
                List<List<String>> sampleRows = new ArrayList<>();
                String s;
                int rows = 0;
                while (rows < 120 && (s = br.readLine()) != null) {
                    List<String> raw = CsvHeader.splitHeader(s);
                    sampleRows.add(project(raw, keepIdx));
                    rows++;
                }
                convertPressures = PressureChannelNormalizer.shouldConvertBySampling(sampleRows, pressureCols, 50);
                br.reset();
            }

            // 5) CLUTCH — detection by sampling
            ClutchChannelNormalizer.Detection clutchDet;
            {
                br.mark(1 << 22);
                List<List<String>> sampleRows = new ArrayList<>();
                String s;
                int rows = 0;
                while (rows < 120 && (s = br.readLine()) != null) {
                    List<String> raw = CsvHeader.splitHeader(s);
                    sampleRows.add(project(raw, keepIdx));
                    rows++;
                }
                clutchDet = ClutchChannelNormalizer.analyze(sampleRows, headerOutArr, 80);
                br.reset();
            }

            // 6) Loop dati con trasformazioni
            DecimalFormat dfPsi = PressureChannelNormalizer.dfPressure();
            DecimalFormat dfClutch = ClutchChannelNormalizer.dfClutch();

            String line;
            while ((line = br.readLine()) != null) {
                List<String> raw = CsvHeader.splitHeader(line);
                List<String> row = project(raw, keepIdx);

                if (convertPressures && !pressureCols.isEmpty()) {
                    PressureChannelNormalizer.convertRowKpaToPsi(row, pressureCols, dfPsi);
                }
                if (clutchDet.shouldInvert) {
                    ClutchChannelNormalizer.invertRowValue(row, clutchDet, dfClutch);
                }

                bw.write(CsvHeader.joinHeader(row));
                bw.newLine();
            }
        }
    }

    // ------------ helpers ------------

    private static boolean isSteerAlias(String name) {
        if (name == null) return false;
        Channel ch = ChannelAliases.aliasMap().get(ChannelAliases.norm(name));
        return ch == Channel.STEER_ANGLE;
    }

    private static List<String> project(List<String> row, List<Integer> keepIdx) {
        List<String> out = new ArrayList<>(keepIdx.size());
        for (int idx : keepIdx) out.add(idx < row.size() ? row.get(idx) : "");
        return out;
    }
}
