package org.simulator.ui.analysis_view;

import javafx.application.Platform;
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
import java.util.concurrent.CompletableFuture;

final class LapSetupPaneBuilder {

    Node build(Lap lap, List<Lap> sessionLaps) {
        final List<Lap> session = (sessionLaps == null) ? Collections.emptyList() : sessionLaps;

        Label styleLbl = new Label("Analisi stile in corso...");
        styleLbl.setMinWidth(Region.USE_PREF_SIZE);

        Label compoundLbl = new Label("Analisi mescole in corso...");
        compoundLbl.setStyle("-fx-font-weight: bold;");
        compoundLbl.setWrapText(true);
        compoundLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(compoundLbl, Priority.ALWAYS);

        ListView<SetupAdvisor.Recommendation> list = new ListView<>();
        list.setPrefHeight(360);
        list.setPlaceholder(new ProgressIndicator()); // Spinner

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

            @Override
            protected void updateItem(SetupAdvisor.Recommendation r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                Color c;
                switch (r.sev()) {
                    case HIGH: c = Color.web("#dc143c"); break;
                    case MEDIUM: c = Color.web("#daa520"); break;
                    case LOW: c = Color.web("#3cb371"); break;
                    default: c = Color.GRAY; break;
                }

                dot.setFill(c);
                txt.setText(r.area() + " — " + r.message());
                setGraphic(row);
                setText(null);
            }
        });

        // --- ASYNC LOADING ---
        CompletableFuture.supplyAsync(() -> {
            // Calcolo Pesante: Analisi Stile su tutta la sessione
            // Il metodo restituisce SetupAdvisor.DriverStyle
            var style = SetupAdvisor.analyzeStyle(session);

            // Calcolo Pesante: Analisi suggerimenti giro specifico
            var recs = (lap == null)
                    ? Collections.<SetupAdvisor.Recommendation>emptyList()
                    : SetupAdvisor.forLap(lap, style);

            // Calcolo Pesante: Analisi Gomme
            TyreCompoundAdvisor.Choice choice = TyreCompoundAdvisor.suggest(session, style);

            // Pacchetto risultati
            return new SetupResult(style, recs, choice);

        }).thenAcceptAsync(res -> {
            String styleText;
            switch (res.style) {
                case SMOOTH: styleText = "Stile: pulito"; break;
                case NEUTRAL: styleText = "Stile: neutro"; break;
                case AGGRESSIVE: styleText = "Stile: aggressivo"; break;
                default: styleText = "Stile: sconosciuto"; break;
            }
            styleLbl.setText(styleText);

            list.getItems().setAll(res.recs);

            compoundLbl.setText("Compound consigliato: " + res.choice.compound() + "  (" + res.choice.reason() + ")");

        }, Platform::runLater);


        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, styleLbl, compoundLbl, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 0, 6, 0));

        VBox content = new VBox(8, header, list);
        TitledPane tp = new TitledPane("Consigli setup", content);
        tp.setCollapsible(false);
        return tp;
    }

    private static class SetupResult {
        final SetupAdvisor.DriverStyle style; // CORRETTO: DriverStyle
        final List<SetupAdvisor.Recommendation> recs;
        final TyreCompoundAdvisor.Choice choice;

        public SetupResult(SetupAdvisor.DriverStyle style,
                           List<SetupAdvisor.Recommendation> recs,
                           TyreCompoundAdvisor.Choice choice) {
            this.style = style;
            this.recs = recs;
            this.choice = choice;
        }
    }
}