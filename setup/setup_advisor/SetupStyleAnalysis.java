package org.simulator.setup.setup_advisor;

import org.simulator.canale.Lap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SetupStyleAnalysis {

    private SetupStyleAnalysis(){}

    // ========== API invariata ==========
    static SetupAdvisor.DriverStyle analyzeStyle(List<Lap> laps){
        var a = analyzeStyleDetailed(laps);
        return a == null ? SetupAdvisor.DriverStyle.NEUTRAL : a.primary();
    }

    static SetupAdvisor.Assessment analyzeStyleDetailed(List<Lap> laps){
        if (laps == null || laps.isEmpty()) return null;

        // ---- raccogli metriche per giro ----
        List<Double> thrOscL   = new ArrayList<>();
        List<Double> steerHarL = new ArrayList<>();
        List<Double> stompL    = new ArrayList<>();
        List<Double> overlapL  = new ArrayList<>();
        List<Double> coastL    = new ArrayList<>();
        List<Double> revMinL   = new ArrayList<>();
        List<Double> clipL     = new ArrayList<>();
        List<Double> tyreL     = new ArrayList<>();
        List<Double> brakeL    = new ArrayList<>();

        int overHits = 0, n = 0;

        for (Lap lap : laps){
            if (lap == null || lap.samples == null || lap.samples.isEmpty()) continue;
            SetupAdvisor.StyleMetrics m = SetupMetrics.compute(lap);

            thrOscL.add(m.thrOscPct());
            steerHarL.add(m.steerHarshPct());
            stompL.add(m.brakeStompPct());
            overlapL.add(m.throttleBrakeOverlap());
            coastL.add(m.coastingPct());
            revMinL.add(m.steerReversalsPerMin());
            clipL.add(m.ffbClipPct());
            if (!Double.isNaN(m.avgTyreTemp()))  tyreL.add(m.avgTyreTemp());
            if (!Double.isNaN(m.avgBrakeTemp())) brakeL.add(m.avgBrakeTemp());
            if (m.oversteerLike()) overHits++;
            n++;
        }
        if (n == 0) return null;

        // ---- medie sessione ----
        double thrOsc     = SetupMath.mean(thrOscL);
        double steerHar   = SetupMath.mean(steerHarL);
        double stomp      = SetupMath.mean(stompL);
        double overlap    = SetupMath.mean(overlapL);
        double coast      = SetupMath.mean(coastL);
        double revPerMin  = SetupMath.mean(revMinL);
        double clip       = SetupMath.mean(clipL);
        double tyreAvg    = SetupMath.mean(tyreL);
        double brakeAvg   = SetupMath.mean(brakeL);
        boolean overLike  = overHits > n/2;

        // ---- normalizzazioni robuste (0..1) per il punteggio stile ----
        double z_thr   = SetupMath.clamp01(thrOsc);                  // già 0..1
        double z_steer = SetupMath.clamp01(steerHar);                // già 0..1
        double z_stomp = SetupMath.clamp01(stomp);                   // già 0..1
        double z_ovl   = SetupMath.clamp01(overlap / 0.15);          // 15% overlap ≈ 1.0
        double z_rev   = SetupMath.clamp01(revPerMin / 12.0);        // 12 inversioni/min ≈ 1.0
        double z_clip  = SetupMath.clamp01(clip / 0.25);             // 25% clipping ≈ 1.0
        double z_over  = overLike ? 1.0 : 0.0;

        // Coasting alto è tipico di stile prudente → lo usiamo con peso negativo
        double z_coast = SetupMath.clamp01(coast / 0.30);            // 30% coasting “alto”
        double z_coast_penalty = 0.6 * z_coast;                      // penalizza aggressività

        // ---- punteggio aggressività (continuo) ----
        double score =
                1.4*z_thr +
                        1.2*z_steer +
                        0.9*z_stomp +
                        0.6*z_ovl +
                        0.5*z_rev +
                        0.3*z_clip +
                        0.15*z_over -
                        z_coast_penalty;

        // Soglie calibrate per SMOOTH / NEUTRAL / AGGRESSIVE
        SetupAdvisor.DriverStyle style =
                (score > 0.90) ? SetupAdvisor.DriverStyle.AGGRESSIVE
                        : (score < 0.35) ? SetupAdvisor.DriverStyle.SMOOTH
                        :                  SetupAdvisor.DriverStyle.NEUTRAL;

        // ---- metrics aggregati in un unico StyleMetrics (firma invariata) ----
        String summary = String.format(Locale.ITALIAN,
                "SESSIONE: thrOsc=%.0f%%, steerHarsh=%.0f%%, stomp=%.0f%%, overlap=%.0f%%, coasting=%.0f%%, " +
                        "reversals=%.1f/min, FFB clip=%.0f%% | score=%.2f → %s",
                100*thrOsc, 100*steerHar, 100*stomp, 100*overlap, 100*coast,
                revPerMin, 100*clip, score, style);

        // notes dettagliate: includo range utili per debugging/telemetria rapida
        String notes = String.format(Locale.ITALIAN,
                "Agg: thr=%.0f%%, steer=%.0f%%, stomp=%.0f%%, ovl=%.0f%%, coast=%.0f%%, rev=%.1f/min, clip=%.0f%%; " +
                        "tyre=%.1f°C, brake=%.0f°C; overLike=%s",
                100*thrOsc, 100*steerHar, 100*stomp, 100*overlap, 100*coast, revPerMin, 100*clip,
                tyreAvg, brakeAvg, overLike ? "sì" : "no");

        SetupAdvisor.StyleMetrics aggMetrics = new SetupAdvisor.StyleMetrics(
                thrOsc, steerHar, overLike, stomp, overlap, coast, revPerMin, clip, tyreAvg, brakeAvg, notes
        );

        return new SetupAdvisor.Assessment(style, aggMetrics, summary);
    }
}
