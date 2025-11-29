package org.simulator.ui.time_line_view.Track.AC;

public final class AcSection {
    public final String name;
    public final double inFrac;  // 0..1
    public final double outFrac; // 0..1
    AcSection(String name, double inFrac, double outFrac){
        this.name = name; this.inFrac = inFrac; this.outFrac = outFrac;
    }
    public boolean isCorner(){
        String n = name==null? "" : name.toLowerCase();
        return !(n.contains("rettilineo") || n.contains("straight") || n.contains("pit") || n.contains("box"));
    }
}
