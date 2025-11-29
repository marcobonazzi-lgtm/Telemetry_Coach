package org.simulator.ui.compare_sessions_view;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.simulator.analisi_base.lap_analysis.LapAnalysis;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.ui.ChartManager;
import org.simulator.ui.ChartPane;
import org.simulator.ui.DataController;
import org.simulator.ui.asix_pack.AxisChoice;
import org.simulator.ui.asix_pack.AxisPicker;
import org.simulator.ui.analysis_view.LapForceStatsAggregator;
import org.simulator.widget.UiWidgets;
import org.simulator.ui.compare_sessions_view.CompareCoach.CornerFinding;
import org.simulator.ui.time_line_view.Track.AC.AcSectionsLoader;
import org.simulator.ui.time_line_view.Track.Curve.CurveSegment;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Vista di Confronto Sessioni.
 */
public class CompareSessionsView {

    private final DataController baseData;
    private final DataController cmpData = new DataController();
    private Lap leftLap;
    private Lap rightLap;

    private final BorderPane root = new BorderPane();
    private final ChartManager charts = new ChartManager();

    private final ComboBox<Integer> leftLapSelector = new ComboBox<>();
    private final ComboBox<Integer> rightLapSelector = new ComboBox<>();
    private final ComboBox<ChartPane.PlotType> plotTypeSelector = new ComboBox<>();

    private final LineChart<Number,Number> chartA = charts.buildChart("Sessione attuale", "X", "Y");
    private final LineChart<Number,Number> chartB = charts.buildChart("Sessione comparata", "X", "Y");
    private final LineChart<Number,Number> chartC = charts.buildChart("Confronto (sovrapposto)", "X", "Y");

    private final ScrollPane rightPane = new ScrollPane();
    private final VBox rightContentBox = new VBox(14);

    private Stage stageForChooser;

    // Cache tracciato
    private String cachedTrackName = null;
    private List<CurveSegment> cachedCurves = null;

    public CompareSessionsView(DataController baseData) {
        this.baseData = baseData;

        chartB.getProperties().put("palette", "cmp");
        chartC.getProperties().put("compareMode", "overlay");
        chartA.setAnimated(false); chartB.setAnimated(false); chartC.setAnimated(false);

        buildUI();
        wireEvents();
        refreshLeftLapSelector();

        updateViewChain();
    }

    public Parent getRoot() { return root; }
    public void setStage(Stage stage) { this.stageForChooser = stage; }

    public void onBaseDataChanged() {
        cachedTrackName = null; // Resetta cache se cambia file base
        refreshLeftLapSelector();
        updateViewChain();
    }

    private void buildUI() {
        Button loadRightBtn = new Button("Confronta nuovo CSV…");
        // RIMOSSO STYLE HARDCODED
        loadRightBtn.getStyleClass().add("action-button");
        loadRightBtn.setOnAction(e -> chooseAndLoadRightCsv());

        HBox selectors = new HBox(12,
                new Label("Giro SX"), leftLapSelector,
                loadRightBtn,
                new Label("Giro DX"), rightLapSelector,
                new Label("Grafico"), plotTypeSelector
        );
        selectors.setAlignment(Pos.CENTER_LEFT);
        selectors.setPadding(new Insets(10));

        plotTypeSelector.getItems().setAll(ChartPane.PlotType.values());
        plotTypeSelector.getSelectionModel().select(ChartPane.PlotType.SPEED_DIST);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10));
        grid.add(chartA, 0, 0);
        grid.add(chartB, 1, 0);
        grid.add(chartC, 0, 1);
        GridPane.setColumnSpan(chartC, 2);

        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(50);
        grid.getColumnConstraints().setAll(c1, c2);

        rightPane.setFitToWidth(true);
        rightPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightPane.setContent(rightContentBox);
        rightPane.setPrefWidth(560);
        rightPane.setMinWidth(420);
        // Assegna classe CSS per sfondo trasparente/adattivo
        rightPane.getStyleClass().add("analysis-scroll-pane");

        SplitPane split = new SplitPane();
        split.setDividerPositions(0.62);
        split.getItems().addAll(grid, rightPane);

        root.setTop(selectors);
        root.setCenter(split);
        BorderPane.setMargin(split, new Insets(0,10,10,10));
    }

    private void wireEvents() {
        configureLapSelector(leftLapSelector, baseData);
        configureLapSelector(rightLapSelector, cmpData);

        leftLapSelector.valueProperty().addListener((o, ov, nv) -> {
            this.leftLap = findLap(baseData.getLaps(), nv);
            updateViewChain();
        });
        rightLapSelector.valueProperty().addListener((o, ov, nv) -> {
            this.rightLap = findLap(cmpData.getLaps(), nv);
            updateViewChain();
        });
        plotTypeSelector.valueProperty().addListener((o, ov, nv) -> updateViewChain());
    }

    private void configureLapSelector(ComboBox<Integer> combo, DataController dc) {
        combo.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Integer idx, boolean empty) {
                super.updateItem(idx, empty);
                setText((empty || idx==null) ? null : labelForLap(dc.getLaps(), idx));
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Integer idx, boolean empty) {
                super.updateItem(idx, empty);
                setText((empty || idx==null) ? "" : labelForLap(dc.getLaps(), idx));
            }
        });
    }

    private String labelForLap(List<Lap> laps, int idx){
        Lap l = findLap(laps, idx);
        return idx + " (" + (l==null ? "n/d" : l.validityStatus(laps)) + ")";
    }

    private void updateViewChain() {
        if (leftLap == null) {
            var laps = baseData.getLaps();
            if (laps != null && !laps.isEmpty()) leftLap = laps.get(0);
        }

        Lap effectiveLeft = leftLap;
        Lap effectiveRight = rightLap;
        AxisChoice axis = AxisPicker.pick(effectiveLeft != null ? effectiveLeft : effectiveRight);
        ChartPane.PlotType pt = plotTypeSelector.getValue();

        rightContentBox.getChildren().clear();
        rightContentBox.getChildren().add(new Label("Calcolo analisi in corso..."));

        Platform.runLater(() -> {
            charts.renderChart(chartA, pt, effectiveLeft, null, axis);
            Platform.runLater(() -> {
                charts.renderChart(chartB, pt, effectiveRight, null, axis);
                Platform.runLater(() -> {
                    charts.renderChart(chartC, pt, effectiveLeft, effectiveRight, axis);

                    // Passiamo al calcolo statistiche (Curve incluse)
                    buildAndShowAnalysis(effectiveLeft, effectiveRight);
                });
            });
        });
    }

    private void buildAndShowAnalysis(Lap sx, Lap dx) {
        // 1. Risoluzione Tracciato (Cached)
        String currentTrack = resolveTrackName(sx, baseData);
        if (!Objects.equals(currentTrack, cachedTrackName)) {
            cachedTrackName = currentTrack;
            cachedCurves = loadTrackCurves(currentTrack);
        }

        CompletableFuture.supplyAsync(() -> {
            return new CompareCoach().compare(sx, dx, cachedCurves);
        }).thenAcceptAsync(result -> {
            Node content = buildAnalysisContent(sx, dx, result);
            rightContentBox.getChildren().setAll(content);
        }, Platform::runLater);
    }

    private Node buildAnalysisContent(Lap sx, Lap dx, CompareCoach.Result r) {
        VBox box = new VBox(14);
        box.setPadding(new Insets(12));

        // USA CLASSI CSS INVECE DI COLORI HEX
        if (sx != null)  box.getChildren().add(sectionPane("Statistiche – Sessione attuale", buildStatsPane(sx, baseData), "session-a-pane"));
        if (dx != null) box.getChildren().add(sectionPane("Statistiche – Sessione comparata", buildStatsPane(dx, cmpData), "session-b-pane"));

        if (sx != null && dx != null) {
            box.getChildren().add(sectionPane("Coach Comparativo", buildCoachPane(r), "coach-pane"));
        }
        return box;
    }

    private Node buildStatsPane(Lap lap, DataController dc) {
        Map<String, Double> merged = new LinkedHashMap<>(LapAnalysis.basicStats(lap));
        try { merged.putAll(LapForceStatsAggregator.build(lap)); } catch (Exception ignored) {}
        Node accNode = UiWidgets.buildStatsAccordion(merged);
        if (accNode instanceof Accordion acc) {
            String status = lap.validityStatus(dc.getLaps());
            if (!acc.getPanes().isEmpty()) {
                TitledPane first = acc.getPanes().get(0);
                Node content = first.getContent();
                first.setContent(new VBox(4, new Label("Stato: " + status), content));
            }
        }
        return accNode;
    }

    private Node buildCoachPane(CompareCoach.Result r) {
        DecimalFormat df = new DecimalFormat("0.000");

        // Box Narrativo
        boolean isWarning = r.narrativeSummary.startsWith("⚠");

        Label narrative = new Label(r.narrativeSummary);
        narrative.setWrapText(true);
        // Usa Classi CSS
        narrative.getStyleClass().add("narrative-box");
        if (isWarning) narrative.getStyleClass().add("narrative-warning");

        VBox v = new VBox(10, narrative);

        if (!isWarning) {
            // Liste miglioramenti/peggioramenti - Passiamo la classe CSS
            Node improvements = listBlock("Miglioramenti (DX meglio di SX)", r.improvements, 5, df, "improvement-list");
            Node regressions = listBlock("Peggioramenti (DX peggio di SX)", r.regressions, 5, df, "regression-list");

            // Tabelle dati
            Node table = diffGrid("Tabella Differenze Chiave", r.topTable, df);
            Node setupNode = r.setupDiffs.isEmpty() ? null : diffGrid("Differenze Setup", r.setupDiffs, df);

            if (improvements != null) v.getChildren().add(improvements);
            if (regressions != null) v.getChildren().add(regressions);
            v.getChildren().add(table);
            if (setupNode != null) v.getChildren().add(setupNode);

            // Warning circuiti diversi
            if (r.warning != null) {
                Label w = new Label(r.warning);
                w.getStyleClass().add("coach-warning-label");
                v.getChildren().add(w);
            }

            // --- TABELLA CURVE CON LEGENDA ---
            if (!r.cornerFindings.isEmpty()) {
                Label legend = new Label(
                        "LEGENDA: I valori indicano (Comparata - Attuale).\n" +
                                "• Verde (Negativo): La Comparata (DX) è più veloce/migliore.\n" +
                                "• Rosso (Positivo): La Comparata (DX) è più lenta/peggiore."
                );
                legend.getStyleClass().add("legend-label");

                TableView<CornerFinding> tbl = buildCornerTable(r.cornerFindings);

                VBox curveBox = new VBox(4, legend, tbl);
                TitledPane perCurva = new TitledPane("Analisi Dettaglio Curve", curveBox);
                perCurva.setExpanded(false);
                v.getChildren().add(perCurva);
            }
        }

        return v;
    }

    // --- Helpers UI Standard ---
    private Node sectionPane(String title, Node content, String cssClass) {
        VBox wrap = new VBox(8, bold(title), content);
        wrap.setPadding(new Insets(10));
        // Assegna la classe CSS passata (es. session-a-pane)
        wrap.getStyleClass().addAll("section-pane", cssClass);
        return wrap;
    }

    private Node listBlock(String title, List<CompareCoach.Item> items, int max, DecimalFormat df, String cssClass) {
        if (items.isEmpty()) return null;
        VBox rows = new VBox(4);
        items.stream().limit(max).forEach(it -> {
            String sign = it.delta() > 0 ? "+" : "";
            rows.getChildren().add(new Label("• " + it.name() + ": " + sign + df.format(it.delta())));
        });
        TitledPane tp = new TitledPane(title, rows);
        tp.setExpanded(true);
        // Assegna classe CSS
        tp.getStyleClass().add(cssClass);
        return tp;
    }

    private Node diffGrid(String title, List<CompareCoach.Item> items, DecimalFormat df) {
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(4);
        int r = 0;
        addRow(grid, r++, bold("Parametro"), bold("SX"), bold("DX"), bold("Diff."));
        for (var it : items) {
            Label deltaLbl = new Label(fmtSigned(it.delta(), df));
            // Colorazione testo tramite CSS
            if (it.delta() > 0) deltaLbl.getStyleClass().add("delta-positive"); // Contestuale: potrebbe essere good o bad
            else if (it.delta() < 0) deltaLbl.getStyleClass().add("delta-negative");

            addRow(grid, r++, new Label(it.name()), new Label(df.format(it.sx())), new Label(df.format(it.dx())), deltaLbl);
        }
        return new TitledPane(title, grid);
    }

    private void addRow(GridPane g, int row, Node... nodes) { for(int i=0; i<nodes.length; i++) g.add(nodes[i], i, row); }
    private static Label bold(String s) { Label l = new Label(s); l.setStyle("-fx-font-weight: bold;"); return l; }
    private static String fmtSigned(double v, DecimalFormat df) { return (v>=0?"+":"") + df.format(v); }

    private TableView<CornerFinding> buildCornerTable(List<CornerFinding> findings) {
        TableView<CornerFinding> table = new TableView<>();

        // 1. Colonna Numero (#)
        TableColumn<CornerFinding, Number> colNum = new TableColumn<>("#");
        colNum.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().cornerId()));
        colNum.setPrefWidth(35);
        colNum.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        // 2. Colonna Nome
        TableColumn<CornerFinding, String> colName = new TableColumn<>("Curva");
        colName.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().cornerName()));
        colName.setPrefWidth(110);

        // 3. Colonna Delta Tempo
        TableColumn<CornerFinding, String> colDt = new TableColumn<>("Δ Tempo");
        colDt.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(String.format("%+.3f s", d.getValue().deltaTime())));
        colDt.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("delta-good", "delta-bad", "delta-neutral"); // Pulisci vecchie classi

                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    CornerFinding row = getTableView().getItems().get(getIndex());
                    double val = row.deltaTime();
                    // Logica CSS: Good = Verde, Bad = Rosso
                    if (val <= -0.005) getStyleClass().add("delta-good");
                    else if (val >= 0.005) getStyleClass().add("delta-bad");
                    else getStyleClass().add("delta-neutral");

                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight:bold;");
                }
            }
        });
        colDt.setPrefWidth(80);

        // 4. Colonna Delta Vmin
        TableColumn<CornerFinding, String> colDv = new TableColumn<>("Δ Vmin");
        colDv.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(String.format("%+.0f km/h", d.getValue().deltaSpeedMin())));
        colDv.setStyle("-fx-alignment: CENTER-RIGHT;");
        colDv.setPrefWidth(70);

        // 5. Colonna Consiglio
        TableColumn<CornerFinding, String> colAdv = new TableColumn<>("Consiglio");
        colAdv.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().advice()));
        colAdv.setMinWidth(350);
        colAdv.setPrefWidth(450);

        table.getColumns().addAll(colNum, colName, colDt, colDv, colAdv);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.getItems().setAll(findings);
        table.setPrefHeight(280);

        return table;
    }

    private String resolveTrackName(Lap l, DataController dc) {
        if (l == null) return null;
        if (dc.getCsvPath() != null) return AcSectionsLoader.normalizeVenue(dc.getCsvPath().getFileName().toString());
        return null;
    }
    private List<CurveSegment> loadTrackCurves(String trackName) {
        if (trackName == null) return null;
        var acSections = AcSectionsLoader.tryLoadFromVenue(trackName);
        if (!acSections.isEmpty()) {
            List<CurveSegment> out = new ArrayList<>();
            int idx = 1;
            for (var sec : acSections) {
                if (sec.isCorner()) {
                    out.add(new CurveSegment(idx++, sec.name, sec.inFrac, sec.outFrac,
                            0,0,0, 0,0,0, 0,0,0,0,0,0, CurveSegment.Dir.LEFT, 0));
                }
            }
            return out;
        }
        return null;
    }
    private void chooseAndLoadRightCsv() {
        if (stageForChooser == null) return;
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Telemetria CSV", "*.csv"));
        File f = fc.showOpenDialog(stageForChooser);
        if (f != null) {
            root.setCursor(javafx.scene.Cursor.WAIT);
            CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Channel> mapping = new HashMap<>();
                    cmpData.load(f.toPath(), mapping);
                    Platform.runLater(() -> { refreshRightLapSelector(); updateViewChain(); root.setCursor(javafx.scene.Cursor.DEFAULT); });
                } catch (Exception e) {
                    Platform.runLater(() -> { root.setCursor(javafx.scene.Cursor.DEFAULT); new Alert(Alert.AlertType.ERROR, "Errore CSV: " + e.getMessage()).showAndWait(); });
                }
            });
        }
    }
    private void refreshLeftLapSelector() {
        List<Lap> laps = baseData.getLaps();
        leftLapSelector.getItems().setAll(laps.stream().map(l -> l.index).toList());
        if (!laps.isEmpty()) leftLapSelector.getSelectionModel().select(0);
    }
    private void refreshRightLapSelector() {
        List<Lap> laps = cmpData.getLaps();
        rightLapSelector.getItems().setAll(laps.stream().map(l -> l.index).toList());
        if (!laps.isEmpty()) rightLapSelector.getSelectionModel().select(0);
    }
    private Lap findLap(List<Lap> laps, Integer index) {
        if (laps == null || index == null) return null;
        return laps.stream().filter(l -> Objects.equals(l.index, index)).findFirst().orElse(null);
    }
}