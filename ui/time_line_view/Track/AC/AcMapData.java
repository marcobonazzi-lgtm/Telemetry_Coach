package org.simulator.ui.time_line_view.Track.AC;

import javafx.scene.image.Image;
import org.simulator.ui.time_line_view.Track.TrackGeometry;

/**
 * Contiene i dati di calibrazione della mappa di Assetto Corsa (map.ini + map.png).
 */
public record AcMapData(
        Image image,
        double xOffset,   // World X del top-left dell'immagine
        double zOffset,   // World Z del top-left dell'immagine
        double widthM,    // Larghezza totale in metri coperta dall'immagine
        double heightM,   // Altezza totale in metri coperta dall'immagine
        double margin     // Margine usato nel file ini (opzionale)
) {
    /** Calcola i bounds del mondo reale coperti dalla mappa. */
    public TrackGeometry.Bounds2D getBounds() {
        // In AC, solitamente Z decresce andando giù nell'immagine, o viceversa a seconda del sistema.
        // Qui assumiamo il sistema standard mappa: (x, z) -> (x + width, z - height) o simili.
        // Per sicurezza usiamo min/max assoluti.
        return new TrackGeometry.Bounds2D(xOffset, zOffset - heightM, xOffset + widthM, zOffset);
    }
}