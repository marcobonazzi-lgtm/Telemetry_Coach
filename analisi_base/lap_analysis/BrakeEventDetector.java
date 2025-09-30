package org.simulator.analisi_base.lap_analysis;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.ArrayList;
import java.util.List;

/** Rilevamento degli eventi di frenata (estratto da LapAnalysis). */
final class BrakeEventDetector {

    private BrakeEventDetector(){}

    static List<Integer> detect(Lap lap, double thresholdPct, int minSamples) {
        List<Integer> events = new ArrayList<>();
        if (lap == null || lap.samples == null || lap.samples.isEmpty()) return events;

        boolean in = false;
        int startIdx = -1;
        int count = 0;

        for (int i = 0; i < lap.samples.size(); i++) {
            Sample s = lap.samples.get(i);
            Double br = s.values().get(Channel.BRAKE);
            if (br == null || br.isNaN()) {
                if (in && count >= minSamples) events.add(startIdx);
                in = false; count = 0; startIdx = -1;
                continue;
            }
            double b = br;
            if (b <= 1.0) b *= 100.0;

            if (b >= thresholdPct) {
                if (!in) { in = true; startIdx = i; count = 1; }
                else count++;
            } else {
                if (in && count >= minSamples) events.add(startIdx);
                in = false; count = 0; startIdx = -1;
            }
        }
        if (in && count >= minSamples) events.add(startIdx);
        return events;
    }
}
