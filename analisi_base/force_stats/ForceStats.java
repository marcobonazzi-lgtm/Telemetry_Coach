package org.simulator.analisi_base.force_stats;

import org.simulator.canale.Lap;

/**
 * Facade pubblico per le statistiche derivate da SeatForce / PedalForce / FFB.
 * API invariata rispetto alla versione precedente.
 */
public final class ForceStats {

    private ForceStats(){}

    /** Percentuale (0..1) di campioni con "urto" sedile, soglia adattiva con fallback continuo. */
    public static double seatRoughnessPct(Lap lap) {
        return SeatRoughnessCalc.compute(lap);
    }

    /** Distribuzione sedile (SX, POST, DX) come frazioni 0..1 che sommano ~1. */
    public static SeatDistribution distribution(Lap lap) {
        return SeatDistributionCalc.compute(lap);
    }

    // ---------- DTO pubblico invariato ----------
    public static final class SeatDistribution {
        public double left;   // 0..1
        public double rear;   // 0..1
        public double right;  // 0..1
    }
}
