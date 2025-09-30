package org.simulator.importCSVFW;

import org.simulator.canale.Channel;
import org.simulator.canale.Sample;

import java.util.EnumMap;
import java.util.Map;

class CsvLapBuilder {

    /** Calcola il numero di giro corrente (logica invariata). */
    static int computeLap(String[] row, int lapIdx, int lapTimeIdx, int distIdx,
                          int currLap, double prevDist, double prevLapTime, boolean firstRow) {

        // 1) Preferisci "Session Lap Count" (o alias “LAP”): 0,1,2,... → +1
        if (lapIdx >= 0) {
            Integer lapVal = CsvParsers.getInt(row, lapIdx); // accetta "0.0"
            if (lapVal != null && lapVal >= 0) return lapVal + 1;
        }

        // 2) Fallback: "Lap Time" che riparte (~0) o Distance che cala
        Double d   = CsvParsers.getDouble(row, distIdx);
        Double lap = CsvParsers.getDouble(row, lapTimeIdx);

        if (!firstRow) {
            boolean newLapByDist = (!CsvParsers.isNaN(d)   && !CsvParsers.isNaN(prevDist)    && (prevDist - d) > 50.0);
            boolean newLapByTime = (!CsvParsers.isNaN(lap) && !CsvParsers.isNaN(prevLapTime) && (prevLapTime - lap) > 0.05);
            if (newLapByDist || newLapByTime) return currLap + 1;
        }
        return currLap;
    }

    /** Costruisce un Sample da una riga CSV. */
    static Sample buildSample(String[] row, int timeIdx, int distIdx, Map<Integer, Channel> idx2ch) {
        Double t = CsvParsers.getDouble(row, timeIdx);
        Double d = CsvParsers.getDouble(row, distIdx);
        var values = new EnumMap<Channel, Double>(Channel.class);
        for (java.util.Map.Entry<Integer, Channel> e : idx2ch.entrySet()) {
            int i = e.getKey();
            if (i < 0 || i >= row.length) continue;
            Channel ch = e.getValue();
            String cell = row[i];
            if (cell == null || cell.isEmpty()) continue;
            Double v = CsvParsers.parseNumber(cell);
            if (!CsvParsers.isNaN(v)) {
                if (ch == Channel.STEER_ANGLE) v = -v; // inverti segno del volante
                values.put(ch, v);
            }
        }
        return new Sample(CsvParsers.isNaN(t) ? Double.NaN : t,
                CsvParsers.isNaN(d) ? Double.NaN : d,
                values);
    }
}
