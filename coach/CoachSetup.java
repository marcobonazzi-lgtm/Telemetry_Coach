package org.simulator.coach;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;

import java.util.ArrayList;
import java.util.List;

import static org.simulator.coach.CoachCore.*;

final class CoachSetup {

    private CoachSetup(){}

    static List<Note> lapSetupNotes(Lap lap){
        List<Note> out = new ArrayList<>();
        for (String s: tyreAdvice(lap))   out.add(new Note(Priority.LOW, Category.GOMME, s));
        for (String s: brakeAdvice(lap))  out.add(new Note(Priority.LOW, Category.FRENI, s));
        for (String s: damageAdvice(lap)) out.add(new Note(Priority.LOW, Category.DANNI, s));
        for (String s: transmAdvice(lap)) out.add(new Note(Priority.LOW, Category.TRASM, s));
        out.addAll(VehicleAdviceUtil.setupNotes(lap));
        return out;
    }

    // ---- consigli per singolo giro ----
    static List<String> tyreAdvice(Lap lap){
        List<String> out = new ArrayList<>();
        double[] t = avgTyreTemps(lap);
        double[] p = avgTyrePsis(lap);
        if (t == null && p == null) return out;

        if (t != null){
            double avg = mean(t);
            double fl=t[0], fr=t[1], rl=t[2], rr=t[3];
            if (!Double.isNaN(avg)){
                if (avg < TYRE_OK_MIN) out.add("Gomm. fredde (" + Math.round(avg) + "°C): alza un filo le pressioni o spingi di più nel giro di lancio.");
                else if (avg > TYRE_OK_MAX) out.add("Gomm. calde (" + Math.round(avg) + "°C): scendi di 0.2-0.4 psi o fai cooling lap.");
            }
            if (!Double.isNaN(fl) && !Double.isNaN(fr) && Math.abs(fl-fr) > 8)
                out.add("Sbilan. anteriore L/R (" + Math.round(fl) + "°C vs " + Math.round(fr) + "°C): rivedi camber/pressioni o ingresso curva.");
            if (!Double.isNaN(rl) && !Double.isNaN(rr) && Math.abs(rl-rr) > 8)
                out.add("Sbilan. posteriore L/R (" + Math.round(rl) + "°C vs " + Math.round(rr) + "°C): attenzione a trazione e diff.");
            double frontAvg = mean(new double[]{fl,fr});
            double rearAvg  = mean(new double[]{rl,rr});
            if (!Double.isNaN(frontAvg) && !Double.isNaN(rearAvg) && frontAvg - rearAvg > 10)
                out.add("Anteriore molto più caldo: possibile sottosterzo. Riduci ingresso o aumenta rotazione (linea/ARB).");
        }

        if (p != null){
            double fl=p[0], fr=p[1], rl=p[2], rr=p[3];
            double avg = mean(new double[]{fl,fr,rl,rr});
            if (!Double.isNaN(avg)){
                if (avg < PSI_GOOD_MIN) out.add("Pressioni basse (" + fmt1(avg) + " psi): +0.2/+0.4 psi.");
                else if (avg > PSI_GOOD_MAX) out.add("Pressioni alte (" + fmt1(avg) + " psi): -0.2/-0.4 psi.");
            }
            if (!Double.isNaN(fl) && !Double.isNaN(fr) && Math.abs(fl-fr) > 0.8)
                out.add("Pressioni ant. sbilanciate (FL " + fmt1(fl) + " / FR " + fmt1(fr) + "): pareggia per stabilità in inserimento.");
            if (!Double.isNaN(rl) && !Double.isNaN(rr) && Math.abs(rl-rr) > 0.8)
                out.add("Pressioni post. sbilanciate (RL " + fmt1(rl) + " / RR " + fmt1(rr) + "): pareggia per trazione prevedibile.");
        }

        // Slip: indizi per camber/toe/diff (solo suggerimenti soft)
        double[] srFR = slipRatioFrontRearPct(lap);
        double[] saFR = slipAngleFrontRearPct(lap);
        if (srFR[0] > srFR[1]*1.2 || saFR[0] > saFR[1]*1.2)
            out.add("Setup: sottosterzo (slip ant. alto). Idee: un filo di ARB post più rigido o ant più morbido; controlla toe/camber ant.");
        if (srFR[1] > srFR[0]*1.2 || saFR[1] > saFR[0]*1.2)
            out.add("Setup: sovrasterzo (slip post. alto). Idee: ARB post un filo più morbido, diff-power +1, rebound post -1.");

        // Meteo/pista
        double road = firstNonNaN(lap, Channel.ROAD_TEMP);
        double air  = firstNonNaN(lap, Channel.AIR_TEMP);
        if (!Double.isNaN(road))
            out.add("Asfalto " + Math.round(road) + "°C" + (!Double.isNaN(air) ? (", aria " + Math.round(air) + "°C") : "") + ": adatta pressioni di ±0.1/0.2 psi se molto diverso dal target.");
        double grip = firstNonNaN(lap, Channel.SURFACE_GRIP);
        if (!Double.isNaN(grip) && grip < GRIP_LOW)
            out.add("Grip pista basso: valuta +1 click ali o mappa diff più stabile per trazione.");

        return out;
    }

    static List<String> brakeAdvice(Lap lap){
        List<String> out = new ArrayList<>();
        double[] b = avgBrakeTemps(lap);
        if (b == null) return out;
        double avg = mean(b);
        if (!Double.isNaN(avg)){
            if (avg < BRAKE_OK_MIN) out.add("Freni freddi (" + Math.round(avg) + "°C): chiudi leggermente i brake ducts o prepara di più la frenata.");
            else if (avg > BRAKE_OK_MAX) out.add("Freni caldi (" + Math.round(avg) + "°C): apri i ducts o gestisci con giri di raffreddamento.");
        }
        double fl=b[0], fr=b[1], rl=b[2], rr=b[3];
        if (!Double.isNaN(fl) && !Double.isNaN(fr) && Math.abs(fl-fr) > 40)
            out.add("Freni anteriori sbilanciati L/R: verifica bias e raffreddamento.");
        double front = mean(new double[]{fl,fr});
        double rear  = mean(new double[]{rl,rr});
        if (!Double.isNaN(front) && !Double.isNaN(rear)){
            if (front - rear > 40) out.add("Freni anteriori molto più caldi: valuta spostare leggermente il bias al posteriore.");
            if (rear - front > 40) out.add("Freni posteriori molto più caldi: valuta bias un filo più avanti per stabilità.");
        }
        // Brake bias diretto, se presente
        double bias = firstNonNaN(lap, Channel.BRAKE_BIAS);
        if (!Double.isNaN(bias)) {
            if (bias > 70) out.add("Brake bias molto avanti (" + fmt1(bias) + "%): -0.5% per ridurre blocco anteriore.");
            if (bias < 58) out.add("Brake bias molto al posteriore (" + fmt1(bias) + "%): +0.5% per stabilità in ingresso.");
        }
        return out;
    }

    static List<String> transmAdvice(Lap lap){
        List<String> out = new ArrayList<>();
        // DRS/ERS ricap lato setup (mappa)
        if (has(lap, Channel.DRS_AVAILABLE) && has(lap, Channel.DRS_ACTIVE)) {
            double avail = fractionActive(lap, Channel.DRS_AVAILABLE);
            double used  = fractionActive(lap, Channel.DRS_ACTIVE);
            if (avail > 0.02 && used < avail * DRS_UNUSED_FACTOR)
                out.add("Mappa DRS: assicurati che il binding sia comodo; usa DRS appena disponibile in rettilineo.");
        }
        if (has(lap, Channel.ERS_IS_CHARGING) && has(lap, Channel.KERS_DEPLOYED_ENERGY)) {
            double chg = fractionActive(lap, Channel.ERS_IS_CHARGING);
            double dep = fraction(lap, s -> !Double.isNaN(val(s, Channel.KERS_DEPLOYED_ENERGY)) && val(s, Channel.KERS_DEPLOYED_ENERGY) > 0);
            if (chg > dep * ERS_RECOV_OVER_DEPLOY)
                out.add("Mappa ERS conservativa: aumenta leggermente il deploy in uscita curva.");
        }
        // Engine brake / Turbo
        double eb = firstNonNaN(lap, Channel.ENGINE_BRAKE_SETTING);
        if (!Double.isNaN(eb) && eb >= 7) out.add("Engine brake alto: prova -1/-2 step per ingresso più stabile.");
        if (has(lap, Channel.TURBO_BOOST) && has(lap, Channel.THROTTLE)) {
            double lowBoostPct = fraction(lap, s -> (val(s, Channel.THROTTLE) > 80) && !Double.isNaN(val(s, Channel.TURBO_BOOST)) && val(s, Channel.TURBO_BOOST) < 0.5);
            if (lowBoostPct > 0.10) out.add("Boost spesso basso con gas alto: verifica mappa turbo.");
        }
        return out;
    }

    static List<String> damageAdvice(Lap lap){
        List<String> out = new ArrayList<>();
        double front = firstNonNaN(lap, Channel.CAR_DAMAGE_FRONT);
        double rear  = firstNonNaN(lap, Channel.CAR_DAMAGE_REAR);
        if (!Double.isNaN(front) && front > 0) out.add("Danni frontali: evita cordoli aggressivi e contatti in inserimento.");
        if (!Double.isNaN(rear)  && rear  > 0) out.add("Danni posteriori: attenzione alla trazione sui cordoli e ai trasferimenti.");

        // ride height/sospensioni → kerb warning/setup
        double rhFL = firstNonNaN(lap, Channel.RIDE_HEIGHT_FL);
        double rhFR = firstNonNaN(lap, Channel.RIDE_HEIGHT_FR);
        if ((!Double.isNaN(rhFL) && rhFL < RIDE_LOW_WARN) || (!Double.isNaN(rhFR) && rhFR < RIDE_LOW_WARN))
            out.add("Ride height anteriore molto basso: rischio tocar piano/kerb; alza di 1-2 mm o evita cordoli alti.");
        // spike travel
        if (has(lap, Channel.SUSP_TRAVEL_FL) || has(lap, Channel.SUSP_TRAVEL_FR)) {
            double kerbHit = fraction(lap, s -> {
                double tfl = val(s, Channel.SUSP_TRAVEL_FL);
                double tfr = val(s, Channel.SUSP_TRAVEL_FR);
                return (!Double.isNaN(tfl) && Math.abs(tfl) > SUSP_SPIKE_N) || (!Double.isNaN(tfr) && Math.abs(tfr) > SUSP_SPIKE_N);
            });
            if (kerbHit > 0.05) out.add("Sospensioni anteriori a pacco/colpi sui cordoli (" + pctFmt(kerbHit) + "): valuta +rebound anteriore o evita kerb alti.");
        }
        return out;
    }

    // ---- riepiloghi testuali per la sessione ----
    static List<String> formatTyreTempSummary(double[] t){
        List<String> out = new ArrayList<>();
        double avg = mean(t);
        if (Double.isNaN(avg)) return out;
        out.add("Temperature gomme (media): " + Math.round(avg) + "°C (target ~" + (int)TYRE_OK_MIN + "-" + (int)TYRE_OK_MAX + "°C).");
        if (avg < TYRE_OK_MIN) out.add("Soluzione: aumenta leggermente pressioni o spingi di più nel giro di lancio.");
        if (avg > TYRE_OK_MAX) out.add("Soluzione: riduci pressioni o inserisci giri di raffreddamento.");
        return out;
    }
    static List<String> formatTyrePressureSummary(double[] p){
        List<String> out = new ArrayList<>();
        double avg = mean(p);
        if (Double.isNaN(avg)) return out;
        out.add("Pressione (media): " + fmt1(avg) + " psi (target ~" + PSI_GOOD_MIN + "-" + PSI_GOOD_MAX + ").");
        if (avg < PSI_GOOD_MIN) out.add("Soluzione: considera +0.2/+0.4 psi per finestra ottimale.");
        if (avg > PSI_GOOD_MAX) out.add("Soluzione: considera -0.2/-0.4 psi per finestra ottimale.");
        return out;
    }
    static List<String> formatBrakeTempSummary(double[] b){
        List<String> out = new ArrayList<>();
        double avg = mean(b);
        if (Double.isNaN(avg)) return out;
        out.add("Temperature freni (media): " + Math.round(avg) + "°C (target ~" + (int)BRAKE_OK_MIN + "-" + (int)BRAKE_OK_MAX + "°C).");
        if (avg < BRAKE_OK_MIN) out.add("Soluzione: chiudi leggermente i brake ducts o lavora sulla preparazione delle frenate.");
        if (avg > BRAKE_OK_MAX) out.add("Soluzione: apri i brake ducts o gestisci con raffreddamento attivo.");
        return out;
    }
}
