package org.simulator.setup.setup_advisor;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.coach.CoachCore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SetupSessionRecommender {

    private SetupSessionRecommender(){}

    // ---- soglie locali per i nuovi segnali (evitiamo dipendenze da costanti esterne) ----
    private static final double KERB_WARN              = 0.12;   // quota cordoli “alta”
    private static final double FFB_CLIP_WARN          = 0.20;   // quota clipping media
    private static final double DRS_UNUSED_FACTOR      = 0.6;    // usato < 60% di avail => consiglio
    private static final double ERS_RECOV_OVER_DEPLOY  = 1.4;    // recupero >> deploy
    private static final double RIDE_LOW_WARN_MM       = 30.0;   // ride anteriore “basso” (mm)
    private static final double GRIP_LOW               = 0.96;   // grip pista basso (es. 96%)
    private static final double WIND_STRONG_MS         = 7.0;    // vento forte (m/s)
    private static final double PSI_LR_DIFF_WARN       = 0.8;    // sbilancio L/R pressioni (psi)
    private static final double BOOST_LOW_FRAC         = 0.10;   // quota “gas alto / boost basso” elevata

    // NUOVE soglie/sessione
    private static final double ABS_ACTIVE_HIGH        = 0.20;   // ABS spesso attivo
    private static final double TC_ACTIVE_HIGH         = 0.20;   // TC spesso attivo
    private static final double SLIP_REAR_DOM_FACTOR   = 1.20;   // slip post > 120% del front
    private static final double SLIP_FRONT_DOM_FACTOR  = 1.20;   // slip ant > 120% del rear
    private static final double RAKE_LOW_MM            = 5.0;    // rake (RR-FF) troppo basso
    private static final double RAKE_HIGH_MM           = 20.0;   // rake troppo alto
    private static final double LIMITER_HIT_FRAC       = 0.02;   // colpi limitatore rilevanti
    private static final double TYRE_AXLE_DIFF_HOT     = 8.0;    // anteriore vs posteriore (°C)
    private static final double BRAKE_AXLE_DIFF_HOT    = 80.0;   // freni ant vs post (°C)
    private static final double PSI_AXLE_IMBALANCE     = 0.6;    // media assi (psi)

    static List<SetupAdvisor.Recommendation> forSession(List<Lap> laps){
        SetupAdvisor.Assessment a = SetupStyleAnalysis.analyzeStyleDetailed(laps);
        SetupAdvisor.DriverStyle style = (a == null) ? SetupAdvisor.DriverStyle.NEUTRAL : a.primary();
        return forSession(laps, style);
    }

    static List<SetupAdvisor.Recommendation> forSession(List<Lap> laps, SetupAdvisor.DriverStyle style){
        List<SetupAdvisor.Recommendation> out = new ArrayList<>();
        if (laps == null || laps.isEmpty()) return out;

        // nuovi: tratti medi della sessione
        VehicleTraits vt = VehicleTraits.detect(laps);

        var agg = aggregateSessionMetrics(laps);

        // ===== Bilancio meccanico (sovra/sottosterzo) =====
        if (agg.oversteerLike || (agg.thrOscAvg > 0.12 && agg.steerHarshAvg > 0.12) || agg.rearSlipDom){
            switch (vt.drivetrain){
                case FWD -> out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Barre",
                        "Sessione: FWD con uscite nervose → barra posteriore +1 e meno toe-out posteriore (−0.02°)."));
                case AWD -> out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Differenziale",
                        "Sessione: AWD con sovrasterzo in trazione → riduci leggermente preload del center diff o sposta ripartizione verso il front."));
                default -> out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Differenziale",
                        "Sessione: sovrasterzo/uscite nervose → Power +1 e Preload +1; se presente, +1 TC."));
            }
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Ammortizzatori",
                    "Sessione: riduci Rebound posteriore (−1) per più appoggio in trazione; Bump posteriore −1 se serve."));
            if (vt.category == VehicleTraits.Category.FORMULA){
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Aero",
                        "Sessione: Formula → +1 ala posteriore o −1 mm rake per aiutare l’uscita a medio-veloce."));
            } else {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Aero",
                        "Sessione: +1 ala posteriore per trazione su GT/Proto."));
            }
            if (agg.coastingAvg < 0.10 && agg.steerHarshAvg > 0.12){
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Differenziale",
                        "Sessione: ingresso nervoso → Coast +1 per stabilità in rilascio freno."));
            }
        } else if (agg.coastingAvg > 0.18 && agg.steerHarshAvg < 0.10 || agg.frontSlipDom) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Barre",
                    "Sessione: sottosterzo → barra anteriore −1 / barra posteriore +1."));
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Geometria",
                    "Sessione: più camber ant. (−0.2°) e un filo di toe-out ant. (+0.02°)."));
            if (vt.drivetrain == VehicleTraits.Drivetrain.FWD) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Trasmissione",
                        "Sessione: FWD → engine brake −1 per stabilità in ingresso."));
            } else {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Freni",
                        "Sessione: bias un filo indietro (~0.3%) per aiutare il trail-braking."));
            }
        }

        // ===== Overlap / Stomp =====
        if (agg.overlapAvg > 0.08) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Impianto freni",
                    "Sessione: overlap freno/gas elevato → riduci leggermente la pressione o anticipa il rilascio."));
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Mappature",
                    "Sessione: curva gas più progressiva per ridurre l’overlap."));
        }
        if (agg.stompAvg > 0.10) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Ammortizzatori",
                    "Sessione: picchi sul freno → riduci Bump ant. (−1) e Rebound post. (−1)."));
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Freni",
                    "Sessione: considera pressione freno −2/−3% (se disponibile)."));
        }

        // ===== Temperature gomme: axle balance =====
        if (!Double.isNaN(agg.tyreAvg)) {
            double hotHi = (vt.category==VehicleTraits.Category.FORMULA) ? 102 : 105;
            double coldLo = (vt.category==VehicleTraits.Category.FORMULA) ? 78 : 80;
            if (agg.tyreAvg < coldLo) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Gomme",
                        String.format(Locale.ITALIAN,
                                "Gomme fredde in media (%.1f°C, dev %.1f°C): pressioni +0.1/+0.2 psi; ducts −1; assetto un filo più morbido.",
                                agg.tyreAvg, agg.tyreDev)));
            } else if (agg.tyreAvg > hotHi) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Gomme",
                        String.format(Locale.ITALIAN,
                                "Gomme calde in media (%.1f°C, dev %.1f°C): pressioni −0.1/−0.2 psi; ducts +1%s.",
                                agg.tyreAvg, agg.tyreDev,
                                vt.category==VehicleTraits.Category.FORMULA?"; valuta −1 mm rake":"")));
            }
            if (!Double.isNaN(agg.tyreFrontAvg) && !Double.isNaN(agg.tyreRearAvg)) {
                double dt = agg.tyreFrontAvg - agg.tyreRearAvg;
                if (dt > TYRE_AXLE_DIFF_HOT) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Geometria",
                            String.format(Locale.ITALIAN, "Anteriore più caldo del posteriore (+%.0f°C): −0.1° camber ant. o +0.1° camber post. per bilanciare il carico.", dt)));
                } else if (dt < -TYRE_AXLE_DIFF_HOT) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Geometria",
                            String.format(Locale.ITALIAN, "Posteriore più caldo dell’anteriore (+%.0f°C): +0.1° camber ant. o −0.1° camber post.", -dt)));
                }
            }
        }

        // ===== Temperature freni: axle balance =====
        if (!Double.isNaN(agg.brakeAvg)) {
            double hi = (vt.category==VehicleTraits.Category.FORMULA)? 700 : 600;
            if (agg.brakeAvg < 120) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW,  "Freni",
                        String.format(Locale.ITALIAN, "Freni freddi in media (%.0f°C): chiudi i brake ducts di uno step.", agg.brakeAvg)));
            } else if (agg.brakeAvg > hi) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Freni",
                        String.format(Locale.ITALIAN, "Freni molto caldi in media (%.0f°C): apri ducts; valuta compound freni più duro.", agg.brakeAvg)));
            }
            if (!Double.isNaN(agg.brakeFrontAvg) && !Double.isNaN(agg.brakeRearAvg)) {
                double db = agg.brakeFrontAvg - agg.brakeRearAvg;
                if (db > BRAKE_AXLE_DIFF_HOT) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Freni",
                            "Freni anteriori più caldi: apri un filo i ducts anteriori o sposta bias leggermente indietro (−0.2/−0.3%)."));
                } else if (db < -BRAKE_AXLE_DIFF_HOT) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Freni",
                            "Freni posteriori più caldi: apri i ducts posteriori o sposta bias leggermente avanti (+0.2/+0.3%)."));
                }
            }
        }

        // ===== FFB =====
        double clipWarn = (vt.category==VehicleTraits.Category.FORMULA)? (FFB_CLIP_WARN*0.85) : FFB_CLIP_WARN;
        if (agg.ffbClipAvg > clipWarn) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "FFB",
                    String.format(Locale.ITALIAN, "FFB in clipping medio (%.0f%%): riduci il gain o aumenta il filtro.", 100*agg.ffbClipAvg)));
        }

        // ===== Cordoli / sospensioni / ride =====
        double kerbWarn = (vt.category==VehicleTraits.Category.FORMULA)? Math.min(0.10, KERB_WARN) : KERB_WARN;
        if (agg.kerbAvg > kerbWarn) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Sospensioni/Ride",
                    String.format(Locale.ITALIAN, "Cordoli usati spesso in sessione (%.0f%%): %s",
                            100*agg.kerbAvg,
                            vt.category==VehicleTraits.Category.FORMULA
                                    ? "monoposto: riduci Bump ant. (−1) e alza 1 mm il front ride."
                                    : "riduci Bump ant. (−1) e gestisci i kerb in appoggio.")));
        }
        if (!Double.isNaN(agg.rideFAvg) && agg.rideFAvg < RIDE_LOW_WARN_MM) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Sospensioni/Ride",
                    String.format(Locale.ITALIAN, "Ride height anteriore basso (avg %.0f mm): alza di 1–2 mm o irrigidisci leggermente le molle.", agg.rideFAvg)));
        }
        // rake (RR - FF)
        if (!Double.isNaN(agg.rideFAvg) && !Double.isNaN(agg.rideRAvg)) {
            double rake = agg.rideRAvg - agg.rideFAvg;
            if (rake < RAKE_LOW_MM) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Aero/Ride",
                        "Rake basso: +1 mm posteriore o −1 mm anteriore per ritrovare carico sull’avantreno (attenzione ai fondi)."));
            } else if (rake > RAKE_HIGH_MM) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Aero/Ride",
                        "Rake alto: −1/−2 mm posteriore per stabilità ad alta velocità e minore drag residuo."));
            }
        }

        // ===== Bias freni / pressioni L/R =====
        if (!Double.isNaN(agg.biasAvg)) {
            if (agg.biasAvg > 70) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Freni",
                        String.format(Locale.ITALIAN, "Bias medio molto avanti (%.1f%%): −0.5%% per ridurre blocco anteriore in staccata.", agg.biasAvg)));
            } else if (agg.biasAvg < 58) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Freni",
                        String.format(Locale.ITALIAN, "Bias medio molto al posteriore (%.1f%%): +0.5%% per maggiore stabilità in ingresso.", agg.biasAvg)));
            }
        }
        if (agg.psiLRFrontDiffAvg > PSI_LR_DIFF_WARN) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Gomme",
                    String.format(Locale.ITALIAN, "Pressioni ant. sbilanciate (ΔLR medio %.1f psi): pareggia FL/FR per stabilità in inserimento.", agg.psiLRFrontDiffAvg)));
        }
        if (agg.psiLRRearDiffAvg > PSI_LR_DIFF_WARN) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Gomme",
                    String.format(Locale.ITALIAN, "Pressioni post. sbilanciate (ΔLR medio %.1f psi): pareggia RL/RR per trazione prevedibile.", agg.psiLRRearDiffAvg)));
        }
        // media assi
        if (!Double.isNaN(agg.psiFrontAvg) && !Double.isNaN(agg.psiRearAvg)
                && Math.abs(agg.psiFrontAvg - agg.psiRearAvg) > PSI_AXLE_IMBALANCE) {
            if (agg.psiFrontAvg > agg.psiRearAvg) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Gomme",
                        "Pressioni anteriori > posteriori: riduci ant. di 0.1/0.2 psi o aumenta un filo il carico posteriore."));
            } else {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Gomme",
                        "Pressioni posteriori > anteriori: riduci post. di 0.1/0.2 psi o alza di 1 mm il posteriore (se serve rotazione)."));
            }
        }

        // ===== DRS / ERS / Turbo =====
        if (agg.drsAvailAvg > 0.02 && agg.drsUsedAvg < agg.drsAvailAvg * DRS_UNUSED_FACTOR) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "DRS",
                    "Sessione: DRS spesso disponibile ma poco usato → rendi il comando più comodo e aprilo all’inizio della zona utile."));
        }
        if (agg.ersRecAvg > agg.ersUseAvg * ERS_RECOV_OVER_DEPLOY) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Elettrica/ERS",
                    "Sessione: ERS conservativo → aumenta leggermente il deploy in uscita curva (mappa ERS più aggressiva)."));
        }
        if (agg.lowBoostHiThrAvg > BOOST_LOW_FRAC) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Trasmissione",
                    "Sessione: spesso gas alto con boost basso → verifica mappa turbo o anticipo wastegate/boost controller."));
        }

        // ===== ABS / TC livello =====
        if (agg.absActiveAvg > ABS_ACTIVE_HIGH) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "ABS",
                    "ABS spesso attivo: prova +1 livello ABS oppure riduci leggermente la pressione freno."));
        }
        if (agg.tcActiveAvg > TC_ACTIVE_HIGH && (agg.oversteerLike || agg.rearSlipDom)) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "TC",
                    "TC spesso attivo in uscita: +1 livello TC o ammorbidisci il posteriore (ARB −1 / Rebound −1)."));
        }

        // ===== Motore/rapporto finale (limit.) =====
        if (agg.limiterFrac > LIMITER_HIT_FRAC) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Trasmissione",
                    "Limitatore colpito frequentemente: allunga il rapporto finale (o ultime marce più lunghe)."));
        }

        // ===== Meteo/Pista =====
        if (!Double.isNaN(agg.gripAvg) && agg.gripAvg < GRIP_LOW) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Meteo/Pista",
                    "Grip pista basso in media: aumenta un filo il carico (ala +1) o ammorbidisci leggermente le ARB."));
        }
        if (!Double.isNaN(agg.windAvg) && agg.windAvg > WIND_STRONG_MS) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Meteo/Pista",
                    "Vento forte medio: tieni un filo più di ala per stabilità e anticipa le correzioni sul dritto."));
        }
        if (!Double.isNaN(agg.roadTAvg) && !Double.isNaN(agg.airTAvg)) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Meteo/Pista",
                    String.format(Locale.ITALIAN, "Asfalto %.0f°C, aria %.0f°C: adatta pressioni ±0.1/0.2 psi rispetto al target.", agg.roadTAvg, agg.airTAvg)));
        }

        // ===== Stile sessione (tocco finale) =====
        if (style == SetupAdvisor.DriverStyle.AGGRESSIVE) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Mappature",
                    "Sessione: curve gas/freno più progressive per migliorare modulazione e trazione; se presente, +1 TC."));
        } else if (style == SetupAdvisor.DriverStyle.SMOOTH) {
            if (vt.category == VehicleTraits.Category.FORMULA){
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Aero",
                        "Sessione: stile pulito su monoposto → puoi scendere di −1 mm al front o −1 click ala se resta stabile."));
            } else {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Aero",
                        "Sessione: stile pulito → puoi ridurre un filo il carico (−1 ala posteriore) se l’auto resta stabile."));
            }
        }

        // ---- ordinamento: HIGH → MEDIUM → LOW, poi area alfabetica ----
        out.sort((r1, r2) -> {
            int w1 = sevWeight(r1.sev()), w2 = sevWeight(r2.sev());
            if (w1 != w2) return Integer.compare(w2, w1); // desc
            return r1.area().compareToIgnoreCase(r2.area());
        });

        // ---- dedup concettuale: stesso area+testo -> tieni la più severa (già ordinato) ----
        Set<String> seen = new LinkedHashSet<>();
        List<SetupAdvisor.Recommendation> dedup = new ArrayList<>();
        for (SetupAdvisor.Recommendation r : out){
            String key = r.area()+"|"+r.message();
            if (seen.add(key)) dedup.add(r);
        }
        return dedup;
    }

    // ---- aggregazione metriche di sessione (con nuovi segnali) ----
    private static final class SessionAgg {
        double thrOscAvg, steerHarshAvg, stompAvg, overlapAvg, coastingAvg, ffbClipAvg;
        boolean oversteerLike;
        boolean rearSlipDom, frontSlipDom;

        double tyreAvg, tyreDev, brakeAvg;
        double tyreFrontAvg, tyreRearAvg;
        double brakeFrontAvg, brakeRearAvg;

        double kerbAvg, biasAvg;
        double rideFAvg, rideRAvg;
        double psiLRFrontDiffAvg, psiLRRearDiffAvg;
        double psiFrontAvg, psiRearAvg;

        double drsAvailAvg, drsUsedAvg, ersUseAvg, ersRecAvg;
        double lowBoostHiThrAvg;

        double absActiveAvg, tcActiveAvg;
        double limiterFrac;

        double gripAvg, windAvg, roadTAvg, airTAvg;
    }

    private static SessionAgg aggregateSessionMetrics(List<Lap> laps){
        List<Double> tyresAll = new ArrayList<>();
        List<Double> brakesAll= new ArrayList<>();

        double thr=0, st=0, stomp=0, ovl=0, coast=0, clip=0;
        int n=0, overHits=0;

        // per slips/front-rear
        int rearDomHits=0, frontDomHits=0, slipCnt=0;

        double kerbSum=0, biasSum=0, biasCnt=0;
        double rideFSum=0, rideRSum=0, rideCnt=0;
        double psiFrontDiffSum=0, psiRearDiffSum=0, psiCnt=0;
        double psiFrontSum=0, psiRearSum=0;

        double tyreFrontSum=0, tyreRearSum=0, tyreAxleCnt=0;
        double brakeFrontSum=0, brakeRearSum=0, brakeAxleCnt=0;

        double drsAvailSum=0, drsUsedSum=0, ersUseSum=0, ersRecSum=0, ersCnt=0, drsCnt=0;
        double lowBoostSum=0, lowBoostCnt=0;

        double absSum=0, tcSum=0, atcCnt=0;
        double limiterSum=0, limiterCnt=0;

        double gripSum=0, windSum=0, roadTSum=0, airTSum=0, envCnt=0;

        for (Lap l : laps){
            if (l == null || l.samples == null || l.samples.isEmpty()) continue;

            var m = SetupMetrics.compute(l);
            thr   += m.thrOscPct();
            st    += m.steerHarshPct();
            stomp += m.brakeStompPct();
            ovl   += m.throttleBrakeOverlap();
            coast += m.coastingPct();
            clip  += m.ffbClipPct();
            if (m.oversteerLike()) overHits++;
            if (!Double.isNaN(m.avgTyreTemp()))  tyresAll.add(m.avgTyreTemp());
            if (!Double.isNaN(m.avgBrakeTemp())) brakesAll.add(m.avgBrakeTemp());
            n++;

            // slip front/rear (se disponibili)
            double[] srFR = CoachCore.slipRatioFrontRearPct(l);
            double[] saFR = CoachCore.slipAngleFrontRearPct(l);
            if (srFR != null && saFR != null){
                boolean rearDom = (srFR[1] > srFR[0]*SLIP_REAR_DOM_FACTOR) || (saFR[1] > saFR[0]*SLIP_REAR_DOM_FACTOR);
                boolean frontDom= (srFR[0] > srFR[1]*SLIP_FRONT_DOM_FACTOR) || (saFR[0] > saFR[1]*SLIP_FRONT_DOM_FACTOR);
                if (rearDom) rearDomHits++;
                if (frontDom) frontDomHits++;
                slipCnt++;
            }

            // --- nuovi segnali per sessione ---
            kerbSum += CoachCore.seatKerbPct(l);

            double bias = CoachCore.firstNonNaN(l, Channel.BRAKE_BIAS);
            if (!Double.isNaN(bias)) { biasSum += bias; biasCnt++; }

            double rf = meanPair(l, Channel.RIDE_HEIGHT_FL, Channel.RIDE_HEIGHT_FR);
            double rr = meanPair(l, Channel.RIDE_HEIGHT_RL, Channel.RIDE_HEIGHT_RR);
            if (!Double.isNaN(rf)) { rideFSum += rf; rideCnt++; }
            if (!Double.isNaN(rr)) { rideRSum += rr; }

            double[] psi = CoachCore.avgTyrePsis(l);
            if (psi != null && psi.length == 4){
                double frontDiff = diffAbs(psi[0], psi[1]);
                double rearDiff  = diffAbs(psi[2], psi[3]);
                if (!Double.isNaN(frontDiff) && !Double.isNaN(rearDiff)){
                    psiFrontDiffSum += frontDiff; psiRearDiffSum += rearDiff; psiCnt++;
                }
                double pFront = mean(psi[0], psi[1]);
                double pRear  = mean(psi[2], psi[3]);
                if (!Double.isNaN(pFront) && !Double.isNaN(pRear)){
                    psiFrontSum += pFront; psiRearSum += pRear;
                }
            }

            // temperature assi
            double[] t = CoachCore.avgTyreTemps(l);
            if (t != null && t.length == 4){
                double tf = mean(t[0], t[1]);
                double tr = mean(t[2], t[3]);
                if (!Double.isNaN(tf) && !Double.isNaN(tr)){
                    tyreFrontSum += tf; tyreRearSum += tr; tyreAxleCnt++;
                }
            }
            double[] bt = CoachCore.avgBrakeTemps(l);
            if (bt != null && bt.length == 4){
                double bf = mean(bt[0], bt[1]);
                double br = mean(bt[2], bt[3]);
                if (!Double.isNaN(bf) && !Double.isNaN(br)){
                    brakeFrontSum += bf; brakeRearSum += br; brakeAxleCnt++;
                }
            }

            if (CoachCore.has(l, Channel.DRS_AVAILABLE) || CoachCore.has(l, Channel.DRS_ACTIVE)) {
                drsAvailSum += CoachCore.fractionActive(l, Channel.DRS_AVAILABLE);
                drsUsedSum  += CoachCore.fractionActive(l, Channel.DRS_ACTIVE);
                drsCnt++;
            }
            if (CoachCore.has(l, Channel.ERS_IS_CHARGING) || CoachCore.has(l, Channel.KERS_DEPLOYED_ENERGY)) {
                ersRecSum += CoachCore.fractionActive(l, Channel.ERS_IS_CHARGING);
                double use = CoachCore.fraction(l, s -> {
                    double e = CoachCore.val(s, Channel.KERS_DEPLOYED_ENERGY);
                    return !Double.isNaN(e) && e > 0;
                });
                ersUseSum += use; ersCnt++;
            }

            // quota “gas alto / boost basso”
            if (CoachCore.has(l, Channel.TURBO_BOOST) && CoachCore.has(l, Channel.THROTTLE)) {
                double q = CoachCore.fraction(l, s -> (CoachCore.val(s, Channel.THROTTLE) > 80)
                        && !Double.isNaN(CoachCore.val(s, Channel.TURBO_BOOST))
                        && CoachCore.val(s, Channel.TURBO_BOOST) < 0.5);
                lowBoostSum += q; lowBoostCnt++;
            }

            // ABS/TC
            if (CoachCore.has(l, Channel.ABS_ACTIVE) || CoachCore.has(l, Channel.TC_ACTIVE)){
                absSum += CoachCore.fractionActive(l, Channel.ABS_ACTIVE);
                tcSum  += CoachCore.fractionActive(l, Channel.TC_ACTIVE);
                atcCnt++;
            }

            // limitatore (rpm > 99% Max RPM)
            if (CoachCore.has(l, Channel.ENGINE_RPM) && CoachCore.has(l, Channel.MAX_RPM)) {
                double frac = CoachCore.fraction(l, s -> {
                    double rpm = CoachCore.val(s, Channel.ENGINE_RPM);
                    double max = CoachCore.val(s, Channel.MAX_RPM);
                    return !Double.isNaN(rpm) && !Double.isNaN(max) && max > 0 && rpm >= 0.99*max;
                });
                limiterSum += frac; limiterCnt++;
            }

            double grip = CoachCore.firstNonNaN(l, Channel.SURFACE_GRIP);
            double wind = CoachCore.firstNonNaN(l, Channel.WIND_SPEED);
            double road = CoachCore.firstNonNaN(l, Channel.ROAD_TEMP);
            double air  = CoachCore.firstNonNaN(l, Channel.AIR_TEMP);
            if (!Double.isNaN(grip) || !Double.isNaN(wind) || !Double.isNaN(road) || !Double.isNaN(air)){
                if (!Double.isNaN(grip)) gripSum += grip;
                if (!Double.isNaN(wind)) windSum += wind;
                if (!Double.isNaN(road)) roadTSum += road;
                if (!Double.isNaN(air))  airTSum  += air;
                envCnt++;
            }
        }

        SessionAgg a = new SessionAgg();
        if (n>0){
            a.thrOscAvg     = thr/n;
            a.steerHarshAvg = st/n;
            a.stompAvg      = stomp/n;
            a.overlapAvg    = ovl/n;
            a.coastingAvg   = coast/n;
            a.ffbClipAvg    = clip/n;
            a.oversteerLike = overHits > n/2;
        }
        a.tyreAvg  = SetupMath.mean(tyresAll);
        a.tyreDev  = SetupMath.mad(tyresAll, a.tyreAvg);
        a.brakeAvg = SetupMath.mean(brakesAll);

        // slip dominance
        if (slipCnt>0){
            a.rearSlipDom  = rearDomHits  > slipCnt/2;
            a.frontSlipDom = frontDomHits > slipCnt/2;
        }

        a.kerbAvg  = (n>0) ? kerbSum/n : 0.0;
        a.biasAvg  = (biasCnt>0)? biasSum/biasCnt : Double.NaN;
        a.rideFAvg = (rideCnt>0)? rideFSum/rideCnt : Double.NaN;
        a.rideRAvg = (rideCnt>0)? rideRSum/rideCnt : Double.NaN;

        a.psiLRFrontDiffAvg = (psiCnt>0)? psiFrontDiffSum/psiCnt : Double.NaN;
        a.psiLRRearDiffAvg  = (psiCnt>0)? psiRearDiffSum/psiCnt : Double.NaN;
        if (psiCnt>0){
            a.psiFrontAvg = psiFrontSum/psiCnt;
            a.psiRearAvg  = psiRearSum/psiCnt;
        } else {
            a.psiFrontAvg = a.psiRearAvg = Double.NaN;
        }

        a.drsAvailAvg = (drsCnt>0)? drsAvailSum/drsCnt : 0.0;
        a.drsUsedAvg  = (drsCnt>0)? drsUsedSum/drsCnt : 0.0;
        a.ersUseAvg   = (ersCnt>0)? ersUseSum/ersCnt  : 0.0;
        a.ersRecAvg   = (ersCnt>0)? ersRecSum/ersCnt  : 0.0;

        a.lowBoostHiThrAvg = (lowBoostCnt>0)? lowBoostSum/lowBoostCnt : 0.0;

        a.absActiveAvg = (atcCnt>0)? absSum/atcCnt : 0.0;
        a.tcActiveAvg  = (atcCnt>0)? tcSum/atcCnt  : 0.0;

        a.limiterFrac  = (limiterCnt>0)? limiterSum/limiterCnt : 0.0;

        if (tyreAxleCnt>0){
            a.tyreFrontAvg = tyreFrontSum/tyreAxleCnt;
            a.tyreRearAvg  = tyreRearSum/tyreAxleCnt;
        } else {
            a.tyreFrontAvg = a.tyreRearAvg = Double.NaN;
        }
        if (brakeAxleCnt>0){
            a.brakeFrontAvg = brakeFrontSum/brakeAxleCnt;
            a.brakeRearAvg  = brakeRearSum/brakeAxleCnt;
        } else {
            a.brakeFrontAvg = a.brakeRearAvg = Double.NaN;
        }

        if (envCnt>0){
            a.gripAvg  = gripSum/envCnt;
            a.windAvg  = windSum/envCnt;
            a.roadTAvg = roadTSum/envCnt;
            a.airTAvg  = airTSum/envCnt;
        } else {
            a.gripAvg = a.windAvg = a.roadTAvg = a.airTAvg = Double.NaN;
        }

        return a;
    }

    // ---- helpers ----
    private static int sevWeight(SetupAdvisor.Severity s){
        return switch (s){
            case HIGH -> 3; case MEDIUM -> 2; case LOW -> 1;
        };
    }
    private static double meanPair(Lap lap, Channel a, Channel b){
        double va = CoachCore.meanChannel(lap, a);
        double vb = CoachCore.meanChannel(lap, b);
        if (Double.isNaN(va) && Double.isNaN(vb)) return Double.NaN;
        if (Double.isNaN(va)) return vb;
        if (Double.isNaN(vb)) return va;
        return (va+vb)/2.0;
    }
    private static double diffAbs(double x, double y){
        if (Double.isNaN(x) || Double.isNaN(y)) return Double.NaN;
        return Math.abs(x - y);
    }
    private static double mean(double x, double y){
        if (Double.isNaN(x) && Double.isNaN(y)) return Double.NaN;
        if (Double.isNaN(x)) return y;
        if (Double.isNaN(y)) return x;
        return (x+y)/2.0;
    }
}
