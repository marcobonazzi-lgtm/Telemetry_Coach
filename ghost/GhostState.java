package org.simulator.ghost;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.simulator.canale.Lap;

/**
 * Stato globale per il giro Ghost.
 * Valore di default: null (nessun ghost).
 */
public final class GhostState {
    private static final GhostState INSTANCE = new GhostState();
    public static GhostState get() { return INSTANCE; }
    private GhostState() {}

    private final ObjectProperty<Lap> ghostLap = new SimpleObjectProperty<>(null);

    public ObjectProperty<Lap> ghostLapProperty() { return ghostLap; }
    public Lap getGhostLap() { return ghostLap.get(); }
    public void setGhostLap(Lap lap) { ghostLap.set(lap); }
    public void clear() { ghostLap.set(null); }
}
