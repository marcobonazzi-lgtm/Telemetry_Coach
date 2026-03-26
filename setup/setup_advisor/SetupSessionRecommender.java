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

    private static final double KERB_WARN              = 0.12;
    private static final double FFB_CLIP_WARN          = 0.20;
    private static final double DRS_UNUSED_FACTOR      = 0.6;
    private static final double ERS_RECOV_OVER_DEPLOY  = 1.4;
    private static final double RIDE_LOW_WARN_MM       = 30.0;
    private static final double GRIP_LOW               = 0.96;
    private static final double WIND_STRONG_MS         = 7.0;
    private static final double ABS_ACTIVE_HIGH        = 0.20;
    private static final double TC_ACTIVE_HIGH         = 0.20;
    private static final double SLIP_REAR_DOM_FACTOR   = 1.20;
    private static final double BOOST_LOW_FRAC         = 0.10;

    static List<SetupAdvisor.Recommendation> forSession(List<Lap> laps){
        SetupAdvisor.Assessment a = SetupStyleAnalysis.analyzeStyleDetailed(laps);
        SetupAdvisor.DriverStyle style = (a == null) ? SetupAdvisor.DriverStyle.NEUTRAL : a.primary();
        return forSession(laps, style);
    }

    static List<SetupAdvisor.Recommendation> forSession(List<Lap> laps, SetupAdvisor.DriverStyle style){
        List<SetupAdvisor.Recommendation> out = new ArrayList<>();
        if (laps == null || laps.isEmpty()) return out;

        VehicleTraits vt = VehicleTraits.detect(laps);
        VehicleTraits.TargetWindow targets = vt.targets;

        // 1. Fuel & Assetto
        try {
            // Risolto: getFirst() e rimosso controllo != null ridondante
            SetupMetrics.FuelStats fuel = SetupMetrics.fuelStats(laps.getFirst());
            if (SetupMetrics.isF(fuel.level)) {
                double heavyFuelL = switch (vt.category) {
                    case FORMULA -> 35.0;
                    case PROTOTYPE -> 50.0;
                    case GT -> 65.0;
                    default -> 45.0;
                };
                if (fuel.level > heavyFuelL) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Fuel/Assetto",
                            String.format(Locale.ITALIAN, "Vettura ad alta inerzia di massa (Carico carburante ~%.0f L). Controlla le altezze statiche per compensare il peso extra: potresti lavorare a ridosso degli ammortizzatori bumpstop fin dai primi giri.", fuel.level)));
                }
            }
        } catch (Throwable ignore) {}

        // 2. Ducts freno (Dinamico)
        try {
            // Risolto: getLast() e rimosso controllo != null ridondante
            SetupMetrics.BrakeStats br = SetupMetrics.brakeStats(laps.getLast());
            double avgBrake = 0.0; int nB=0;
            for (double v : new double[]{br.avgFL, br.avgFR, br.avgRL, br.avgRR}) { if (SetupMetrics.isF(v)){ avgBrake+=v; nB++; } }
            avgBrake = (nB>0) ? avgBrake/nB : Double.NaN;

            if (SetupMetrics.isF(avgBrake) && SetupMetrics.isF(br.brakeTimePct)) {
                if (avgBrake > targets.tempBrakeMax() && br.brakeTimePct < 0.15) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Freni/Ducts",
                            String.format(Locale.ITALIAN, "Dischi costantemente in overheating termico (%.0f°C) nonostante un'esigua percentuale di tempo passata in frenata. Manca palesemente smaltimento di calore. Apri categoricamente i brake ducts.", avgBrake)));
                } else if (avgBrake < targets.tempBrakeMin() && br.brakeTimePct > 0.15) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Freni/Ducts",
                            String.format(Locale.ITALIAN, "Materiale d'attrito cronicamente freddo (%.0f°C). L'impianto è sovradimensionato per le tue staccate. Parzializza i condotti chiudendo i brake ducts per mantenere temperature operative.", avgBrake)));
                }
            }
        } catch (Throwable ignore) {}

        // 3. Aggregazione Metriche Sessione
        SessionAgg agg = aggregateSessionMetrics(laps);


        // Stile di Guida (Parametro 'style' unused)
        if (style != null && style != SetupAdvisor.DriverStyle.NEUTRAL) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Stile di Guida",
                    "Il tuo stile primario (" + style.name() + ") influenza l'usura. Assicurati che il setup meccanico assecondi le tue tendenze naturali senza forzare troppo gli pneumatici."));
        }

        // Grip Asfalto (GRIP_LOW unused)
        if (!Double.isNaN(agg.gripAvg) && agg.gripAvg < GRIP_LOW) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Grip/Asfalto",
                    String.format(Locale.ITALIAN, "Grip superficiale scarso (%.2f). La pista è 'green' o umida. Aumenta il carico aerodinamico e ammorbidisci le barre antirollio (ARB) per massimizzare la trazione meccanica.", agg.gripAvg)));
        }

        // Sospensioni / Cordoli (KERB_WARN unused - appoggiato a steerHarshAvg)
        if (agg.steerHarshAvg > KERB_WARN) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Sospensioni/Cordoli",
                    "Risposta al volante nervosa e instabile (Possibile abuso dei cordoli o asperità). Aumenta l'altezza da terra o ammorbidisci i Fast Bump dampers per assorbire meglio le irregolarità del tracciato."));
        }

        // Elettronica: TC & ABS (TC_ACTIVE_HIGH e ABS_ACTIVE_HIGH unused)
        if (agg.tcAvg > TC_ACTIVE_HIGH) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Elettronica/TC",
                    "Controllo di trazione (TC) estremamente invasivo. Stai perdendo spinta in uscita. Riduci i valori del TC, oppure ammorbidisci il retrotreno (Molle/ARB) per trovare più trazione meccanica."));
        }
        if (agg.absAvg > ABS_ACTIVE_HIGH) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Elettronica/ABS",
                    "ABS costantemente in funzione. Stai allungando gli spazi di frenata. Sposta il Brake Bias verso il posteriore o riduci la pressione massima del pedale/livello ABS."));
        }

        // Power Unit: ERS & Boost (ERS_RECOV_OVER_DEPLOY e BOOST_LOW_FRAC unused)
        if (agg.ersRecAvg > (agg.ersDepAvg * ERS_RECOV_OVER_DEPLOY)) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Power Unit/ERS",
                    "Recupero ERS sbilanciato: immagazzini molta più energia di quanta ne eroghi. Aumenta la modalità di deploy (MGU-K) per abbassare il tempo sul giro."));
        }
        if (!Double.isNaN(agg.boostAvg) && agg.boostAvg < BOOST_LOW_FRAC) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Power Unit/Turbo",
                    "Pressione del turbo (Boost) cronicamente bassa. Modifica l'Engine Map per erogare più potenza, se i consumi e le temperature lo permettono."));
        }

        // -- FINE IMPLEMENTAZIONE NUOVE LOGICHE --

        // Gomme Sessione
        if (!Double.isNaN(agg.tyreAvg)) {
            if (agg.tyreAvg < targets.tempCoreMin()) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Gomme",
                        String.format(Locale.ITALIAN, "Carcasse strutturalmente fredde (avg %.0f°C). Mancando calore al core, la gomma non sviluppa grip meccanico. Alza le pressioni (psi) di partenza o chiudi i brake ducts per trattenere l'irraggiamento termico nei cerchi.", agg.tyreAvg)));
            } else if (agg.tyreAvg > targets.tempCoreMax()) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Gomme",
                        String.format(Locale.ITALIAN, "Surriscaldamento cronico della mescola (avg %.0f°C). La gomma degraderà precocemente nel long run (blistering/thermal drop). Abbassa le pressioni (psi) o apri i brake ducts.", agg.tyreAvg)));
            }
        }

        // Bilancio Meccanico
        if (agg.oversteerLike || agg.rearSlipDom) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Bilanciamento",
                    "Instabilità cronica al posteriore nel corso del long-run. Trasferisci grip al retrotreno: chiudi il differenziale in rilascio (+1 Preload/Coast), aumenta il carico dell'ala aero posteriore o ammorbidisci la barra antirollio (ARB) al posteriore."));
        } else if (agg.frontSlipDom) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Bilanciamento",
                    "Piattaforma pigra in inserimento curva (Sottosterzo sistematico). Favorisci la rotazione geometrica indurendo la barra antirollio (ARB) posteriore, ammorbidendo l'anteriore, oppure spostando il bilanciamento aero in avanti (più ala anteriore)."));
        }

        // FFB
        if (agg.ffbClipAvg > FFB_CLIP_WARN) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "FFB",
                    String.format(Locale.ITALIAN, "Forze al volante saturate (FFB clipping %.0f%% del tempo). La scatola sterzo digitale è a fondocorsa logico, nascondendoti le perdite di aderenza. Riduci il gain.", agg.ffbClipAvg * 100)));
        }

        // DRS
        if (agg.drsAvailAvg > 0.02 && agg.drsUsedAvg < agg.drsAvailAvg * DRS_UNUSED_FACTOR) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "DRS",
                    "Efficienza DRS sacrificata: stai sprecando margine sul tempo sul giro non aprendo il flap mobile appena possibile nelle zone di attivazione."));
        }

        // Meteo
        if (!Double.isNaN(agg.windAvg) && agg.windAvg > WIND_STRONG_MS) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Meteo",
                    "Impatto eolico frontale/laterale rilevante (Vento forte). Aumenta marginalmente il carico aero posteriore per contrastare l'instabilità direzionale nei punti di massima velocità."));
        }

        // Stomp / Overlap (Guida -> Setup)
        if (agg.overlapAvg > 0.08) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Guida/Pedali",
                    "Sovrapposizione parassita Gas/Freno (Overlap). Stai mantenendo attivi entrambi i circuiti idraulici, confondendo il differenziale e destabilizzando il pitch. Regola la corsa dei pedali o arretra il brake bias."));
        }
        if (agg.stompAvg > 0.10) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Ammortizzatori",
                    "Pestoni aggressivi al freno rilevati: il muso fa un 'dive' violento scomponendo il retrotreno. Indurisci gli ammortizzatori in Slow Bump anteriore per resistere al trasferimento di carico, oppure alza il livello ABS."));
        }

        // Ride Height
        if (!Double.isNaN(agg.rideFAvg) && agg.rideFAvg < RIDE_LOW_WARN_MM) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Ride Height",
                    String.format(Locale.ITALIAN, "Pressione aerodinamica asfissiante: altezza anteriore media sotto-soglia (%.0f mm). La variazione della downforce è troppo nervosa al variare del beccheggio. Alza la scocca.", agg.rideFAvg)));
        }

        // Limitatore
        if (agg.limiterFrac > 0.02) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Trasmissione",
                    "Frequenti micro-tagli al limitatore (Over-revving). Stai sprecando potenziale d'allungo del motore e scaldando olio/acqua inutilmente. Allunga il Final Ratio o la singola marcia di punta."));
        }

        // Deduplicazione finale
        Set<String> seen = new LinkedHashSet<>();
        List<SetupAdvisor.Recommendation> dedup = new ArrayList<>();
        for (SetupAdvisor.Recommendation r : out){
            String key = r.area()+"|"+r.message();
            if (seen.add(key)) dedup.add(r);
        }
        return dedup;
    }

    // ---- DTO e Aggregazione Completa ----

    private static final class SessionAgg {
        double thrOscAvg, steerHarshAvg, stompAvg, overlapAvg, coastingAvg, ffbClipAvg;
        boolean oversteerLike;
        boolean rearSlipDom, frontSlipDom;
        double tyreAvg, brakeAvg;
        double rideFAvg, rideRAvg;
        double drsAvailAvg, drsUsedAvg;
        double limiterFrac;
        double gripAvg, windAvg;

        // Nuovi campi per risolvere i warning
        double tcAvg, absAvg;
        double ersRecAvg, ersDepAvg;
        double boostAvg;
    }

    private static SessionAgg aggregateSessionMetrics(List<Lap> laps){
        SessionAgg a = new SessionAgg();
        int n=0;
        double sumThr=0, sumSteer=0, sumStomp=0, sumOvl=0, sumCoast=0, sumClip=0;
        int overHits=0;
        double sumTyre=0, sumBrake=0; int nTyre=0, nBrake=0;
        double sumRideF=0, sumRideR=0; int nRide=0;
        double sumDrsAvail=0, sumDrsUsed=0; int nDrs=0;
        double sumLim=0; int nLim=0;
        double sumGrip=0, sumWind=0; int nEnv=0;
        int rearDomHits=0, frontDomHits=0, slipCnt=0;

        // Variabili per i nuovi campi
        double sumTc=0, sumAbs=0; int nElettronica=0;
        double sumErsRec=0, sumErsDep=0; int nErs=0;
        double sumBoost=0; int nBoost=0;

        for (Lap l : laps){
            if (l == null || l.samples == null || l.samples.isEmpty()) continue;
            n++;

            var m = SetupMetrics.compute(l);
            sumThr += m.thrOscPct();
            sumSteer += m.steerHarshPct();
            sumStomp += m.brakeStompPct();
            sumOvl += m.throttleBrakeOverlap();
            sumCoast += m.coastingPct();
            sumClip += m.ffbClipPct();
            if (m.oversteerLike()) overHits++;

            if (!Double.isNaN(m.avgTyreTemp())) { sumTyre += m.avgTyreTemp(); nTyre++; }
            if (!Double.isNaN(m.avgBrakeTemp())) { sumBrake += m.avgBrakeTemp(); nBrake++; }

            double rf = CoachCore.meanChannel(l, Channel.RIDE_HEIGHT_FL);
            double rr = CoachCore.meanChannel(l, Channel.RIDE_HEIGHT_RL);
            if (!Double.isNaN(rf)) { sumRideF += rf; nRide++; }
            if (!Double.isNaN(rr)) { sumRideR += rr; }

            // Risolto: rimosso controllo != null ridondante su srFR
            double[] srFR = CoachCore.slipRatioFrontRearPct(l);
            if (srFR[1] > srFR[0] * SLIP_REAR_DOM_FACTOR) rearDomHits++;
            if (srFR[0] > srFR[1] * SLIP_REAR_DOM_FACTOR) frontDomHits++;
            slipCnt++;

            if (CoachCore.has(l, Channel.DRS_AVAILABLE)) {
                sumDrsAvail += CoachCore.fractionActive(l, Channel.DRS_AVAILABLE);
                sumDrsUsed += CoachCore.fractionActive(l, Channel.DRS_ACTIVE);
                nDrs++;
            }

            if (CoachCore.has(l, Channel.ENGINE_RPM) && CoachCore.has(l, Channel.MAX_RPM)) {
                double frac = CoachCore.fraction(l, s -> {
                    double rpm = CoachCore.val(s, Channel.ENGINE_RPM);
                    double max = CoachCore.val(s, Channel.MAX_RPM);
                    return !Double.isNaN(rpm) && !Double.isNaN(max) && max > 0 && rpm >= 0.99*max;
                });
                sumLim += frac; nLim++;
            }

            double g = CoachCore.firstNonNaN(l, Channel.SURFACE_GRIP);
            double w = CoachCore.firstNonNaN(l, Channel.WIND_SPEED);
            if (!Double.isNaN(g)) { sumGrip += g; nEnv++; }
            if (!Double.isNaN(w)) { sumWind += w; }

            // Estrazione Elettronica (TC / ABS)
            if (CoachCore.has(l, Channel.TC_ACTIVE) || CoachCore.has(l, Channel.ABS_ACTIVE)) {
                sumTc += CoachCore.fractionActive(l, Channel.TC_ACTIVE);
                sumAbs += CoachCore.fractionActive(l, Channel.ABS_ACTIVE);
                nElettronica++;
            }

            // Estrazione Power Unit (ERS / Boost)
            try {
                double eR = CoachCore.meanChannel(l, Channel.ERS_RECOVERY_LEVEL);
                double eD = CoachCore.meanChannel(l, Channel.ERS_POWER_LEVEL);
                if (!Double.isNaN(eR) && !Double.isNaN(eD)) { sumErsRec += eR; sumErsDep += eD; nErs++; }

                double bst = CoachCore.meanChannel(l, Channel.TURBO_BOOST);
                if (!Double.isNaN(bst)) { sumBoost += bst; nBoost++; }
            } catch (Exception ignore) {}
        }

        if (n > 0) {
            a.thrOscAvg = sumThr / n;
            a.steerHarshAvg = sumSteer / n;
            a.stompAvg = sumStomp / n;
            a.overlapAvg = sumOvl / n;
            a.coastingAvg = sumCoast / n;
            a.ffbClipAvg = sumClip / n;
            a.oversteerLike = overHits > (n / 2);
        }
        a.tyreAvg = (nTyre > 0) ? sumTyre / nTyre : Double.NaN;
        a.brakeAvg = (nBrake > 0) ? sumBrake / nBrake : Double.NaN;
        a.rideFAvg = (nRide > 0) ? sumRideF / nRide : Double.NaN;
        a.rideRAvg = (nRide > 0) ? sumRideR / nRide : Double.NaN;
        if (slipCnt > 0) {
            a.rearSlipDom = rearDomHits > (slipCnt / 2);
            a.frontSlipDom = frontDomHits > (slipCnt / 2);
        }
        a.drsAvailAvg = (nDrs > 0) ? sumDrsAvail / nDrs : 0.0;
        a.drsUsedAvg = (nDrs > 0) ? sumDrsUsed / nDrs : 0.0;
        a.limiterFrac = (nLim > 0) ? sumLim / nLim : 0.0;
        a.gripAvg = (nEnv > 0) ? sumGrip / nEnv : Double.NaN;
        a.windAvg = (nEnv > 0) ? sumWind / nEnv : Double.NaN;

        // Medie Nuove Metriche
        a.tcAvg = (nElettronica > 0) ? sumTc / nElettronica : 0.0;
        a.absAvg = (nElettronica > 0) ? sumAbs / nElettronica : 0.0;
        a.ersRecAvg = (nErs > 0) ? sumErsRec / nErs : 0.0;
        a.ersDepAvg = (nErs > 0) ? sumErsDep / nErs : 0.0;
        a.boostAvg = (nBoost > 0) ? sumBoost / nBoost : Double.NaN;

        return a;
    }
}
