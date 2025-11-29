package org.simulator.ui.time_line_view.Track;

/** Geometria immutabile per un giro. */
public record TrackGeometry(
        double[] xAxis,                    // asse timeline (tempo o distanza)
        double[] px,                       // metri Est
        double[] py,                       // metri Nord
        TrackGeometry.Bounds2D bounds      // bounds in metri
) {
    /** Bounds 2D immutabili. */
    public static record Bounds2D(double minX, double minY, double maxX, double maxY) {
        public double width()  { return maxX - minX; }
        public double height() { return maxY - minY; }
    }
}
