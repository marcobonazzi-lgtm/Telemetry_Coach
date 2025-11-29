package org.simulator.ui.time_line_view.Track.Circuit;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import org.simulator.ui.settings.UiSettings;
import org.simulator.ui.time_line_view.Track.AC.AcMapData;
import org.simulator.ui.time_line_view.Track.TrackGeometry;

import java.util.List;

public final class CircuitView extends Pane {

    private final Group world = new Group();

    private final ImageView mapLayer = new ImageView();
    private final Path aiPath = new Path();
    private final Group asphalt  = new Group();
    private final Group drsLayer = new Group();
    private final Group idealLineLayer = new Group();
    private final Path shoulder  = new Path();
    private final Path outline   = new Path();
    private final Path body      = new Path();

    private final Group segments = new Group();
    private final Group flags    = new Group();
    private final Group overlay  = new Group();

    private final Circle carDot    = new Circle(4.0);
    private final ImageView carIcon = new ImageView();
    private double carAnchorX = 0.5, carAnchorY = 0.5;

    // --- STATO NAVIGAZIONE ---
    private double panX = 0.0, panY = 0.0;
    private double zoomK = 1.0;

    private TrackGeometry geom;
    private AcMapData currentMapData;

    // --- STATO FOLLOW ---
    private boolean followCar = false;
    private double followWindowMeters = 80.0;

    private double carX = 0.0, carY = 0.0;
    private double lastCarX = Double.NaN, lastCarY = Double.NaN;
    private Double externalHeadingDeg = null;
    private double visualHeadingDeg = 0.0;
    private double headingSmooth = 0.35;
    private double carIconWidthMeters = 30.0;

    private boolean initialFitDone = false;

    public CircuitView() {
        getStyleClass().add("circuit-view");

        // 1. Stili Grafici
        // Colore iniziale (verrà sovrascritto dal listener)
        Color trackColor = UiSettings.get().trackMapColorProperty().get();
        aiPath.setStroke(trackColor);
        aiPath.setStrokeLineCap(StrokeLineCap.ROUND);
        aiPath.setStrokeLineJoin(StrokeLineJoin.ROUND);
        aiPath.setFill(null);
        aiPath.setEffect(new DropShadow(10, Color.rgb(0,0,0,0.3)));

        drsLayer.setMouseTransparent(true);
        drsLayer.setVisible(false);
        idealLineLayer.setMouseTransparent(true);
        idealLineLayer.setVisible(false);
        idealLineLayer.setEffect(new DropShadow(5, Color.rgb(0,0,0,0.5)));

        mapLayer.setPreserveRatio(false);
        mapLayer.setSmooth(true);
        mapLayer.setVisible(false);

        asphalt.getChildren().addAll(shoulder, outline, body);
        shoulder.setStroke(Color.TRANSPARENT);
        outline.setStroke(Color.BLACK); // Outline sempre nero per contrasto
        outline.setStrokeLineCap(StrokeLineCap.ROUND);

        body.setStroke(trackColor);
        body.setStrokeLineCap(StrokeLineCap.ROUND);
        asphalt.setEffect(new DropShadow(10, Color.rgb(0,0,0,0.3)));
        asphalt.setVisible(false);

        carDot.getStyleClass().add("track-car");
        carDot.setFill(Color.ORANGERED);
        carDot.setStroke(Color.WHITE);
        carDot.setStrokeWidth(1.5);

        carIcon.setPreserveRatio(true);
        carIcon.setViewOrder(-1000);

        // Layers trasparenti al mouse
        mapLayer.setMouseTransparent(true);
        aiPath.setMouseTransparent(true);
        asphalt.setMouseTransparent(true);
        segments.setMouseTransparent(true);
        flags.setMouseTransparent(true);

        // Composizione Scena
        world.getChildren().addAll(mapLayer, aiPath, asphalt, drsLayer, idealLineLayer, segments, flags, overlay, carDot, carIcon);
        getChildren().add(world);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        setupInteractions();

        // --- LISTENER CAMBIO COLORE TEMA ---
        UiSettings.get().trackMapColorProperty().addListener((o, oldC, newC) -> {
            aiPath.setStroke(newC);
            body.setStroke(newC);
        });
    }

    public void setGeometry(TrackGeometry g) {
        this.geom = g;
        this.followCar = false;
        this.initialFitDone = false;
        if (g != null) autoScaleTrackStyle(g.bounds());
        layoutWorld();
    }

    private void autoScaleTrackStyle(TrackGeometry.Bounds2D b) {
        double diagMeters = Math.hypot(b.width(), b.height());
        double refDiag = 1600.0;
        double refStroke = 26.0;
        double factor = Math.sqrt(diagMeters / refDiag);
        double targetStroke = Math.max(12.0, Math.min(80.0, refStroke * factor));

        aiPath.setStrokeWidth(targetStroke);
        body.setStrokeWidth(targetStroke);
        outline.setStrokeWidth(targetStroke + 5.0);
    }

    public TrackGeometry getGeometry() { return geom; }

    public void setAiReferenceTrack(double[] px, double[] py) {
        aiPath.getElements().clear();
        if (px == null || px.length < 2) { aiPath.setVisible(false); return; }
        aiPath.getElements().add(new MoveTo(px[0], py[0]));
        for (int i = 1; i < px.length; i++) {
            aiPath.getElements().add(new LineTo(px[i], py[i]));
        }
        aiPath.getElements().add(new ClosePath());
        aiPath.setVisible(true);
        mapLayer.setVisible(false);
        asphalt.setVisible(false);
        layoutWorld();
    }

    public void setMapData(AcMapData mapData) {
        this.currentMapData = mapData;
        if (mapData != null) {
            mapLayer.setImage(mapData.image());
            mapLayer.setX(mapData.xOffset()); mapLayer.setY(mapData.zOffset());
            mapLayer.setFitWidth(mapData.widthM()); mapLayer.setFitHeight(mapData.heightM());
            mapLayer.setVisible(true);
            asphalt.setVisible(false);
            autoScaleTrackStyle(mapData.getBounds());
        } else {
            mapLayer.setVisible(false);
        }
        layoutWorld();
    }

    public void setAsphaltFromLine(double[] px, double[] py, double trackWidthMeters) {
        shoulder.getElements().clear(); outline.getElements().clear(); body.getElements().clear();
        if (px == null || px.length < 2) { asphalt.setVisible(false); return; }
        asphalt.setVisible(true);
        if (mapLayer.isVisible() || aiPath.isVisible()) asphalt.setVisible(false);
        shoulder.getElements().add(new MoveTo(px[0], py[0]));
        outline.getElements().add(new MoveTo(px[0], py[0]));
        body.getElements().add(new MoveTo(px[0], py[0]));
        for (int i = 1; i < px.length; i++) {
            LineTo l = new LineTo(px[i], py[i]);
            shoulder.getElements().add(l); outline.getElements().add(l); body.getElements().add(l);
        }
    }

    public void setColoredTrack(double[] px, double[] py, Color[] stroke) {
        segments.getChildren().clear();
        if (px == null || py == null || stroke == null) return;
        double telWidth = 3.0;
        int n = Math.min(Math.min(px.length, py.length), stroke.length);
        for (int i = 0; i < n - 1; i++) {
            if (!Double.isFinite(px[i]) || !Double.isFinite(py[i])) continue;
            Line l = new Line(px[i], py[i], px[i+1], py[i+1]);
            l.setStroke(stroke[i]);
            l.setStrokeWidth(telWidth);
            l.setStrokeLineCap(StrokeLineCap.ROUND);
            segments.getChildren().add(l);
        }
    }

    // --- LOGICA LOCK E POSIZIONE ---

    public void setCarPosition(double x, double y) {
        this.carX = x; this.carY = y;
        carDot.setCenterX(x); carDot.setCenterY(y);
        if (carIcon.isVisible()) {
            carIcon.setLayoutX(x); carIcon.setLayoutY(y);
            double targetDeg = (externalHeadingDeg != null) ? externalHeadingDeg : deriveHeadingDegFromMotion(x, y);
            visualHeadingDeg = lerpAngleDeg(visualHeadingDeg, targetDeg, headingSmooth);
            carIcon.setRotate(visualHeadingDeg + 90.0);
        }
        lastCarX = x; lastCarY = y;

        if (followCar) {
            layoutWorld();
        }
    }

    public void setFollowCarEnabled(boolean e) {
        this.followCar = e;
        if (e) {
            this.followWindowMeters = 80.0;
        }
        layoutWorld();
    }

    // --- LOGICA LAYOUT E ZOOM ---

    public void fitToTrack() { fitToTrack(20.0); }

    public void fitToTrack(double paddingPx) {
        this.followCar = false;
        this.panX = 0;
        this.panY = 0;

        double minX, minY, w, h;
        if (geom != null) {
            var b = geom.bounds(); minX = b.minX(); minY = b.minY(); w = b.width(); h = b.height();
        } else if (currentMapData != null) {
            minX = currentMapData.xOffset(); minY = currentMapData.zOffset(); w = currentMapData.widthM(); h = currentMapData.heightM();
        } else return;

        double panelW = getWidth(); double panelH = getHeight();
        if (panelW <= 0 || panelH <= 0) {
            initialFitDone = false;
            return;
        }

        double sx = (panelW - paddingPx * 2) / w;
        double sy = (panelH - paddingPx * 2) / h;

        this.zoomK = Math.min(sx, sy);
        if (this.zoomK <= 0) this.zoomK = 1.0;

        initialFitDone = true;
        layoutWorld();
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (!initialFitDone && getWidth() > 0 && getHeight() > 0) {
            fitToTrack();
        } else {
            layoutWorld();
        }
    }

    private void layoutWorld() {
        double W = getWidth(); double H = getHeight();
        if (W == 0 || H == 0) return;

        double cxPane = W / 2.0;
        double cyPane = H / 2.0;

        double targetX, targetY;
        double finalScale;

        if (followCar) {
            targetX = carX;
            targetY = carY;
            finalScale = Math.min(W, H) / Math.max(1.0, followWindowMeters);

            world.getTransforms().setAll(
                    new Translate(cxPane, cyPane),
                    new Scale(finalScale, -finalScale),
                    new Translate(-targetX, -targetY)
            );

        } else {
            if (geom != null) { var b = geom.bounds(); targetX = b.minX() + b.width()/2; targetY = b.minY() + b.height()/2; }
            else if (currentMapData != null) { targetX = currentMapData.xOffset() + currentMapData.widthM()/2; targetY = currentMapData.zOffset() + currentMapData.heightM()/2; }
            else { targetX = 0; targetY = 0; }

            finalScale = zoomK;

            world.getTransforms().setAll(
                    new Translate(cxPane + panX, cyPane + panY),
                    new Scale(finalScale, -finalScale),
                    new Translate(-targetX, -targetY)
            );
        }
    }

    private void setupInteractions() {
        this.addEventHandler(ScrollEvent.SCROLL, e -> {
            if (e.getDeltaY() == 0) return;
            double factor = (e.getDeltaY() > 0) ? 1.1 : 0.9;

            if (followCar) {
                double mFactor = (e.getDeltaY() > 0) ? 0.8 : 1.25;
                followWindowMeters = Math.max(5.0, Math.min(5000.0, followWindowMeters * mFactor));
            } else {
                zoomK = Math.max(1e-5, Math.min(10000.0, zoomK * factor));
            }
            layoutWorld();
            e.consume();
        });

        CircuitInteractions.installPanOnly(this, world, dx -> {
            if (!followCar) { panX += dx; layoutWorld(); }
        }, dy -> {
            if (!followCar) { panY += dy; layoutWorld(); }
        });

        carDot.setOnMouseClicked(e -> setFollowCarEnabled(true));
        carIcon.setOnMouseClicked(e -> setFollowCarEnabled(true));

        addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if ((e.getTarget() == this || e.getTarget() == mapLayer) && e.getClickCount() == 2) fitToTrack();
        });

        widthProperty().addListener(o -> layoutWorld());
        heightProperty().addListener(o -> layoutWorld());
    }

    public void setWaypointFlags(double[] wx, double[] wy) { flags.getChildren().clear(); if(wx==null)return; for(int i=0;i<Math.min(wx.length,wy.length);i++){ Line p=new Line(wx[i],wy[i],wx[i],wy[i]+24); p.setStrokeWidth(2); flags.getChildren().addAll(p, new Circle(wx[i],wy[i],2,Color.RED)); } }
    public void setVehicleIcon(Image img, double ax, double ay) { this.carAnchorX=ax; this.carAnchorY=ay; if(img!=null){carIcon.setImage(img);updateIconSize();carIcon.setVisible(true);carDot.setVisible(true);}else{carIcon.setVisible(false);carDot.setVisible(true);} }
    private void updateIconSize(){ if(carIcon.getImage()!=null){ carIcon.setFitWidth(carIconWidthMeters); carIcon.setTranslateX(-carIconWidthMeters*carAnchorX); carIcon.setTranslateY(-carIcon.getBoundsInLocal().getHeight()*carAnchorY); } }
    public void setOverlay(Node n) { overlay.getChildren().clear(); if(n!=null)overlay.getChildren().add(n); }
    private double deriveHeadingDegFromMotion(double x, double y){ if(!Double.isFinite(lastCarX))return visualHeadingDeg; double dx=x-lastCarX,dy=y-lastCarY; return (Math.hypot(dx,dy)<0.5)?visualHeadingDeg:Math.toDegrees(Math.atan2(dy,dx)); }
    private static double lerpAngleDeg(double a, double b, double t){ double d=wrapDeg(b-a); return a+d*Math.max(0,Math.min(1,t)); }
    private static double wrapDeg(double d){ while(d>180)d-=360; while(d<-180)d+=360; return d; }
    public void setDrsShapes(List<Node> shapes) {
        drsLayer.getChildren().clear();
        if (shapes != null && !shapes.isEmpty()) {
            drsLayer.getChildren().addAll(shapes);
        }
    }
    public void setDrsVisible(boolean v) {
        drsLayer.setVisible(v);
    }
    public void setIdealLineShapes(List<Node> shapes) {
        idealLineLayer.getChildren().clear();
        if (shapes != null) {
            idealLineLayer.getChildren().addAll(shapes);
        }
    }
    public void setIdealLineVisible(boolean v) {
        idealLineLayer.setVisible(v);
    }
}