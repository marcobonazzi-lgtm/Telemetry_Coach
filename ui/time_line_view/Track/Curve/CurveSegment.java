package org.simulator.ui.time_line_view.Track.Curve;

public final class CurveSegment {
    public enum Dir { LEFT, RIGHT }
    private final int index;
    private final String name;

    // Dati per il "Data Retrieval" (Telemetria)
    private final double inPct;
    private final double outPct;

    // Dati per la "Visualizzazione" (Geometria Pista)
    private final int iStart, iApex, iEnd;
    private final double sStart, sApex, sEnd;
    private final double xStart, yStart, xApex, yApex, xEnd, yEnd;

    private final Dir dir;
    private final double kappaPeak;
    private final double radius;
    private final double length;

    public CurveSegment(int index, String name,
                        double inPct, double outPct,
                        int iStart, int iApex, int iEnd,
                        double sStart, double sApex, double sEnd,
                        double xStart, double yStart, double xApex, double yApex, double xEnd, double yEnd,
                        Dir dir, double kappaPeak) {
        this.index = index;
        this.name = (name == null || name.isBlank()) ? "Curva " + index : name;
        this.inPct = inPct;
        this.outPct = outPct;

        this.iStart = iStart; this.iApex = iApex; this.iEnd = iEnd;
        this.sStart = sStart; this.sApex = sApex; this.sEnd = sEnd;
        this.xStart = xStart; this.yStart = yStart; this.xApex = xApex; this.yApex = yApex; this.xEnd = xEnd; this.yEnd = yEnd;
        this.dir = dir; this.kappaPeak = kappaPeak;
        this.radius = (Math.abs(kappaPeak) > 1e-6) ? 1.0/Math.abs(kappaPeak) : Double.POSITIVE_INFINITY;
        this.length = sEnd - sStart;
    }

    public int index(){ return index; }
    public String name(){ return name; }
    public double inPct(){ return inPct; }
    public double outPct(){ return outPct; }

    public int iStart(){ return iStart; }
    public int iApex(){ return iApex; }
    public int iEnd(){ return iEnd; }
    public double sStart(){ return sStart; }
    public double sApex(){ return sApex; }
    public double sEnd(){ return sEnd; }
    public double xStart(){ return xStart; }
    public double yStart(){ return yStart; }
    public double xApex(){ return xApex; }
    public double yApex(){ return yApex; }
    public double xEnd(){ return xEnd; }
    public double yEnd(){ return yEnd; }
    public Dir dir(){ return dir; }
    public double kappaPeak(){ return kappaPeak; }
    public double radius(){ return radius; }
    public double length(){ return length; }
}