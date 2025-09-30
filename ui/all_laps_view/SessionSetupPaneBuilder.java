package org.simulator.ui.all_laps_view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.simulator.canale.Lap;
import org.simulator.setup.TyreCompoundAdvisor;
import org.simulator.setup.setup_advisor.SetupAdvisor;

import java.util.*;

/** Pannello "Consigli setup (sessione)". */
final class SessionSetupPaneBuilder {

    Node build(List<Lap> laps) {
        final List<Lap> session = (laps == null) ? Collections.emptyList() : laps;

        Label styleLbl = new Label("-");
        Label compoundLbl = new Label("-");
        compoundLbl.setStyle("-fx-font-weight: bold;");
        compoundLbl.setWrapText(true);
        compoundLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(compoundLbl, Priority.ALWAYS);

        ListView<SetupAdvisor.Recommendation> list = new ListView<>();
        list.setPrefHeight(360);
        list.setCellFactory(v -> new ListCell<>() {
            private final Circle dot = new Circle(5);
            private final Label txt = new Label();
            private final HBox row = new HBox(8, dot, txt);
            {
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(2, 6, 2, 6));
                txt.setWrapText(true);
                txt.maxWidthProperty().bind(list.widthProperty().subtract(40));
            }
            @Override protected void updateItem(SetupAdvisor.Recommendation r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) { setGraphic(null); setText(null); return; }
                dot.setFill(colorForSeverity(r.sev()));
                txt.setText(r.area() + " — " + r.message());
                setGraphic(row); setText(null);
            }
        });

        Runnable refresh = () -> {
            var assess = SetupAdvisor.analyzeStyleDetailed(session);
            var style = (assess == null) ? SetupAdvisor.DriverStyle.NEUTRAL : assess.primary();

            styleLbl.setText(switch (style) {
                case SMOOTH -> "Stile: pulito";
                case NEUTRAL -> "Stile: neutro";
                case AGGRESSIVE -> "Stile: aggressivo";
            });

            var recs = SetupAdvisor.forSession(session, style);
            list.getItems().setAll(recs);

            // === Torna all’API breve (Choice) per un’etichetta compatta ===
            TyreCompoundAdvisor.Choice choice = TyreCompoundAdvisor.suggest(session, style);
            compoundLbl.setText("Compound consigliato: " + choice.compound() + "  (" + choice.reason() + ")");
        };
        refresh.run();

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, styleLbl, compoundLbl, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 0, 6, 0));

        VBox content = new VBox(8, header, list);
        TitledPane tp = new TitledPane("Consigli setup (sessione)", content);
        tp.setCollapsible(false);
        return tp;
    }

    private Color colorForSeverity(SetupAdvisor.Severity s){
        return switch (s){
            case HIGH -> Color.CRIMSON;
            case MEDIUM -> Color.GOLD;
            case LOW -> Color.MEDIUMSEAGREEN;
        };
    }
}
