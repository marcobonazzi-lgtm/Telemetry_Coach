package org.simulator.ui.time_line_view.Track.Circuit;

import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import java.util.function.DoubleConsumer;

final class CircuitInteractions {
    private CircuitInteractions() {}


    static void installPanOnly(Node root, Node world, DoubleConsumer addPanX, DoubleConsumer addPanY) {
        final double[] last = new double[2];
        root.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            last[0] = e.getSceneX(); last[1] = e.getSceneY();
        });
        root.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            double dx = e.getSceneX() - last[0];
            double dy = e.getSceneY() - last[1];
            last[0] = e.getSceneX(); last[1] = e.getSceneY();
            addPanX.accept(dx);
            addPanY.accept(dy);
        });
    }
}