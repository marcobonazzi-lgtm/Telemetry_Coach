package org.simulator.tracks;

import java.util.*;

/** Modello dati per pista (statico, da JSON). */
public final class TrackInfo {
    public final String id;
    public final String displayName;
    public final double lengthKm;
    public final List<Double> sectorSplits; // [0..1]
    public final String imageResource;      // /assets/tracks/<id>_map.png
    public final boolean imageHasNumbers;   // se true, non disegnare i badge numerati
    public final double startFinishNorm;    // [0..1]
    public final Double pitEntryNorm;       // [0..1] opzionale
    public final Double pitExitNorm;        // [0..1] opzionale
    public final String description;        // descrizione testuale

    public final List<Turn> turns;

    public TrackInfo(String id, String displayName, double lengthKm, List<Double> sectorSplits,
                     String imageResource, boolean imageHasNumbers, double startFinishNorm, Double pitEntryNorm, Double pitExitNorm,
                     String description, List<Turn> turns){
        this.id=id; this.displayName=displayName; this.lengthKm=lengthKm;
        this.sectorSplits = sectorSplits==null? List.of(1.0) : sectorSplits;
        this.imageResource=imageResource;
        this.imageHasNumbers=imageHasNumbers;
        this.startFinishNorm=startFinishNorm;
        this.pitEntryNorm=pitEntryNorm; this.pitExitNorm=pitExitNorm;
        this.description = description==null? "" : description;
        this.turns = turns==null? List.of() : turns;
    }

    public static final class Turn {
        public final int number;
        public final String name;
        public final double posNorm;
        public final double imgX;
        public final double imgY;
        public final Map<String, Advice> adviceByVehicle;

        public Turn(int number, String name, double posNorm, double imgX, double imgY, Map<String, Advice> advice){
            this.number=number; this.name=name; this.posNorm=posNorm; this.imgX=imgX; this.imgY=imgY;
            this.adviceByVehicle = advice==null? Map.of() : advice;
        }
    }

    public static final class Advice {
        public final Integer gear;          // può essere null
        public final Double vMinIdealKmh;   // target
        public final Double vRangeKmh;      // +/-
        public final String note;           // testo
        public Advice(Integer gear, Double vMinIdealKmh, Double vRangeKmh, String note){
            this.gear=gear; this.vMinIdealKmh=vMinIdealKmh; this.vRangeKmh=vRangeKmh; this.note=note;
        }
    }
}
