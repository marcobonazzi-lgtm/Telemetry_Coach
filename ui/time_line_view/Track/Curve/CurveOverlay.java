package org.simulator.ui.time_line_view.Track.Curve;

import javafx.scene.Group;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import org.simulator.ui.settings.UiSettings; // Import necessario
import org.simulator.ui.time_line_view.Track.TrackGeometry;

import java.util.*;
import java.util.function.Consumer;

final class CurveOverlay {
    private final Group root = new Group();
    private final List<Marker> markers = new ArrayList<>();
    private final Set<Integer> lockedIdx = new HashSet<>();
    private Consumer<CurveSegment> onSelected;

    // Listener per aggiornare la dimensione dinamicamente
    private final javafx.beans.value.ChangeListener<Number> sizeListener = (o, ov, nv) -> resizeAll(nv.doubleValue());

    public CurveOverlay() {
        // Bind visibilità generale
        root.visibleProperty().bind(UiSettings.get().showCurveMarkersProperty());

        // Ascolta i cambi di dimensione
        UiSettings.get().curveMarkerSizeProperty().addListener(sizeListener);
    }

    Node node(){ return root; }

    void clear(){
        root.getChildren().clear();
        markers.clear();
    }

    void setOnSelected(Consumer<CurveSegment> cb){ this.onSelected = cb; }

    void setCurves(List<CurveSegment> curves){
        setMarkers(null, curves, null);
    }

    void setMarkers(TrackGeometry g, List<CurveSegment> curves, List<Color> colors){
        root.getChildren().clear();
        markers.clear();

        // Recupera la dimensione corrente dalle impostazioni
        double currentSize = UiSettings.get().curveMarkerSizeProperty().get();

        int i = 0;
        for (CurveSegment c : curves){
            Color col = (colors!=null && i<colors.size() && colors.get(i)!=null)? colors.get(i) : Color.ORANGE;
            Marker m = new Marker(c, i+1, col, currentSize);

            if (lockedIdx.contains(c.index())) m.setLocked(true);
            markers.add(m);
            root.getChildren().add(m.root);
            i++;
        }
    }

    void colorize(java.util.function.IntToDoubleFunction deltaProvider){
        for (Marker m : markers){
            double d = deltaProvider.applyAsDouble(m.curve.index());
            Color col = CurveColorScale.colorFor(d);
            m.setColor(col);
        }
    }

    private void resizeAll(double r) {
        for (Marker m : markers) {
            m.setSize(r);
        }
    }

    int getLockedCurveIndex(){
        if (lockedIdx.isEmpty()) return -1;
        return lockedIdx.iterator().next();
    }

    private final class Marker {
        final CurveSegment curve;
        final Group root = new Group();
        final Circle halo = new Circle();
        final Circle dot  = new Circle();
        final Text label  = new Text();
        boolean locked = false;
        final double x, y;

        Marker(CurveSegment c, int number, Color color, double radius){
            this.curve = c;
            this.x = c.xApex();
            this.y = c.yApex();

            halo.setCenterX(x); halo.setCenterY(y);
            halo.setFill(Color.WHITE);
            halo.setStroke(Color.BLACK);
            halo.setStrokeWidth(0.5);

            dot.setCenterX(x); dot.setCenterY(y);
            dot.setFill(color);
            dot.setStroke(Color.TRANSPARENT);
            dot.setStrokeType(StrokeType.CENTERED);

            label.setText(String.valueOf(number));
            label.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            label.setFill(Color.BLACK);
            label.setBoundsType(TextBoundsType.VISUAL);
            label.setScaleY(-1);

            // Imposta dimensione iniziale
            setSize(radius);

            root.getChildren().setAll(halo, dot, label);
            Tooltip.install(root, new Tooltip("Curva " + number));

            root.setCursor(Cursor.HAND);
            root.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY){
                    if (onSelected != null) onSelected.accept(curve);
                } else if (e.getButton() == MouseButton.SECONDARY){
                    setLocked(!locked);
                }
            });
        }

        void setSize(double r) {
            halo.setRadius(r + 1.5);
            dot.setRadius(r);

            // Ricalcola centratura testo
            double txtW = label.getLayoutBounds().getWidth();
            double txtH = label.getLayoutBounds().getHeight();
            label.setX(x - txtW / 2.0);
            label.setY(y + txtH / 3.5);

            // Adatta font se il pallino diventa molto piccolo o molto grande
            double fontSize = Math.max(8, r * 1.1);
            label.setFont(Font.font("Arial", FontWeight.BOLD, fontSize));
        }

        void setColor(Color c){ dot.setFill(c); }

        void setLocked(boolean on){
            locked = on;
            if (on) lockedIdx.add(curve.index()); else lockedIdx.remove(curve.index());
            if (on) { halo.setStroke(Color.BLUE); halo.setStrokeWidth(2.0); }
            else { halo.setStroke(Color.BLACK); halo.setStrokeWidth(0.5); }
        }
    }
}