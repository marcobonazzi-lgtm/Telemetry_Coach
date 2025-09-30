package org.simulator.ui.analysis_view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.simulator.canale.Lap;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class LapCoachPane {

    // TTS state isolato qui
    private volatile boolean paused = false;
    private volatile boolean stopped = false;
    private Thread ttsThread;
    private Label ttsStatus;
    private ProgressIndicator ttsSpinner;

    Node build(Lap lap){
        List<String> allNotes = org.simulator.coach.Coach.generateNotes(lap);

        // Filtri
        CheckBox cGuida = new CheckBox("Guida");        cGuida.setSelected(true);
        CheckBox cGomme = new CheckBox("Gomme");        cGomme.setSelected(true);
        CheckBox cFreni = new CheckBox("Freni");        cFreni.setSelected(true);
        CheckBox cFFB   = new CheckBox("FFB");          cFFB.setSelected(true);
        CheckBox cTrasm = new CheckBox("Trasmissione"); cTrasm.setSelected(true);
        CheckBox cDanni = new CheckBox("Danni");        cDanni.setSelected(true);

        HBox filters = new HBox(10, new Label("Filtri:"), cGuida, cGomme, cFreni, cFFB, cTrasm, cDanni);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.setPadding(new Insets(6, 0, 6, 0));

        // Lista
        ListView<String> notesList = new ListView<>();
        notesList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        notesList.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(String raw, boolean empty) {
                super.updateItem(raw, empty);
                if (empty || raw == null) { setGraphic(null); setText(null); return; }
                Circle dot = new Circle(5, colorForImportance(detectImportance(raw)));
                Label text = new Label(cleanForDisplay(raw));
                text.setWrapText(true);
                HBox row = new HBox(8, dot, text);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(2,6,2,6));
                setGraphic(row); setText(null);
            }
        });

        // Stato TTS
        ttsStatus  = new Label("Completato");
        ttsSpinner = new ProgressIndicator(); ttsSpinner.setPrefSize(16,16); ttsSpinner.setVisible(false);
        HBox statusRow = new HBox(8, ttsSpinner, ttsStatus);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setPadding(new Insets(4,0,6,0));

        Runnable rebuild = () -> {
            List<String> cats = new ArrayList<>();
            if (cGuida.isSelected()) cats.add("[Guida]");
            if (cGomme.isSelected()) cats.add("[Gomme]");
            if (cFreni.isSelected()) cats.add("[Freni]");
            if (cFFB.isSelected())   cats.add("[FFB]");
            if (cTrasm.isSelected()) cats.add("[Trasmissione]");
            if (cDanni.isSelected()) cats.add("[Danni]");

            List<String> filtered = new ArrayList<>();
            for (String s : allNotes) if (cats.stream().anyMatch(s::contains)) filtered.add(s);
            notesList.getItems().setAll(filtered);

            ttsStatus.setText("Completato");
            ttsSpinner.setVisible(false);
        };
        cGuida.setOnAction(e -> rebuild.run());
        cGomme.setOnAction(e -> rebuild.run());
        cFreni.setOnAction(e -> rebuild.run());
        cFFB.setOnAction(e -> rebuild.run());
        cTrasm.setOnAction(e -> rebuild.run());
        cDanni.setOnAction(e -> rebuild.run());
        rebuild.run();

        // Controlli
        Button playAllBtn = new Button("▶ Riproduci tutti");
        Button playSelBtn = new Button("▶ Riproduci selezionati");
        Button pauseBtn   = new Button("⏸ Pausa");
        Button stopBtn    = new Button("⏹ Stop");

        playAllBtn.setOnAction(e -> speakNotesAsync(new ArrayList<>(notesList.getItems())));
        playSelBtn.setOnAction(e -> speakNotesAsync(new ArrayList<>(notesList.getSelectionModel().getSelectedItems())));
        pauseBtn.setOnAction(e -> {
            if (ttsThread != null && ttsThread.isAlive()) {
                paused = !paused;
                pauseBtn.setText(paused ? "▶ Riprendi" : "⏸ Pausa");
                ttsStatus.setText(paused ? "In pausa" : "In riproduzione…");
            }
        });
        stopBtn.setOnAction(e -> {
            stopped = true; paused = false;
            if (ttsThread != null) ttsThread.interrupt();
            ttsStatus.setText("Interrotto");
            ttsSpinner.setVisible(false);
            notesList.getSelectionModel().clearSelection();
        });

        HBox controls = new HBox(10, playAllBtn, playSelBtn, pauseBtn, stopBtn);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(5,0,5,0));

        TitledPane coachPane = new TitledPane("Consigli del coach (giro)", new VBox(filters, controls, statusRow, notesList));
        coachPane.setPrefHeight(460);
        coachPane.setCollapsible(false);
        return coachPane;
    }

    // ---------- TTS ----------
    private void speakNotesAsync(List<String> notes) {
        if (notes == null || notes.isEmpty()) return;
        stopped = false; paused = false;
        Platform.runLater(() -> { ttsStatus.setText("In riproduzione…"); ttsSpinner.setVisible(true); });

        ttsThread = new Thread(() -> {
            try {
                for (int i = 0; i < notes.size(); i++) {
                    if (stopped) break;
                    while (paused && !stopped) { try { Thread.sleep(120); } catch (InterruptedException ignored) {} }
                    if (stopped) break;
                    final int idx = i;
                    Platform.runLater(() -> ttsStatus.setText(String.format("In riproduzione… (%d/%d)", idx+1, notes.size())));
                    speakWindows(cleanForSpeech(notes.get(i)));
                }
            } catch (Exception ignored) {
            } finally {
                Platform.runLater(() -> { ttsSpinner.setVisible(false); ttsStatus.setText(stopped ? "Interrotto" : "Completato"); });
            }
        }, "coach-tts");
        ttsThread.setDaemon(true);
        ttsThread.start();
    }

    private void speakWindows(String text) throws IOException, InterruptedException {
        String psCmd =
                "Add-Type -AssemblyName System.Speech; " +
                        "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                        "$s.Rate=0; $s.Volume=100; " +
                        "$t=[Console]::In.ReadToEnd(); $s.Speak($t);";
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", psCmd);
        Process p = pb.start();
        try (OutputStreamWriter w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)) { w.write(text); }
        while (p.isAlive()) { if (stopped) { p.destroyForcibly(); break; } Thread.sleep(60); }
    }

    // ---------- pulizia/styling testo + colori ----------
    private enum Importance { LOW, MEDIUM, HIGH }
    private Importance detectImportance(String s) {
        String u = s.toLowerCase(Locale.ITALIAN);
        if (u.contains("[alta]") || u.contains("[high]") || u.contains("[!!]") || u.contains("!!")) return Importance.HIGH;
        if (u.contains("[bassa]") || u.contains("[low]")) return Importance.LOW;
        if (u.contains("[media]") || u.contains("[med]") || u.contains("[!]")) return Importance.MEDIUM;
        String[] HIGH_WORDS = {"attenzione","critico","pericolo","anomalia","irregolare","abs frequente","sterzo nervoso","coasting elevato","downshift","apertura gas irregolare","danni frontali","freni freddi","sbilanc"};
        for (String w : HIGH_WORDS) if (u.contains(w)) return Importance.HIGH;
        String[] LOW_WORDS = {"considera","valuta","leggermente","ottimale","consiglio","lievemente"};
        for (String w : LOW_WORDS) if (u.contains(w)) return Importance.LOW;
        return Importance.MEDIUM;
    }
    private Color colorForImportance(Importance imp) {
        return switch (imp) { case HIGH -> Color.CRIMSON; case MEDIUM -> Color.GOLD; case LOW -> Color.MEDIUMSEAGREEN; };
    }
    private String cleanForDisplay(String s) {
        String t = s.replace("[ALTA]","").replace("[MEDIA]","").replace("[BASSA]","")
                .replace("[HIGH]","").replace("[MED]","").replace("[LOW]","")
                .replace("[!!]","").replace("[!]","")
                .replace("[Sessione]","")
                .replace("[Guida]","").replace("[Gomme]","").replace("[Freni]","")
                .replace("[FFB]","").replace("[Trasmissione]","").replace("[Danni]","")
                .trim();
        String BULLET_PREFIX = "^[-–—\\s]*[\\u2022\\u25CF\\u25CB\\u25E6\\u00B7\\u2023\\u25AA\\u25AB\\u2219]+\\s*";
        t = t.replaceFirst(BULLET_PREFIX, ""); t = t.replaceFirst(BULLET_PREFIX, "");
        return t.trim();
    }
    private String stripLeadingCategoryWord(String t) {
        return t.trim().replaceFirst("^(?i)(sessione|guida|gomme|freni|ffb|trasmissione|danni)\\s*[:\\-–—·]*\\s*", "");
    }
    private String normalizeNumbersForSpeech(String t) {
        String r = t;
        r = r.replaceAll("(?i)(\\d+[\\.,]\\d+|\\d+)\\s*°\\s*C", "$1 gradi");
        r = r.replaceAll("(?i)(\\d+[\\.,]\\d+|\\d+)\\s*km/?h", "$1 chilometri orari");
        r = r.replaceAll("(?i)(\\d+[\\.,]\\d+|\\d+)\\s*s(?![a-z])", "$1 secondi");
        r = r.replaceAll("(?i)(\\d+[\\.,]\\d+|\\d+)\\s*psi", "$1 psi");
        r = r.replaceAll("(?i)(\\d+[\\.,]\\d+|\\d+)\\s*%", "$1 per cento");
        r = r.replaceAll("(?i)\\bavg\\b", "media");
        r = r.replaceAll("(\\d+)\\s*/\\s*(\\d+)\\s*giri", "$1 su $2 giri");
        r = r.replaceAll("(\\d+)\\s*/\\s*(\\d+)\\b", "$1 su $2");
        r = r.replaceAll("\\bFL\\b", " anteriore sinistra ");
        r = r.replaceAll("\\bFR\\b", " anteriore destra ");
        r = r.replaceAll("\\bRL\\b", " posteriore sinistra ");
        r = r.replaceAll("\\bRR\\b", " posteriore destra ");
        r = r.replaceAll("\\bFFB\\b", " force feedback ");
        r = r.replaceAll("(\\d+)\\.(\\d+)", "$1,$2");
        r = r.replaceAll("\\s{2,}", " ").trim();
        return r;
    }
    private String cleanForSpeech(String s) {
        String t = cleanForDisplay(s);
        t = stripLeadingCategoryWord(t);
        t = t.replace("->", " soluzione ").replace("→", " soluzione ").replace("=>", " soluzione ");
        return normalizeNumbersForSpeech(t);
    }
}
