package org.simulator.gemini;

import org.simulator.canale.Lap;
import org.simulator.coach.CoachSession;
import org.simulator.setup.setup_advisor.SetupAdvisor;
import org.simulator.setup.setup_advisor.SetupMetrics;
import org.simulator.setup.setup_advisor.VehicleTraits;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GeminiContextBuilder {

    public static String buildLapContext(Lap lap) {
        if (lap == null) return "Nessun dato giro disponibile.";
        return buildCommonContext(Collections.singletonList(lap), true);
    }

    public static String buildSessionContext(List<Lap> laps) {
        if (laps == null || laps.isEmpty()) return "Nessun dato sessione disponibile.";
        return buildCommonContext(laps, false);
    }

    private static String buildCommonContext(List<Lap> laps, boolean singleLap) {
        StringBuilder sb = new StringBuilder();

        // 1. Veicolo
        VehicleTraits traits = VehicleTraits.detect(laps);
        sb.append("VEICOLO: ").append(traits.category).append(" ").append(traits.drivetrain).append("\n");
        sb.append("TARGET GOMME (Psi): ").append(traits.targets.psiMin()).append("-").append(traits.targets.psiMax()).append("\n");

        // 2. Analisi Stile
        SetupAdvisor.Assessment assess = SetupAdvisor.analyzeStyleDetailed(laps);
        if (assess != null) {
            SetupAdvisor.StyleMetrics m = assess.metrics();
            sb.append("STILE GUIDA: ").append(assess.primary()).append("\n");
            sb.append(String.format(Locale.US, "Metriche: Oscil.Gas=%.0f%%, BruschezzaSterzo=%.0f%%, PestoniFreno=%.0f%%\n",
                    m.thrOscPct()*100, m.steerHarshPct()*100, m.brakeStompPct()*100));
            if (m.oversteerLike()) sb.append("NOTA: Rilevata tendenza al sovrasterzo.\n");
        }

        // 3. Dati Fisici Ultimo Giro (o giro specifico)
        Lap ref = laps.get(laps.size()-1);

        // CORREZIONE: SetupMetrics.compute restituisce SetupAdvisor.StyleMetrics
        SetupAdvisor.StyleMetrics phys = SetupMetrics.compute(ref);

        sb.append(String.format(Locale.US, "FISICA (Ultimo/Giro): Gomme=%.0fC, Freni=%.0fC, FFB Clip=%.0f%%\n",
                phys.avgTyreTemp(), phys.avgBrakeTemp(), phys.ffbClipPct()*100));

        // Setup Geometrico (Camber/Toe hints)
        try {
            SetupMetrics.EdgeDeltas edges = SetupMetrics.edgeDeltas(ref);
            double fDelta = edges.frontInMid() - edges.frontOutMid();
            sb.append(String.format(Locale.US, "USURA GOMME ANT (Inner-Outer): %.1f (Valori alti >7 indicano troppo camber)\n", fDelta));
        } catch (Exception e) {
            sb.append("USURA GOMME: Dati insufficienti.\n");
        }

        // 4. Note del Coach (Hard Math)
        sb.append("NOTE TECNICHE RILEVATE (Coach): \n");
        List<String> hardNotes = CoachSession.generateSessionNotes(laps);
        for(String n : hardNotes) sb.append("- ").append(n).append("\n");

        return sb.toString();
    }
}