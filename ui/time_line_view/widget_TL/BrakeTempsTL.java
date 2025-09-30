package org.simulator.ui.time_line_view.widget_TL;

import javafx.scene.Node;
import javafx.scene.layout.VBox;
import org.simulator.ui.time_line_view.Quad;

public final class BrakeTempsTL{
    private final VBox root = new VBox(6);
    private final Quad quad = new Quad("Freni (°C)");
    public BrakeTempsTL(){ root.getChildren().setAll(quad.root); }
    public void update(Double fl, Double fr, Double rl, Double rr){
        quad.set(fl, fr, rl, rr, "°C");
    }
    public Node getRoot(){ return root; }
}
