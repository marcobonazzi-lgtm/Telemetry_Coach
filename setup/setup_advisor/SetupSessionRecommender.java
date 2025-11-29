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
            SetupMetrics.FuelStats fuel = SetupMetrics.fuelStats(laps.get(0));
            if (fuel != null && SetupMetrics.isF(fuel.level)) {
                double heavyFuelL = switch (vt.category) {
                    case FORMULA -> 35.0;
                    case PROTOTYPE -> 50.0;
                    case GT -> 65.0;
                    default -> 45.0;
                };
                if (fuel.level > heavyFuelL) {
                    out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Fuel/Assetto",
                            String.format(Locale.ITALIAN, "Carico carburante alto (~%.0f L): controlla altezza anteriore e bumpstop.", fuel.level)));
                }
            }
        } catch (Throwable ignore) {}

        // 2. Ducts freno (Dinamico)
        try {
            SetupMetrics.BrakeStats br = SetupMetrics.brakeStats(laps.get(laps.size()-1));
            if (br != null) {
                double avgBrake = 0.0; int nB=0;
                for (double v : new double[]{br.avgFL, br.avgFR, br.avgRL, br.avgRR}) { if (SetupMetrics.isF(v)){ avgBrake+=v; nB++; } }
                avgBrake = (nB>0) ? avgBrake/nB : Double.NaN;

                if (SetupMetrics.isF(avgBrake) && SetupMetrics.isF(br.brakeTimePct)) {
                    if (avgBrake > targets.tempBrakeMax() && br.brakeTimePct < 0.15) {
                        out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.HIGH, "Freni/Ducts",
                                String.format(Locale.ITALIAN, "Temp. freni alta (%.0f°C) con frenate brevi: apri ducts.", avgBrake)));
                    } else if (avgBrake < targets.tempBrakeMin() && br.brakeTimePct > 0.15) {
                        out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Freni/Ducts",
                                String.format(Locale.ITALIAN, "Temp. freni bassa (%.0f°C): chiudi ducts.", avgBrake)));
                    }
                }
            }
        } catch (Throwable ignore) {}

        // 3. Aggregazione Metriche Sessione
        SessionAgg agg = aggregateSessionMetrics(laps);

        // Gomme Sessione
        if (!Double.isNaN(agg.tyreAvg)) {
            if (agg.tyreAvg < targets.tempCoreMin()) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Gomme",
                        String.format(Locale.ITALIAN, "Sessione fredda (gomme %.0f°C): alza pressioni/chiudi ducts.", agg.tyreAvg)));
            } else if (agg.tyreAvg > targets.tempCoreMax()) {
                out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Gomme",
                        String.format(Locale.ITALIAN, "Sessione calda (gomme %.0f°C): abbassa pressioni/apri ducts.", agg.tyreAvg)));
            }
        }

        // Bilancio Meccanico
        if (agg.oversteerLike || agg.rearSlipDom) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Bilanciamento",
                    "Tendenza al Sovrasterzo: +1 Power/Preload, +1 Ala Posteriore o ammorbidisci ARB post."));
        } else if (agg.frontSlipDom) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Bilanciamento",
                    "Tendenza al Sottosterzo: ammorbidisci ARB ant. o aumenta Ala Anteriore."));
        }

        // FFB
        if (agg.ffbClipAvg > FFB_CLIP_WARN) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "FFB",
                    String.format(Locale.ITALIAN, "FFB in clipping (%.0f%%): riduci gain.", agg.ffbClipAvg * 100)));
        }

        // DRS
        if (agg.drsAvailAvg > 0.02 && agg.drsUsedAvg < agg.drsAvailAvg * DRS_UNUSED_FACTOR) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "DRS",
                    "DRS poco utilizzato: ottimizza le zone di attivazione."));
        }

        // Meteo
        if (!Double.isNaN(agg.windAvg) && agg.windAvg > WIND_STRONG_MS) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Meteo",
                    "Vento forte: aumenta leggermente l'ala per stabilità."));
        }

        // Stomp / Overlap (Guida -> Setup)
        if (agg.overlapAvg > 0.08) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Guida/Pedali",
                    "Overlap Gas/Freno elevato: controlla la calibrazione o ammorbidisci la curva gas."));
        }
        if (agg.stompAvg > 0.10) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.LOW, "Ammortizzatori",
                    "Pestoni sul freno rilevati: prova a ridurre Bump anteriore (-1)."));
        }

        // Ride Height
        if (!Double.isNaN(agg.rideFAvg) && agg.rideFAvg < RIDE_LOW_WARN_MM) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Ride Height",
                    String.format(Locale.ITALIAN, "Altezza anteriore media bassa (%.0f mm): rischio fondo.", agg.rideFAvg)));
        }

        // Limitatore
        if (agg.limiterFrac > 0.02) {
            out.add(new SetupAdvisor.Recommendation(SetupAdvisor.Severity.MEDIUM, "Trasmissione",
                    "Limitatore colpito frequentemente: allunga i rapporti."));
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

            double[] srFR = CoachCore.slipRatioFrontRearPct(l);
            if (srFR != null) {
                if (srFR[1] > srFR[0] * SLIP_REAR_DOM_FACTOR) rearDomHits++;
                if (srFR[0] > srFR[1] * SLIP_REAR_DOM_FACTOR) frontDomHits++;
                slipCnt++;
            }

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

        return a;
    }
}