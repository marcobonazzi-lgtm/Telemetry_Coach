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
            } catch(Throwable t){}
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
            add(tmp, absPct > profAbsMuch(kind), Priority.HIGH, Category.GUIDA,
                    "ABS attivo spesso (" + pctFmt(absPct) + "): entra più graduale e modula meglio.");
        }

        var apexes = LapAnalysis.apexes(lap, 5);
        double vMin = apexMinSpeed(lap, apexes);
        add(tmp, !Double.isNaN(vMin) && vMin < 50, Priority.MEDIUM, Category.GUIDA,
                "Apex molto lenti (" + Math.round(vMin) + " km/h): anticipa leggermente l’apertura del gas.");

        // logica sovrasterzo (Core)
        if (suspectedOversteer(lap)){
            add(tmp, true, Priority.HIGH, Category.GUIDA,
                    "Segnali di sovrasterzo (correzioni rapide): dosa il gas e raddrizza prima lo sterzo.");
        }

        // Analisi Coasting
        if (hasThr || hasBrk) {
            double coasting = fraction(lap, s -> nearZero(val(s, Channel.THROTTLE), COAST_THR)
                    && nearZero(val(s, Channel.BRAKE), COAST_THR));
            // Per le Formula/Hybrid, il coasting è spesso Fuel Saving tecnico, siamo più permissivi
            double limit = profCoastBad(kind);
            if (kind == VehicleKind.FORMULA_HYBRID || kind == VehicleKind.LMP) limit += 0.05;

            add(tmp, coasting > limit, Priority.MEDIUM, Category.GUIDA,
                    "Coasting elevato (" + pctFmt(coasting) + "): anticipa gas o ritarda la frenata.");
        }

        //Analisi Overlap (Gas + Freno insieme) - Tipico errore simracing / AC / LMU
        if (hasThr && hasBrk) {
            double overlap = fraction(lap, s ->
                    val(s, Channel.THROTTLE) > 10.0 && val(s, Channel.BRAKE) > 5.0 && val(s, Channel.SPEED) > 50
            );
            // Tolleriamo un po' (brake warming o punta tacco manuale), ma non sopra il 3%
            add(tmp, overlap > OVERLAP_LIMIT, Priority.HIGH, Category.GUIDA,
                    "Sovrapposizione Gas/Freno eccessiva (" + pctFmt(overlap) + "): togli completamente il gas prima di frenare.");
        }

        if (hasBrk && hasSte) {
            double trail = fraction(lap, s -> val(s, Channel.BRAKE) > BRK_TRAIL*100
                    && Math.abs(val(s, Channel.STEER_ANGLE)) > 10);
            add(tmp, trail < profTrailLow(kind),  Priority.MEDIUM, Category.GUIDA,
                    "Poco trail-braking (" + pctFmt(trail) + "): rilascia il freno più graduale in ingresso.");
            add(tmp, trail > profTrailHigh(kind), Priority.MEDIUM, Category.GUIDA,
                    "Troppo freno in inserimento (" + pctFmt(trail) + "): rischio sottosterzo, rilascia prima.");
        }

        if (hasThr) {
            double oscPct = throttleOscillationPct(lap, profThrOscDps(kind));
            add(tmp, oscPct > 0.10, Priority.MEDIUM, Category.GUIDA,
                    "Gas irregolare (" + pctFmt(oscPct) + "): apri più progressivo in uscita.");
        }

        if (hasSte) {
            double harsh = steeringHarshPct(lap, profSteerRevRate(kind));
            add(tmp, harsh > 0.10, Priority.MEDIUM, Category.GUIDA,
                    "Sterzo nervoso (" + pctFmt(harsh) + "): pulisci gli input e la traiettoria.");
        }

        if (hasTC) {
            double tcPct = fractionActive(lap, Channel.TC_ACTIVE);
            add(tmp, tcPct > profTcMuch(kind), Priority.MEDIUM, Category.GUIDA,
                    "TC spesso attivo (" + pctFmt(tcPct) + "): dosa meglio il gas o rivedi livello TC.");
        }

        // --- FFB ---
        if (hasFFB) {
            double clip = fraction(lap, s -> {
                double f = val(s, Channel.FFB);
                if (Double.isNaN(f)) return false;
                double fn = (f > 1.5) ? (f/100.0) : f;
                return fn >= FFB_CLIP;
            });
            add(tmp, clip > FFB_BADPCT, Priority.MEDIUM, Category.FFB,
                    "FFB in clipping (" + pctFmt(clip) + "): riduci il gain nel gioco/driver.");
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