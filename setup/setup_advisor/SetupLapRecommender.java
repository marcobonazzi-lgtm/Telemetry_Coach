package org.simulator.setup.setup_advisor;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.coach.CoachCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SetupLapRecommender {

    private SetupLapRecommender(){}

    static List<SetupAdvisor.Recommendation> forLap(Lap lap, SetupAdvisor.DriverStyle style){
        List<SetupAdvisor.Recommendation> out = new ArrayList<>();
        if (lap == null) return out;

        // 1. Rilevamento Auto e Target Fisici
        VehicleTraits traits = VehicleTraits.detect(java.util.List.of(lap));
        VehicleTraits.TargetWindow targets = traits.targets;

        // 2. Metriche base
        boolean over        = CoachCore.suspectedOversteer(lap);
        int badDown         = CoachCore.downshiftsWithHighThrottle(lap);
        double[] tyresMid   = CoachCore.avgTyreTemps(lap);
        double[] brakesAvg  = CoachCore.avgBrakeTemps(lap);
        double[] psiAvg     = CoachCore.avgTyrePsis(lap);

        // 3. Metriche Avanzate (Geometriche)
        SetupMetrics.EdgeDeltas edges = SetupMetrics.edgeDeltas(lap);
        SetupMetrics.SuspUtil susp   = SetupMetrics.suspUtil(lap);

        // ===== GOMME: Pressioni Assolute (Target Dinamici) =====
        if (psiAvg != null) {
            double avgPsi = SetupMath.mean(psiAvg);
            if (!Double.isNaN(avgPsi)) {
                if (avgPsi < targets.psiMin()) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Pressioni",
                            String.format(Locale.ITALIAN, "Pressioni globalmente basse (avg %.1f): target %.1f–%.1f psi.", avgPsi, targets.psiMin(), targets.psiMax())));
                } else if (avgPsi > targets.psiMax()) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Pressioni",
                            String.format(Locale.ITALIAN, "Pressioni globalmente alte (avg %.1f): target %.1f–%.1f psi.", avgPsi, targets.psiMin(), targets.psiMax())));
                }
            }
        }

        // ===== GOMME: Analisi Profilo (Cappello vs U)=====
        try {
            double dMidF = (edges.midFL + edges.midFR) / 2.0;
            double dAvgEdgesF = ((edges.inFL + edges.outFL) + (edges.inFR + edges.outFR)) / 4.0;
            double frontCap = dMidF - dAvgEdgesF;
            if (SetupMetrics.isF(frontCap)) {
                if (frontCap > 4.0) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Pressioni",
                            "Profilo ‘a cappello’ anteriore (centro caldo): abbassa le pressioni di −0.1/−0.2 psi."));
                } else if ((dAvgEdgesF - dMidF) > 4.0) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Pressioni",
                            "Profilo ‘a U’ anteriore (spalle calde): aumenta le pressioni di +0.1/+0.2 psi."));
                }
            }
            double dMidR = (edges.midRL + edges.midRR) / 2.0;
            double dAvgEdgesR = ((edges.inRL + edges.outRL) + (edges.inRR + edges.outRR)) / 4.0;
            double rearCap = dMidR - dAvgEdgesR;
            if (SetupMetrics.isF(rearCap)) {
                if (rearCap > 4.0) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Pressioni",
                            "Profilo ‘a cappello’ posteriore: −0.1 psi per restare nel target."));
                } else if ((dAvgEdgesR - dMidR) > 4.0) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Pressioni",
                            "Profilo ‘a U’ posteriore: +0.1 psi per ridurre flessione carcassa."));
                }
            }
        } catch (Throwable ignore) {}

        // ===== GEOMETRIA: Camber (Inner vs Outer)=====
        try {
            double inMidF = edges.frontInMid();
            double outMidF = edges.frontOutMid();
            if (SetupMetrics.isF(inMidF) && SetupMetrics.isF(outMidF)) {
                if (inMidF - outMidF > 7.0) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Geometria",
                            "Anteriore Inner molto caldo: Camber forse eccessivo, riduci di ~0.2°."));
                } else if (outMidF - inMidF > 5.0) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Geometria",
                            "Anteriore Outer molto caldo: Camber insufficiente, aumenta di ~0.2°."));
                }
            }
            double inMidR = edges.rearInMid();
            double outMidR = edges.rearOutMid();
            if (SetupMetrics.isF(inMidR) && SetupMetrics.isF(outMidR)) {
                if (inMidR - outMidR > 5.0) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Geometria",
                            "Posteriore Inner caldo: riduci camber posteriore di ~0.1°."));
                }
            }
        } catch (Throwable ignore) {}

        // ===== GEOMETRIA: Toe (Delta L/R)=====
        try {
            double leftBias = (edges.inFL - edges.outFL) - (edges.inFR - edges.outFR);
            if (SetupMetrics.isF(leftBias) && Math.abs(leftBias) > 5.0) {
                String lato = leftBias > 0 ? "sinistra" : "destra";
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Geometria",
                        "Differenza termica L/R anteriore: verifica consumo anomalo a " + lato + " (possibile Toe eccessivo su quel lato)."));
            }
        } catch (Throwable ignore) {}

        // ===== SOSPENSIONI: Travel Analysis =====
        try {
            double rUtil = susp.rear();
            if (SetupMetrics.isF(rUtil) && rUtil > 0.94) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Sospensioni",
                        "Posteriore a pacco (travel >94%): irrigidisci molle/bumpstop o alza il rake (+1-2mm)."));
            }
            double fUtil = susp.front();
            if (SetupMetrics.isF(fUtil) && fUtil > 0.94) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Sospensioni",
                        "Anteriore a pacco: rischio bottoming. Alza ride height o indurisci molle."));
            }
        } catch (Throwable ignore) {}

        // ===== TEMPERATURE GOMME =====
        double tyreAvg = SetupMath.mean(tyresMid);
        if (!Double.isNaN(tyreAvg)) {
            if (tyreAvg < targets.tempCoreMin()) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Gomme",
                        String.format(Locale.ITALIAN, "Gomme fredde (avg %.0f°C): chiudi ducts o alza pressione.", tyreAvg)));
            } else if (tyreAvg > targets.tempCoreMax()) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Gomme",
                        String.format(Locale.ITALIAN, "Gomme calde (avg %.0f°C): apri ducts o abbassa pressione.", tyreAvg)));
            }
        }

        // ===== FRENI (Core) - Dinamico =====
        double brakes = SetupMath.mean(brakesAvg);
        if (!Double.isNaN(brakes)) {
            if (brakes < targets.tempBrakeMin()) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Freni",
                        String.format(Locale.ITALIAN, "Freni freddi (%.0f°C): chiudi brake ducts.", brakes)));
            } else if (brakes > targets.tempBrakeMax()) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Freni",
                        String.format(Locale.ITALIAN, "Freni surriscaldati (%.0f°C): apri ducts o gestisci la staccata.", brakes)));
            }
        }

        // ===== BILANCIO E TRAZIONE (Logica Drivetrain) =====
        double[] srFR = CoachCore.slipRatioFrontRearPct(lap);
        double[] saFR = CoachCore.slipAngleFrontRearPct(lap);

        boolean rearSlipDom = (srFR != null && saFR != null) && ((srFR[1] > srFR[0]*1.2) || (saFR[1] > saFR[0]*1.2)) || over;
        boolean frontSlipDom = (srFR != null && saFR != null) && ((srFR[0] > srFR[1]*1.2) || (saFR[0] > saFR[1]*1.2));

        if (rearSlipDom) {
            if (traits.drivetrain == VehicleTraits.Drivetrain.FWD) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Barre",
                        "FWD Sovrasterzante: ammorbidisci la barra posteriore o riduci il rake."));
            } else {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Differenziale",
                        "Sovrasterzo in uscita: +1 Power, +1 Preload (o +1 TC)."));
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Ammortizzatori",
                        "Riduci Rebound posteriore per trazione; ammorbidisci Bump posteriore se nervoso."));
            }
        } else if (frontSlipDom) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Barre",
                    "Sottosterzo: ammorbidisci barra ant. o indurisci post."));
            if (traits.drivetrain == VehicleTraits.Drivetrain.FWD) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Differenziale",
                        "FWD Sottosterzo: aumenta il bloccaggio diff (Power) per trascinare il muso dentro."));
            }
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Geometria",
                    "Aumenta Camber anteriore (-0.1°) per grip laterale."));
        }

        // ===== Downshift / Engine Brake =====
        if (badDown >= 3) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Trasmissione",
                    "Downshift aggressivi (RPM alti): aumenta Coast (+1) o riduci Engine Brake per non bloccare il ponte."));
        }

        // ===== Ride Height (Kerb Strikes) =====
        double rhFL = CoachCore.firstNonNaN(lap, Channel.RIDE_HEIGHT_FL);
        if (!Double.isNaN(rhFL) && rhFL < CoachCore.RIDE_LOW_WARN) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Sospensioni/Ride",
                    "Ride height anteriore critico: rischio danni/bottoming. Alza di 1-2 mm."));
        }

        // ===== Aids (TC/ABS) =====
        double absAct = CoachCore.fractionActive(lap, Channel.ABS_ACTIVE);
        if (absAct > 0.25) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "ABS",
                    "ABS molto attivo: prova ad aumentare il livello ABS o ridurre la pressione freno."));
        }

        // ===== Stile Guida =====
        if (style == SetupAdvisor.DriverStyle.AGGRESSIVE && rearSlipDom) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Mappature",
                    "Guida aggressiva: usa una mappa acceleratore più lineare/progressiva per gestire la trazione."));
        }

        return out;
    }
}