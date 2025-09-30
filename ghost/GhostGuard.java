package org.simulator.ghost;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.simulator.canale.Lap;

/** Guardrail per impedire che il ghost sia lo stesso giro selezionato. */
public final class GhostGuard {
    private GhostGuard() {}

    /** @return true se la scelta è valida e viene impostata; false se rifiutata. */
    public static boolean trySetGhost(Lap selectedLap, Lap chosenGhost) {
        if (chosenGhost == null) {
            GhostState.get().clear();
            return true;
        }
        if (selectedLap != null && selectedLap == chosenGhost) {
            Alert a = new Alert(Alert.AlertType.WARNING,
                    "Il ghost non può essere lo stesso giro visualizzato.",
                    ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
            GhostState.get().clear();
            return false;
        }
        GhostState.get().setGhostLap(chosenGhost);
        return true;
    }
}
