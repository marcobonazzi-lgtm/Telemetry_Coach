package org.simulator.ui.help_view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.simulator.importCSVFW.ChannelAliases;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Guide & Glossary V2 - Modernized, De-duplicated, CSS-driven.
 */
public class GuideGlossaryView {

    private final BorderPane root = new BorderPane();

    private final TextField search = new TextField();
    private final ComboBox<String> sectionFilter = new ComboBox<>();
    private final ListView<String> namesList = new ListView<>();
    private final ScrollPane centerScroll = new ScrollPane();
    private final Accordion sectionsAcc = new Accordion();

    // Model: Section Name -> List of Glossary Entries
    private final Map<String, List<Entry>> model = new TreeMap<>(); // TreeMap keeps sections sorted

    public GuideGlossaryView() {
        buildUI();
        rebuildGeneral();
    }

    public Parent getRoot() {
        return root;
    }

    public void rebuildGeneral() {
        buildModel();
        refreshIndexAndFilter();
    }

    // -------------------------- UI CONSTRUCTION --------------------------
    private void buildUI() {
        // -- Toolbar --
        search.setPromptText("🔍 Cerca dato o problema (es. 'sottosterzo')...");
        search.textProperty().addListener((o, ov, nv) -> applyFilter());
        search.setPrefWidth(250);

        sectionFilter.setPromptText("Tutte le sezioni");
        sectionFilter.setOnAction(e -> applyFilter());
        // RIMOSSO STILE HARDCODED
        sectionFilter.getStyleClass().add("combo-filter");

        // Quick Filters for common issues
        HBox quickFilters = new HBox(6);
        quickFilters.getChildren().addAll(
                createQuickFilter("Sottosterzo"),
                createQuickFilter("Sovrasterzo"),
                createQuickFilter("Freni"),
                createQuickFilter("Gomme")
        );
        quickFilters.setAlignment(Pos.CENTER_LEFT);

        HBox topTools = new HBox(10, new Label("Filtra:"), sectionFilter, search, new Separator(javafx.geometry.Orientation.VERTICAL), quickFilters);
        topTools.setAlignment(Pos.CENTER_LEFT);
        topTools.setPadding(new Insets(10));
        // RIMOSSO SFONDO HARDCODED
        topTools.getStyleClass().add("glossary-toolbar");

        // -- Sidebar (Index) --
        namesList.setPrefWidth(260);
        namesList.getStyleClass().add("glossary-list-view");
        namesList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
            }
        });
        namesList.setOnMouseClicked(e -> {
            String sel = namesList.getSelectionModel().getSelectedItem();
            if (sel != null) reveal(sel);
        });

        Label indexHeader = new Label(" INDICE RAPIDO");
        indexHeader.getStyleClass().add("sidebar-header");

        VBox leftPane = new VBox(0, indexHeader, namesList);
        VBox.setVgrow(namesList, Priority.ALWAYS);
        // RIMOSSO SFONDO HARDCODED
        leftPane.getStyleClass().add("glossary-sidebar");

        // -- Center Content --
        centerScroll.setContent(sectionsAcc);
        centerScroll.setFitToWidth(true);
        // Classi CSS per trasparenza e scroll
        centerScroll.getStyleClass().add("glossary-scroll-pane");
        sectionsAcc.getStyleClass().add("glossary-accordion");

        // Disclaimer Footer
        Label disclaimer = new Label(
                "NOTA: La disponibilità dei canali dipende dal simulatore e dalla mod specifica. " +
                        "I consigli di setup sono indicativi e vanno adattati allo stile di guida."
        );
        disclaimer.setWrapText(true);
        disclaimer.getStyleClass().add("glossary-disclaimer");

        BorderPane mainArea = new BorderPane();
        mainArea.setCenter(centerScroll);
        mainArea.setBottom(disclaimer);

        root.setTop(topTools);
        root.setLeft(leftPane);
        root.setCenter(mainArea);
    }

    private Button createQuickFilter(String text) {
        Button b = new Button(text);
        // USO CLASSE CSS
        b.getStyleClass().add("quick-filter-btn");
        b.setOnAction(e -> {
            search.setText(text);
            applyFilter();
        });
        return b;
    }

    private Node buildEntryContent(Entry en) {
        VBox v = new VBox(10);
        v.setPadding(new Insets(10));

        // Explanation - Passiamo "card-def" come stile CSS
        v.getChildren().add(createCard("📖 Significato", en.definition, "card-def"));

        // Setup Tips - Passiamo "card-tips" come stile CSS
        if (en.hasTips()) {
            v.getChildren().add(createCard("🛠️ Consigli Setup & Guida", en.tips, "card-tips"));
        }

        // Technical Details (Aliases & Units)
        StringBuilder techDetails = new StringBuilder();
        if (!en.unit.isEmpty()) techDetails.append("Unità: ").append(en.unit).append("\n");
        if (!en.aliases.isEmpty()) techDetails.append("Alias CSV: ").append(String.join(", ", en.aliases));

        if (techDetails.length() > 0) {
            Label l = new Label(techDetails.toString());
            l.getStyleClass().add("tech-details-label");
            v.getChildren().add(l);
        }

        return v;
    }

    // MODIFICATO: Accetta styleClass invece di colori Hex
    private Node createCard(String title, String content, String styleClass) {
        Label h = new Label(title);
        h.getStyleClass().add("card-title");

        TextFlow tf = new TextFlow();
        String[] lines = content.split("\n");
        for (String line : lines) {
            Text t = new Text(line + "\n");
            // Gestione semplice del colore testo via CSS
            t.getStyleClass().add("card-text");
            tf.getChildren().add(t);
        }

        VBox box = new VBox(5, h, tf);
        box.setPadding(new Insets(10));
        // Assegna classe base + variante (def o tips)
        box.getStyleClass().addAll("glossary-card", styleClass);
        return box;
    }

    private String getSectionIcon(String sec) {
        if (sec.contains("Coach")) return "🎓";
        if (sec.contains("Setup")) return "🔧";
        if (sec.contains("Giri")) return "⏱️";
        if (sec.contains("Motore") || sec.contains("Power")) return "🚀";
        if (sec.contains("Gomme") || sec.contains("Pneu")) return "🛞";
        if (sec.contains("Freni")) return "🛑";
        if (sec.contains("Sospensioni")) return "amort";
        if (sec.contains("Aero")) return "💨";
        if (sec.contains("Elettronica")) return "⚡";
        return "📊";
    }

    // -------------------------- LOGIC & FILTERING --------------------------

    private void refreshIndexAndFilter() {
        List<String> secs = new ArrayList<>(model.keySet());
        sectionFilter.getItems().setAll(secs);
        sectionFilter.getItems().add(0, "Tutte le sezioni");
        sectionFilter.getSelectionModel().select(0);
        applyFilter();
    }

    private void applyFilter() {
        String q = Optional.ofNullable(search.getText()).orElse("").trim().toLowerCase(Locale.ROOT);
        String secSel = sectionFilter.getSelectionModel().getSelectedItem();
        boolean allSections = (secSel == null || "Tutte le sezioni".equals(secSel));

        List<String> filteredNames = new ArrayList<>();
        sectionsAcc.getPanes().clear();

        for (Map.Entry<String, List<Entry>> entry : model.entrySet()) {
            String sectionName = entry.getKey();
            if (!allSections && !sectionName.equals(secSel)) continue;

            List<Entry> matchingEntries = entry.getValue().stream()
                    .filter(en -> en.matches(q))
                    .collect(Collectors.toList());

            if (!matchingEntries.isEmpty()) {
                VBox container = new VBox(8);
                container.setPadding(new Insets(10));

                for (Entry en : matchingEntries) {
                    filteredNames.add(en.displayName);
                    TitledPane tp = new TitledPane(en.displayName, buildEntryContent(en));
                    tp.setExpanded(false);
                    if (!q.isEmpty()) tp.setExpanded(true);
                    container.getChildren().add(tp);
                }

                TitledPane sectionPane = new TitledPane(getSectionIcon(sectionName) + "  " + sectionName, container);
                sectionPane.setExpanded(!q.isEmpty() || (allSections && sectionsAcc.getPanes().isEmpty()));
                sectionsAcc.getPanes().add(sectionPane);
            }
        }

        Collections.sort(filteredNames);
        namesList.getItems().setAll(filteredNames);
    }

    private void reveal(String name) {
        for (TitledPane sec : sectionsAcc.getPanes()) {
            if (sec.getContent() instanceof VBox box) {
                for (Node n : box.getChildren()) {
                    if (n instanceof TitledPane tp && tp.getText().equals(name)) {
                        sectionsAcc.setExpandedPane(sec);
                        tp.setExpanded(true);
                        centerScroll.setVvalue(0);
                        return;
                    }
                }
            }
        }
        if (!search.getText().isEmpty()) {
            search.clear();
            reveal(name);
        }
    }

    // -------------------------- DATA MODEL (Invariato) --------------------------
    private static class Entry {
        final String displayName;
        final String section;
        final String definition;
        final String unit;
        final Set<String> aliases = new TreeSet<>();
        String tips = "";

        Entry(String name, String sec, String def, String u) {
            this.displayName = name;
            this.section = sec;
            this.definition = def;
            this.unit = u;
        }

        void addAlias(String a) { if (a != null && !a.isBlank()) aliases.add(a); }
        void setTips(String t) { this.tips = t; }
        boolean hasTips() { return tips != null && !tips.isBlank(); }

        boolean matches(String query) {
            if (query.isEmpty()) return true;
            return displayName.toLowerCase().contains(query) ||
                    definition.toLowerCase().contains(query) ||
                    tips.toLowerCase().contains(query) ||
                    aliases.stream().anyMatch(a -> a.toLowerCase().contains(query));
        }
    }

    private void buildModel() {
        model.clear();
        List<Entry> allEntries = new ArrayList<>();
        Set<String> processedNormKeys = new HashSet<>();

        for (String raw : ChannelAliases.headerCandidates()) {
            String norm = ChannelAliases.norm(raw);
            String baseKey = GlossaryData.wheelAgnosticKey(norm);
            if (processedNormKeys.contains(baseKey)) continue;
            processedNormKeys.add(baseKey);

            String prettyName = GlossaryData.prettify(baseKey);
            if (isWheelData(norm)) prettyName += " (per ruota)";
            String section = GlossaryData.getSection(norm);
            String def = GlossaryData.getDefinition(norm);
            String tips = GlossaryData.getTips(norm);
            String unit = GlossaryData.guessUnit(raw);

            Entry e = new Entry(prettyName, section, def, unit);
            e.setTips(tips);
            e.addAlias(raw);
            allEntries.add(e);
        }
        allEntries.addAll(GlossaryData.getStaticEntries());
        for (Entry e : allEntries) {
            model.computeIfAbsent(e.section, k -> new ArrayList<>()).add(e);
        }
        model.values().forEach(list -> list.sort(Comparator.comparing(e -> e.displayName)));
    }

    private boolean isWheelData(String norm) {
        return Pattern.compile("(fl|fr|rl|rr|anteriore|posteriore|wheel|tire|tyre)").matcher(norm).find();
    }

    // -------------------------- STATIC CONTENT DATABASE (Invariato) --------------------------
    private static class GlossaryData {
        static String prettify(String key) {
            String s = key.replace("_", " ").replace("  ", " ");
            return s.substring(0, 1).toUpperCase() + s.substring(1);
        }
        static String wheelAgnosticKey(String norm) {
            String s = norm.toLowerCase();
            s = s.replaceAll("(fl|fr|rl|rr|front left|front right|rear left|rear right|anteriore sinistra|anteriore destra|posteriore sinistra|posteriore destra)", "").trim();
            s = s.replaceAll(" +", " ");
            return s;
        }
        static String getSection(String key) {
            if (contains(key, "speed", "rpm", "gear", "throttle", "brake", "clutch", "engine")) return "1. Powertrain & Comandi";
            if (contains(key, "steer", "g force", "lat", "long", "yaw", "pitch", "roll")) return "2. Dinamica Veicolo";
            if (contains(key, "susp", "travel", "damper", "ride height")) return "3. Sospensioni";
            if (contains(key, "tire", "tyre", "pressure", "temp", "wear")) return "4. Pneumatici";
            if (contains(key, "lap", "time", "sector")) return "5. Tempi & Giri";
            return "6. Altro / Telemetria";
        }
        static String guessUnit(String raw) {
            if (raw.contains("[") && raw.contains("]")) return raw.substring(raw.indexOf("[")+1, raw.indexOf("]"));
            if (raw.contains("(") && raw.contains(")")) return raw.substring(raw.indexOf("(")+1, raw.indexOf(")"));
            return "";
        }
        static String getDefinition(String key) {
            if (contains(key, "speed")) return "Velocità istantanea del veicolo. Fondamentale per analizzare velocità di percorrenza curva e top speed.";
            if (contains(key, "rpm")) return "Giri motore al minuto. Utile per verificare punti di cambiata e utilizzo della coppia.";
            if (contains(key, "throttle")) return "Input acceleratore (0-100%). Verifica la fluidità in uscita e parzializzazioni inutili.";
            if (contains(key, "brake")) return "Input freno (Pressione o Pedale). La forma della curva indica la qualità della staccata (es. picco iniziale + rilascio).";
            if (contains(key, "steer")) return "Angolo di sterzo. Movimenti rapidi o eccessivi indicano instabilità o sottosterzo.";
            if (contains(key, "gear")) return "Marcia inserita.";
            if (contains(key, "lat")) return "Forza G Laterale. Indica quanto grip stai sfruttando in curva.";
            if (contains(key, "long")) return "Forza G Longitudinale. Indica potenza di frenata (negativa) e accelerazione (positiva).";
            if (contains(key, "suspension travel")) return "Movimento della sospensione. Analizza l'uso della corsa su cordoli e dossi.";
            if (contains(key, "tire temp")) return "Temperatura superficie o core della gomma. Deve restare nella finestra ottimale.";
            if (contains(key, "tire pressure")) return "Pressione gomme in tempo reale. Varia con la temperatura.";
            if (contains(key, "ride height")) return "Altezza del telaio da terra. Fondamentale per l'aerodinamica (Rake).";
            return "Dato telemetrico grezzo.";
        }
        static String getTips(String key) {
            if (contains(key, "brake")) return "• Se il picco non è immediato: stai frenando 'lungo' o con indecisione.\n• Se rilasci di colpo a centro curva: causerai sottosterzo. Pratica il Trail Braking (rilascio graduale).";
            if (contains(key, "throttle")) return "• 'Dente di sega' (on-off rapidi) in uscita: significa che stai perdendo trazione. Ammorbidisci posteriore o riduci Preload diff.\n• 100% gas ritardato: stai entrando troppo forte o l'auto non ruota.";
            if (contains(key, "steer")) return "• Se aumenti l'angolo ma l'auto non gira di più: SOTTOSTERZO. Raddrizza leggermente per riprendere grip.\n• Correzioni rapide in uscita: SOVRASTERZO di potenza.";
            if (contains(key, "tire pressure")) return "• Se la parte centrale della gomma è più calda dei bordi: Pressione troppo ALTA.\n• Se i bordi sono più caldi del centro: Pressione troppo BASSA.\n• Obiettivo: Pressioni a caldo uniformi o come da spec costruttore.";
            if (contains(key, "ride height")) return "• Oscillazioni costanti in rettilineo: Porpoising (alzare auto o irrigidire molle).\n• Tocca terra (spikes negativi): Alzare l'auto o aumentare Bump stop.";
            return "";
        }
        static List<Entry> getStaticEntries() {
            List<Entry> list = new ArrayList<>();
            Entry apex = new Entry("Apex (Punto di Corda)", "0. Concetti Guida", "Il punto più interno della curva dove l'auto tocca il cordolo.", "");
            apex.setTips("• Apex Anticipato: Utile per difendersi, ma sacrifica l'uscita.\n• Apex Ritardato: Ideale per massimizzare l'accelerazione in uscita (slow in, fast out).");
            list.add(apex);
            Entry trail = new Entry("Trail Braking", "0. Concetti Guida", "Tecnica di mantenere una leggera pressione sul freno mentre si inserisce l'auto in curva.", "");
            trail.setTips("• Serve a mantenere carico sulle gomme anteriori per aiutare l'inserimento.\n• Se rilasci il freno troppo presto, l'anteriore si alza e perdi direzionalità (Sottosterzo).");
            list.add(trail);
            Entry understeer = new Entry("Sottosterzo", "0. Concetti Guida", "L'auto curva meno di quanto richiesto (va dritto).", "");
            understeer.setTips("• Guida: Entra più piano, usa più Trail Braking.\n• Setup: Ammorbidire ARB anteriore, aumentare Ala anteriore, ammorbidire molle anteriori, dare Rake.");
            list.add(understeer);
            Entry oversteer = new Entry("Sovrasterzo", "0. Concetti Guida", "Il posteriore dell'auto scivola verso l'esterno (testacoda).", "");
            oversteer.setTips("• Guida: Parzializza il gas, controsterza rapidamente.\n• Setup (In entrata): Spostare Brake Bias in avanti.\n• Setup (In uscita): Ammorbidire ARB posteriore, ridurre Preload differenziale, ammorbidire molle posteriori.");
            list.add(oversteer);
            Entry camber = new Entry("Camber (Campanatura)", "4. Pneumatici", "Angolo verticale della ruota. Negativo = parte alta verso l'interno.", "deg");
            camber.setTips("• Più Camber Negativo: Più grip in curva, meno grip in frenata/accelerazione longitudinale.\n• Regola in base alle temperature: Interno gomma deve essere leggermente più caldo dell'esterno (5-10°C).");
            list.add(camber);
            Entry toe = new Entry("Toe (Convergenza)", "3. Sospensioni", "Angolo delle ruote rispetto all'asse longitudinale.", "deg");
            toe.setTips("• Toe-OUT Anteriore: Migliora inserimento curva, peggiora stabilità rettilineo.\n• Toe-IN Posteriore: Aumenta stabilità in accelerazione, riduce rotazione.");
            list.add(toe);
            Entry bb = new Entry("Brake Bias (Ripartizione)", "1. Powertrain & Comandi", "Percentuale di forza frenante sull'asse anteriore.", "%");
            bb.setTips("• Troppo avanti: Bloccaggio anteriori, sottosterzo in ingresso, auto stabile.\n• Troppo indietro: Bloccaggio posteriori, sovrasterzo in ingresso (auto si gira in staccata).");
            list.add(bb);
            return list;
        }
        private static boolean contains(String s, String... keys) {
            for (String k : keys) if (s.contains(k)) return true;
            return false;
        }
    }
}