package org.simulator.ui.analysis_view;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import org.simulator.gemini.GoogleTTSService;
import org.simulator.setup.setup_advisor.SetupAdvisor;
import org.simulator.tracks.TrackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Pannello di interazione con l'Ingegnere Virtuale (AI).
 * Integra la visualizzazione del report, la chat testuale e il feedback vocale (TTS).
 */
public class EngineerChatPane {

    // Memoria statica per mantenere la cronologia tra i cambi di vista
    private static final ObservableList<ChatMessage> GLOBAL_HISTORY = FXCollections.observableArrayList();

    public static void clearGlobalChat() {
        GLOBAL_HISTORY.clear();
        GLOBAL_HISTORY.add(new ChatMessage("Ciao! 👋 Analisi telemetrica pronta. Seleziona un giro.", false));
    }

    private final SplitPane splitPane = new SplitPane();
    private final VBox reportContent = new VBox(8);

    private final ListView<ChatMessage> chatList = new ListView<>();
    private final TextField chatInput = new TextField();
    private final Button sendButton = new Button("Invia");
    private final ToggleButton audioButton = new ToggleButton("🔇"); // Pulsante Mute/Unmute
    private final ProgressIndicator aiSpinner = new ProgressIndicator();

    private String currentTechnicalContext = "";
    private boolean isCoolingDown = false;
    private TrackInfo currentTrackInfo;

    public EngineerChatPane() {
        // Setup Report View (Parte Superiore)
        ScrollPane reportScroll = new ScrollPane(reportContent);
        reportScroll.setFitToWidth(true);
        reportScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        reportScroll.getStyleClass().add("analysis-scroll-pane");
        reportContent.setPadding(new Insets(15));

        TitledPane mainReportPane = new TitledPane("RAPPORTO INGEGNERE (Dati Ufficiali)", reportScroll);
        mainReportPane.setCollapsible(false);
        mainReportPane.setMaxHeight(Double.MAX_VALUE);
        mainReportPane.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Setup Chat View (Parte Inferiore)
        chatList.setCellFactory(lv -> new ChatCell());
        chatList.setPlaceholder(new Label("L'Assistente AI è online."));
        chatList.getStyleClass().add("chat-list-view");
        chatList.setStyle("-fx-hbar-policy: never;");
        HBox.setHgrow(chatList, Priority.ALWAYS);
        chatList.setItems(GLOBAL_HISTORY);

        if (GLOBAL_HISTORY.isEmpty()) {
            GLOBAL_HISTORY.add(new ChatMessage("Ciao! 👋 Analisi telemetrica pronta.", false));
        }

        aiSpinner.setMaxSize(16, 16);
        aiSpinner.setVisible(false);

        chatInput.getStyleClass().add("chat-input");
        HBox.setHgrow(chatInput, Priority.ALWAYS);
        sendButton.getStyleClass().add("button-primary");

        if (!GeminiService.isPremium()) {
            chatInput.setDisable(true);
            sendButton.setDisable(true);
            chatInput.setPromptText("🔒 Sblocca il Premium per chattare con l'Ingegnere");
        } else {
            chatInput.setDisable(false);
            sendButton.setDisable(false);
            chatInput.setPromptText("Scrivi qui... (Consiglio: fai una domanda alla volta)");
        }

        sendButton.setOnAction(e -> sendMessage());
        chatInput.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) sendMessage(); });

        setupAudioButton();

        HBox inputBox = new HBox(8, audioButton, chatInput, sendButton, aiSpinner);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.setPadding(new Insets(5, 0, 0, 0));

        VBox chatContainer = new VBox(5, chatList, inputBox);
        chatContainer.setPadding(new Insets(10));
        VBox.setVgrow(chatList, Priority.ALWAYS);

        TitledPane aiBetaPane = new TitledPane("ASSISTENTE AI (BETA)", chatContainer);
        aiBetaPane.setCollapsible(false);
        aiBetaPane.setMaxHeight(Double.MAX_VALUE);
        aiBetaPane.setStyle("-fx-font-size: 12px;");

        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(mainReportPane, aiBetaPane);
        splitPane.setDividerPositions(0.65);

        if (!GLOBAL_HISTORY.isEmpty()) {
            Platform.runLater(() -> chatList.scrollTo(GLOBAL_HISTORY.size() - 1));
        }
    }

    private void setupAudioButton() {
        audioButton.setPrefWidth(40);

        // Sincronizza lo stato UI con il servizio statico
        boolean isMuted = GoogleTTSService.isMuted();
        audioButton.setSelected(!isMuted);
        updateAudioIcon(!isMuted);

        audioButton.setOnAction(e -> {
            boolean audioOn = audioButton.isSelected();
            GoogleTTSService.setMuted(!audioOn);
            updateAudioIcon(audioOn);
        });
    }

    private void updateAudioIcon(boolean audioOn) {
        if (audioOn) {
            audioButton.setText("🔊");
            audioButton.setStyle("-fx-base: #4CAF50;"); // Verde attivo
        } else {
            audioButton.setText("🔇");
            audioButton.setStyle(""); // Default
        }
    }

    public Node getRoot() { return splitPane; }

    public void setTrackInfo(TrackInfo t) {
        this.currentTrackInfo = t;
    }

    public void setContextMode(boolean isSingleLap) {
        // Metodo mantenuto per compatibilità, non resetta la chat
    }

    public void loadLap(Lap lap, List<Lap> session) {
        showLoadingReportOnly();
        this.currentTechnicalContext = GeminiContextBuilder.buildLapContext(lap, currentTrackInfo);

        // Ferma l'audio quando cambia il contesto
        GoogleTTSService.stopSpeaking();

        String separatorMsg = "--- 🏁 Analisi spostata sul Giro selezionato";
        addSeparatorIfNeeded(separatorMsg);

        CompletableFuture.supplyAsync(() -> {
            var style = SetupAdvisor.analyzeStyle(session);
            var recs = SetupAdvisor.forLap(lap, style);
            var tyre = SetupAdvisor.suggestTyreCompound(session, style);
            List<String> notes = org.simulator.coach.Coach.generateNotes(lap);
            return new AnalysisResult(recs, notes, tyre);
        }).thenAcceptAsync(this::fillReportUI, Platform::runLater);
    }

    public void loadSession(List<Lap> laps) {
        showLoadingReportOnly();
        this.currentTechnicalContext = GeminiContextBuilder.buildSessionContext(laps, currentTrackInfo);

        GoogleTTSService.stopSpeaking();

        String separatorMsg = "--- 📊 Analisi spostata sull'intera Sessione ---";
        addSeparatorIfNeeded(separatorMsg);
        CompletableFuture.supplyAsync(() -> {
            var style = SetupAdvisor.analyzeStyle(laps);
            var recs = SetupAdvisor.forSession(laps, style);
            var tyre = SetupAdvisor.suggestTyreCompound(laps, style);
            List<String> notes = org.simulator.coach.CoachSession.generateSessionNotes(laps);
            return new AnalysisResult(recs, notes, tyre);
        }).thenAcceptAsync(this::fillReportUI, Platform::runLater);
    }

    private void addSeparatorIfNeeded(String msg) {
        if (GLOBAL_HISTORY.isEmpty() || !GLOBAL_HISTORY.getLast().text.equals(msg)) {
            Platform.runLater(() -> {
                GLOBAL_HISTORY.add(new ChatMessage(msg, false));
                chatList.scrollTo(GLOBAL_HISTORY.size() - 1);
            });
        }
    }

    private void showLoadingReportOnly() {
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
                String clean = note.replaceAll("\\[.*?]", "").replace("Coach:", "").replace("Sessione:", "").trim();
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

        // Se l'utente interrompe, l'AI smette di parlare
        GoogleTTSService.stopSpeaking();

        GLOBAL_HISTORY.add(new ChatMessage(text, true));
        chatInput.clear();
        chatList.scrollTo(GLOBAL_HISTORY.size() - 1);

        isCoolingDown = true;
        chatInput.setDisable(true);
        sendButton.setDisable(true);
        sendButton.setText("Attendi...");
        aiSpinner.setVisible(true);

        CompletableFuture.supplyAsync(() -> GeminiService.chat(text, currentTechnicalContext))
                .thenAcceptAsync(response -> {
                    GLOBAL_HISTORY.add(new ChatMessage(response, false));
                    chatList.scrollTo(GLOBAL_HISTORY.size() - 1);
                    aiSpinner.setVisible(false);

                    // Attiva il TTS sulla risposta dell'AI
                    GoogleTTSService.speak(response);

                }, Platform::runLater);

        // Cooldown timer per evitare spam API
        new Thread(() -> {
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            Platform.runLater(() -> {
                isCoolingDown = false;
                chatInput.setDisable(false);
                sendButton.setDisable(false);
                sendButton.setText("Invia");
                chatInput.requestFocus();
            });
        }).start();
    }

    private record AnalysisResult(List<SetupAdvisor.Recommendation> recs, List<String> coachNotes, org.simulator.setup.TyreCompoundAdvisor.Choice tyreChoice) {}
    private record ChatMessage(String text, boolean isUser) {}

    private static class ChatCell extends ListCell<ChatMessage> {
        private final VBox box = new VBox();
        private final Label lbl = new Label();

        public ChatCell() {
            box.setFillWidth(false);
            lbl.setWrapText(true);
            lbl.setPadding(new Insets(10, 14, 10, 14));
            box.getChildren().add(lbl);
            setGraphic(box);
            setStyle("-fx-padding: 5; -fx-background-color: transparent;");
            box.setMaxWidth(Double.MAX_VALUE);
            listViewProperty().addListener((obs, oldVal, currentListView) -> {
                if (currentListView != null) {
                    lbl.maxWidthProperty().bind(Bindings.min(currentListView.widthProperty().multiply(0.75), 600.0));
                }
            });
        }
        @Override
        protected void updateItem(ChatMessage msg, boolean empty) {
            super.updateItem(msg, empty);
            if (empty || msg == null) { setGraphic(null); return; }
            lbl.setText(msg.text);
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
