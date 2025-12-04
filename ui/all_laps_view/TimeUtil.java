package org.simulator.ui.all_laps_view;

public class TimeUtil {

    /**
     * Converte un tempo espresso in secondi in formato m:ss.SSS
     * Esempio: 82.345 -> "1:22.345"
     *          125.7  -> "2:05.700"
     *          NaN    -> "--:--.---"
     */
    public static String formatLapTime(double seconds) {
        if (Double.isNaN(seconds) || seconds <= 0.0) {
            return "--:--.---";
        }
        int minutes = (int) (seconds / 60);
        double secs = seconds % 60;
        return String.format("%d:%06.3f", minutes, secs);
    }
}
