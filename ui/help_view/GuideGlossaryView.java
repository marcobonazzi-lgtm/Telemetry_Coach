package org.simulator.ui.help_view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Guide & Glossary V3 - Curata per Ingegneria di Pista.
 * Nessuna derivazione inutile dal CSV, solo concetti chiave di Dinamica Veicolo e Setup.
 */
public class GuideGlossaryView {

    private final BorderPane root = new BorderPane();

    private final TextField search = new TextField();
    private final ComboBox<String> sectionFilter = new ComboBox<>();
    private final ListView<String> namesList = new ListView<>();
    private final ScrollPane centerScroll = new ScrollPane();
    private final Accordion sectionsAcc = new Accordion();

    private final Map<String, List<Entry>> model = new TreeMap<>();

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
        search.setPromptText("🔍 Cerca (es. 'sottosterzo', 'camber')...");
        search.textProperty().addListener((o, ov, nv) -> applyFilter());
        search.setPrefWidth(250);

        sectionFilter.setPromptText("Tutte le sezioni");
        sectionFilter.setOnAction(e -> applyFilter());
        sectionFilter.getStyleClass().add("combo-filter");

        HBox quickFilters = new HBox(6);
        quickFilters.getChildren().addAll(
                createQuickFilter("Sottosterzo"),
                createQuickFilter("Sovrasterzo"),
                createQuickFilter("Gomme"),
                createQuickFilter("Freni")
        );
        quickFilters.setAlignment(Pos.CENTER_LEFT);

        HBox topTools = new HBox(10, new Label("Filtra:"), sectionFilter, search, new Separator(javafx.geometry.Orientation.VERTICAL), quickFilters);
        topTools.setAlignment(Pos.CENTER_LEFT);
        topTools.setPadding(new Insets(10));
        topTools.getStyleClass().add("glossary-toolbar");

        namesList.setPrefWidth(260);
        namesList.getStyleClass().add("glossary-list-view");
        namesList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item);
            }
        });
        namesList.setOnMouseClicked(e -> {
            String sel = namesList.getSelectionModel().getSelectedItem();
            if (sel != null) reveal(sel);
        });

        Label indexHeader = new Label(" INDICE INGEGNERE");
        indexHeader.getStyleClass().add("sidebar-header");

        VBox leftPane = new VBox(0, indexHeader, namesList);
        VBox.setVgrow(namesList, Priority.ALWAYS);
        leftPane.getStyleClass().add("glossary-sidebar");

        centerScroll.setContent(sectionsAcc);
        centerScroll.setFitToWidth(true);
        centerScroll.getStyleClass().add("glossary-scroll-pane");
        sectionsAcc.getStyleClass().add("glossary-accordion");

        Label disclaimer = new Label("NOTA TECNICA: Le regolazioni suggerite alterano la piattaforma aerodinamica e meccanica del veicolo. Effettuare una modifica alla volta per isolarne gli effetti in telemetria.");
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

        v.getChildren().add(createCard("📖 Analisi Dinamica", en.definition, "card-def"));
        if (en.hasTips()) {
            v.getChildren().add(createCard("🛠️ Intervento (Setup & Guida)", en.tips, "card-tips"));
        }
        return v;
    }

    private Node createCard(String title, String content, String styleClass) {
        Label h = new Label(title);
        h.getStyleClass().add("card-title");

        TextFlow tf = new TextFlow();
        for (String line : content.split("\n")) {
            Text t = new Text(line + "\n");
            t.getStyleClass().add("card-text");
            tf.getChildren().add(t);
        }

        VBox box = new VBox(5, h, tf);
        box.setPadding(new Insets(10));
        box.getStyleClass().addAll("glossary-card", styleClass);
        return box;
    }

    private String getSectionIcon(String sec) {
        if (sec.contains("Guida")) return "🎓";
        if (sec.contains("Pneumatici")) return "🛞";
        if (sec.contains("Freni")) return "🛑";
        if (sec.contains("Sospensioni")) return "⚙️";
        if (sec.contains("Dinamica")) return "🏎️";
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

    // -------------------------- DATA MODEL & DATABASE --------------------------
    private static class Entry {
        final String displayName;
        final String section;
        final String definition;
        String tips = "";

        Entry(String name, String sec, String def) {
            this.displayName = name;
            this.section = sec;
            this.definition = def;
        }
        void setTips(String t) { this.tips = t; }
        boolean hasTips() { return tips != null && !tips.isBlank(); }
        boolean matches(String query) {
            if (query.isEmpty()) return true;
            return displayName.toLowerCase().contains(query) ||
                    definition.toLowerCase().contains(query) ||
                    tips.toLowerCase().contains(query);
        }
    }

    private void buildModel() {
        model.clear();
        for (Entry e : GlossaryDatabase.getEntries()) {
            model.computeIfAbsent(e.section, k -> new ArrayList<>()).add(e);
        }
        model.values().forEach(list -> list.sort(Comparator.comparing(e -> e.displayName)));
    }

    /**
     * DATABASE STATICO DELL'INGEGNERE DI PISTA.
     * Sostituisce la noiosa e inutile lettura dei canali CSV con concetti reali.
     */
    private static class GlossaryDatabase {
        static List<Entry> getEntries() {
            List<Entry> list = new ArrayList<>();

            // ================= 1. DINAMICA VEICOLO =================
            Entry under = new Entry("Sottosterzo (Understeer)", "1. Dinamica Veicolo", "Saturazione dell'asse anteriore. L'angolo di deriva (slip angle) delle ruote anteriori supera il limite di aderenza, facendo allargare la traiettoria al muso.");
            under.setTips("• Guida: Riduci la velocità d'ingresso, rilascia gradualmente il freno (Trail Braking) per tenere il peso sull'anteriore.\n• Setup: Ammorbidisci la barra antirollio (ARB) anteriore, aumenta l'ala anteriore, riduci il precarico del differenziale in rilascio (Coast).");
            list.add(under);

            Entry over = new Entry("Sovrasterzo (Oversteer)", "1. Dinamica Veicolo", "Saturazione dell'asse posteriore. Il retrotreno perde aderenza prima dell'avantreno, innescando una rotazione indesiderata (imbardata).");
            over.setTips("• Entry (In Staccata): Sposta il Brake Bias in avanti. Aumenta il Coast/Preload del differenziale.\n• Exit (In Trazione): Apri il gas più dolcemente. Riduci la barra antirollio (ARB) posteriore o abbassa la mappa dell'acceleratore.");
            list.add(over);

            Entry pitch = new Entry("Trasferimento di Carico (Pitch/Roll)", "1. Dinamica Veicolo", "Spostamento della massa del veicolo in frenata/accelerazione (Beccheggio/Pitch) o in curva (Rollio/Roll). Gestire questo trasferimento è l'essenza del setup.");
            pitch.setTips("• Rollio eccessivo: L'auto è troppo lenta nei cambi di direzione. Indurisci le Barre Antirollio (ARB) o le molle.\n• Beccheggio eccessivo (Nosedive): Indurisci gli ammortizzatori in Slow Bump all'anteriore per stabilizzare la frenata.");
            list.add(pitch);

            // ================= 2. TECNICHE DI GUIDA =================
            Entry trail = new Entry("Trail Braking", "2. Tecniche di Guida", "Rilascio progressivo del pedale del freno mentre si inserisce il volante verso il punto di corda (Apex).");
            trail.setTips("• Scopo: Mantiene compresse le sospensioni anteriori, garantendo grip direzionale alla gomma. Rilasciare di scatto (Brake Stomp) fa sollevare il muso causando sottosterzo immediato.");
            list.add(trail);

            Entry coast = new Entry("Coasting (Veleggiamento)", "2. Tecniche di Guida", "Fase in cui il pilota non preme né il freno né l'acceleratore.");
            coast.setTips("• Errore comune: Un coasting prolungato sbilancia l'aerodinamica e lascia l'auto 'galleggiare' senza appoggio. Cerca di unire la fase finale del rilascio freno con la prima fase di applicazione del gas.");
            list.add(coast);

            Entry overlap = new Entry("Overlap (Gas e Freno)", "2. Tecniche di Guida", "Pressione simultanea del pedale del freno e dell'acceleratore.");
            overlap.setTips("• Problema: Cuoce i dischi dei freni e confonde i differenziali attivi. Assicurati che il piede sinistro rilasci completamente il freno prima di trazionare, e viceversa.");
            list.add(overlap);

            Entry ffb = new Entry("FFB Clipping", "2. Tecniche di Guida", "Saturazione del segnale del Force Feedback. Il volante raggiunge la massima forza erogabile dal motore, 'tagliando' i picchi di forza.");
            ffb.setTips("• Effetto: Il volante diventa un blocco di marmo a centro curva, impedendoti di sentire se la gomma sta perdendo aderenza. Abbassa il 'Gain' generale nel simulatore o nel driver.");
            list.add(ffb);

            // ================= 3. PNEUMATICI & TEMPERATURE =================
            Entry press = new Entry("Pressione Gomme (Profilo Cappello / U)", "3. Pneumatici", "L'impronta a terra (Contact Patch) varia con la pressione a caldo. Va letta analizzando il gradiente termico tra Interno, Centro ed Esterno della gomma.");
            press.setTips("• Profilo a Cappello (Centro troppo caldo): Pressione ECCESSIVA. La gomma 'spancia'. Riduci di 0.1/0.2 psi.\n• Profilo a U (Spalle troppo calde): Pressione TROPPO BASSA. La carcassa flette. Aumenta di 0.1/0.2 psi.");
            list.add(press);

            Entry camber = new Entry("Camber (Campanatura)", "3. Pneumatici", "Inclinazione verticale della ruota. Un setup racing usa sempre un Camber Negativo (parte superiore della ruota piegata verso l'interno dell'auto).");
            camber.setTips("• Regola d'oro: Analizza le temperature (IMO). La spalla Interna deve essere dai 5 ai 10°C più calda della spalla Esterna. Se la differenza è maggiore, hai troppo camber e perdi grip in staccata.");
            list.add(camber);

            Entry toe = new Entry("Toe (Convergenza)", "3. Pneumatici", "L'angolazione delle ruote rispetto all'asse longitudinale viste dall'alto.");
            toe.setTips("• Anteriore (Toe-OUT / Divergenza): Rende lo sterzo più reattivo (turn-in aggressivo) ma scalda l'interno gomma in rettilineo.\n• Posteriore (Toe-IN / Convergenza): Stabilizza il retrotreno in accelerazione, ma crea un leggero sottosterzo cronico.");
            list.add(toe);

            // ================= 4. SOSPENSIONI =================
            Entry arb = new Entry("Barra Antirollio (ARB)", "4. Sospensioni", "Elemento torsionale che collega le sospensioni dello stesso asse. Regola la resistenza al rollio trasferendo il carico sulla ruota esterna.");
            arb.setTips("• ARB Anteriore più rigida: Aumenta la reattività iniziale, ma induce sottosterzo a centro curva.\n• ARB Posteriore più rigida: Fa scivolare il posteriore aiutando l'inserimento, ma riduce la trazione in uscita.");
            list.add(arb);

            Entry bump = new Entry("Ammortizzatori (Bump & Rebound)", "4. Sospensioni", "Controllano la VELOCITÀ con cui la molla si comprime (Bump) o si estende (Rebound).");
            bump.setTips("• Slow Bump: Controlla beccheggio e rollio in frenata/curva. Aumentalo per stabilizzare la piattaforma aero.\n• Fast Bump: Assorbe i cordoli. Se l'auto salta, riducilo.\n• Rebound: Irrigidirlo all'anteriore aiuta a non far alzare il muso in accelerazione.");
            list.add(bump);

            Entry bot = new Entry("Bottoming / Bumpstop", "4. Sospensioni", "L'auto tocca il suolo (Bottoming) o la sospensione esaurisce la sua corsa sbattendo sul tampone di fine corsa (Bumpstop).");
            bot.setTips("• Rilevamento: Se vedi il parametro 'Suspension Travel' al >95%, sei a pacco. Innesca reazioni nervosissime. Indurisci le molle o alza la Ride Height.");
            list.add(bot);

            // ================= 5. FRENI E TRASMISSIONE =================
            Entry bb = new Entry("Brake Bias (Ripartizione Frenata)", "5. Freni & Trasmissione", "Regola quanta forza frenante va sull'asse anteriore rispetto al posteriore.");
            bb.setTips("• Spostare in avanti (es. 58%): L'auto diventa molto stabile in staccata, ma fa fatica a girare (sottosterzo indotto) e le ruote anteriori si bloccano facilmente.\n• Spostare indietro (es. 52%): Aiuta l'inserimento curva, ma se esageri il posteriore cercherà di sorpassare l'anteriore (testacoda).");
            list.add(bb);

            Entry ducts = new Entry("Brake Ducts (Condotti Freni)", "5. Freni & Trasmissione", "Prese d'aria che raffreddano i dischi freno (e, di riflesso, il cerchio e l'aria dentro la gomma).");
            ducts.setTips("• Freni troppo caldi (Es. >650°C su GT3): Ossidazione e fading (pedale lungo). APRI i condotti.\n• Freni freddi (Es. <300°C su GT3): Vetrificazione, attrito inesistente. CHIUDI i condotti. Usali anche per variare le pressioni delle gomme.");
            list.add(ducts);

            Entry diff = new Entry("Differenziale (Power / Coast / Preload)", "5. Freni & Trasmissione", "Determina il grado di bloccaggio tra la ruota sinistra e destra dello stesso asse guidato.");
            diff.setTips("• Power (Accelerazione): Più bloccato = l'auto spinge dritto (sottosterzo) ma ha più trazione (se AWD/RWD).\n• Coast (Rilascio): Più bloccato = stabilizza l'auto in ingresso curva limitando l'oversteer.\n• Preload (Precarico): Forza iniziale per far scattare il differenziale. Aumentarlo placa l'auto nei transitori (da freno a gas).");
            list.add(diff);

            return list;
        }
    }
}
