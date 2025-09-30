package org.simulator.ui.time_line_view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.Effect;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;

/** Wrapper che sovrappone un'etichetta numerica a una card widget. */
public final class WidgetValueOverlay {
    private final StackPane root;
    private final Label label = new Label();

    public WidgetValueOverlay(Node content, Pos position, Insets margin) {
        this.root = new StackPane(content, label);
        StackPane.setAlignment(label, position == null ? Pos.BOTTOM_RIGHT : position);
        if (margin != null) StackPane.setMargin(label, margin);
        label.setMouseTransparent(true);
        // Nessuno stile forzato: lo copiamo dinamicamente dal widget Pedali
    }

    public StackPane getRoot() { return root; }
    public void setText(String txt) { label.setText(txt); }
    public Label getLabel() { return label; }

    /** Applica font/colore/effect dalla label di riferimento (Throttle/Brake/Clutch). */
    public void applyFrom(Label ref) {
        if (ref == null) return;
        Font f = ref.getFont();
        if (f != null) label.setFont(f);
        try {
            Paint p = ref.getTextFill();
            if (p != null) label.setTextFill(p);
        } catch (Throwable ignored) {}
        Effect eff = ref.getEffect();
        if (eff != null) label.setEffect(eff);
        String st = ref.getStyle();
        if (st != null && !st.isBlank()) label.setStyle(st);
        // copia anche eventuali styleClass utili
        if (!ref.getStyleClass().isEmpty()) {
            label.getStyleClass().setAll(ref.getStyleClass());
        }
    }
}
