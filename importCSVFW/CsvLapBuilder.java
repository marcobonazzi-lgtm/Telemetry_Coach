package org.simulator.importCSVFW;

import org.simulator.canale.Channel;
import org.simulator.canale.Sample;
import java.util.EnumMap;
import java.util.Map;

class CsvLapBuilder {

    static int computeLap(String[] row, int lapIdx, int lapTimeIdx, int distIdx,
                          int currLap, double prevDist, double prevLapTime, boolean firstRow) {

        if (lapIdx >= 0) {
            Integer lapVal = CsvParsers.getInt(row, lapIdx);
            if (lapVal != null && lapVal >= 0) return lapVal + 1;
        }

        Double d = CsvParsers.getDouble(row, distIdx);
        Double lap = CsvParsers.getDouble(row, lapTimeIdx);

        if (!firstRow) {
            boolean newLapByDist = (!CsvParsers.isNaN(d) && !CsvParsers.isNaN(prevDist) && (prevDist - d) > 50.0);
            boolean newLapByTime = (!CsvParsers.isNaN(lap) && !CsvParsers.isNaN(prevLapTime) && (prevLapTime - lap) > 0.05);
            if (newLapByDist || newLapByTime) return currLap + 1;
        }
        return currLap;
    }

    static Sample buildSample(String[] row, int timeIdx, int distIdx, Map<Integer, Channel> idx2ch) {
        Double t = CsvParsers.getDouble(row, timeIdx);
        Double d = CsvParsers.getDouble(row, distIdx);

        EnumMap<Channel, Double> values = new EnumMap<>(Channel.class);

        for (Map.Entry<Integer, Channel> e : idx2ch.entrySet()) {
            int i = e.getKey();
            if (i < 0 || i >= row.length) continue;

            Double v = CsvParsers.parseNumber(row[i]);
            if (!CsvParsers.isNaN(v)) {
                if (e.getValue() == Channel.STEER_ANGLE) v = -v;
                values.put(e.getValue(), v);
            }
        }
        return new Sample(CsvParsers.isNaN(t) ? Double.NaN : t, CsvParsers.isNaN(d) ? Double.NaN : d, values);
    }
}