package org.simulator.ui.analysis_view;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.simulator.canale.Lap;
import org.simulator.gemini.GeminiContextBuilder;
import org.simulator.gemini.GeminiService;
import org.simulator.setup.setup_advisor.SetupAdvisor;
import org.simulator.tracks.TrackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EngineerChatPane {

    // Struttura principale
    private final SplitPane splitPane = new SplitPane();

    // UI Componenti Report (Parte Superiore)
    private final VBox reportContent = new VBox(8);
    private final ScrollPane reportScroll = new ScrollPane(reportContent);
    private final TitledPane mainReportPane;

    // UI Componenti Chat (Parte Inferiore - AI)
    private final ListView<ChatMessage> chatList = new ListView<>();
    private final TextField chatInput = new TextField();
    private final Button sendButton = new Button("Invia");
    private final ProgressIndicator aiSpinner = new ProgressIndicator();
    private final TitledPane aiBetaPane;

    // Dati e Stato
    private String currentTechnicalContext = "";
    private boolean isCoolingDown = false;
    private TrackInfo currentTrackInfo;

    public EngineerChatPane() {
        // --- 1. SEZIONE REPORT ---
        reportScroll.setFitToWidth(true);
        reportScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        reportScroll.getStyleClass().add("analysis-scroll-pane");
        reportContent.setPadding(new Insets(15));

        mainReportPane = new TitledPane("RAPPORTO INGEGNERE (Dati Ufficiali)", reportScroll);
        mainReportPane.setCollapsible(false);
        mainReportPane.setMaxHeight(Double.MAX_VALUE);
        mainReportPane.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // --- 2. SEZIONE AI ---
        chatList.setCellFactory(lv -> new ChatCell());
        chatList.setPlaceholder(new Label("L'Assistente AI è online."));
        chatList.getStyleClass().add("chat-list-view");

        // Disabilita policy orizzontale
        chatList.setStyle("-fx-hbar-policy: never;");

        HBox.setHgrow(chatList, Priority.ALWAYS);

        // Messaggio Placeholder
        chatList.getItems().add(new ChatMessage("Ciao! 👋 Analisi telemetrica pronta.", false));

        aiSpinner.setMaxSize(16, 16);
        aiSpinner.setVisible(false);

        chatInput.setPromptText("Scrivi qui... (Consiglio: fai una domanda alla volta)");
        chatInput.getStyleClass().add("chat-input");
        HBox.setHgrow(chatInput, Priority.ALWAYS);

        sendButton.getStyleClass().add("button-primary");
        sendButton.setOnAction(e -> sendMessage());
        chatInput.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) sendMessage(); });

        HBox inputBox = new HBox(8, chatInput, sendButton, aiSpinner);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.setPadding(new Insets(5, 0, 0, 0));

        VBox chatContainer = new VBox(5, chatList, inputBox);
        chatContainer.setPadding(new Insets(10));
        VBox.setVgrow(chatList, Priority.ALWAYS);

        aiBetaPane = new TitledPane("ASSISTENTE AI (BETA)", chatContainer);
        aiBetaPane.setCollapsible(false);
        aiBetaPane.setMaxHeight(Double.MAX_VALUE);
        aiBetaPane.setStyle("-fx-font-size: 12px;");

        // --- 3. ASSEMBLAGGIO ---
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(mainReportPane, aiBetaPane);
        splitPane.setDividerPositions(0.65);
    }

    public Node getRoot() { return splitPane; }

    public void setTrackInfo(TrackInfo t) {
        this.currentTrackInfo = t;
    }

    public void setContextMode(boolean isSingleLap) {
        chatList.getItems().clear();
        String welcomeMsg;
        if (isSingleLap) {
            welcomeMsg = "Ciao! 👋 Qui analizziamo il SINGOLO GIRO.\n\n" +
                    "Chiedimi: \"Dove ho sbagliato in curva 1?\" o \"Com'erano le temperature in questo giro?\"";
        } else {
            welcomeMsg = "Ciao! 👋 Qui analizziamo il PASSO GARA e la COSTANZA.\n\n" +
                    "Chiedimi: \"Come sto gestendo le gomme sulla lunga distanza?\" o \"Sono abbastanza costante?\"";
        }
        chatList.getItems().add(new ChatMessage(welcomeMsg, false));
    }

    public void loadLap(Lap lap, List<Lap> session) {
        showLoading();
        this.currentTechnicalContext = GeminiContextBuilder.buildLapContext(lap, currentTrackInfo);
        CompletableFuture.supplyAsync(() -> {
            var style = SetupAdvisor.analyzeStyle(session);
            var recs = SetupAdvisor.forLap(lap, style);
            List<String> notes = org.simulator.coach.Coach.generateNotes(lap);
            return new AnalysisResult(recs, notes);
        }).thenAcceptAsync(this::fillReportUI, Platform::runLater);
    }

    public void loadSession(List<Lap> laps) {
        showLoading();
        this.currentTechnicalContext = GeminiContextBuilder.buildSessionContext(laps, currentTrackInfo);
        CompletableFuture.supplyAsync(() -> {
            var style = SetupAdvisor.analyzeStyle(laps);
            var recs = SetupAdvisor.forSession(laps, style);
            List<String> notes = org.simulator.coach.CoachSession.generateSessionNotes(laps);
            return new AnalysisResult(recs, notes);
        }).thenAcceptAsync(this::fillReportUI, Platform::runLater);
    }

    private void showLoading() {
        reportContent.getChildren().clear();
        Label l = new Label("Analisi telemetrica in corso...");
        l.setFont(Font.font("System", FontWeight.BOLD, 12));
        reportContent.getChildren().add(l);
    }

    private void fillReportUI(AnalysisResult res) {
        reportContent.getChildren().clear();

        Label coachHeader = new Label("🏎️ NOTE DI GUIDA");
        coachHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #e24a4a; -fx-font-size: 13px;");
        reportContent.getChildren().add(coachHeader);

        if (res.coachNotes == null || res.coachNotes.isEmpty()) {
            reportContent.getChildren().add(createAdviceRow(SetupAdvisor.Severity.LOW, "Guida molto pulita. Ottimo lavoro!"));
        } else {
            for (String note : res.coachNotes) {
                SetupAdvisor.Severity sev = SetupAdvisor.Severity.MEDIUM;
                if (note.contains("!")) sev = SetupAdvisor.Severity.HIGH;
                String clean = note.replaceAll("\\[.*?\\]", "").replace("Coach:", "").replace("Sessione:", "").trim();
                reportContent.getChildren().add(createAdviceRow(sev, clean));
            }
        }

        Separator sep = new Separator();
        sep.setPadding(new Insets(15, 0, 5, 0));
        reportContent.getChildren().add(sep);

        Label setupHeader = new Label("🔧 CONSIGLI SETUP");
        setupHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #4a90e2; -fx-font-size: 13px;");
        reportContent.getChildren().add(setupHeader);

        if (res.recs == null || res.recs.isEmpty()) {
            reportContent.getChildren().add(createAdviceRow(SetupAdvisor.Severity.LOW, "Setup bilanciato. Nessuna modifica urgente richiesta."));
        } else {
            for (SetupAdvisor.Recommendation r : res.recs) {
                reportContent.getChildren().add(createAdviceRow(r.sev(), r.area() + ": " + r.message()));
            }
        }
    }

    private HBox createAdviceRow(SetupAdvisor.Severity sev, String text) {
        Color c = switch (sev) {
            case HIGH -> Color.TOMATO;
            case MEDIUM -> Color.ORANGE;
            case LOW -> Color.LIMEGREEN;
        };

        Circle dot = new Circle(4, c);
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setMaxWidth(600);
        lbl.setStyle("-fx-font-size: 13px;");

        HBox row = new HBox(12, dot, lbl);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("advice-card");

        return row;
    }

    private void sendMessage() {
        String text = chatInput.getText().trim();
        if (text.isEmpty() || isCoolingDown) return;

        chatList.getItems().add(new ChatMessage(text, true));
        chatInput.clear();
        chatList.scrollTo(chatList.getItems().size() - 1);

        isCoolingDown = true;
        chatInput.setDisable(true);
        sendButton.setDisable(true);
        sendButton.setText("Attendi...");
        aiSpinner.setVisible(true);

        CompletableFuture.supplyAsync(() -> GeminiService.chat(text, currentTechnicalContext))
                .thenAcceptAsync(response -> {
                    chatList.getItems().add(new ChatMessage(response, false));
                    chatList.scrollTo(chatList.getItems().size() - 1);
                    aiSpinner.setVisible(false);
                }, Platform::runLater);

        new Thread(() -> {
            try { Thread.sleep(10000); } catch (InterruptedException ignored) {}
            Platform.runLater(() -> {
                isCoolingDown = false;
                chatInput.setDisable(false);
                sendButton.setDisable(false);
                sendButton.setText("Invia");
                chatInput.requestFocus();
            });
        }).start();
    }

    private record AnalysisResult(List<SetupAdvisor.Recommendation> recs, List<String> coachNotes) {}
    private record ChatMessage(String text, boolean isUser) {}

    // --- CLASSE CHAT CELL (Versione CORRETTA: MaxWidth + FillWidth=False) ---
    private static class ChatCell extends ListCell<ChatMessage> {
        private final VBox box = new VBox();
        private final Label lbl = new Label();

        public ChatCell() {
            // 1. QUESTO È FONDAMENTALE: Impedisce al VBox di stirare il contenuto.
            // Se il testo è corto, il box resta corto.
            box.setFillWidth(false);

            // 2. Configurazione Label
            lbl.setWrapText(true);
            lbl.setPadding(new Insets(10, 14, 10, 14));

            box.getChildren().add(lbl);
            setGraphic(box);

            // Stile base (trasparente)
            setStyle("-fx-padding: 5; -fx-background-color: transparent;");

            // Il contenitore esterno occupa tutta la larghezza per poter allineare DX/SX
            box.setMaxWidth(Double.MAX_VALUE);

            // 3. BINDING CORRETTO:
            // Usiamo maxWidthProperty (non prefWidth!).
            // Questo dice: "NON superare il 75% della lista".
            // Se il testo è più corto del 75%, grazie a fillWidth(false), resta piccolo.
            listViewProperty().addListener((obs, oldVal, currentListView) -> {
                if (currentListView != null) {
                    lbl.maxWidthProperty().bind(
                            Bindings.min(
                                    currentListView.widthProperty().multiply(0.75), // Limite dinamico
                                    600.0                                           // Limite fisso massimo
                            )
                    );
                }
            });
        }

        @Override
        protected void updateItem(ChatMessage msg, boolean empty) {
            super.updateItem(msg, empty);

            if (empty || msg == null) {
                setGraphic(null);
                return;
            }

            lbl.setText(msg.text);

            // Reset stili (i colori sono gestiti dal CSS)
            lbl.getStyleClass().removeAll("chat-bubble-user", "chat-bubble-ai");

            if (msg.isUser) {
                box.setAlignment(Pos.CENTER_RIGHT);
                lbl.getStyleClass().add("chat-bubble-user");
            } else {
                box.setAlignment(Pos.CENTER_LEFT);
                lbl.getStyleClass().add("chat-bubble-ai");
            }

            setGraphic(box);
        }
    }
}