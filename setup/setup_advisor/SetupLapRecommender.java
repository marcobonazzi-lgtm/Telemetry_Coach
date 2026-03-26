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
                            String.format(Locale.ITALIAN, "Pressione a caldo sotto la finestra operativa (avg %.1f psi). Stai perdendo supporto strutturale sulla carcassa. Alza le pressioni a freddo per centrare il target %.1f–%.1f psi.", avgPsi, targets.psiMin(), targets.psiMax())));
                } else if (avgPsi > targets.psiMax()) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Pressioni",
                            String.format(Locale.ITALIAN, "Pressione a caldo eccessiva (avg %.1f psi). L'impronta a terra (contact patch) è ridotta. Abbassa le pressioni a freddo (target %.1f–%.1f psi).", avgPsi, targets.psiMin(), targets.psiMax())));
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
                            "Gradiente termico a 'cappello' sull'anteriore (centro surriscaldato). Abbassa le pressioni di −0.1/−0.2 psi per spalmare il carico su tutto il battistrada."));
                } else if ((dAvgEdgesF - dMidF) > 4.0) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Pressioni",
                            "Gradiente termico a 'U' sull'anteriore (spalle sovraccaricate). La carcassa flette eccessivamente al centro. Aumenta le pressioni di +0.1/+0.2 psi per stabilizzarla."));
                }
            }
            double dMidR = (edges.midRL + edges.midRR) / 2.0;
            double dAvgEdgesR = ((edges.inRL + edges.outRL) + (edges.inRR + edges.outRR)) / 4.0;
            double rearCap = dMidR - dAvgEdgesR;
            if (SetupMetrics.isF(rearCap)) {
                if (rearCap > 4.0) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Pressioni",
                            "Gradiente a 'cappello' al posteriore: riduci di −0.1 psi per massimizzare la trazione longitudinale."));
                } else if ((dAvgEdgesR - dMidR) > 4.0) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Pressioni",
                            "Gradiente a 'U' al posteriore: aumenta di +0.1 psi per supportare le pareti laterali."));
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
                            "Anteriore Inner in overheating termico: il Camber statico negativo è eccessivo per le curve di questo tracciato. Riduci l'angolo di ~0.2°."));
                } else if (outMidF - inMidF > 5.0) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Geometria",
                            "Anteriore Outer surriscaldato: l'esterno spalla lavora troppo in appoggio. Manca Camber negativo, aumentalo di ~0.2° per resistere al rollio."));
                }
            }
            double inMidR = edges.rearInMid();
            double outMidR = edges.rearOutMid();
            if (SetupMetrics.isF(inMidR) && SetupMetrics.isF(outMidR)) {
                if (inMidR - outMidR > 5.0) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Geometria",
                            "Posteriore Inner troppo caldo: perdi grip vitale in trazione a ruote dritte. Riduci il camber posteriore di ~0.1°."));
                }
            }
        } catch (Throwable ignore) {}

        // ===== GEOMETRIA: Toe (Delta L/R)=====
        try {
            double leftBias = (edges.inFL - edges.outFL) - (edges.inFR - edges.outFR);
            if (SetupMetrics.isF(leftBias) && Math.abs(leftBias) > 5.0) {
                String lato = leftBias > 0 ? "sinistra" : "destra";
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Geometria",
                        "Asimmetria termica L/R anteriore (" + lato + " più critica). Se il circuito è sbilanciato è normale, altrimenti verifica di non aver settato valori di Toe asimmetrici."));
            }
        } catch (Throwable ignore) {}

        // ===== SOSPENSIONI: Travel Analysis =====
        try {
            double rUtil = susp.rear();
            if (SetupMetrics.isF(rUtil) && rUtil > 0.94) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Sospensioni",
                        "Sospensione posteriore a pacco (travel >94%). Stai toccando i bumpstop in compressione, innescando snap oversteer imprevedibili. Irrigidisci il wheel rate (molle) o alza il posteriore (+1-2mm)."));
            }
            double fUtil = susp.front();
            if (SetupMetrics.isF(fUtil) && fUtil > 0.94) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Sospensioni",
                        "Fondocorsa anteriore rilevato sotto carico (Bumpstop attivi). Rischio di bloccaggi in staccata. Indurisci le molle anteriori o alza la ride height."));
            }
        } catch (Throwable ignore) {}

        // ===== TEMPERATURE GOMME =====
        double tyreAvg = SetupMath.mean(tyresMid);
        if (!Double.isNaN(tyreAvg)) {
            if (tyreAvg < targets.tempCoreMin()) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Gomme",
                        String.format(Locale.ITALIAN, "Gomme fredde sotto la working window (avg %.0f°C): la mescola non garantisce adesione meccanica. Chiudi i brake ducts o alza le pressioni (psi).", tyreAvg)));
            } else if (tyreAvg > targets.tempCoreMax()) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Gomme",
                        String.format(Locale.ITALIAN, "Gomme calde oltre il picco di grip (avg %.0f°C): surriscaldamento chimico. Apri i brake ducts o abbassa le pressioni (psi).", tyreAvg)));
            }
        }

        // ===== FRENI (Core) - Dinamico =====
        double brakes = SetupMath.mean(brakesAvg);
        if (!Double.isNaN(brakes)) {
            if (brakes < targets.tempBrakeMin()) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Freni",
                        String.format(Locale.ITALIAN, "Freni freddi (%.0f°C): materiale d'attrito fuori range. Rischio vetrificazione (glazing). Chiudi i brake ducts.", brakes)));
            } else if (brakes > targets.tempBrakeMax()) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Freni",
                        String.format(Locale.ITALIAN, "Freni surriscaldati (%.0f°C): stai superando la soglia di ossidazione. Rischio fade critico sul pedale. Apri i brake ducts o sposta il brake bias.", brakes)));
            }
        }

        // ===== BILANCIO E TRAZIONE (Logica Drivetrain) =====
        double[] srFR = CoachCore.slipRatioFrontRearPct(lap);
        double[] saFR = CoachCore.slipAngleFrontRearPct(lap);

        boolean rearSlipDom = srFR[1] > srFR[0] * 1.2 || saFR[1] > saFR[0] * 1.2 || over;
        boolean frontSlipDom = srFR[0] > srFR[1] * 1.2 || saFR[0] > saFR[1] * 1.2;

        if (rearSlipDom) {
            if (traits.drivetrain == VehicleTraits.Drivetrain.FWD) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Barre",
                        "Lift-off oversteer eccessivo (FWD): il posteriore è troppo libero. Ammorbidisci la barra antirollio (ARB) posteriore o riduci il rake idrodinamico."));
            } else {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Differenziale",
                        "Trazione posteriore critica (Power Oversteer): il retrotreno pattina in uscita. Chiudi il differenziale in trazione (+1 Power) o aumenta il Preload. In alternativa valuta di ridurre la mappa del TC."));
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Ammortizzatori",
                        "Instabilità longitudinale posteriore: riduci ammortizzatori in Slow Rebound posteriore per massimizzare l'impronta a terra in accelerazione, e ammorbidisci il Bump se saltelli sui cordoli."));
            }
        } else if (frontSlipDom) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Barre",
                    "Saturazione asse anteriore (Sottosterzo cronico): il muso scivola. Ammorbidisci la barra antirollio (ARB) anteriore o indurisci la posteriore per forzare la rotazione."));
            if (traits.drivetrain == VehicleTraits.Drivetrain.FWD) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Differenziale",
                        "Sottosterzo in uscita (FWD): il differenziale aperto disperde la coppia sulla ruota interna scarica. Aumenta il bloccaggio in tiro (Power) per far chiudere la curva al muso."));
            }
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Geometria",
                    "Aumenta leggermente il Camber anteriore (-0.1°) per incrementare il limite di aderenza laterale a centro curva."));
        }

        // ===== Downshift / Engine Brake =====
        if (badDown >= 3) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Trasmissione",
                    "Engine braking invasivo: downshift aggressivi stanno causando bloccaggio o saltellamento del ponte posteriore. Aumenta il livello di Coast (+1) nel differenziale o riduci il freno motore via elettronica."));
        }

        // ===== Ride Height (Kerb Strikes) =====
        double rhFL = CoachCore.firstNonNaN(lap, Channel.RIDE_HEIGHT_FL);
        if (!Double.isNaN(rhFL) && rhFL < CoachCore.RIDE_LOW_WARN) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Sospensioni/Ride",
                    "Ride height anteriore critico: l'aero-plank sfiora il suolo. Altissimo rischio di stallo aerodinamico e bottoming violento. Alza la vettura di 1-2 mm."));
        }

        // ===== Aids (TC/ABS) =====
        double absAct = CoachCore.fractionActive(lap, Channel.ABS_ACTIVE);
        if (absAct > 0.25) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "ABS",
                    "Intervento ABS cronico: l'algoritmo di salvataggio taglia pressione frenante per troppa frazione di curva. Prova a ridurre il livello ABS per avere un feeling più analogico sul pedale."));
        }

        // ===== Stile Guida =====
        if (style == SetupAdvisor.DriverStyle.AGGRESSIVE && rearSlipDom) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Mappature",
                    "Throttle mapping non compatibile col tuo stile aggressivo: usa una curva del gas più piatta/lineare (Throttle Map) per dosare la coppia motrice in uscita."));
        }

        return out;
    }
}
