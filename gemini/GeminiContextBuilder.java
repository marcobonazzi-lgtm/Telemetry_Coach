package org.simulator.gemini;

import org.simulator.canale.Lap;
import org.simulator.coach.CoachSession;
import org.simulator.setup.setup_advisor.SetupAdvisor;
import org.simulator.setup.setup_advisor.SetupMetrics;
import org.simulator.setup.setup_advisor.VehicleTraits;
import org.simulator.tracks.TrackInfo; // Importa TrackInfo

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GeminiContextBuilder {

    // Aggiornato: Accetta anche TrackInfo
    public static String buildLapContext(Lap lap, TrackInfo track) {
        if (lap == null) return "Nessun dato giro disponibile.";
        return buildCommonContext(Collections.singletonList(lap), track);
    }

    // Aggiornato: Accetta anche TrackInfo
    public static String buildSessionContext(List<Lap> laps, TrackInfo track) {
        if (laps == null || laps.isEmpty()) return "Nessun dato sessione disponibile.";
        return buildCommonContext(laps, track);
    }

    private static String buildCommonContext(List<Lap> laps, TrackInfo track) {
        StringBuilder sb = new StringBuilder();

        // 1. CONTESTO CIRCUITO (NUOVO)
        if (track != null) {
            sb.append("CIRCUITO: ").append(track.displayName).append(" (").append(track.lengthKm).append(" km)\n");
            sb.append("LISTA CURVE (Usa questi nomi): ");
            for (TrackInfo.Turn t : track.turns) {
                sb.append("T").append(t.number).append(":").append(t.name).append(", ");
            }
            sb.append("\n");
        } else {
            sb.append("CIRCUITO: Sconosciuto (Analizza in modo generico)\n");
        }

        // 2. Veicolo
        VehicleTraits traits = VehicleTraits.detect(laps);
        sb.append("VEICOLO: ").append(traits.category).append(" (").append(traits.drivetrain).append(")\n");

        // 3. Analisi Stile Guida
        SetupAdvisor.Assessment assess = SetupAdvisor.analyzeStyleDetailed(laps);
        if (assess != null) {
            SetupAdvisor.StyleMetrics m = assess.metrics();
            sb.append("STILE PILOTA: ").append(assess.primary()).append("\n");
            sb.append(String.format(Locale.US, "INPUT: Gas Oscil=%.0f%%, Sterzo Brusco=%.0f%%, Freno=%.0f%%\n",
                    m.thrOscPct()*100, m.steerHarshPct()*100, m.brakeStompPct()*100));
        }

        // 4. Dati Fisici (Temperature)
        Lap ref = laps.get(laps.size()-1);
        SetupAdvisor.StyleMetrics phys = SetupMetrics.compute(ref);
        sb.append(String.format(Locale.US, "TECNICA: Gomme=%.0fC, Freni=%.0fC\n",
                phys.avgTyreTemp(), phys.avgBrakeTemp()));

        // Setup Geometrico (Camber Hints)
        try {
            SetupMetrics.EdgeDeltas edges = SetupMetrics.edgeDeltas(ref);
            double fDelta = edges.frontInMid() - edges.frontOutMid();
            sb.append(String.format(Locale.US, "DELTA T° GOMME ANT: %.1f\n", fDelta));
        } catch (Exception e) {}

        // 5. Note del Coach
        sb.append("NOTE RILEVATE:\n");
        List<String> hardNotes = CoachSession.generateSessionNotes(laps);
        int limit = Math.min(hardNotes.size(), 5);
        for (int i = 0; i < limit; i++) {
            sb.append("- ").append(hardNotes.get(i)).append("\n");
        }

        return sb.toString();
    }
}