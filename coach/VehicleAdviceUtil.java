package org.simulator.coach;

import org.simulator.canale.Lap;
import org.simulator.setup.setup_advisor.VehicleTraits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


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

// --- CATEGORIA VEICOLO ---
            switch (vt.category) {
                case GT:
                    out.add("GT: Sfrutta il pitch aerodinamico. Frenata decisa (picco alto) a ruote dritte per caricare l'anteriore, poi usa il trail-braking per far ruotare l'auto verso l'apex. Ottimizza il TC: deve assisterti, ma non tagliare bruscamente.");
                    break;
                case FORMULA:
                    out.add("Formula: Frena in modo violento all'inizio (quando hai massima downforce), poi riduci rapidamente la pressione (bleed-off) man mano che la velocità e il carico aero diminuiscono per non bloccare. Evita sterzate brusche che stallano l'aerodinamica.");
                    break;
                case PROTOTYPE:
                    out.add("Prototype/Hypercar: Gestisci il brake bias in base al deploy ibrido. Sii fluido sullo sterzo per non stallare i flussi sotto il fondo piatto. Attenzione al wheelspin a basse velocità: apri il gas in modo molto progressivo.");
                    break;
                case ROAD:
                    out.add("Road/Street: Sospensioni morbide significano forti trasferimenti di carico. Frena a ruote dritte, fai assestare il rollio prima di inserire e attendi che l'auto si appoggi sulle gomme esterne prima di dare gas.");
                    break;
                default:
                    out.add("Focus: Frena a ruote dritte, usa il rilascio del pedale per bilanciare l'auto verso il punto di corda e apri il gas solo quando puoi farlo aprendo lo sterzo.");
                    break;
            }

// --- TRAZIONE ---
            switch (vt.drivetrain) {
                case FWD:
                    out.add("FWD (Trazione Anteriore): Sfrutta il 'lift-off oversteer' (rilascio del gas) in ingresso per far ruotare il posteriore. Sii paziente a centro curva: ridare gas troppo presto causa un sottosterzo letale in uscita.");
                    break;
                case RWD:
                    out.add("RWD (Trazione Posteriore): Dosa il gas al punto di corda per stabilizzare il posteriore. Se c'è pattinamento, stai sprecando grip longitudinale: raddrizza lo sterzo prima di affondare al 100%.");
                    break;
                case AWD:
                    out.add("AWD (Trazione Integrale): Adotta una linea a 'V'. Spigola la curva, raddrizza il volante il prima possibile e sfrutta i differenziali aprendo il gas violentemente per farti tirare fuori dalla curva.");
                    break;
            }

            return out;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

}
