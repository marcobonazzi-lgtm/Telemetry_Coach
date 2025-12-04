package org.simulator.coach;

import org.simulator.canale.Lap;
import org.simulator.setup.setup_advisor.VehicleTraits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.simulator.coach.CoachCore.*;

/**
 * Utility di sola aggiunta (nessuna modifica alla logica esistente) che
 * fornisce consigli di guida e di setup in base alla tipologia di veicolo
 * rilevata da VehicleTraits. I consigli sono aggiunti come testo/Note.
 */
final class VehicleAdviceUtil {

    private VehicleAdviceUtil() {}

    /** Suggerimenti di guida additivi per CoachDriving (List<String>) */
    static List<String> coachExtras(Lap lap) {
        try {
            VehicleTraits vt = VehicleTraits.detect(Collections.singletonList(lap));
            List<String> out = new ArrayList<>();

            // Consigli per categoria
            switch (vt.category) {
                case GT:
                    out.add("GT: lavora su trazione progressiva in uscita (evita anti-spin sui cordoli) e freni modulati: punta a ~600–650°C ant. e ~450°C post., regola i condotti per restare nel range.");
                    break;
                case FORMULA:
                    out.add("Formula: privilegia velocità minima e rotazione in ingresso; evita trailing brake profondo sui bump per non innescare sovrasterzo aero.");
                    break;
                case PROTOTYPE:
                    out.add("Prototype/Hypercar: gestisci l’ibrido/ERS con deploy in uscita medio-lungo; evita wheelspin in 2ª/3ª aprendo il gas in modo progressivo.");
                    break;
                case ROAD:
                    out.add("Road/Street: frena più diritto e rilascia il freno graduale per stabilizzare l’avantreno; evita cordoli alti con sospensioni morbide.");
                    break;
                default:
                    out.add("Consiglio generale: frena dritto, invita l’auto al punto di corda e apri gas in modo progressivo per massimizzare il grip meccanico.");
                    break;
            }

            // Consigli per trazione
            switch (vt.drivetrain) {
                case FWD:
                    out.add("FWD: entra con pazienza e genera rotazione in rilascio; evita gas anticipato che allarga la traiettoria; mantieni freno motore basso.");
                    break;
                case RWD:
                    out.add("RWD: dosa il gas al corda e raddrizza lo sterzo prima di aprire; se pattina, ritarda il full-throttle.");
                    break;
                case AWD:
                    out.add("AWD: sfrutta la trazione ma evita grandi angoli di sterzo in uscita; punta ad aprire il volante presto.");
                    break;
                default:
                    // nessun extra
                    break;
            }

            return out;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

}
