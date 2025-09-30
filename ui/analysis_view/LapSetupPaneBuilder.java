package org.simulator.ui.analysis_view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.simulator.canale.Lap;
import org.simulator.setup.TyreCompoundAdvisor;
import org.simulator.setup.setup_advisor.SetupAdvisor;

import java.util.Collections;
import java.util.List;

final class LapSetupPaneBuilder {

    Node build(Lap lap, List<Lap> sessionLaps){
        final List<Lap> session = (sessionLaps == null) ? Collections.emptyList() : sessionLaps;

        Label styleLbl = new Label("-");
        styleLbl.setMinWidth(Region.USE_PREF_SIZE);

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
                Color c = switch (r.sev()) {
                    case HIGH -> Color.web("#dc143c");
                    case MEDIUM -> Color.web("#daa520");
                    case LOW -> Color.web("#3cb371");
                };
                dot.setFill(c);
                txt.setText(r.area() + " — " + r.message());
                setGraphic(row); setText(null);
            }
        });

        Runnable refresh = () -> {
            var style = SetupAdvisor.analyzeStyle(session);
            styleLbl.setText(switch (style) {
                case SMOOTH -> "Stile: pulito";
                case NEUTRAL -> "Stile: neutro";
                case AGGRESSIVE -> "Stile: aggressivo";
            });

            var recs = (lap == null)
                    ? Collections.<SetupAdvisor.Recommendation>emptyList()
                    : SetupAdvisor.forLap(lap, style);
            list.getItems().setAll(recs);

            // === Torna all’API breve (Choice) ===
            TyreCompoundAdvisor.Choice choice = TyreCompoundAdvisor.suggest(session, style);
            compoundLbl.setText("Compound consigliato: " + choice.compound() + "  (" + choice.reason() + ")");
        };
        refresh.run();

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, styleLbl, compoundLbl, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 0, 6, 0));

        VBox content = new VBox(8, header, list);
        TitledPane tp = new TitledPane("Consigli setup", content);
        tp.setCollapsible(false);
        return tp;
    }
}
