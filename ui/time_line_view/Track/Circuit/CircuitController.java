package org.simulator.ui.time_line_view.Track.Circuit;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.StrokeLineCap;
// Import grafici
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.setup.setup_advisor.VehicleTraits;
import org.simulator.ui.settings.UiSettings;
import org.simulator.ui.time_line_view.Track.*;
import org.simulator.ui.time_line_view.Track.AC.*;
import org.simulator.ui.time_line_view.Track.Curve.CurveManager;
import org.simulator.ui.time_line_view.Track.Curve.CurveSegment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class CircuitController {

    private CurveManager curveMgr;

    public interface CurveInfoSink { void onCurveInfo(String title, java.util.Map<String,Object> data); }

    private CurveInfoSink curveSink;
    public void setCurveInfoSink(CurveInfoSink sink){ this.curveSink = sink; }

    private boolean currentVehicleHasDrs = false;
    private boolean hasIdealLineData = false;

    private final CircuitView view;
    private TrackGeometry geom;
    private Color[] strokeColors;
    private final java.util.Map<Integer,String> curveNames = new java.util.HashMap<>();

    private TrackLayout currentLayout = null;

    private String lastTrackHint = null;
    private TrackLayout lastLayout = null;

    public CircuitController(CircuitView view) {
        this.view = view;
    }

    public void setLapData(List<? extends Map<Channel, Double>> samples,
                           double[] xAxis,
                           String trackHint,
                           TrackLayout forceLayout) {
        this.currentLayout = forceLayout;

        // 1. Controllo Capacità Auto (DRS)
        this.currentVehicleHasDrs = checkDrsCapability(samples);

        boolean zDataValid = checkZDataValidity(samples);
        TrackConfig cfg = getManualConfig(trackHint);

        TrackGeometry aiGeom = null;
        AcMapData mapData = null;
        TrackGeometry idealLineGeom = null;

        // 2. Caricamento Dati Pista
        if (forceLayout != null) {
            aiGeom = AcAiLineLoader.loadAiLineFromPath(forceLayout.folder());
            idealLineGeom = loadSpecificAi(forceLayout.folder(), "ideal_line.ai");
            if (aiGeom == null) mapData = AcSectionsLoader.loadMapFromPath(forceLayout.folder());
        } else {
            aiGeom = AcAiLineLoader.loadAiLine(trackHint);
            Path trackFolder = AcSectionsLoader.findTrackFolderByVenue(trackHint);
            if (trackFolder != null) idealLineGeom = loadSpecificAi(trackFolder, "ideal_line.ai");

            if (aiGeom == null) mapData = AcSectionsLoader.loadMap(trackHint);
        }

        // Fallback Ideal Line
        if (idealLineGeom == null && aiGeom != null) {
            idealLineGeom = aiGeom;
        }

        // 3. Costruzione Geometria Base
        if (aiGeom != null) {
            if (cfg.rotateAI) aiGeom = rotateGeometry90(aiGeom);
            if (cfg.flipAI_X) aiGeom = flipGeometryX(aiGeom);
            view.setAiReferenceTrack(aiGeom.px(), aiGeom.py());
            view.setGeometry(aiGeom);

            if (zDataValid) {
                this.geom = buildAbsoluteGeometry(samples, xAxis, false);
                if (cfg.rotateAI) this.geom = rotateGeometry90(this.geom);
                if (cfg.flipAI_X) this.geom = flipGeometryX(this.geom);
            } else {
                TrackGeometry rawGeom = buildAbsoluteGeometry(samples, xAxis, true);
                this.geom = fitGeometryToReference(rawGeom, aiGeom, cfg);
            }
        } else if (mapData != null) {
            view.setMapData(mapData);
            if (zDataValid) {
                this.geom = buildAbsoluteGeometry(samples, xAxis, false);
            } else {
                TrackGeometry rawGeom = buildAbsoluteGeometry(samples, xAxis, true);
                this.geom = fitGeometryToMapManual(rawGeom, mapData, trackHint, cfg);
            }
            view.setGeometry(this.geom);
            view.setAsphaltFromLine(null, null, 0);
        } else {
            view.setMapData(null); view.setAiReferenceTrack(null, null);
            this.geom = TrackBuilder.build(samples, xAxis);
            if (geom != null) view.setAsphaltFromLine(geom.px(), geom.py(), 14);
            view.setGeometry(this.geom);
        }

        if (geom == null) return;

        // 4. Visualizzazione Telemetria Utente
        this.strokeColors = buildStrokeColors(samples);
        view.setColoredTrack(geom.px(), geom.py(), strokeColors);

        // 5. Setup Curve e DRS
        setupCurves(samples, xAxis, trackHint, forceLayout, (aiGeom != null) ? aiGeom : this.geom);
        setupDrsZones(trackHint, forceLayout, (aiGeom != null) ? aiGeom : this.geom);

        // 6. Setup Ideal Line
        if (idealLineGeom != null) {
            if (cfg.rotateAI) idealLineGeom = rotateGeometry90(idealLineGeom);
            if (cfg.flipAI_X) idealLineGeom = flipGeometryX(idealLineGeom);
            setupIdealLineVisuals(idealLineGeom);
            this.hasIdealLineData = true;
        } else {
            view.setIdealLineShapes(null);
            this.hasIdealLineData = false;
        }

        view.fitToTrack();

        // *** 7. Setup Icona Veicolo (Logica Avanzata) ***
        setupVehicleIcon(samples);

        view.setFollowCarEnabled(false);
    }

    public void setLapData(List<? extends Map<Channel, Double>> s, double[] x, String t) {
        setLapData(s, x, t, null);
    }

    private TrackGeometry loadSpecificAi(Path folder, String filename) {
        try {
            Path p = folder.resolve("data/" + filename);
            if (!Files.exists(p)) p = folder.resolve("ai/" + filename);
            if (!Files.exists(p)) p = folder.resolve(filename);
            if (Files.exists(p)) {
                return AcAiLineLoader.loadAiLineFromPath(folder);
            }
        } catch (Exception e) {}
        return null;
    }

    public boolean hasDrs() { return currentVehicleHasDrs; }
    public boolean hasIdealLine() { return hasIdealLineData; }

    // ============================================================================================
    //  VISUALS & MARKERS
    // ============================================================================================

    private void setupIdealLineVisuals(TrackGeometry ideal) {
        double[] x = ideal.px();
        double[] y = ideal.py();
        if (x.length < 2) return;

        javafx.scene.shape.Path path = new javafx.scene.shape.Path();

        // Bind properties
        path.strokeProperty().bind(UiSettings.get().idealLineColorProperty());
        path.strokeWidthProperty().bind(UiSettings.get().idealLineWidthProperty());
        path.opacityProperty().bind(UiSettings.get().idealLineOpacityProperty());

        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setFill(null);

        path.getElements().add(new MoveTo(x[0], y[0]));
        for (int i = 1; i < x.length; i++) {
            path.getElements().add(new LineTo(x[i], y[i]));
        }

        if (Math.hypot(x[0]-x[x.length-1], y[0]-y[y.length-1]) < 20.0) {
            path.getElements().add(new javafx.scene.shape.ClosePath());
        }

        view.setIdealLineShapes(List.of(path));
    }

    private void setupDrsZones(String trackHint, TrackLayout layout, TrackGeometry refGeom) {
        view.setDrsShapes(null);

        if (refGeom == null) return;
        if (!currentVehicleHasDrs) return;

        Path folder = null;
        if (layout != null) folder = layout.folder();
        else if (trackHint != null) folder = AcSectionsLoader.findTrackFolderByVenue(trackHint);

        if (folder == null) return;

        List<AcDrsZone> zones = AcSectionsLoader.loadDrsZones(folder);
        if (zones.isEmpty()) return;

        List<Node> shapes = buildDrsShapes(refGeom, zones);
        view.setDrsShapes(shapes);
    }

    private List<Node> buildDrsShapes(TrackGeometry g, List<AcDrsZone> zones) {
        List<Node> result = new ArrayList<>();
        double[] px = g.px();
        double[] py = g.py();
        int n = Math.min(px.length, py.length);
        double[] dist = new double[n]; dist[0] = 0;
        for (int i = 1; i < n; i++) dist[i] = dist[i-1] + Math.hypot(px[i]-px[i-1], py[i]-py[i-1]);
        double totalLen = dist[n-1];
        if (Math.hypot(px[0]-px[n-1], py[0]-py[n-1]) < 100) totalLen += Math.hypot(px[0]-px[n-1], py[0]-py[n-1]);

        for (AcDrsZone z : zones) {
            double startM = z.startFrac() * totalLen;
            double endM = z.endFrac() * totalLen;
            int idxStart = findIndex(dist, startM);
            int idxEnd = findIndex(dist, endM);

            javafx.scene.shape.Path path = new javafx.scene.shape.Path();
            path.strokeProperty().bind(UiSettings.get().drsZoneColorProperty());
            path.strokeWidthProperty().bind(UiSettings.get().drsZoneWidthProperty());
            path.opacityProperty().bind(UiSettings.get().drsZoneOpacityProperty());

            path.setStrokeLineCap(StrokeLineCap.BUTT);
            path.setFill(null);
            path.getElements().add(new MoveTo(px[idxStart], py[idxStart]));
            if (idxEnd < idxStart) {
                for (int i = idxStart + 1; i < n; i++) path.getElements().add(new LineTo(px[i], py[i]));
                path.getElements().add(new MoveTo(px[0], py[0]));
                for (int i = 1; i <= idxEnd; i++) path.getElements().add(new LineTo(px[i], py[i]));
            } else {
                for (int i = idxStart + 1; i <= idxEnd; i++) path.getElements().add(new LineTo(px[i], py[i]));
            }
            result.add(path);

            double midFrac = (z.endFrac() < z.startFrac()) ? z.startFrac() + ((1.0 - z.startFrac()) + z.endFrac()) * 0.5 : (z.startFrac() + z.endFrac()) * 0.5;
            if(midFrac >= 1.0) midFrac -= 1.0;
            int idxMid = findIndex(dist, midFrac * totalLen);

            Text text = new Text("DRS");
            text.fillProperty().bind(UiSettings.get().drsZoneColorProperty());
            text.setFont(Font.font("Arial", FontWeight.BOLD, 24));
            text.setBoundsType(TextBoundsType.VISUAL);
            text.setScaleY(-1);

            double txtW = text.getLayoutBounds().getWidth();
            double txtH = text.getLayoutBounds().getHeight();
            text.setX(px[idxMid] - txtW / 2);
            text.setY(py[idxMid] + txtH / 4);

            result.add(text);
        }
        return result;
    }

    private void bindCurveCallback() {
        curveMgr.setOnCurveSelected(seg -> {
            if (!UiSettings.get().showCurvePopupProperty().get()) return;
            if (curveSink != null) {
                java.util.Map<String,Object> info = curveMgr.toInfoMap(seg);
                String name = curveNames.get(seg.index());
                if (name != null) {
                    java.util.LinkedHashMap<String,Object> reordered = new java.util.LinkedHashMap<>();
                    reordered.put("Nome", name);
                    reordered.putAll(info);
                    info = reordered;
                }
                curveSink.onCurveInfo("Curva " + seg.index(), info);
            }
        });
    }

    // ============================================================================================
    //  VEHICLE ICON DETECTION (FIXED)
    // ============================================================================================

    private void setupVehicleIcon(List<? extends Map<Channel, Double>> samples) {
        String icon = detectVehicleType(samples);
        view.setVehicleIcon(tryLoadVehicleIcon(icon), 0.5, 0.5);
    }

    private String detectVehicleType(List<? extends Map<Channel, Double>> samples) {
        if (samples == null || samples.isEmpty()) return "other_top.png";

        // 1. Convertiamo i dati grezzi in un oggetto Lap
        List<Sample> sampleList = new ArrayList<>();
        for (Map<Channel, Double> m : samples) {
            // FIX: Sample richiede (double, double, EnumMap) o simile.
            // Estraiamo tempo e distanza per il costruttore specifico
            double t = m.getOrDefault(Channel.TIME, 0.0);
            double d = m.getOrDefault(Channel.DISTANCE, 0.0);

            // Creiamo una EnumMap come richiesto dal costruttore di Sample
            EnumMap<Channel, Double> em = new EnumMap<>(Channel.class);
            em.putAll(m);

            sampleList.add(new Sample(t, d, em));
        }

        // FIX: Lap richiede (int index, List<Sample>)
        Lap tempLap = new Lap(0, sampleList);

        // 2. Usiamo VehicleTraits per l'analisi euristica completa
        VehicleTraits traits = VehicleTraits.detect(List.of(tempLap));

        // 3. Mappiamo la Categoria all'immagine corretta
        return switch (traits.category) {
            case FORMULA   -> "formula_top.png";
            case PROTOTYPE -> "prototype_top.png";
            case GT        -> "gt_top.png";
            case ROAD      -> "road_top.png";
            default        -> "other_top.png";
        };
    }

    private Image tryLoadVehicleIcon(String fileName) {
        try {
            var is = getClass().getResourceAsStream("/assets/VehicleIcons/" + fileName);
            if(is==null) is = getClass().getResourceAsStream("/assets/vehicleicons/" + fileName);
            if (is != null) return new Image(is);
        } catch(Exception e){}
        return null;
    }

    // ============================================================================================
    //  HELPERS DI GEOMETRIA
    // ============================================================================================

    private record TrackConfig(boolean rotateAI, boolean flipAI_X, boolean swapAxes, boolean flipX, boolean flipZ, double scaleFactor, double offsetX, double offsetZ) {}
    private TrackConfig getManualConfig(String venueName) {
        if (venueName == null) return new TrackConfig(false, false, false, false, false, 0.95, 0, 0);
        String v = venueName.toLowerCase();
        if (v.contains("mugello")) return new TrackConfig(false, true, false, false, false, 1.0, 0.0, 0.0);
        return new TrackConfig(false, true, false, false, false, 1.0, 0, 0);
    }

    private void setupCurves(List<? extends Map<Channel, Double>> samples, double[] xAxis, String trackHint, TrackLayout layout, TrackGeometry referenceGeom) {
        if (curveMgr == null) curveMgr = new CurveManager();
        boolean sameTrack = (trackHint != null && trackHint.equals(lastTrackHint));
        boolean sameLayout = Objects.equals(layout, lastLayout);
        if (sameTrack && sameLayout && !curveMgr.getCurves().isEmpty()) {
            curveMgr.updateSamples(samples);
            return;
        }
        lastTrackHint = trackHint;
        lastLayout = layout;
        curveMgr.resetSession();
        curveNames.clear();
        List<AcSection> acSecs = null;
        if (layout != null) {
            Path p = layout.folder().resolve("data/sections.ini");
            try { if (Files.exists(p)) acSecs = AcSectionsLoader.load(p); } catch(Exception e){}
        }
        if (acSecs == null || acSecs.isEmpty()) {
            acSecs = AcSectionsLoader.tryLoadFromVenue(trackHint);
        }
        if (acSecs != null && !acSecs.isEmpty()) {
            acSecs.removeIf(sec -> isStraight(sec.name));
            if (!acSecs.isEmpty()) {
                try {
                    List<CurveSegment> segs = curveMgr.useAcSections(referenceGeom, acSecs, samples, 0);
                    List<AcSection> ordered = new ArrayList<>(acSecs);
                    ordered.sort(Comparator.comparingDouble(s -> s.inFrac));
                    int m = Math.min(ordered.size(), segs.size());
                    for (int i = 0; i < m; i++) {
                        curveNames.put(segs.get(i).index(), ordered.get(i).name);
                    }
                    bindCurveCallback();
                    view.setOverlay(curveMgr.getOverlayNode());
                    return;
                } catch (Exception e) {
                    System.err.println("[CircuitController] Errore curve AC: " + e.getMessage());
                }
            }
        }
        buildCurvesFallback(samples, xAxis);
    }

    private TrackGeometry rotateGeometry90(TrackGeometry g) {
        double[] ox = g.px(); double[] oy = g.py();
        double[] nx = new double[ox.length]; double[] ny = new double[ox.length];
        for(int i=0; i<ox.length; i++) { nx[i] = -oy[i]; ny[i] = ox[i]; }
        return new TrackGeometry(g.xAxis(), nx, ny, computeBounds(nx, ny));
    }
    private TrackGeometry flipGeometryX(TrackGeometry g) {
        double[] ox = g.px(); double[] oy = g.py();
        double[] nx = new double[ox.length];
        for(int i=0; i<ox.length; i++) { nx[i] = -ox[i]; }
        return new TrackGeometry(g.xAxis(), nx, oy, computeBounds(nx, oy));
    }
    private TrackGeometry fitGeometryToReference(TrackGeometry source, TrackGeometry target, TrackConfig cfg) {
        TrackGeometry.Bounds2D bSrc = source.bounds(); TrackGeometry.Bounds2D bTgt = target.bounds();
        if (bSrc.width() < 1) return source;
        double targetW = bTgt.width(); double targetH = bTgt.height();
        double srcW = bSrc.width(); double srcH = bSrc.height();
        boolean swapAxes = cfg.swapAxes;
        if (!swapAxes && !cfg.flipX && !cfg.flipZ && !cfg.rotateAI && !cfg.flipAI_X) {
            double r1 = Math.abs((targetW/srcW)/(targetH/srcH) - 1.0);
            double r2 = Math.abs((targetW/srcH)/(targetH/srcW) - 1.0);
            if(r2 < r1) swapAxes = true;
        }
        double scaleX = swapAxes ? targetW/srcH : targetW/srcW;
        double scaleZ = swapAxes ? targetH/srcW : targetH/srcH;
        double srcCx = bSrc.minX() + srcW/2.0; double srcCy = bSrc.minY() + srcH/2.0;
        double tgtCx = bTgt.minX() + targetW/2.0; double tgtCy = bTgt.minY() + targetH/2.0;
        double[] sx = source.px(); double[] sy = source.py();
        double[] nx = new double[sx.length]; double[] ny = new double[sy.length];
        for(int i=0; i<sx.length; i++) {
            double dx = sx[i] - srcCx; double dy = sy[i] - srcCy;
            if (cfg.flipX) dx = -dx; if (cfg.flipZ) dy = -dy;
            if (swapAxes) { nx[i] = tgtCx + dy * scaleX; ny[i] = tgtCy + dx * scaleZ; }
            else          { nx[i] = tgtCx + dx * scaleX; ny[i] = tgtCy + dy * scaleZ; }
        }
        return new TrackGeometry(source.xAxis(), nx, ny, computeBounds(nx, ny));
    }
    private TrackGeometry fitGeometryToMapManual(TrackGeometry source, AcMapData map, String trackHint, TrackConfig cfg) {
        TrackGeometry.Bounds2D bSrc = source.bounds(); if (bSrc.width() < 1) return source;
        double targetW = map.widthM(); double targetH = map.heightM();
        double mapCx = map.xOffset() + targetW/2.0; double mapCz = map.zOffset() + targetH/2.0;
        double srcW = bSrc.width(); double srcH = bSrc.height();
        double srcCx = bSrc.minX() + srcW/2.0; double srcCy = bSrc.minY() + srcH/2.0;
        double scaleX = cfg.swapAxes ? targetW / srcH : targetW / srcW;
        double scaleZ = cfg.swapAxes ? targetH / srcW : targetH / srcH;
        double uniformScale = Math.min(scaleX, scaleZ) * cfg.scaleFactor;
        double[] sx = source.px(); double[] sy = source.py();
        double[] nx = new double[sx.length]; double[] ny = new double[sy.length];
        for(int i=0; i<sx.length; i++) {
            double dx = sx[i] - srcCx; double dy = sy[i] - srcCy;
            if (cfg.flipX) dx = -dx; if (cfg.flipZ) dy = -dy;
            if (cfg.swapAxes) { nx[i] = mapCx + (dy * uniformScale); ny[i] = mapCz + (dx * uniformScale); }
            else { nx[i] = mapCx + (dx * uniformScale); ny[i] = mapCz + (dy * uniformScale); }
            nx[i] += cfg.offsetX; ny[i] += cfg.offsetZ;
        }
        return new TrackGeometry(source.xAxis(), nx, ny, computeBounds(nx, ny));
    }
    private boolean checkZDataValidity(List<? extends Map<Channel, Double>> samples) {
        double min=Double.MAX_VALUE,max=-Double.MAX_VALUE; boolean f=false;
        for(Map<Channel,Double> s:samples){ Double z=s.get(Channel.CAR_COORD_Z); if(z!=null && Double.isFinite(z)){ if(z<min)min=z; if(z>max)max=z; f=true;}}
        return f && (max-min)>300.0;
    }
    private TrackGeometry buildAbsoluteGeometry(List<? extends Map<Channel, Double>> samples, double[] xAxis, boolean useYasZ) {
        int n=samples.size(); double[] px=new double[n],py=new double[n];
        for(int i=0;i<n;i++){ Map<Channel,Double> s=samples.get(i); Double x=s.get(Channel.CAR_COORD_X); Double z=useYasZ?s.get(Channel.CAR_COORD_Y):s.get(Channel.CAR_COORD_Z); px[i]=(x!=null&&Double.isFinite(x))?x:((i>0)?px[i-1]:0.0); py[i]=(z!=null&&Double.isFinite(z))?z:((i>0)?py[i-1]:0.0); }
        return new TrackGeometry(xAxis,px,py,computeBounds(px,py));
    }
    private TrackGeometry.Bounds2D computeBounds(double[] x, double[] y) {
        double minX=Double.MAX_VALUE,maxX=-Double.MAX_VALUE,minY=Double.MAX_VALUE,maxY=-Double.MAX_VALUE;
        for(int i=0;i<x.length;i++){ if(Double.isFinite(x[i])&&Double.isFinite(y[i])){ if(x[i]<minX)minX=x[i]; if(x[i]>maxX)maxX=x[i]; if(y[i]<minY)minY=y[i]; if(y[i]>maxY)maxY=y[i]; }}
        return new TrackGeometry.Bounds2D(minX,minY,maxX,maxY);
    }

    private void buildCurvesFallback(List<? extends Map<Channel, Double>> samples, double[] xAxis){
        curveMgr.buildFromGeometry(this.geom, samples);
        curveMgr.setOnCurveSelected(seg -> { if (curveSink != null) curveSink.onCurveInfo("Curva " + seg.index(), curveMgr.toInfoMap(seg)); });
        view.setOverlay(curveMgr.getOverlayNode());
    }
    public void updateCarAtX(double x) {
        if (geom == null) return;
        double[] xs = geom.xAxis(), px = geom.px(), py = geom.py();
        if (xs == null || xs.length == 0) return;
        int i = lowerBound(xs, x);
        if (i >= xs.length - 1) { view.setCarPosition(px[px.length - 1], py[py.length - 1]); return; }
        double t = (xs[i+1] > xs[i]) ? clamp((x - xs[i]) / (xs[i+1] - xs[i]), 0, 1) : 0.0;
        view.setCarPosition(lerp(px[i], px[i+1], t), lerp(py[i], py[i+1], t));
    }
    public void setWaypointsByX(List<Double> wpX) {
        if (geom == null || wpX == null) { view.setWaypointFlags(null, null); return; }
        double[] xs = geom.xAxis(), px = geom.px(), py = geom.py();
        double[] wx = new double[wpX.size()], wy = new double[wpX.size()];
        for (int k = 0; k < wpX.size(); k++) {
            double val = (wpX.get(k)==null) ? Double.NaN : wpX.get(k);
            int i = lowerBound(xs, val);
            if (i >= xs.length - 1) { wx[k] = px[px.length-1]; wy[k] = py[py.length-1]; continue; }
            double t = (xs[i+1] > xs[i]) ? clamp((val - xs[i]) / (xs[i+1] - xs[i]), 0, 1) : 0.0;
            wx[k] = lerp(px[i], px[i+1], t); wy[k] = lerp(py[i], py[i+1], t);
        }
        view.setWaypointFlags(wx, wy);
    }
    private Color[] buildStrokeColors(List<? extends Map<Channel, Double>> samples) {
        int n = Math.max(0, samples.size() - 1); Color[] c = new Color[Math.max(0, n)];
        for (int i = 0; i < n; i++) {
            Map<Channel, Double> s = samples.get(i);
            double thr = pedal01(s.get(Channel.THROTTLE)); double brk = pedal01(s.get(Channel.BRAKE));
            if (brk > thr && brk > 0.015) c[i] = mix(Color.web("#FFA500"), Color.web("#FF0000"), brk);
            else if (thr >= brk && thr > 0.015) c[i] = mix(Color.web("#4FC3F7"), Color.web("#00C853"), thr);
            else c[i] = Color.web("#4FC3F7");
        }
        smoothColors(c, 5); return c;
    }
    private static double pedal01(Double v) { return (v==null || !Double.isFinite(v)) ? 0.0 : clamp((v>1.0001 ? v/100.0 : v), 0, 1); }
    private static void smoothColors(Color[] a, int w) { if (a == null || a.length == 0 || w <= 1) return; int n = a.length, r = w / 2; Color[] src = a.clone(); for (int i = 0; i < n; i++) { double sr = 0, sg = 0, sb = 0; int cnt = 0; for (int k = i - r; k <= i + r; k++) { if (k >= 0 && k < n && src[k] != null) { sr += src[k].getRed(); sg += src[k].getGreen(); sb += src[k].getBlue(); cnt++; } } if (cnt > 0) a[i] = new Color(sr / cnt, sg / cnt, sb / cnt, 1.0); } }
    private static Color mix(Color a, Color b, double t) { return new Color(lerp(a.getRed(),b.getRed(),t), lerp(a.getGreen(),b.getGreen(),t), lerp(a.getBlue(),b.getBlue(),t), 1.0); }
    private static int lowerBound(double[] a, double x) { int lo=0,hi=a.length-1; while(hi-lo>1){int m=(lo+hi)>>>1; if(a[m]<=x)lo=m; else hi=m;} return lo; }
    private static boolean isStraight(String name){ return name!=null && (name.toLowerCase().contains("rettifilo") ||name.toLowerCase().contains("rettilineo") || name.toLowerCase().contains("straight") || name.toLowerCase().contains("pit") || name.toLowerCase().contains("box")); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static int findIndex(double[] dist, double d) { int lo = 0, hi = dist.length - 1; if (d >= dist[hi]) return hi; if (d <= dist[0]) return 0; while (lo < hi) { int mid = (lo + hi) / 2; if (dist[mid] < d) lo = mid + 1; else hi = mid; } return lo; }

    private boolean checkDrsCapability(List<? extends Map<Channel, Double>> samples) {
        for(int i=0; i<Math.min(samples.size(), 1000); i++) {
            Double d = samples.get(i).get(Channel.DRS_AVAILABLE);
            if (d != null && d > 0) return true;
        }
        return false;
    }
}