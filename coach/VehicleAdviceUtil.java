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

    /** Suggerimenti di setup additivi per CoachSetup (List<Note>) */
    static List<Note> setupNotes(Lap lap) {
        try {
            VehicleTraits vt = VehicleTraits.detect(Collections.singletonList(lap));
            List<Note> out = new ArrayList<>();

            // Consigli per categoria
            switch (vt.category) {
                case GT:
                    // Numeri target solo informativi (non vincolanti)
                    out.add(new Note(Priority.LOW, Category.GOMME, "GT: target pressioni dry 26.0–27.0 psi; wet 29.5–31.0 psi. Se media < 26 → +0.2/+0.4 psi; se > 27 → -0.2/-0.4 psi."));
                    out.add(new Note(Priority.LOW, Category.FRENI, "GT: mantieni freni ~600–650°C ant. / ~450°C post.: regola i brake ducts per restare nel range su stint lunghi."));
                    out.add(new Note(Priority.LOW, Category.SESSIONE, "GT: sottosterzo medio → prova ARB ant. -1 o ride height post. -1 mm; sovrasterzo in trazione → diff power -1 o ARB post. +1."));
                    break;
                case FORMULA:
                    out.add(new Note(Priority.LOW, Category.SESSIONE, "Formula: bilanciamento tramite ala ant./post. e rake: sottosterzo medio → +ala ant. +1 o rake +1 mm; sovrasterzo alta velocità → +ala post. +1."));
                    out.add(new Note(Priority.LOW, Category.TRASM, "Formula: se instabile in rilascio → engine brake -1/-2; se pattina in trazione → diff power -1 e/o mappa coppia più dolce."));
                    break;
                case PROTOTYPE:
                    out.add(new Note(Priority.LOW, Category.SESSIONE, "Prototype/Hypercar: bilanciamento aero via altezze e flap post.; cura prima la stabilità alle alte velocità, poi la trazione."));
                    out.add(new Note(Priority.LOW, Category.TRASM, "Prototype/Hybrid: calibra ERS/TC per uscita fluida, evitando spike di coppia sulle posteriori."));
                    break;
                case ROAD:
                    out.add(new Note(Priority.LOW, Category.SESSIONE, "Road/Street: ammortizzatori più morbidi aiutano i cordoli, ma evita il bottoming; se beccheggia, aumenta leggermente il rebound anteriore."));
                    out.add(new Note(Priority.LOW, Category.GOMME, "Road/Street: pressioni conservative per stabilità; riduci camber estremo per non stressare le spalle esterne."));
                    break;
                default:
                    out.add(new Note(Priority.LOW, Category.SESSIONE, "Regola generale: ARB più morbido = più grip su quell’asse; usa camber/pressioni per uniformare I/M/O e tenere le gomme nel range."));
                    break;
            }

            // Consigli per trazione
            switch (vt.drivetrain) {
                case FWD:
                    out.add(new Note(Priority.LOW, Category.SESSIONE, "FWD: riduci roll anteriore (ARB ant. +1) o aumenta grip posteriore (ARB post. -1) per favorire la rotazione."));
                    out.add(new Note(Priority.LOW, Category.FRENI, "FWD: bias freno leggermente più avanti per stabilizzare l’ingresso; evita trail brake profondo se induce sottosterzo."));
                    break;
                case RWD:
                    out.add(new Note(Priority.LOW, Category.TRASM, "RWD: se wheelspin in uscita → diff power -1 e/o TC +1; se snap in lift-off → diff coast +1."));
                    out.add(new Note(Priority.LOW, Category.SESSIONE, "RWD: aumenta anti-squat/controllo ammortizzazione posteriore per trazione; evita ARB post. troppo rigido che induce sovrasterzo."));
                    break;
                case AWD:
                    out.add(new Note(Priority.LOW, Category.SESSIONE, "AWD: mantieni roll relativamente simmetrico; evita assetti che caricano troppo l’avantreno in ingresso (snap understeer)."));
                    out.add(new Note(Priority.LOW, Category.TRASM, "AWD: calibra ripartizione coppia/TC per evitare push in uscita con angolo sterzo elevato."));
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
