package org.simulator.ui.time_line_view.Track.AC;

import org.simulator.ui.time_line_view.Track.TrackGeometry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Legge il file binario fast_lane.ai di Assetto Corsa. */
public final class AcAiLineLoader {
    private AcAiLineLoader() {}

    public static TrackGeometry loadAiLine(String venue) {
        // 1. Trova la cartella
        Path trackFolder = AcSectionsLoader.findTrackFolderByVenue(venue);
        if (trackFolder == null) {
            System.err.println("[AcAiLineLoader] Cartella non trovata per venue: " + venue);
            return null;
        }

        // 2. Cerca il file AI
        Path aiFile = trackFolder.resolve("ai/fast_lane.ai");
        if (!Files.exists(aiFile)) aiFile = trackFolder.resolve("ai/ideal_line.ai");

        if (!Files.exists(aiFile)) {
            System.err.println("[AcAiLineLoader] File AI non trovato in: " + trackFolder.resolve("ai"));
            return null;
        }

        try {
            System.out.println("[AcAiLineLoader] Parsing file: " + aiFile);
            byte[] bytes = Files.readAllBytes(aiFile);
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

            // --- HEADER PARSING (Reverse Engineered dal tuo file) ---
            // Offset 0: Int32 (Versione? Es. 7)
            // Offset 4: Int32 (Point Count -> Es. 3374)
            // Offset 8-15: Padding/Zero
            // Offset 16: Start Data

            if (bytes.length < 16) return null;

            buffer.position(4);
            int pointCount = buffer.getInt();

            System.out.println("[AcAiLineLoader] Header Point Count: " + pointCount);

            if (pointCount <= 0 || pointCount > 100000) {
                System.err.println("[AcAiLineLoader] Point count assurdo, abort.");
                return null;
            }

            // Start Data
            buffer.position(16);

            double[] px = new double[pointCount];
            double[] py = new double[pointCount]; // Z coordinates
            double[] dummyX = new double[pointCount];

            // STRIDE RILEVATO: 20 Byte (5 float: x, y, z, dist, norm)
            // Leggiamo solo i primi 3 e saltiamo gli altri 2.
            int stride = 20;

            // Verifica di sicurezza buffer
            if (buffer.remaining() < pointCount * stride) {
                System.err.println("[AcAiLineLoader] File troppo corto per " + pointCount + " punti a stride 20.");
                // Tentativo disperato: ricalcolo stride
                stride = buffer.remaining() / pointCount;
                System.err.println("[AcAiLineLoader] Fallback stride calcolato: " + stride);
                if (stride < 12) return null; // Meno di 3 float è impossibile
            }

            for (int i = 0; i < pointCount; i++) {
                float x = buffer.getFloat();
                float yHeight = buffer.getFloat(); // Altezza, ignoriamo
                float z = buffer.getFloat();       // Profondità -> Y mappa

                px[i] = x;
                py[i] = z;
                dummyX[i] = i;

                // Salta il resto del pacchetto (stride - 12 bytes letti)
                int skip = stride - 12;
                if (skip > 0) buffer.position(buffer.position() + skip);
            }

            System.out.println("[AcAiLineLoader] Caricamento completato. Punti validi: " + pointCount);
            return new TrackGeometry(dummyX, px, py, computeBounds(px, py));

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static TrackGeometry.Bounds2D computeBounds(double[] x, double[] y) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for(int i=0; i<x.length; i++) {
            if(x[i]<minX) minX=x[i]; if(x[i]>maxX) maxX=x[i];
            if(y[i]<minY) minY=y[i]; if(y[i]>maxY) maxY=y[i];
        }
        return new TrackGeometry.Bounds2D(minX, minY, maxX, maxY);
    }
    /** Carica la AI line da una cartella specifica (per varianti). */
    public static TrackGeometry loadAiLineFromPath(Path trackFolder) {
        if (trackFolder == null) return null;

        Path aiFile = trackFolder.resolve("ai/fast_lane.ai");
        if (!Files.exists(aiFile)) aiFile = trackFolder.resolve("ai/ideal_line.ai");

        if (!Files.exists(aiFile)) return null;

        // Riutilizza la logica di parsing esistente estraendola in un metodo privato 'parseAiFile'
        // OPPURE (più rapido per ora) copia-incolla la logica di lettura byte qui sotto:
        try {
            byte[] bytes = Files.readAllBytes(aiFile);
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);

            if (bytes.length < 16) return null;
            buffer.position(4);
            int pointCount = buffer.getInt();
            if (pointCount <= 0 || pointCount > 100000) return null;

            buffer.position(16);
            double[] px = new double[pointCount];
            double[] py = new double[pointCount];
            double[] dum = new double[pointCount];
            int stride = 20; // Standard fast_lane

            if (buffer.remaining() < pointCount * stride) stride = buffer.remaining()/pointCount;
            if (stride < 12) return null;

            for(int i=0; i<pointCount; i++){
                float x = buffer.getFloat();
                float h = buffer.getFloat();
                float z = buffer.getFloat();
                px[i]=x; py[i]=z; dum[i]=i;
                int skip = stride - 12;
                if(skip>0) buffer.position(buffer.position()+skip);
            }
            return new TrackGeometry(dum, px, py, computeBounds(px, py));
        } catch (Exception e) { return null; }
    }
}