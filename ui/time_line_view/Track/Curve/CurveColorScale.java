package org.simulator.ui.time_line_view.Track.Curve;

import javafx.scene.paint.Color;

final class CurveColorScale {
    private CurveColorScale() {}
    /** delta in [-1..+1] -> colore continuo rosso-giallo-verde */
    static Color colorFor(double delta){
        double t = (delta+1)*0.5; // 0..1
        // 0=rosso(1,0,0) -> 0.5=giallo(1,1,0) -> 1=verde(0,1,0)
        if (t <= 0.5){
            double u = t/0.5;
            return Color.color(1.0, u, 0.0);
        } else {
            double u = (t-0.5)/0.5;
            return Color.color(1.0-u, 1.0, 0.0);
        }
    }
}
