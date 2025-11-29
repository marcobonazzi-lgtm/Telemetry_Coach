package org.simulator.ui.export;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.simulator.ui.DataController;
import org.simulator.ui.settings.ThemeManager;
import org.simulator.ui.settings.UiSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class ExportPdfDialog {

    public static void show(Stage owner, DataController data, Consumer<ExportOptions> onConfirm) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Esporta in PDF");

        // Dati disponibili
        List<Integer> allLaps = data.lapIndices();

        // =================================================================================
        // CARD 1: SELEZIONE GIRI
        // =================================================================================
        Label lblLaps = header("1. Quali giri vuoi analizzare?");

        ToggleGroup tg = new ToggleGroup();

        // Opzioni Rapide
        RadioButton rbAllValid = new RadioButton("Tutti i giri VALIDI (Consigliato)");
        rbAllValid.setUserData(LapSelection.ALL_VALID);
        rbAllValid.setSelected(true);

        RadioButton rbAllRaw = new RadioButton("Tutti i giri (Inclusi In/Out)");
        rbAllRaw.setUserData(LapSelection.ALL);

        RadioButton rbBest = new RadioButton("Solo Best Lap");
        rbBest.setUserData(LapSelection.BEST);

        // Opzione Multipla (Lista)
        RadioButton rbSelected = new RadioButton("Selezione Multipla:");
        rbSelected.setUserData(LapSelection.SELECTED);

        ListView<Integer> listLaps = new ListView<>(FXCollections.observableArrayList(allLaps));
        listLaps.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listLaps.setPrefHeight(100); // Altezza compatta
        listLaps.disableProperty().bind(rbSelected.selectedProperty().not());

        // Opzione Singola (Combo)
        RadioButton rbSingle = new RadioButton("Giro Singolo:");
        rbSingle.setUserData(LapSelection.SINGLE);

        ComboBox<Integer> comboSingle = new ComboBox<>(FXCollections.observableArrayList(allLaps));
        if (!allLaps.isEmpty()) comboSingle.getSelectionModel().select(0);
        comboSingle.disableProperty().bind(rbSingle.selectedProperty().not());

        // Aggiunta al gruppo
        rbAllValid.setToggleGroup(tg);
        rbAllRaw.setToggleGroup(tg);
        rbBest.setToggleGroup(tg);
        rbSelected.setToggleGroup(tg);
        rbSingle.setToggleGroup(tg);

        // Layout Card 1
        VBox boxLaps = card(lblLaps,
                rbAllValid, rbAllRaw, rbBest,
                new Separator(),
                rbSelected, listLaps,
                new Separator(),
                new HBox(10, rbSingle, comboSingle)
        );

        // =================================================================================
        // CARD 2: COACHING & SETUP
        // =================================================================================
        Label lblCoach = header("2. Coaching & Setup");

        // Coaching
        Label subCoach = new Label("Livello Coaching IA:");
        subCoach.setStyle("-fx-font-weight: bold; -fx-opacity: 0.8;");

        ToggleGroup tgCoach = new ToggleGroup();
        RadioButton cNone = new RadioButton("Nessuno");
        cNone.setUserData(CoachingScope.NONE);
        RadioButton cSession = new RadioButton("Sessione");
        cSession.setUserData(CoachingScope.FULL_SESSION);
        RadioButton cLaps = new RadioButton("Per ogni giro");
        cLaps.setUserData(CoachingScope.SELECTED_LAPS);
        cSession.setSelected(true);
        cNone.setToggleGroup(tgCoach); cSession.setToggleGroup(tgCoach); cLaps.setToggleGroup(tgCoach);

        HBox coachRow = new HBox(10, cNone, cSession, cLaps);

        // Setup
        CheckBox cbSetup = new CheckBox("Includi Analisi Setup");
        cbSetup.setSelected(true);

        ToggleGroup tgSetup = new ToggleGroup();
        RadioButton sSession = new RadioButton("Sessione");
        sSession.setUserData(SetupScope.FULL_SESSION);
        RadioButton sLaps = new RadioButton("Per giro");
        sLaps.setUserData(SetupScope.SELECTED_LAPS);
        sSession.setSelected(true);
        sSession.setToggleGroup(tgSetup); sLaps.setToggleGroup(tgSetup);

        HBox setupRow = new HBox(10, sSession, sLaps);
        setupRow.disableProperty().bind(cbSetup.selectedProperty().not());
        setupRow.setPadding(new Insets(0, 0, 0, 20));

        VBox boxCoach = card(lblCoach, subCoach, coachRow, new Separator(), cbSetup, setupRow);

        // =================================================================================
        // CARD 3: CONTENUTI DATI
        // =================================================================================
        Label lblContent = header("3. Contenuti del Report");

        CheckBox cbCharts = new CheckBox("Grafici Telemetria (Speed, G-G...)");
        cbCharts.setSelected(true); cbCharts.setDisable(true);

        CheckBox cbDelta = new CheckBox("Grafico Delta Time (vs Best)");
        cbDelta.setSelected(true);

        CheckBox cbPrimary = new CheckBox("Tabella Canali Primari");
        cbPrimary.setSelected(true);

        CheckBox cbSecondary = new CheckBox("Tabella Canali Secondari");
        cbSecondary.setSelected(true);

        CheckBox cbCircuit = new CheckBox("Mappa Circuito & Dati");
        cbCircuit.setSelected(true);

        CheckBox cbNotes = new CheckBox("Includi Note Personali");
        cbNotes.setSelected(true);
        cbNotes.disableProperty().bind(cbCircuit.selectedProperty().not());

        VBox colA = new VBox(8, cbCharts, cbDelta, cbCircuit, cbNotes);
        VBox colB = new VBox(8, cbPrimary, cbSecondary);
        HBox contentGrid = new HBox(20, colA, colB);

        VBox boxContent = card(lblContent, contentGrid);

        // =================================================================================
        // AZIONE FINALE
        // =================================================================================
        Button btnExport = new Button("Genera PDF");
        btnExport.getStyleClass().add("button-primary");
        btnExport.setStyle("-fx-font-size: 14px; -fx-padding: 10 40; -fx-font-weight: bold;");

        btnExport.setOnAction(e -> {
            try {
                LapSelection lapSel = (LapSelection) tg.getSelectedToggle().getUserData();
                CoachingScope cScope = (CoachingScope) tgCoach.getSelectedToggle().getUserData();
                SetupScope sScope = (SetupScope) tgSetup.getSelectedToggle().getUserData();

                // Recupera la lista multipla se selezionata
                List<Integer> selectedLaps = new ArrayList<>();
                if (lapSel == LapSelection.SELECTED) {
                    selectedLaps.addAll(listLaps.getSelectionModel().getSelectedItems());
                    if (selectedLaps.isEmpty()) {
                        new Alert(Alert.AlertType.WARNING, "Seleziona almeno un giro dalla lista.").showAndWait();
                        return;
                    }
                }

                Integer singleLap = (lapSel == LapSelection.SINGLE) ? comboSingle.getValue() : null;

                ExportOptions opts = new ExportOptions(
                        lapSel, selectedLaps, singleLap,
                        cbCircuit.isSelected(), cbNotes.isSelected(),
                        cScope,
                        cbSetup.isSelected(), sScope,
                        cbPrimary.isSelected(), cbSecondary.isSelected(),
                        cbDelta.isSelected(),
                        null
                );

                if (onConfirm != null) onConfirm.accept(opts);

                PdfExporter exporter = new PdfExporterPdfBox();
                exporter.export(stage, data, opts);
                stage.close();

            } catch (Exception ex) {
                ex.printStackTrace();
                new Alert(Alert.AlertType.ERROR, "Errore export: " + ex.getMessage()).showAndWait();
            }
        });

        // LAYOUT
        GridPane grid = new GridPane();
        grid.setHgap(15); grid.setVgap(15);
        grid.setPadding(new Insets(20));

        VBox leftCol = new VBox(15, boxLaps);
        VBox rightCol = new VBox(15, boxCoach, boxContent); // Coach sopra content

        grid.add(leftCol, 0, 0);
        grid.add(rightCol, 1, 0);

        ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(50);
        grid.getColumnConstraints().addAll(cc, cc);

        BorderPane root = new BorderPane(grid);
        HBox bottom = new HBox(btnExport);
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(15));
        root.setBottom(new VBox(new Separator(), bottom));

        Scene scene = new Scene(root, 900, 700); // Un po' più alta per la lista
        ThemeManager.applyTheme(scene, UiSettings.get().getCurrentTheme());

        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private static Label header(String txt) {
        Label l = new Label(txt);
        l.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: -fx-accent;");
        return l;
    }

    private static VBox card(javafx.scene.Node... nodes) {
        VBox v = new VBox(10, nodes);
        v.setPadding(new Insets(15));
        v.setStyle("-fx-background-color: -fx-control-inner-background; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);");
        return v;
    }
}