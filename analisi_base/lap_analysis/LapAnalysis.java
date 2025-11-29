package org.simulator.analisi_base.lap_analysis;

import org.simulator.canale.Lap;

import java.util.List;
import java.util.Map;

/** Facciata: espone le API storiche delegando alle classi split. */
public final class LapAnalysis {

    private LapAnalysis() {}

    /** Statistiche sintetiche del giro. */
    public static Map<String, Double> basicStats(Lap lap) {
        return BasicLapStats.compute(lap);
    }

    /** Rilevamento eventi di frenata. */
    public static List<Integer> brakeEvents(Lap lap, double thresholdPct, int minSamples) {
        return BrakeEventDetector.detect(lap, thresholdPct, minSamples);
    }

    /** Stima degli apex come minimi locali di Speed smussata. */
    public static List<Integer> apexes(Lap lap, int smoothWindow) {
        return ApexEstimator.estimate(lap, smoothWindow);
    }
}
