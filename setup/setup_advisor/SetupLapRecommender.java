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

        // ===== Traits veicolo (auto) =====
        VehicleTraits traits = VehicleTraits.detect(java.util.List.of(lap));

        // ===== Metriche guida/telemetria (CoachCore + SetupMetrics) =====
        boolean over        = CoachCore.suspectedOversteer(lap);
        double thrOsc       = CoachCore.throttleOscillationPct(lap, CoachCore.THR_OSC_DPS);
        double steerHarsh   = CoachCore.steeringHarshPct(lap, CoachCore.STEER_REV_RATE);
        int badDown         = CoachCore.downshiftsWithHighThrottle(lap);

        double[] tyresMid   = CoachCore.avgTyreTemps(lap);
        double[] brakesAvg  = CoachCore.avgBrakeTemps(lap);
        double[] psiAvg     = CoachCore.avgTyrePsis(lap);

        // slip ratio/angle (nuovi)
        double[] srFR       = CoachCore.slipRatioFrontRearPct(lap);  // [frontPct, rearPct]
        double[] saFR       = CoachCore.slipAngleFrontRearPct(lap);  // [frontPct, rearPct]

        // seat-force → cordoli (nuovo)
        double kerbPct      = CoachCore.seatKerbPct(lap);

        // DRS/ERS/turbo/EB/bias (nuovi)
        double drsAvail     = CoachCore.has(lap, Channel.DRS_AVAILABLE) ? CoachCore.fractionActive(lap, Channel.DRS_AVAILABLE) : 0.0;
        double drsUsed      = CoachCore.has(lap, Channel.DRS_ACTIVE)    ? CoachCore.fractionActive(lap, Channel.DRS_ACTIVE)    : 0.0;
        double ersRec       = CoachCore.has(lap, Channel.ERS_IS_CHARGING) ? CoachCore.fractionActive(lap, Channel.ERS_IS_CHARGING) : 0.0;
        double ersDep       = CoachCore.fraction(lap, s -> !Double.isNaN(CoachCore.val(s, Channel.KERS_DEPLOYED_ENERGY)) && CoachCore.val(s, Channel.KERS_DEPLOYED_ENERGY) > 0);
        double brakeBias    = CoachCore.firstNonNaN(lap, Channel.BRAKE_BIAS);
        double engBrake     = CoachCore.firstNonNaN(lap, Channel.ENGINE_BRAKE_SETTING);

        // ABS/TC (nuovi)
        double absActive    = CoachCore.fractionActive(lap, Channel.ABS_ACTIVE);
        double tcActive     = CoachCore.fractionActive(lap, Channel.TC_ACTIVE);

        // limitatore
        double limiterFrac  = (CoachCore.has(lap, Channel.ENGINE_RPM) && CoachCore.has(lap, Channel.MAX_RPM))
                ? CoachCore.fraction(lap, s -> {
            double rpm = CoachCore.val(s, Channel.ENGINE_RPM);
            double max = CoachCore.val(s, Channel.MAX_RPM);
            return !Double.isNaN(rpm) && !Double.isNaN(max) && max > 0 && rpm >= 0.99*max;
        })
                : 0.0;

        // turbo: quota di campioni con gas alto ma boost basso
        double lowBoostHiThr = CoachCore.has(lap, Channel.TURBO_BOOST) && CoachCore.has(lap, Channel.THROTTLE)
                ? CoachCore.fraction(lap, s -> (CoachCore.val(s, Channel.THROTTLE) > 80)
                && !Double.isNaN(CoachCore.val(s, Channel.TURBO_BOOST))
                && CoachCore.val(s, Channel.TURBO_BOOST) < 0.5)
                : 0.0;

        // ride height/sospensioni (nuovi)
        double rhFL = CoachCore.firstNonNaN(lap, Channel.RIDE_HEIGHT_FL);
        double rhFR = CoachCore.firstNonNaN(lap, Channel.RIDE_HEIGHT_FR);
        double suspKerbHit = (CoachCore.has(lap, Channel.SUSP_TRAVEL_FL) || CoachCore.has(lap, Channel.SUSP_TRAVEL_FR))
                ? CoachCore.fraction(lap, s -> {
            double tfl = CoachCore.val(s, Channel.SUSP_TRAVEL_FL);
            double tfr = CoachCore.val(s, Channel.SUSP_TRAVEL_FR);
            return (!Double.isNaN(tfl) && Math.abs(tfl) > CoachCore.SUSP_SPIKE_N)
                    || (!Double.isNaN(tfr) && Math.abs(tfr) > CoachCore.SUSP_SPIKE_N);
        })
                : 0.0;

        // meteo/pista (nuovi)
        double roadT = CoachCore.firstNonNaN(lap, Channel.ROAD_TEMP);
        double airT  = CoachCore.firstNonNaN(lap, Channel.AIR_TEMP);
        double grip  = CoachCore.firstNonNaN(lap, Channel.SURFACE_GRIP);
        double wind  = CoachCore.firstNonNaN(lap, Channel.WIND_SPEED);

        // metriche sintetiche stile
        SetupAdvisor.StyleMetrics m = SetupMetrics.compute(lap);

        // ===== Bilancio meccanico (differenziale/barre/ammortizzatori) =====
        boolean rearSlipDom = (srFR != null && saFR != null) && ((srFR[1] > srFR[0]*1.2) || (saFR[1] > saFR[0]*1.2)) || over;
        if (rearSlipDom || (thrOsc > 0.12 && steerHarsh > 0.12)) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Differenziale",
                    "Aumenta Power (+1) e Preload (+1) per migliorare trazione in uscita; se disponibile, +1 TC."));
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Barre",
                    "Riduci barra posteriore (−1) o irrigidisci leggermente l’anteriore (+1) per stabilizzare il retrotreno."));
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Ammortizzatori",
                    "Riduci Rebound posteriore (−1) per dare più appoggio in trazione; Bump posteriore (−1) se l’uscita è nervosa."));
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Aero",
                    "Se possibile, +1 ala posteriore per aiutare la trazione nelle uscite a medio-alta velocità."));
        }

        boolean frontSlipDom = (srFR != null && saFR != null) && ((srFR[0] > srFR[1]*1.2) || (saFR[0] > saFR[1]*1.2));
        if (!rearSlipDom && (frontSlipDom || (m.coastingPct() > 0.18 && steerHarsh < 0.10))) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Barre",
                    "Riduci barra anteriore (−1) e/o aumenta barra posteriore (+1) per aiutare l’inserimento."));
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Geometria",
                    "Più camber anteriore (−0.2°) e un filo di toe-out (+0.02°) per aumentare grip in ingresso."));
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Freni",
                    "Sposta il bias leggermente indietro (~0.3%) per far ruotare l’auto in trail braking."));
        }

        // ===== Adattamenti per TRAZIONE =====
        switch (traits.drivetrain) {
            case FWD -> {
                if (frontSlipDom) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Differenziale",
                            "FWD: aumenta il lock in trazione (+1 Power) e un filo di Preload (+1) sul differenziale anteriore per ridurre pattinamento interno."));
                }
                if (rearSlipDom) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Barre",
                            "FWD: ammorbidisci la barra posteriore (−1) per ridurre il sovrasterzo in rilascio/ingresso."));
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Ammortizzatori",
                            "FWD: riduci Rebound posteriore (−1) per mitigare il lift-off oversteer."));
                }
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Geometria",
                        "FWD: un filo di toe-out anteriore (+0.02°) aiuta la rotazione in ingresso senza perdere trazione."));
            }
            case RWD -> {
                if (rearSlipDom) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Freni",
                            "RWD: sposta il brake bias leggermente indietro (−0.2/−0.3%) se l’auto non ruota in trail."));
                }
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Mappature",
                        "RWD: curva gas più progressiva può ridurre il TC attivo e migliorare la trazione meccanica."));
            }
            case AWD -> {
                if (rearSlipDom) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Differenziale",
                            "AWD: se disponibile, aumenta leggermente il lock del differenziale centrale in coast (+1) per stabilità in ingresso."));
                }
                if (frontSlipDom) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Differenziale",
                            "AWD: se disponibile, +1 Power sul centrale o ripartizione coppia più al posteriore per ridurre sottosterzo in trazione."));
                }
            }
            default -> { /* UNKNOWN -> no-op */ }
        }

        // ===== Adattamenti per CATEGORIA =====
        switch (traits.category) {
            case FORMULA -> {
                // Aero più incisivo
                if (rearSlipDom) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Aero",
                            "Formula: +1 ala posteriore (o rake +0.5–1.0 mm) per trazione ad alta velocità."));
                }
                if (frontSlipDom) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Aero",
                            "Formula: +1 ala anteriore per aumentare carico sull’avantreno in inserimento."));
                }
                // DRS/ERS specifico
                if (drsAvail > 0.05 && drsUsed < drsAvail * 0.6) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "DRS",
                            "Formula: usa il DRS il prima possibile in zona — rendi il comando “one-tap” o sul fine-corsa."));
                }
                if (ersRec > ersDep * 1.4) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Elettrica/ERS",
                            "Formula: alza il deploy di 1 step in uscita curva (mappa ERS più aggressiva sui rettilinei)."));
                }
            }
            case PROTOTYPE -> {
                if (rearSlipDom) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Aero",
                            "Proto: +1 ala posteriore per aiutare la trazione nei tratti medio-veloci."));
                }
                if (frontSlipDom) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Aero",
                            "Proto: +1 click di flap anteriore (se presente) per ridurre sottosterzo a medio carico."));
                }
            }
            case GT, ROAD -> {
                // Elettronica più rilevante
                if (absActive > 0.25) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "ABS",
                            "GT/Road: ABS spesso attivo — prova +1 livello ABS o riduci leggermente la brake pressure."));
                }
                if (tcActive > 0.25 && rearSlipDom) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "TC",
                            "GT/Road: TC spesso attivo in uscita — +1 livello TC o ammorbidisci il posteriore (ARB −1 / Rebound −1)."));
                }
            }
            default -> { /* OTHER: neutro */ }
        }

        // ===== Adattamenti per POWERTRAIN =====
        switch (traits.powertrain) {
            case HYBRID -> {
                if (ersRec > ersDep * CoachCore.ERS_RECOV_OVER_DEPLOY) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Elettrica/ERS",
                            "Hybrid: incrementa il deployment nelle marce medio-alte (+1 mappa) e riduci il recupero in rettilineo."));
                }
            }
            case TURBO -> {
                if (lowBoostHiThr > 0.10) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Trasmissione",
                            "Turbo: spesso gas alto con boost basso — alza la turbo map di +1 e anticipa l’apertura (se configurabile)."));
                }
            }
            default -> { /* NA/UNKNOWN: no-op */ }
        }

        // ===== Stomp / overlap =====
        if (m.brakeStompPct() > 0.10) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Ammortizzatori",
                    "Riduci Bump anteriore (−1) e Rebound posteriore (−1) per attenuare trasferimenti bruschi all’inizio della frenata."));
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Freni",
                    "Valuta una pressione freno leggermente più bassa (−2/−3%) se disponibile."));
        }
        if (m.throttleBrakeOverlap() > 0.08) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Impianto freni",
                    "Riduci leggermente il brake pressure o anticipa il rilascio; verifica sovrapposizioni involontarie del piede sinistro."));
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Mappature",
                    "Usa una curva gas più progressiva per diminuire l’overlap freno/gas."));
        }

        // ===== Downshift / engine brake =====
        if (badDown >= 3) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Trasmissione",
                    "Riduci l’aggressività del blip/cut in scalata; aumenta Coast (+1) per più stabilità in inserimento."));
        }
        if (!Double.isNaN(engBrake) && engBrake >= 7) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Trasmissione",
                    "Engine brake elevato: prova −1/−2 step per ingresso più stabile (meno effetto freno motore)."));
        }

        // ===== Gomme & freni =====
        double tyreAvg = SetupMetrics.avg(tyresMid);
        if (!Double.isNaN(tyreAvg)) {
            if (tyreAvg < 80) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Gomme",
                        String.format(Locale.ITALIAN,
                                "Gomme fredde (avg %.1f°C): pressioni +0.1/+0.2 psi; chiudi brake ducts (−1) e ammorbidisci di poco molle/ARB.", tyreAvg)));
            } else if (tyreAvg > 105) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Gomme",
                        String.format(Locale.ITALIAN,
                                "Gomme calde (avg %.1f°C): pressioni −0.1/−0.2 psi; apri brake ducts (+1) e aggiungi un filo di ala.", tyreAvg)));
            }
        }
        double brakes = SetupMetrics.avg(brakesAvg);
        if (!Double.isNaN(brakes)) {
            // leggera taratura per FORMULA: target più alto
            double lowTh  = traits.category == VehicleTraits.Category.FORMULA ? 180 : 120;
            double highTh = traits.category == VehicleTraits.Category.FORMULA ? 700 : 600;
            if (brakes < lowTh) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Freni",
                        String.format(Locale.ITALIAN, "Freni freddi (avg %.0f°C): chiudi i brake ducts di uno step.", brakes)));
            } else if (brakes > highTh) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Freni",
                        String.format(Locale.ITALIAN, "Freni molto caldi (avg %.0f°C): apri ducts, valuta compound freni più duro.", brakes)));
            }
        }
        if (psiAvg != null) {
            double fl = psiAvg[0], fr = psiAvg[1], rl = psiAvg[2], rr = psiAvg[3];
            if (!Double.isNaN(fl) && !Double.isNaN(fr) && Math.abs(fl - fr) > 0.8) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Gomme",
                        "Pressioni anteriori sbilanciate: pareggia FL/FR per stabilità in inserimento."));
            }
            if (!Double.isNaN(rl) && !Double.isNaN(rr) && Math.abs(rl - rr) > 0.8) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Gomme",
                        "Pressioni posteriori sbilanciate: pareggia RL/RR per trazione prevedibile."));
            }
        }

        // ===== FFB =====
        double ffbClip = CoachCore.ffbClipFraction(lap);
        if (ffbClip > 0.20) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "FFB",
                    String.format(Locale.ITALIAN, "FFB in clipping (%s): riduci il gain globale o aumenta il filtro.", CoachCore.pctFmt(ffbClip))));
        }

        // ===== DRS / ERS / Turbo (base) =====
        if (drsAvail > 0.02 && drsUsed < drsAvail * CoachCore.DRS_UNUSED_FACTOR) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "DRS",
                    "DRS spesso disponibile ma poco usato: assicurati che il comando sia comodo; aprilo all’inizio della zona utile."));
        }
        if (ersRec > ersDep * CoachCore.ERS_RECOV_OVER_DEPLOY) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Elettrica/ERS",
                    "ERS conservativo: aumenta leggermente il deploy in uscita curva (mappa ERS più aggressiva)."));
        }
        if (lowBoostHiThr > 0.10) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Trasmissione",
                    "Boost spesso basso con gas alto: verifica mappa turbo o anticipo di apertura wastegate/boost controller."));
        }

        // ===== Freni (bias diretto) =====
        if (!Double.isNaN(brakeBias)) {
            if (brakeBias > 70) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Freni",
                        String.format(Locale.ITALIAN, "Brake bias molto avanti (%.1f%%): −0.5%% per ridurre blocco anteriore in staccata.", brakeBias)));
            } else if (brakeBias < 58) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Freni",
                        String.format(Locale.ITALIAN, "Brake bias molto al posteriore (%.1f%%): +0.5%% per maggiore stabilità in ingresso.", brakeBias)));
            }
        }

        // ===== Sospensioni / Ride / Cordoli =====
        if ((!Double.isNaN(rhFL) && rhFL < CoachCore.RIDE_LOW_WARN) || (!Double.isNaN(rhFR) && rhFR < CoachCore.RIDE_LOW_WARN)) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Sospensioni/Ride",
                    "Ride height anteriore molto basso: rischio toccare il fondo sui cordoli. Alza di 1-2 mm o irrigidisci leggermente le molle."));
        }
        if (suspKerbHit > 0.05 || kerbPct > 0.12) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Sospensioni/Ride",
                    "Colpi sui cordoli frequenti: riduci Bump anteriore (−1) e/o aumenta Rebound anteriore (+1); evita i kerb alti in appoggio."));
        }

        // ===== Meteo/Pista =====
        if (!Double.isNaN(grip) && grip < CoachCore.GRIP_LOW) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Meteo/Pista",
                    "Grip pista basso: aumenta leggermente il carico (ala +1) o ammorbidisci ARB per più aderenza meccanica."));
        }
        if (!Double.isNaN(wind) && wind > CoachCore.WIND_STRONG) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Meteo/Pista",
                    "Vento forte: tieni un filo più di ala per stabilità e anticipa correzioni su rettilineo."));
        }
        if (!Double.isNaN(roadT) && !Double.isNaN(airT)) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Meteo/Pista",
                    String.format(Locale.ITALIAN, "Asfalto %d°C, aria %d°C: adatta pressioni di ±0.1/0.2 psi rispetto al target.",
                            Math.round(roadT), Math.round(airT))));
        }

        // ===== ABS / TC (lap) =====
        if (absActive > 0.25) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "ABS",
                    "ABS spesso attivo: prova +1 livello ABS oppure riduci leggermente brake pressure per evitare flat spot."));
        }
        if (tcActive > 0.25 && rearSlipDom) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "TC",
                    "TC spesso attivo in uscita: +1 livello TC o ammorbidisci il posteriore (ARB −1 / Rebound −1)."));
        }

        // ===== Limitatore (lap) =====
        if (limiterFrac > 0.03) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Trasmissione",
                    "Limitatore colpito più volte nel giro: allunga la marcia finale di uno step (o scala le ultime due più lunghe)."));
        }

        // ===== Stile di guida =====
        if (style == SetupAdvisor.DriverStyle.AGGRESSIVE) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Mappature",
                    "Curva gas/freno più progressiva per favorire modulazione e trazione; se presente, +1 TC."));
        } else if (style == SetupAdvisor.DriverStyle.SMOOTH) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Aero",
                    "Puoi ridurre leggermente il carico per guadagnare velocità sul dritto (−1 ala posteriore se l’auto resta stabile)."));
        }

        return out;
    }
}
