package org.simulator.coach;

import org.simulator.analisi_base.lap_analysis.LapAnalysis;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;

import java.util.ArrayList;
import java.util.List;

import static org.simulator.coach.CoachCore.*;

final class CoachDriving {

    private CoachDriving(){}

    static List<Note> generateLapDrivingNotes(Lap lap){
        List<Note> tmp = new ArrayList<>();
        if (lap == null || lap.samples.isEmpty()) {
            // Recupero info extra anche su lap vuoti/parziali se possibile
            try {
                for (String s : VehicleAdviceUtil.coachExtras(lap)) tmp.add(new Note(Priority.LOW, Category.GUIDA, s));
            } catch(Throwable t){
                System.out.println(t.getMessage());
            }
            return tmp;
        }

        VehicleKind kind = CoachCore.detectVehicleKind(lap);

        boolean hasThr = has(lap, Channel.THROTTLE);
        boolean hasBrk = has(lap, Channel.BRAKE);
        boolean hasSte = has(lap, Channel.STEER_ANGLE);
        boolean hasRpm = has(lap, Channel.ENGINE_RPM);
        boolean hasGear= has(lap, Channel.GEAR);
        boolean hasFFB = has(lap, Channel.FFB);
        boolean hasTC  = has(lap, Channel.TC_ACTIVE);
        boolean hasABS = has(lap, Channel.ABS_ACTIVE);

        // --- Eventi base guida ---
        var brakes = LapAnalysis.brakeEvents(lap, BRK_ON*100, 5);
        add(tmp, brakes.size() > 12, Priority.MEDIUM, Category.GUIDA, // Aumentato leggermente per circuiti complessi
                "Freni molto spesso: punta a più scorrevolezza tra le curve.");

        if (hasABS) {
            double absPct = fractionActive(lap, Channel.ABS_ACTIVE);
        // ABS
            add(tmp, absPct > profAbsMuch(kind), Priority.HIGH, Category.GUIDA,
                    "Saturazione longitudinale (ABS sempre attivo, " + pctFmt(absPct) + "): il picco di frenata va bene, ma devi modulare (rilasciare il freno) prima per non surriscaldare la gomma e per inserire l'auto.");
        }

        var apexes = LapAnalysis.apexes(lap, 5);
        double vMin = apexMinSpeed(lap, apexes);
        // Apex lenti
        add(tmp, !Double.isNaN(vMin) && vMin < 50, Priority.MEDIUM, Category.GUIDA,
                "Over-slowing a centro curva (Min speed: " + Math.round(vMin) + " km/h): stai sacrificando troppa scorrevolezza. Prova a portare più velocità (momentum) dentro la curva.");

        if (suspectedOversteer(lap)){
        // Sovrasterzo
            add(tmp, true, Priority.HIGH, Category.GUIDA,
                    "Snap Oversteer rilevato: stai innescando instabilità al posteriore. Addolcisci il rilascio del freno o anticipa il raddrizzamento dello sterzo in uscita.");
        }

        // Analisi Coasting
        if (hasThr || hasBrk) {
            double coasting = fraction(lap, s -> nearZero(val(s, Channel.THROTTLE), COAST_THR)
                    && nearZero(val(s, Channel.BRAKE), COAST_THR));
            // Per le Formula/Hybrid, il coasting è spesso Fuel Saving tecnico, siamo più permissivi
            double limit = profCoastBad(kind);
            if (kind == VehicleKind.FORMULA_HYBRID || kind == VehicleKind.LMP) limit += 0.05;
            // Coasting
            add(tmp, coasting > limit, Priority.MEDIUM, Category.GUIDA,
                    "Coasting eccessivo (" + pctFmt(coasting) + "): l'auto 'veleggia' senza input, sbilanciando la piattaforma aerodinamica. Unisci la fase di rilascio freno con l'apertura del gas.");
        }

        //Analisi Overlap (Gas + Freno insieme) - Tipico errore simracing / AC / LMU
        if (hasThr && hasBrk) {
            double overlap = fraction(lap, s ->
                    val(s, Channel.THROTTLE) > 10.0 && val(s, Channel.BRAKE) > 5.0 && val(s, Channel.SPEED) > 50
            );
            // Tolleriamo un po' (brake warming o punta tacco manuale), ma non sopra il 3%
            // Overlap (Gas+Freno)
            add(tmp, overlap > OVERLAP_LIMIT, Priority.HIGH, Category.GUIDA,
                    "Overlap Gas/Freno (Freni trascinati, " + pctFmt(overlap) + "): stai cuocendo i dischi e confondendo il differenziale. Separa nettamente le due fasi di guida.");
        }

        if (hasBrk && hasSte) {
            double trail = fraction(lap, s -> val(s, Channel.BRAKE) > BRK_TRAIL*100
                    && Math.abs(val(s, Channel.STEER_ANGLE)) > 10);
            // Trail Braking basso
            add(tmp, trail < profTrailLow(kind),  Priority.MEDIUM, Category.GUIDA,
                    "Assenza di Trail-Braking (" + pctFmt(trail) + "): rilasci il freno di scatto in ingresso. Mantieni una leggera pressione per tenere il peso sull'anteriore e facilitare la rotazione.");

            // Trail Braking alto
            add(tmp, trail > profTrailHigh(kind), Priority.MEDIUM, Category.GUIDA,
                    "Eccesso di freno a centro curva (" + pctFmt(trail) + "): stai saturando la gomma anteriore (sottosterzo indotto). Fai respirare l'anteriore rilasciando il pedale verso l'apex.");
        }

        if (hasThr) {
            double oscPct = throttleOscillationPct(lap, profThrOscDps(kind));
            // Gas irregolare
            add(tmp, oscPct > 0.10, Priority.MEDIUM, Category.GUIDA,
                    "Input gas esitante/micro-correzioni (" + pctFmt(oscPct) + "): sbilanci il trasferimento di carico longitudinale al posteriore. Attendi il momento giusto e apri il gas in modo deciso e fluido.");
        }

        if (hasSte) {
            double harsh = steeringHarshPct(lap, profSteerRevRate(kind));
            // Sterzo nervoso
            add(tmp, harsh > 0.10, Priority.MEDIUM, Category.GUIDA,
                    "Over-driving sullo sterzo (" + pctFmt(harsh) + "): input troppo aggressivi inducono scivolamento (slip angle eccessivo) e surriscaldano la spalla della gomma. Sii più morbido e progressivo.");
        }

        if (hasTC) {
            double tcPct = fractionActive(lap, Channel.TC_ACTIVE);
            // TC
            add(tmp, tcPct > profTcMuch(kind), Priority.MEDIUM, Category.GUIDA,
                    "Taglio TC eccessivo (" + pctFmt(tcPct) + "): chiedi troppa potenza a ruote ancora sterzate. Raddrizza prima il volante, o rivedi le pressioni posteriori se manca grip meccanico.");
        }

        // --- FFB ---
        if (hasFFB) {
            double clip = fraction(lap, s -> {
                double f = val(s, Channel.FFB);
                if (Double.isNaN(f)) return false;
                double fn = (f > 1.5) ? (f/100.0) : f;
                return fn >= FFB_CLIP;
            });
            // FFB Clipping
            add(tmp, clip > FFB_BADPCT, Priority.MEDIUM, Category.FFB,
                    "Saturazione Force Feedback (Clipping " + pctFmt(clip) + "): stai perdendo dettagli vitali sul comportamento delle gomme. Abbassa il gain nel pannello del volante.");
        }

        // --- Trasmissione ---
        if (hasRpm && hasThr) {
            double qRed = rpmQuantile(lap, UPSHIFT_RPM_HIGH_Q);
            if (!Double.isNaN(qRed)) {
                double timeNear = fraction(lap, s -> val(s, Channel.ENGINE_RPM) >= qRed
                        && val(s, Channel.THROTTLE) > THR_FULL*100);
                add(tmp, timeNear > 0.05, Priority.LOW, Category.TRASM,
                        "Vicino al limitatore (" + pctFmt(timeNear) + "): cambia un filo prima.");
            }
        }
        if (hasGear && hasThr && hasSte && hasBrk) {
            int bad = downshiftsWithHighThrottle(lap);
            add(tmp, bad > 0, Priority.HIGH, Category.TRASM,
                    "Downshift con gas alto in rettilineo: verifica mappatura comandi e tecnica.");
        }
        if (hasThr && hasBrk) {
            add(tmp, lateLiftBeforeBrake(lap, LIFT_BEFORE_BRAKE_S), Priority.MEDIUM, Category.GUIDA,
                    "Freni spesso senza un breve lift del gas: fai “respirare” l’anteriore prima del punto di frenata.");
        }
        if (hasThr && !apexes.isEmpty()) {
            DelayStats ds = apexToThrottleStats(lap, apexes, 0.80);
            add(tmp, ds.valid && ds.avg > profExitGasDelay(kind), Priority.MEDIUM, Category.GUIDA,
                    "Ritardo nell’apertura dopo apex: avg " + fmt1s(ds.avg) + ". Anticipa leggermente la trazione.");
        }

        // ======= NUOVI DATI (GUIDA/TRASM) =======
        if (has(lap, Channel.NUM_TIRES_OFF_TRACK)) {
            double offFrac = fraction(lap, s -> val(s, Channel.NUM_TIRES_OFF_TRACK) >= 2.0);
            add(tmp, offFrac > OFFTRACK_BAD, Priority.MEDIUM, Category.GUIDA,
                    "Track limits a rischio (" + pctFmt(offFrac) + " del giro): lascia un margine in uscita curva.");
        }
        // Usa l’euristica completa del Lap
        try {
            boolean invalid = lap.isInvalid();
            add(tmp, invalid, Priority.LOW, Category.GUIDA,
                    "Giro invalidato: priorità alla precisione su punti di corda e uscita.");
        } catch (Throwable t) {
            if (has(lap, Channel.LAP_INVALIDATED)) {
                boolean invalid = fraction(lap, s -> {
                    double v = val(s, Channel.LAP_INVALIDATED);
                    return !Double.isNaN(v) && Math.abs(v) > 0.001;
                }) > 0.0;
                add(tmp, invalid, Priority.LOW, Category.GUIDA,
                        "Giro invalidato: priorità alla precisione su punti di corda e uscita.");
            }
        }

        if (has(lap, Channel.DRS_AVAILABLE) && has(lap, Channel.DRS_ACTIVE)) {
            double avail = fractionActive(lap, Channel.DRS_AVAILABLE);
            double used  = fractionActive(lap, Channel.DRS_ACTIVE);
            add(tmp, avail > 0.02 && used < avail * DRS_UNUSED_FACTOR, Priority.LOW, Category.TRASM,
                    "DRS spesso disponibile ma poco usato: aprilo prima/di più nelle zone utili.");
        }

        // Note Performance extra (Shift, Brake Analysis dettagliata)
        tmp.addAll(CoachPerformance.generate(lap));

        // Extra veicolo (testo statico)
        for (String s : VehicleAdviceUtil.coachExtras(lap)) {
            tmp.add(new Note(Priority.LOW, Category.GUIDA, s));
        }

        return tmp;
    }
}
