package org.simulator.ui.all_laps_view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.simulator.canale.Lap;
// ↓ usa quello giusto nel tuo progetto:
// import org.simulator.NoteVocali.Coach;
import org.simulator.coach.Coach;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pannello "Consigli del coach (sessione)" con filtri corretti e testo pulito. */
public final class SessionCoachPane {

    // ===== Modello nota =====
    private static final Pattern CAT_RE =
            Pattern.compile("\\[(Sessione|Guida|Gomme|Tyre|Tyres|Freni|Brake|Brakes|FFB|Trasmissione|Transmission|Danni|Damage)\\]",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern SEV_RE = Pattern.compile("\\[(ALTA|MEDIA|BASSA|HIGH|MED|LOW|!!|!)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern BULLET_PREFIX = Pattern.compile("^[-–—\\s]*[\\u2022\\u25CF\\u25CB\\u25E6\\u00B7\\u2023\\u25AA\\u25AB\\u2219]+\\s*");

    private static final class Note {
        final String raw;
        final String clean;       // testo per la UI (senza [tag])
        final Set<String> tags;   // categorie es. Sessione, Guida, ...
        final Importance imp;
        Note(String raw){
            this.raw = (raw==null) ? "" : raw.trim();
            this.tags = extractTags(this.raw);
            this.clean = cleanForDisplay(this.raw);
            this.imp = detectImportance(this.raw);
        }
    }

    private enum Importance { LOW, MEDIUM, HIGH }

    private static Set<String> extractTags(String s){
        Set<String> out = new HashSet<>();
        Matcher m = CAT_RE.matcher(s);
        while (m.find()) out.add(normalizeCat(m.group(1)));

        // se non c'è nessuna categoria, trattala come "Sessione" per non perderla
        if (out.isEmpty()) out.add("Sessione");
        return out;
    }
    private static String capitalize(String s){
        if (s==null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase(Locale.ITALIAN) + s.substring(1).toLowerCase(Locale.ITALIAN);
    }

    // colore puntino
    private static Color colorForImportance(Importance imp){
        return switch (imp){
            case HIGH   -> Color.CRIMSON;
            case MEDIUM -> Color.GOLD;
            case LOW    -> Color.MEDIUMSEAGREEN;
        };
    }
    private static Importance detectImportance(String s){
        String u = s.toLowerCase(Locale.ITALIAN);
        if (u.contains("[alta]") || u.contains("[high]") || u.contains("[!!]") || u.contains("!!")) return Importance.HIGH;
        if (u.contains("[bassa]")|| u.contains("[low]")) return Importance.LOW;
        if (u.contains("[media]")|| u.contains("[med]") || u.contains("[!]")) return Importance.MEDIUM;
        String[] HIGH_WORDS = {"attenzione","critico","pericolo","anomalia","irregolare","abs frequente","sterzo nervoso","coasting elevato","downshift","apertura gas irregolare","danni frontali","freni freddi","sbilanc"};
        for (String w : HIGH_WORDS) if (u.contains(w)) return Importance.HIGH;
        String[] LOW_WORDS  = {"considera","valuta","leggermente","ottimale","consiglio","lievemente"};
        for (String w : LOW_WORDS)  if (u.contains(w)) return Importance.LOW;
        return Importance.MEDIUM;
    }
    private static String normalizeCat(String s){
        String u = (s == null ? "" : s).toLowerCase(Locale.ITALIAN);
        switch (u) {
            case "sessione":                      return "Sessione";
            case "guida":                         return "Guida";
            case "gomme": case "tyre": case "tyres":
                return "Gomme";
            case "freni": case "brake": case "brakes":
                return "Freni";
            case "ffb":                           return "FFB";
            case "trasmissione": case "transmission":
                return "Trasmissione";
            case "danni": case "damage":          return "Danni";
            default:                              return "Sessione"; // fallback
        }
    }

    // pulizia testo per la lista
    private static String cleanForDisplay(String s){
        // togli categorie
        String t = CAT_RE.matcher(s).replaceAll("");
        // togli severità
        t = SEV_RE.matcher(t).replaceAll("");
        // togli [!!] e [!]
        t = t.replace("[!!]","").replace("[!]","");
        // togli eventuali bullet d'inizio
        t = BULLET_PREFIX.matcher(t.trim()).replaceFirst("");
        t = BULLET_PREFIX.matcher(t.trim()).replaceFirst("");
        return t.trim();
    }

    // ===== UI & stato =====
    private final CheckBox cSess  = new CheckBox("Sessione");
    private final CheckBox cGuida = new CheckBox("Guida");
    private final CheckBox cGomme = new CheckBox("Gomme");
    private final CheckBox cFreni = new CheckBox("Freni");
    private final CheckBox cFFB   = new CheckBox("FFB");
    private final CheckBox cTrasm = new CheckBox("Trasmissione");
    private final CheckBox cDanni = new CheckBox("Danni");

    private final ListView<Note> list = new ListView<>();

    // TTS (manteniamo la tua UX)
    private volatile boolean paused = false;
    private volatile boolean stopped = false;
    private Thread ttsThread;
    private Label ttsStatus;
    private ProgressIndicator ttsSpinner;

    private List<Note> allNotes = List.of();

    public Node build(List<Lap> laps){
        // stato iniziale filtri
        cSess.setSelected(true); cGuida.setSelected(true); cGomme.setSelected(true);
        cFreni.setSelected(true); cFFB.setSelected(true); cTrasm.setSelected(true); cDanni.setSelected(true);

        // celle lista con pallino + testo pulito
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(Note n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) { setGraphic(null); setText(null); return; }
                Circle dot = new Circle(5, colorForImportance(n.imp));
                Label text = new Label(n.clean);
                text.setWrapText(true);
                HBox row = new HBox(8, dot, text);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(2,6,2,6));
                setGraphic(row); setText(null);
            }
        });

        // TTS status
        ttsStatus  = new Label("Completato");
        ttsSpinner = new ProgressIndicator(); ttsSpinner.setPrefSize(16,16); ttsSpinner.setVisible(false);
        HBox statusRow = new HBox(8, ttsSpinner, ttsStatus);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setPadding(new Insets(4,0,6,0));

        // filtri
        HBox filters = new HBox(10, new Label("Filtri:"), cSess, cGuida, cGomme, cFreni, cFFB, cTrasm, cDanni);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.setPadding(new Insets(6, 0, 6, 0));

        // bottoni
        Button playAllBtn = new Button("▶ Riproduci tutti");
        Button playSelBtn = new Button("▶ Riproduci selezionati");
        Button pauseBtn   = new Button("⏸ Pausa");
        Button stopBtn    = new Button("⏹ Stop");

        playAllBtn.setOnAction(e -> speakNotesAsync(list.getItems().stream().map(n -> n.clean).toList()));
        playSelBtn.setOnAction(e -> speakNotesAsync(list.getSelectionModel().getSelectedItems().stream().map(n -> n.clean).toList()));
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
            list.getSelectionModel().clearSelection();
        });

        HBox controls = new HBox(10, playAllBtn, playSelBtn, pauseBtn, stopBtn);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(5,0,5,0));

        // carica note e primo refresh
        allNotes = loadNotes(laps);
        Runnable refresh = this::refreshList;
        cSess.setOnAction(e -> refresh.run());
        cGuida.setOnAction(e -> refresh.run());
        cGomme.setOnAction(e -> refresh.run());
        cFreni.setOnAction(e -> refresh.run());
        cFFB.setOnAction(e -> refresh.run());
        cTrasm.setOnAction(e -> refresh.run());
        cDanni.setOnAction(e -> refresh.run());
        refresh.run();

        VBox content = new VBox(filters, controls, statusRow, list);
        TitledPane tp = new TitledPane("Consigli del coach (sessione)", content);
        tp.setCollapsible(false);
        VBox.setVgrow(list, Priority.ALWAYS);
        return tp;
    }

    private List<Note> loadNotes(List<Lap> laps){
        List<String> raw = Coach.generateSessionNotes(laps);
        List<Note> out = new ArrayList<>(raw.size());
        for (String s : raw) out.add(new Note(s));
        return out;
    }

    private void refreshList(){
        // categorie abilitate
        Set<String> on = new HashSet<>();
        if (cSess.isSelected())  on.add("Sessione");
        if (cGuida.isSelected()) on.add("Guida");
        if (cGomme.isSelected()) on.add("Gomme");
        if (cFreni.isSelected()) on.add("Freni");
        if (cFFB.isSelected())   on.add("FFB");
        if (cTrasm.isSelected()) on.add("Trasmissione");
        if (cDanni.isSelected()) on.add("Danni");

        // filtra: tieni le note che condividono almeno una categoria attiva
        List<Note> filtered = new ArrayList<>();
        for (Note n : allNotes){
            boolean keep = false;
            for (String t : n.tags) if (on.contains(t)) { keep = true; break; }
            if (keep) filtered.add(n);
        }
        list.getItems().setAll(filtered);

        ttsStatus.setText("Completato");
        ttsSpinner.setVisible(false);
    }

    // ====== TTS minimale (Windows — identico a prima) ======
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
                    speakWindows(notes.get(i));
                }
            } catch (Exception ignored) {
            } finally {
                Platform.runLater(() -> { ttsSpinner.setVisible(false); ttsStatus.setText(stopped ? "Interrotto" : "Completato"); });
            }
        }, "session-tts");
        ttsThread.setDaemon(true);
        ttsThread.start();
    }

    private void speakWindows(String text) throws Exception {
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
}
