package org.simulator.ui.compare_sessions_view;

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
import org.simulator.analisi_base.force_stats.ForceStats;
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

import java.io.File;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.*;

public class CompareSessionsView {

    private final DataController baseData;                // Sessione attuale (SX)
    private final DataController cmpData = new DataController(); // Sessione comparata (DX)
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
    private Stage stageForChooser;

    public CompareSessionsView(DataController baseData) {
        this.baseData = baseData;
        buildUI();

        chartB.getProperties().put("palette", "cmp");
        chartC.getProperties().put("compareMode", "overlay");

        wireEvents();
        refreshLeftLapSelector();
        renderChartsAndStats();
    }

    public Parent getRoot() { return root; }
    public void setStage(Stage stage) { this.stageForChooser = stage; }

    public void onBaseDataChanged() {
        refreshLeftLapSelector();
        renderChartsAndStats();
    }

    public Lap getLeftLap()  { return leftLap;  }
    public Lap getRightLap() { return rightLap; }

    private void buildUI() {
        Button loadRightBtn = new Button("Confronta nuovo CSV…");
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
        rightPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        rightPane.setPrefWidth(560);
        rightPane.setMinWidth(420);

        SplitPane split = new SplitPane();
        split.setDividerPositions(0.62);
        split.getItems().addAll(grid, rightPane);

        root.setTop(selectors);
        root.setCenter(split);
        BorderPane.setMargin(split, new Insets(0,10,10,10));
    }

    private void wireEvents() {
        // Celle con stato giro (valido/non valido/non terminato)
        leftLapSelector.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Integer idx, boolean empty) {
                super.updateItem(idx, empty);
                if (empty || idx == null) { setText(null); return; }
                setText(labelForLap(baseData.getLaps(), idx));
            }
        });
        leftLapSelector.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Integer idx, boolean empty) {
                super.updateItem(idx, empty);
                setText((empty || idx==null) ? "" : labelForLap(baseData.getLaps(), idx));
            }
        });
        rightLapSelector.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Integer idx, boolean empty) {
                super.updateItem(idx, empty);
                if (empty || idx == null) { setText(null); return; }
                setText(labelForLap(cmpData.getLaps(), idx));
            }
        });
        rightLapSelector.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Integer idx, boolean empty) {
                super.updateItem(idx, empty);
                setText((empty || idx==null) ? "" : labelForLap(cmpData.getLaps(), idx));
            }
        });

        leftLapSelector.valueProperty().addListener((o, ov, nv) -> {
            this.leftLap = findLap(baseData.getLaps(), nv);
            renderChartsAndStats();
        });
        rightLapSelector.valueProperty().addListener((o, ov, nv) -> {
            this.rightLap = findLap(cmpData.getLaps(), nv);
            renderChartsAndStats();
        });
        plotTypeSelector.valueProperty().addListener((o, ov, nv) -> renderChartsAndStats());
    }

    private String labelForLap(List<Lap> laps, int idx){
        Lap l = findLap(laps, idx);
        String status = (l==null) ? "n/d" : l.validityStatus(laps);
        return idx + " (" + status + ")";
    }

    private void renderChartsAndStats() {
        if (leftLap == null) {
            var laps = baseData.getLaps();
            if (laps != null && !laps.isEmpty()) leftLap = laps.get(0);
        }
        AxisChoice axis = AxisPicker.pick(leftLap != null ? leftLap : rightLap);
        ChartPane.PlotType pt = plotTypeSelector.getValue();

        charts.renderChart(chartA, pt, leftLap, null,     axis);
        charts.renderChart(chartB, pt, rightLap, null,    axis);
        charts.renderChart(chartC, pt, leftLap, rightLap, axis);

        renderRightPane();
    }

    private void renderRightPane() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(12));

        bestCompleteLapOf(baseData.getLaps()).ifPresent(best -> {
            String txt = "Miglior giro sessione attuale: #" + best.index +
                    " — " + formatLapSeconds(best.lapTimeSafe());
            box.getChildren().add(sectionPane("Best Lap", labeledBox(txt, "-fx-background-color:#eef7ff;"), "#eef7ff"));
        });

        bestCompleteLapOf(cmpData.getLaps()).ifPresent(best -> {
            String txt = "Miglior giro sessione comparata: #" + best.index +
                    " — " + formatLapSeconds(best.lapTimeSafe());
            box.getChildren().add(sectionPane("Best Lap (DX)", labeledBox(txt, "-fx-background-color:#f6f0ff;"), "#f6f0ff"));
        });

        if (leftLap != null)  box.getChildren().add(sectionPane("Statistiche – Sessione attuale", buildStatsPane(leftLap), "#e9f6ff"));
        if (rightLap != null) box.getChildren().add(sectionPane("Statistiche – Sessione comparata", buildStatsPane(rightLap), "#f6f0ff"));

        if (leftLap != null && rightLap != null) {
            box.getChildren().add(sectionPane("Coach Comparativo", buildCoachPane(leftLap, rightLap), "#f9fff0"));
        }

        rightPane.setContent(box);
    }

    private Node buildStatsPane(Lap lap) {
        Map<String, Double> lapStats = LapAnalysis.basicStats(lap);

        ForceStats.SeatDistribution dist = ForceStats.distribution(lap);
        lapStats.put("Seat dist SX [%]",   dist.left  * 100.0);
        lapStats.put("Seat dist POST [%]", dist.rear  * 100.0);
        lapStats.put("Seat dist DX [%]",   dist.right * 100.0);

        Map<String, Double> forceAgg = LapForceStatsAggregator.build(lap);
        Map<String, Double> merged = new LinkedHashMap<>(lapStats);
        merged.putAll(forceAgg);

        Node accNode = UiWidgets.buildStatsAccordion(merged);

        String status = lap.validityStatus((lap == leftLap) ? baseData.getLaps() : cmpData.getLaps());
        if (accNode instanceof Accordion acc) {
            for (TitledPane tp : acc.getPanes()) {
                if ("Giro".equalsIgnoreCase(tp.getText())) {
                    Label statusLbl = new Label("Stato giro: " + status);
                    statusLbl.setStyle("-fx-font-style: italic; -fx-text-fill:#555; -fx-padding:0 0 6 0;");
                    Node content = tp.getContent();
                    VBox wrap = new VBox(4, statusLbl, content);
                    tp.setContent(wrap);
                    break;
                }
            }
        }
        return accNode;
    }

    private Node buildCoachPane(Lap sx, Lap dx) {
        CompareCoach.Result r = new CompareCoach().compare(sx, dx);
        DecimalFormat df = new DecimalFormat("0.000");

        String deltaStr = (Double.isNaN(r.deltaLapTime)) ? "n/d"
                : (r.deltaLapTime >= 0 ? "+" : "") + df.format(r.deltaLapTime) + " s";
        Label deltaLbl = new Label("Delta Lap Time (Comparata − Attuale): " + deltaStr);
        deltaLbl.setStyle(
                "-fx-font-weight: bold; -fx-background-color:#fff3cd; -fx-padding:6 8; " +
                        "-fx-border-color:#ffe58f; -fx-border-radius:6; -fx-background-radius:6;"
        );

        Node improvements = listBlock(
                "Miglioramenti principali", r.improvements, 6, df,
                "-fx-background-color:#e8ffe8; -fx-border-color:#9be29b;"
        );
        Node regressions = listBlock(
                "Peggioramenti principali", r.regressions, 6, df,
                "-fx-background-color:#ffecec; -fx-border-color:#f0a3a3;"
        );
        Node table = diffGrid("Differenze chiave (Attuale vs Comparata)", r.topTable, df);

        Node setupNode = r.setupDiffs.isEmpty()
                ? labeledBox("Dati setup non presenti nel CSV o non rilevati.",
                "-fx-background-color:#f5f5f5; -fx-border-color:#d9d9d9;")
                : diffGrid("Setup – Differenze individuate", r.setupDiffs, df);

        var full = r.cornerFindings;
        int LIMIT = 8;
        if (full.isEmpty()) {
            Label warn = new Label("⚠ Analisi per curva non disponibile: giri troppo diversi o dati insufficienti.");
            warn.setStyle("-fx-text-fill:#8a6d3b; -fx-background-color:#fcf8e3; -fx-padding:6 8; -fx-border-color:#faebcc; -fx-border-radius:6; -fx-background-radius:6;");
            return new VBox(10, deltaLbl, improvements, regressions, table, warn, setupNode);
        }

        var shown = new ArrayList<>(full.subList(0, Math.min(LIMIT, full.size())));
        TableView<CornerFinding> perTable = buildCornerTable(shown);
        for (TableColumn<?, ?> col : perTable.getColumns()) col.setSortable(false);
        perTable.setFixedCellSize(24);
        double header = 28;
        Runnable resize = () -> {
            int n = perTable.getItems().size();
            perTable.setPrefHeight(header + n * perTable.getFixedCellSize());
        };

        Button toggle = new Button(full.size() > LIMIT ? "Mostra tutte" : "Mostra meno");
        toggle.setOnAction(e -> {
            if (perTable.getItems().size() > LIMIT) {
                perTable.getItems().setAll(full.subList(0, Math.min(LIMIT, full.size())));
                toggle.setText("Mostra tutte");
            } else {
                perTable.getItems().setAll(full);
                toggle.setText("Mostra meno");
            }
            resize.run();
        });
        resize.run();

        TitledPane perCurva = new TitledPane("Analisi per curva", new VBox(6, perTable, toggle));
        perCurva.setExpanded(true);

        VBox v = new VBox(10, deltaLbl, improvements, regressions, table, perCurva, setupNode);
        v.setFillWidth(true);
        return v;
    }

    private Node sectionPane(String title, Node content, String bg) {
        VBox wrap = new VBox(8, new Label(title), content);
        wrap.setPadding(new Insets(10));
        wrap.setStyle("-fx-background-color:" + bg + "; -fx-border-color:#cfd8dc; -fx-background-radius:8; -fx-border-radius:8;");
        ((Label)wrap.getChildren().get(0)).setStyle("-fx-font-weight:bold; -fx-text-fill:#333; -fx-padding:2 0 6 0;");
        return wrap;
    }

    private Node listBlock(String title, List<CompareCoach.Item> items, int max,
                           DecimalFormat df, String style) {
        VBox rows = new VBox(4);
        int n = Math.min(max, items.size());
        for (int i = 0; i < n; i++) {
            var it = items.get(i);
            String sign = it.delta() > 0 ? "+" : "";
            Label l = new Label("• " + it.name() + ": " + fmt(it.sx(), df) + " → " + fmt(it.dx(), df) +
                    " (" + sign + df.format(it.delta()) + ")");
            rows.getChildren().add(l);
        }
        if (n == 0) rows.getChildren().add(new Label("— nessuna variazione significativa —"));

        Label hdr = new Label("Attuale vs Comparata");
        hdr.setStyle("-fx-font-weight: bold; -fx-padding: 2 4 6 4;");

        VBox content = new VBox(4, hdr, rows);

        TitledPane tp = new TitledPane(title, content);
        tp.setExpanded(true);
        tp.setStyle(style + " -fx-padding:6; -fx-border-width:1; -fx-background-radius:8; -fx-border-radius:8;");
        return tp;
    }

    private Node diffGrid(String title, List<CompareCoach.Item> items, DecimalFormat df) {
        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(4);
        int r = 0;
        addRow(grid, r++, bold("Metrica"), bold("Attuale"), bold("Comparata"), bold("Δ (C−A)"));
        for (CompareCoach.Item it : items) {
            addRow(grid, r++, new Label(it.name()),
                    new Label(fmt(it.sx(), df)),
                    new Label(fmt(it.dx(), df)),
                    new Label(fmtSigned(it.delta(), df)));
        }
        if (items.isEmpty())
            addRow(grid, r, new Label("— nessun dato —"), new Label(""), new Label(""), new Label(""));
        Label hdr = new Label("Attuale vs Comparata");
        hdr.setStyle("-fx-font-weight: bold; -fx-padding: 2 4 6 4;");
        VBox content = new VBox(4, hdr, grid);
        TitledPane tp = new TitledPane(title, content);
        tp.setExpanded(true);
        tp.setStyle("-fx-background-color:#ffffff; -fx-border-color:#d0d0d0; -fx-padding:6; " +
                "-fx-border-width:1; -fx-background-radius:8; -fx-border-radius:8;");
        return tp;
    }

    private static void addRow(GridPane g, int row, Node c1, Node c2, Node c3, Node c4) {
        g.add(c1, 0, row); g.add(c2, 1, row); g.add(c3, 2, row); g.add(c4, 3, row);
        if (g.getColumnConstraints().isEmpty()) {
            for (int i=0;i<4;i++) { ColumnConstraints c = new ColumnConstraints(); c.setPercentWidth(25); g.getColumnConstraints().add(c); }
        }
    }
    private static Label bold(String s) { Label l = new Label(s); l.setStyle("-fx-font-weight: bold;"); return l; }
    private static String fmt(double v, DecimalFormat df) { return (Double.isNaN(v) || Double.isInfinite(v)) ? "n/d" : df.format(v); }
    private static String fmtSigned(double v, DecimalFormat df) { return (Double.isNaN(v) || Double.isInfinite(v)) ? "n/d" : (v>=0?"+":"") + df.format(v); }

    private Node labeledBox(String text, String extraStyle) {
        Label lbl = new Label(text);
        VBox box = new VBox(lbl);
        box.setPadding(new Insets(8));
        box.setStyle((extraStyle == null ? "" : extraStyle) +
                " -fx-padding:8; -fx-background-radius:8; -fx-border-radius:8; -fx-border-width:1;");
        return box;
    }

    private void refreshLeftLapSelector() {
        List<Lap> laps = baseData.getLaps();
        leftLapSelector.getItems().setAll(laps.stream().map(l -> l.index).toList());
        Lap def = laps.stream().filter(l -> "valido".equals(l.validityStatus(laps))).findFirst()
                .orElse(laps.isEmpty() ? null : laps.get(0));
        this.leftLap = def;
        if (def != null) leftLapSelector.getSelectionModel().select(Integer.valueOf(def.index));
    }

    private void refreshRightLapSelector() {
        List<Lap> laps = cmpData.getLaps();
        rightLapSelector.getItems().setAll(laps.stream().map(l -> l.index).toList());
        Lap def = laps.stream().filter(l -> "valido".equals(l.validityStatus(laps))).findFirst()
                .orElse(laps.isEmpty() ? null : laps.get(0));
        this.rightLap = def;
        if (def != null) rightLapSelector.getSelectionModel().select(Integer.valueOf(def.index));
    }

    private Lap findLap(List<Lap> laps, Integer index) {
        if (laps == null || index == null) return null;
        return laps.stream().filter(l -> Objects.equals(l.index, index)).findFirst().orElse(null);
    }

    private void chooseAndLoadRightCsv() {
        if (stageForChooser == null) return;
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File f = fc.showOpenDialog(stageForChooser);
        if (f == null) return;
        if (!loadRightCsv(f.toPath())) return;
        refreshRightLapSelector();
        renderChartsAndStats();
    }

    private boolean loadRightCsv(Path p) {
        try {
            Map<String, Channel> mapping = new HashMap<>();
            cmpData.load(p, mapping);
            return true;
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Errore caricamento CSV (confronto): " + ex.getMessage()).showAndWait();
            return false;
        }
    }

    private TableView<CornerFinding> buildCornerTable(List<CornerFinding> findings) {
        TableView<CornerFinding> table = new TableView<>();

        TableColumn<CornerFinding, Number> colId = new TableColumn<>("Curva");
        colId.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().cornerId()));
        colId.setSortable(false);

        TableColumn<CornerFinding, Number> colDt = new TableColumn<>("Δt [s]");
        colDt.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().deltaTime()));
        colDt.setCellFactory(col -> new TableCell<CornerFinding, Number>() {
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null || Double.isNaN(v.doubleValue())) setText(null);
                else setText(String.format("%+,.3f", v.doubleValue()));
            }
        });
        colDt.setSortable(false);

        TableColumn<CornerFinding, String> colMsg = new TableColumn<>("Messaggio");
        colMsg.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().message()));
        colMsg.setSortable(false);

        table.getColumns().setAll(colId, colDt, colMsg);
        table.getItems().setAll(findings);
        table.setOnMouseClicked(evt -> {
            CornerFinding sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) System.out.println("Selected corner " + sel.cornerId());
        });
        return table;
    }

    private Optional<Lap> bestCompleteLapOf(List<Lap> laps){
        if (laps == null || laps.isEmpty()) return Optional.empty();
        List<Lap> valid = new ArrayList<>();
        for (Lap l : laps) if ("valido".equals(l.validityStatus(laps))) valid.add(l);
        return valid.stream().min(Comparator.comparingDouble(Lap::lapTimeSafe));
    }

    private static String formatLapSeconds(double sec){
        if (Double.isNaN(sec) || sec <= 0) return "n/d";
        int minutes = (int)Math.floor(sec / 60.0);
        double s = sec - minutes*60.0;
        return String.format("%d:%06.3f s", minutes, s);
    }
    private boolean isOutLap(Lap lap, List<Lap> sessionLaps) {
        if (lap == null) return false;
        String st = lap.validityStatus(sessionLaps);
        st = (st == null ? "" : st).toLowerCase(Locale.ROOT);
        boolean looksOut = lap.index == 1 || st.contains("out") || st.contains("box");
        return looksOut && !st.equals("valido");
    }

}
