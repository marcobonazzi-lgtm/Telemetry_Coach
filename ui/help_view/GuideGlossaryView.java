package org.simulator.ui.help_view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.simulator.importCSVFW.ChannelAliases;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Guida & Glossario – quadro generale di TUTTE le metriche supportate (organizzato e ricercabile). */
public class GuideGlossaryView {

    private final BorderPane root = new BorderPane();

    private final TextField search = new TextField();
    private final ComboBox<String> sectionFilter = new ComboBox<>();
    private final ListView<String> namesList = new ListView<>();
    private final ScrollPane centerScroll = new ScrollPane();
    private final Accordion sectionsAcc = new Accordion();

    // modello: sezione -> entries
    private final Map<String, List<Entry>> model = new LinkedHashMap<>();

    public GuideGlossaryView() {
        buildUI();
        rebuildGeneral();
    }

    public Parent getRoot() {
        return root;
    }

    /**
     * Quadro generale (basato su ChannelAliases), non dipende dai laps correnti.
     */
    public void rebuildGeneral() {
        buildModelFromAliases();
        rebuildSections();
        refreshIndexAndFilter();
    }

    // -------------------------- UI --------------------------
    private void buildUI() {
        search.setPromptText("Cerca un dato per nome…");
        search.textProperty().addListener((o, ov, nv) -> applyFilter());

        sectionFilter.setPromptText("Tutte le sezioni");
        sectionFilter.setOnAction(e -> applyFilter());
        sectionFilter.setStyle("-fx-background-radius:10; -fx-padding:4 10;");

        namesList.setPrefWidth(280);
        namesList.setMaxHeight(240);
        namesList.setOnMouseClicked(e -> {
            String sel = namesList.getSelectionModel().getSelectedItem();
            if (sel != null) reveal(sel);
        });

        // Barra strumenti + disclaimer
        HBox tools = new HBox(10, new Label("Sezione"), sectionFilter, new Label("Ricerca"), search);
        tools.setAlignment(Pos.CENTER_LEFT);
        tools.setPadding(new Insets(10));
        tools.setStyle("-fx-background-color:#f7f9fc; -fx-border-color:#e1e6ef; -fx-background-radius:12; -fx-border-radius:12;");

        Label disclaimer = new Label(
                "Avviso agli utenti: in Assetto Corsa (e in molti simulatori) la fisica e i dati disponibili variano da auto ad auto. " +
                        "Se un dato non è visibile oppure il setup non è completamente modificabile, non è un problema di questo software " +
                        "ma dipende dal contenuto/veicolo del gioco, dal mod o dalle regole della sessione/server."
        );
        disclaimer.setWrapText(true);
        disclaimer.setStyle("-fx-text-fill:#5b6676; -fx-font-size:11px;");

        VBox top = new VBox(6, tools, disclaimer);
        top.setPadding(new Insets(6, 10, 10, 10));

        VBox left = new VBox(8, new Label("Elenco nomi"), namesList);
        left.setPadding(new Insets(10));
        left.setPrefWidth(300);

        centerScroll.setContent(sectionsAcc);
        centerScroll.setFitToWidth(true);
        centerScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        SplitPane split = new SplitPane(left, centerScroll);
        split.setDividerPositions(0.30);

        root.setTop(top);       // mini-toolbar locale (niente top bar globale)
        root.setCenter(split);
    }

    private void rebuildSections() {
        sectionsAcc.getPanes().clear();
        for (var e : model.entrySet()) {
            String section = e.getKey();
            VBox box = new VBox(8);
            box.setPadding(new Insets(10));
            for (Entry en : e.getValue()) {
                Node content = buildEntryContent(en);
                TitledPane tp = new TitledPane(en.displayName, content);
                tp.setExpanded(false);
                box.getChildren().add(tp);
            }
            TitledPane sectionPane = new TitledPane(section, box);
            sectionPane.setExpanded(false);
            sectionsAcc.getPanes().add(sectionPane);
        }
        if (!sectionsAcc.getPanes().isEmpty()) sectionsAcc.getPanes().get(0).setExpanded(true);
    }

    private Node buildEntryContent(Entry en) {
        VBox v = new VBox(6);
        v.getChildren().add(coloredText("Cosa significa", en.expl, "#eaf7ff"));
        if (!en.aliases.isEmpty()) {
            v.getChildren().add(coloredText("Alias tipici (header CSV)", String.join(", ", en.aliases), "#f6f0ff"));
        }
        if (!en.unit.isEmpty()) {
            v.getChildren().add(coloredText("Unità", en.unit, "#fff7e6"));
        }
        if (!en.tips.isEmpty()) {
            v.getChildren().add(coloredText("Consigli setup", en.tips, "#eefbf2"));
        }
        v.setFillWidth(true);
        return v;
    }

    private Node coloredText(String title, String body, String bg) {
        Label h = new Label(title);
        h.setStyle("-fx-font-weight:bold; -fx-text-fill:#333;");
        Label b = new Label(body);
        b.setWrapText(true);
        VBox box = new VBox(4, h, b);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color:" + bg + "; -fx-border-color:#d9e0e8; -fx-background-radius:8; -fx-border-radius:8;");
        return box;
    }

    private void refreshIndexAndFilter() {
        // popola filtro sezione
        List<String> secs = new ArrayList<>(model.keySet());
        sectionFilter.getItems().setAll(secs);
        sectionFilter.getItems().add(0, "Tutte le sezioni");
        sectionFilter.getSelectionModel().select(0);

        // indice nomi
        List<String> names = model.values().stream().flatMap(List::stream).map(e -> e.displayName).sorted().toList();
        namesList.getItems().setAll(names);
    }

    private void applyFilter() {
        String q = Optional.ofNullable(search.getText()).orElse("").trim().toLowerCase(Locale.ROOT);
        String secSel = sectionFilter.getSelectionModel().getSelectedItem();
        boolean all = (secSel == null || "Tutte le sezioni".equals(secSel));

        // filtra elenco nomi
        List<String> names = model.entrySet().stream()
                .filter(e -> all || e.getKey().equals(secSel))
                .flatMap(e -> e.getValue().stream())
                .map(en -> en.displayName)
                .filter(n -> n.toLowerCase(Locale.ROOT).contains(q))
                .sorted()
                .toList();
        namesList.getItems().setAll(names);

        // espandi solo le sezioni coinvolte
        sectionsAcc.getPanes().forEach(p -> p.setExpanded(false));
        for (TitledPane p : sectionsAcc.getPanes()) {
            if (all || p.getText().equals(secSel)) {
                p.setExpanded(true);
            }
        }
    }

    private void reveal(String name) {
        for (TitledPane sec : sectionsAcc.getPanes()) {
            VBox box = (VBox) sec.getContent();
            for (Node n : box.getChildren()) {
                if (n instanceof TitledPane tp && tp.getText().equals(name)) {
                    sectionsAcc.getPanes().forEach(p -> p.setExpanded(false));
                    sec.setExpanded(true);
                    tp.setExpanded(true);
                    tp.requestFocus();
                    return;
                }
            }
        }
    }

    // -------------------------- MODEL --------------------------
    private static class Entry {
        final String displayName;   // nome leggibile (già deduplicato dove serve)
        final String section;       // sezione
        final String expl;          // spiegazione semplice
        final String unit;          // unità (se riconoscibile)
        final List<String> aliases; // alias header tipici
        final String tips;          // consigli setup (Aumenta/Diminuisci)

        Entry(String n, String s, String e, String u, List<String> a) {
            this(n, s, e, u, a, "");
        }

        Entry(String n, String s, String e, String u, List<String> a, String t) {
            displayName = n;
            section = s;
            expl = e;
            unit = u;
            aliases = a == null ? List.of() : a;
            tips = t == null ? "" : t;
        }

        Entry mergedWith(Entry other) {
            // Unisce alias e preserva campi non-vuoti con preferenza al più descrittivo
            String name = displayName; // manteniamo il nome di base già “generalizzato”
            String sec = section;
            String e = longer(expl, other.expl);
            String u = unit.isEmpty() ? other.unit : unit;
            List<String> a = concatDistinct(aliases, other.aliases);
            String t = longer(tips, other.tips);
            return new Entry(name, sec, e, u, a, t);
        }

        private static String longer(String a, String b) {
            return (a == null ? "" : a).length() >= (b == null ? 0 : b.length()) ? a : b;
        }

        private static List<String> concatDistinct(List<String> a, List<String> b) {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            if (a != null) set.addAll(a);
            if (b != null) set.addAll(b);
            return new ArrayList<>(set);
        }
    }

    private void buildModelFromAliases() {
        model.clear();

        // 1) Prendiamo tutti i "candidati header" come base del glossario
        List<String> headers = new ArrayList<>(ChannelAliases.headerCandidates());
        headers.sort(Comparator.naturalOrder());

        // 2) Costruiamo voci base (derivate dagli header), poi dedup per-ruota
        Map<String, Entry> tempByKey = new LinkedHashMap<>();
        for (String raw : headers) {
            String norm = ChannelAliases.norm(raw);
            String baseKey = wheelAgnosticKey(norm);                 // chiave che ignora FL/FR/RL/RR e simili
            String display = generalizedPrettyName(raw);             // nome leggibile già “aggregato”
            String unit = extractUnit(raw);
            String section = sectionFor(norm);
            String expl = explanationFor(norm, display, unit);
            List<String> aliases = collectAliasesFor(norm);

            String tips = setupTipsFor(norm, section, display);      // consigli set-up se pertinenti

            Entry en = new Entry(display, section, expl, unit, aliases, tips);
            tempByKey.merge(section + "||" + baseKey, en, Entry::mergedWith);
        }

        // 3) Estendiamo con termini di Setup/Coach (no duplicazioni) — anche questi passano per il merge
        var extra = buildSetupCoachExtraEntries();
        if (extra != null) {
            for (Entry e : extra) {
                String baseKey = wheelAgnosticKey(ChannelAliases.norm(e.displayName));
                tempByKey.merge(e.section + "||" + baseKey, e, Entry::mergedWith);
            }
        }

        // 4) Riporta nella mappa sezionale
        for (Entry e : tempByKey.values()) {
            model.computeIfAbsent(e.section, k -> new ArrayList<>()).add(e);
        }

        // 5) ordina le voci per nome all'interno di ciascuna sezione
        for (var e : model.entrySet()) {
            e.getValue().sort(Comparator.comparing(x -> x.displayName));
        }
    }

    // -------------------------- Dedup per-ruota --------------------------
    /** Pattern per riconoscere suffissi e tag relativi alle 4 ruote, varie notazioni. */
    private static final Pattern WHEEL_TOKENS = Pattern.compile(
            "(^|[^a-zA-Z])(?:(?:f(?:ront)?)[ _-]?(?:l(?:eft)?)|fl|anteriore[ _-]?sinistra)\\b"
                    + "|(^|[^a-zA-Z])(?:(?:f(?:ront)?)[ _-]?(?:r(?:ight)?)|fr|anteriore[ _-]?destra)\\b"
                    + "|(^|[^a-zA-Z])(?:(?:r(?:ear)?)[ _-]?(?:l(?:eft)?)|rl|posteriore[ _-]?sinistra)\\b"
                    + "|(^|[^a-zA-Z])(?:(?:r(?:ear)?)[ _-]?(?:r(?:ight)?)|rr|posteriore[ _-]?destra)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /** Normalizza una chiave ignorando riferimenti alla singola ruota. */
    private String wheelAgnosticKey(String norm) {
        String s = norm.replace('_', ' ').replace('-', ' ');
        s = WHEEL_TOKENS.matcher(s).replaceAll(" ");
        s = s.replaceAll("\\b(fl|fr|rl|rr)\\b", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s.toLowerCase(Locale.ROOT);
    }

    /** Restituisce un pretty name “aggregato” quando trova varianti per-ruota. */
    private String generalizedPrettyName(String raw) {
        String base = prettyName(raw);
        String lowered = base.toLowerCase(Locale.ROOT);
        if (WHEEL_TOKENS.matcher(lowered).find() || lowered.matches(".*\\b(fl|fr|rl|rr)\\b.*")) {
            // Collassa in “<nome base> (per ruota)”
            String cleaned = prettyName(wheelAgnosticKey(ChannelAliases.norm(raw)));
            if (cleaned.isBlank()) cleaned = base;
            return cleaned + " (per ruota)";
        }
        return base;
    }

    private List<String> collectAliasesFor(String normalizedKey) {
        List<String> aliases = new ArrayList<>();
        try {
            var map = ChannelAliases.aliasMap(); // getter pubblico
            var thisChannel = map.get(normalizedKey);
            if (thisChannel != null) {
                for (var e : map.entrySet()) {
                    if (Objects.equals(e.getValue(), thisChannel)) aliases.add(e.getKey());
                }
            } else {
                // fallback: alias che condividono il "token" principale
                String token = mainToken(normalizedKey);
                for (var e : map.entrySet()) {
                    if (e.getKey().contains(token)) aliases.add(e.getKey());
                }
            }
        } catch (Throwable ignore) {}
        // Rende gli alias “carini” e rimuove duplicati; su metriche per-ruota sostituisce le 4 varianti con una sola chiave base
        return aliases.stream()
                .map(this::prettyName)
                .map(this::generalizedPrettyName) // armonizza alias “per ruota”
                .distinct()
                .sorted()
                .toList();
    }

    private static String mainToken(String norm) {
        for (String p : ChannelAliases.partialKeys()) // getter pubblico
            if (norm.contains(p)) return p;
        String[] parts = norm.split(" ");
        return parts.length > 0 ? parts[0] : norm;
    }

    // -------------------------- Heuristics --------------------------
    private String sectionFor(String norm) {
        if (containsAny(norm, "lap time", "last sector", "best lap")) return "Giri & Tempi";
        if (containsAny(norm, "lap #", "lap number", "session lap count", "lap ")) return "Giri & Tempi";
        if (containsAny(norm, "time", "distance")) return "Tempo & Distanza";
        if (containsAny(norm, "speed", "rpm", "gear", "throttle", "brake", "clutch", "engine", "limiter"))
            return "Powertrain & Comandi";
        if (containsAny(norm, "abs", "tc", "aid ")) return "Aiuti & Regolamenti";
        if (containsAny(norm, "ers", "kers", "drs")) return "ERS / KERS / DRS";
        if (containsAny(norm, "accel", "cg ", "cg height", "pitch", "roll", "yaw", "steer angle", "brake bias", "downforce", "seat force"))
            return "Assetto & Dinamica";
        if (containsAny(norm, "ride height", "suspension travel", "max sus travel")) return "Sospensioni & Altezze";
        if (containsAny(norm, "camber", "caster", "toe")) return "Geometrie Ruote";
        if (containsAny(norm, "brake temp")) return "Freni";
        if (containsAny(norm, "tire ", "tyre ", "wheel angular", "tire temp", "tire load", "tire pressure", "tire slip", "tire radius", "tire grip", "tire dirt"))
            return "Pneumatici";
        if (containsAny(norm, "fuel", "max fuel", "max power", "max torque", "turbo")) return "Fuel & Limiti Potenza";
        if (containsAny(norm, "air ", "road temp", "wind", "surface grip", "ballast")) return "Meteo & Pista";
        if (containsAny(norm, "car damage")) return "Danni";
        if (containsAny(norm, "car coord", "car pos norm", "chassis velocity")) return "Coordinate & Velocità Telaio";
        if (containsAny(norm, "flags", "in pit", "lap invalidated", "num tires off track")) return "Flags & Pit";
        if (containsAny(norm, "sample clock", "sample rate")) return "Telemetria & Sampling";
        if (containsAny(norm, "ffb", "steer torque", "wheel force", "self align torque")) return "FFB (Force Feedback)";
        return "Altro";
    }

    private String explanationFor(String norm, String display, String unit) {
        String u = unit.isEmpty() ? "" : " (" + unit + ")";
        if (containsAny(norm, "time [s]", "time", "absolute time"))
            return "Tempo assoluto campione per campione" + u + ".";
        if (containsAny(norm, "distance"))
            return "Distanza percorsa sul giro" + u + ", utile per allineare grafici lungo il tracciato.";
        if (containsAny(norm, "lap time", "best lap time", "last lap time"))
            return "Tempo sul giro" + u + " — obiettivo: più basso è meglio.";
        if (containsAny(norm, "last sector time")) return "Tempo dell’ultimo settore completato" + u + ".";
        if (containsAny(norm, "best lap delta")) return "Delta rispetto al miglior giro (negativo = stai migliorando).";
        if (containsAny(norm, "lap #", "lap number", "session lap count")) return "Indice/n° giro nella sessione.";
        if (containsAny(norm, "speed")) return "Velocità veicolo" + u + " (minima in curva, massima in rettilineo).";
        if (containsAny(norm, "rpm"))
            return "Giri motore" + u + ". Non superare il limitatore per evitare cali di prestazione.";
        if (containsAny(norm, "gear")) return "Marcia inserita.";
        if (containsAny(norm, "throttle")) return "Posizione acceleratore" + u + " — misura l’apertura del gas.";
        if (containsAny(norm, "brake pos", "brake"))
            return "Posizione freno" + u + " — misura l’intensità della frenata.";
        if (containsAny(norm, "clutch")) return "Posizione frizione" + u + " — utile in partenze e cambi marcia.";
        if (containsAny(norm, "engine limiter"))
            return "Limitatore motore attivo: taglio potenza vicino al regime massimo.";
        if (containsAny(norm, "engine brake setting"))
            return "Grado di freno motore: influisce sulla stabilità in rilascio.";
        if (containsAny(norm, "abs"))
            return "ABS (frenata assistita): riduce il bloccaggio ruote, stabilizza in staccata.";
        if (containsAny(norm, "tc ")) return "TC (traction control): limita lo slittamento in accelerazione.";
        if (containsAny(norm, "aid "))
            return "Aiuti di guida e regole server (cambio automatico, linea ideale, danni, ecc.).";
        if (containsAny(norm, "ers")) return "ERS: gestione energia ibrida (recupero e deployment).";
        if (containsAny(norm, "kers")) return "KERS: recupero energia in frenata e riutilizzo per accelerazione.";
        if (containsAny(norm, "drs")) return "DRS: ala mobile per ridurre resistenza aerodinamica in rettilineo.";
        if (containsAny(norm, "accel") || containsAny(norm, "cg accel"))
            return "Accelerazioni (longitudinale/laterale/verticale) in g — misura le forze in gioco.";
        if (containsAny(norm, "cg height")) return "Altezza del baricentro: influenza rollio, beccheggio e stabilità.";
        if (containsAny(norm, "pitch", "roll", "yaw"))
            return "Angoli e velocità del telaio (beccheggio/rollio/imbardata).";
        if (containsAny(norm, "steer angle")) return "Angolo sterzo" + u + " — quanto stai girando il volante.";
        if (containsAny(norm, "brake bias")) return "Ripartizione frenata (FR/RR): influenza stabilità in staccata.";
        if (containsAny(norm, "ride height"))
            return "Altezza da terra sulle 4 ruote: incide su rake e carico aerodinamico.";
        if (containsAny(norm, "suspension travel")) return "Corsa sospensioni: quanta escursione usi nelle varie fasi.";
        if (containsAny(norm, "max sus travel"))
            return "Corsa sospensioni massima registrata (utile per urti/fine corsa).";
        if (containsAny(norm, "camber"))
            return "Campanatura ruote (camber): aiuta l’aderenza in curva ma penalizza rettilineo.";
        if (containsAny(norm, "caster")) return "Caster: influenza ritorno dello sterzo e stabilità.";
        if (containsAny(norm, "toe in"))
            return "Convergenza (toe): stabilità in rettilineo vs prontezza in inserimento.";
        if (containsAny(norm, "brake temp"))
            return "Temperatura freni: troppo freddi frenano poco, troppo caldi degradano.";
        if (containsAny(norm, "tire pressure"))
            return "Pressione gomme: determina impronta a terra e temperatura di lavoro.";
        if (containsAny(norm, "tire load")) return "Carico sulla gomma: più carico = più grip (fino al limite).";
        if (containsAny(norm, "tire radius", "loaded radius"))
            return "Raggio gomma (statico/carico): misura deformazione e assetto.";
        if (containsAny(norm, "tire rubber grip"))
            return "Grip gomma modellato: indica la frazione di aderenza disponibile.";
        if (containsAny(norm, "tire slip angle")) return "Angolo di deriva: correlato al limite di aderenza laterale.";
        if (containsAny(norm, "tire slip ratio")) return "Slittamento longitudinale: importante in frenata e trazione.";
        if (containsAny(norm, "tire dirt level"))
            return "Sporcizia gomma: riduce temporaneamente grip dopo uscite pista.";
        if (containsAny(norm, "tire temp"))
            return "Temperature gomma (core/inner/middle/outer): finestra ideale = grip costante.";
        if (containsAny(norm, "wheel angular speed"))
            return "Velocità angolare ruota: utile per vedere bloccaggi o pattinamenti.";
        if (containsAny(norm, "fuel level")) return "Carburante residuo: incide su peso e bilanciamento.";
        if (containsAny(norm, "max fuel", "max power", "max torque", "max turbo"))
            return "Limiti e massimi del powertrain.";
        if (containsAny(norm, "air temp", "air density", "road temp", "wind", "surface grip", "ballast"))
            return "Condizioni meteo/pista & ballast.";
        if (containsAny(norm, "car damage")) return "Danni carrozzeria: aumentano resistenza e peggiorano bilancio.";
        if (containsAny(norm, "car coord", "car pos norm"))
            return "Coordinate vettura sul tracciato / progress sul giro.";
        if (containsAny(norm, "chassis velocity")) return "Velocità del telaio sui 3 assi.";
        if (containsAny(norm, "flags", "in pit", "lap invalidated", "off track"))
            return "Stato gara/pit e validità del giro.";
        if (containsAny(norm, "sample clock", "sample rate")) return "Frequenze di campionamento/clock telemetria.";
        if (containsAny(norm, "ffb", "steer torque", "self align torque", "wheel force"))
            return "Force Feedback: forza al volante e allineamento.";
        if (containsAny(norm, "seat force")) return "Forze sedile / accelerometri ausiliari: indizi su cordoli/urti e trasferimenti.";
        return "Metrica telemetrica/di sessione" + u + ".";
    }

    private static boolean containsAny(String s, String... keys) {
        String t = s.toLowerCase(Locale.ROOT);
        for (String k : keys) if (t.contains(k.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private String prettyName(String raw) {
        String r = raw.replace('_', ' ').replace('-', ' ');
        r = r.replaceAll("\\bfl\\b", "anteriore sinistra")
                .replaceAll("\\bfr\\b", "anteriore destra")
                .replaceAll("\\brl\\b", "posteriore sinistra")
                .replaceAll("\\brr\\b", "posteriore destra");
        String[] parts = r.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            if (p.length() == 1) {
                sb.append(p.toUpperCase()).append(' ');
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private String extractUnit(String raw) {
        int i1 = raw.indexOf('[');
        int i2 = raw.indexOf(']');
        if (i1 >= 0 && i2 > i1) return raw.substring(i1 + 1, i2);
        int p1 = raw.indexOf('(');
        int p2 = raw.indexOf(')');
        if (p1 >= 0 && p2 > p1) return raw.substring(p1 + 1, p2);
        return "";
    }

    // -------------------------- Consigli setup (Aumenta/Diminuisci) --------------------------
    /**
     * Genera consigli di set-up (stringa breve con “Aumenta se… / Diminuisci se…”)
     * in base al tipo di metrica o alla sezione in cui cade.
     */
    private String setupTipsFor(String norm, String section, String display) {
        String n = norm.toLowerCase(Locale.ROOT);

        // Pneumatici
        if (containsAny(n, "tire pressure", "tyre pressure")) {
            return bulletTips(
                    "Aumenta se: le spalle surriscaldano, l'auto è troppo reattiva, vuoi ridurre rolling-resistance in rettilineo.",
                    "Diminuisci se: impronta a terra insufficiente, difficoltà a portare in temperatura, usura irregolare (bordi freddi).",
                    "Obiettivo: pressioni “a caldo” nella finestra consigliata dal costruttore/mod."
            );
        }
        if (containsAny(n, "camber")) {
            return bulletTips(
                    "Aumenta (più negativo) se: serve più grip in curva prolungata, bordi esterni surriscaldano.",
                    "Diminuisci (meno negativo) se: troppo usura lato interno, instabilità in frenata/ rettilineo.",
                    "Nota: controlla le temperature inner/middle/outer: più uniformi → camber corretto."
            );
        }
        if (containsAny(n, "toe")) {
            return bulletTips(
                    "Aumenta toe-in anteriore se: vuoi più stabilità in rettilineo.",
                    "Diminuisci (o usa toe-out) se: vuoi inserimento più rapido e rotazione in ingresso.",
                    "Posteriore: più toe-in = stabilità in trazione; troppo toe-in = usura/drag."
            );
        }
        if (containsAny(n, "caster")) {
            return bulletTips(
                    "Aumenta se: desideri più auto-centraggio e stabilità a velocità elevate.",
                    "Diminuisci se: sterzo troppo pesante o risposta lenta nei cambi di direzione."
            );
        }
        if (containsAny(n, "ride height")) {
            return bulletTips(
                    "Aumenta se: tocchi il fondo, superi cordoli aggressivi, vuoi più stabilità sui bump.",
                    "Diminuisci se: cerchi più efficienza aero/abbassare il CoG (attenzione a bottoming).",
                    "Rake: anteriore più basso e posteriore più alto → più carico; esagera = instabilità alta velocità."
            );
        }
        if (containsAny(n, "arb", "anti-roll bar")) {
            return bulletTips(
                    "Aumenta anteriore se: vuoi ridurre rollio e sottosterzo in ingresso/medio curva.",
                    "Diminuisci anteriore se: vuoi più grip anteriore su bump/cordoli.",
                    "Posteriore più rigido → rotazione migliore ma rischio sovrasterzo in trazione."
            );
        }
        if (containsAny(n, "spring rate", "spring ")) {
            return bulletTips(
                    "Aumenta se: bottoming frequente, trasferimenti eccessivi, risposta più secca desiderata.",
                    "Diminuisci se: vuoi più meccanico su asfalti sconnessi/cordoli, trazione su uscite sporche.",
                    "Ricorda di ritarare i damper dopo grosse variazioni."
            );
        }
        if (containsAny(n, "damper", "bump", "rebound")) {
            return bulletTips(
                    "Aumenta Bump se: compressione troppo rapida, eccesso di affondamento in staccata.",
                    "Aumenta Rebound se: rimbalzo/oscillazioni dopo il trasferimento, perdita di contatto in estensione.",
                    "Diminuisci se: la vettura risulta “legata” e perde aderenza su bump/cordoli."
            );
        }
        if (containsAny(n, "brake bias")) {
            return bulletTips(
                    "Sposta in avanti se: il retro si muove troppo in staccata (tendenza al sovrasterzo).",
                    "Sposta indietro se: sottosterzo marcato e ruote anteriori bloccano facilmente."
            );
        }
        if (containsAny(n, "differential", "preload", "power ramp", "coast ramp")) {
            return bulletTips(
                    "Aumenta Precarico/Power se: vuoi più trazione in uscita su asfalto liscio (occhio al sottosterzo in uscita).",
                    "Diminuisci Precarico/Coast se: vuoi rotazione in ingresso e ridurre trascinamento a gas chiuso.",
                    "Rifletti sul circuito: tornanti → più lock in trazione; rapidi cambi direzione → troppo lock penalizza."
            );
        }
        if (containsAny(n, "wing", "downforce", "aero")) {
            return bulletTips(
                    "Aumenta se: serve stabilità in curva veloce e in appoggio.",
                    "Diminuisci se: troppa resistenza in rettilineo, fatichi a raggiungere la Vmax.",
                    "Bilanciamento: più ala posteriore → stabilità ma possibile sottosterzo medio/uscita."
            );
        }
        if (containsAny(n, "engine brake")) {
            return bulletTips(
                    "Aumenta (più freno motore) se: cerchi più decelerazione a gas chiuso e inserimento “puntato”.",
                    "Diminuisci se: il posteriore si alleggerisce troppo in rilascio (instabilità)."
            );
        }
        if (containsAny(n, "abs")) {
            return bulletTips(
                    "Aumenta se: blocchi spesso le anteriori, pista scivolosa/bagnata.",
                    "Diminuisci se: vuoi massimizzare la potenza frenante su grip elevato (servono piedi raffinati)."
            );
        }
        if (containsAny(n, "tc ")) {
            return bulletTips(
                    "Aumenta se: troppo pattinamento in uscita curva, degrado gomme eccessivo.",
                    "Diminuisci se: l’auto “taglia” troppo potenza e non accelera bene su alto grip."
            );
        }

        // Sezione “Coach” o “Setup”: fallback generico utile
        if ("Setup".equals(section) || "Coach".equals(section)) {
            return bulletTips(
                    "Osserva prima i dati: temperatura, usura e comportamento dinamico indicano la direzione.",
                    "Applica piccole variazioni (1-2 click) e verifica con stint comparabili.",
                    "Non cambiare più di un parametro per volta se possibile."
            );
        }

        return "";
    }

    private String bulletTips(String... lines) {
        return Arrays.stream(lines).filter(s -> s != null && !s.isBlank())
                .map(s -> "• " + s)
                .collect(Collectors.joining("\n"));
    }

    /** Voci aggiuntive per Glossario: termini di Setup & Coach (solo dati testuali, logica invariata). */
    private List<Entry> buildSetupCoachExtraEntries() {
        List<Entry> list = new ArrayList<>();
        // --- SETUP ---
        list.add(new Entry(
                "Pressione gomme (per ruota)", "Setup",
                "Pressione di gonfiaggio degli pneumatici. Influisce su grip, temperatura e usura.",
                "psi / bar",
                List.of("Tyre Pressure", "Pneumatici Pressione", "Tire Pres"),
                setupTipsFor("tire pressure", "Setup", "Pressione gomme (per ruota)")
        ));
        list.add(new Entry(
                "Campanatura (Camber) (per ruota)", "Setup",
                "Inclinazione della ruota rispetto alla verticale. Negativo = ruota inclinata verso l'interno.",
                "deg",
                List.of("Camber", "Camber anteriore", "Camber posteriore"),
                setupTipsFor("camber", "Setup", "Campanatura (Camber)")
        ));
        list.add(new Entry(
                "Convergenza (Toe) (per ruota)", "Setup",
                "Angolo di convergenza/divergenza delle ruote guardando dall'alto. Influisce su stabilità e usura.",
                "deg",
                List.of("Toe", "Toe-In", "Toe-Out", "Convergenza"),
                setupTipsFor("toe", "Setup", "Convergenza (Toe)")
        ));
        list.add(new Entry(
                "Caster", "Setup",
                "Inclinazione dell'asse di sterzo. Aumenta stabilità e ritorno del volante ma può ridurre agilità.",
                "deg",
                List.of("Caster Angle"),
                setupTipsFor("caster", "Setup", "Caster")
        ));
        list.add(new Entry(
                "Barre antirollio (ARB)", "Setup",
                "Rigidezza delle barre antirollio. Controlla il rollio e il bilanciamento tra avantreno e retrotreno.",
                "",
                List.of("Anti-Roll Bar", "ARB Front", "ARB Rear"),
                setupTipsFor("anti-roll bar", "Setup", "Barre antirollio")
        ));
        list.add(new Entry(
                "Molle (Spring Rate)", "Setup",
                "Rigidezza delle molle delle sospensioni. Influisce su trasferimenti di carico e risposta.",
                "N/mm",
                List.of("Spring", "Spring Rate", "Molle"),
                setupTipsFor("spring rate", "Setup", "Molle")
        ));
        list.add(new Entry(
                "Altezza da terra (Ride Height)", "Setup",
                "Distanza fondo-vettura/terreno. Cambia aerodinamica, baricentro e fondo.",
                "mm",
                List.of("Ride Height", "Rake"),
                setupTipsFor("ride height", "Setup", "Altezza da terra")
        ));
        list.add(new Entry(
                "Rake (concetto)", "Setup",
                "Differenza di altezza tra posteriore e anteriore. Più rake → avantreno più carico ma più drag se eccessivo.",
                "mm",
                List.of("Rake", "Rake Angle", "Assetto rake"),
                bulletTips("Aumenta: +1 mm al posteriore o -1 mm all’anteriore per più carico davanti.",
                        "Diminuisci: -1/-2 mm al posteriore per stabilità alta velocità e meno drag.")
        ));
        list.add(new Entry(
                "Ala/Carico aerodinamico", "Setup",
                "Imposta il carico aerodinamico (ala). Più carico = più grip in curva, meno velocità in rettilineo.",
                "",
                List.of("Wing", "Downforce", "Ala"),
                setupTipsFor("wing downforce aero", "Setup", "Ala")
        ));
        list.add(new Entry(
                "Differenziale (Precarico / Power / Coast)", "Setup",
                "Ripartizione della coppia tra le ruote motrici. Precarico, in trazione (Power) e rilascio (Coast).",
                "",
                List.of("Differential", "Preload", "Power Ramp", "Coast Ramp"),
                setupTipsFor("differential preload power coast", "Setup", "Differenziale")
        ));
        list.add(new Entry(
                "Freni (Bias/Brake Balance)", "Setup",
                "Ripartizione frenante tra asse anteriore e posteriore. Influisce su stabilità in frenata.",
                "%",
                List.of("Brake Bias", "Brake Balance", "Ripartizione freni"),
                setupTipsFor("brake bias", "Setup", "Freni")
        ));
        list.add(new Entry(
                "Smorzatori (Dampers: Bump/Rebound)", "Setup",
                "Ammortizzazione in compressione (Bump) e estensione (Rebound). Regola velocità di movimento sospensione.",
                "",
                List.of("Damper", "Bump", "Rebound", "Compressione", "Estensione"),
                setupTipsFor("damper bump rebound", "Setup", "Smorzatori")
        ));
        list.add(new Entry(
                "Differenziale elettronico (se presente)", "Setup",
                "Mappa elettronica del differenziale/controllo di trazione se disponibile.",
                "",
                List.of("eDiff", "TC", "Traction Control"),
                setupTipsFor("tc ediff", "Setup", "Differenziale elettronico")
        ));
        list.add(new Entry(
                "Rapporti cambio", "Setup",
                "Scelta dei rapporti per ottimizzare accelerazione e velocità massima in base al tracciato.",
                "",
                List.of("Gear Ratios", "Final Drive"),
                bulletTips(
                        "Più corti: accelerazione migliore, rischio limitatore in rettilineo.",
                        "Più lunghi: Vmax maggiore, ma uscita di curva meno brillante.",
                        "Adatta in funzione del tracciato e della curva più lenta."
                )
        ));
        // --- COACH (nuovi termini aggiunti) ---
        list.add(new Entry("Overlap gas/freno", "Coach",
                "Fase in cui acceleratore e freno sono premuti insieme. Allunga le frenate e scalda eccessivamente gli impianti.",
                "",
                List.of("Throttle/Brake Overlap", "Overlap", "Gas+Freno"),
                bulletTips("Riduci: rilascia il freno prima di aprire il gas; usa trail braking più pulito.")
        ));
        list.add(new Entry("Coasting", "Coach",
                "Tratto senza gas e senza freno. Può indicare indecisione o punti di riferimento non ottimali.",
                "",
                List.of("Off-throttle", "Neutral Coasting"),
                bulletTips("Riduci: ritarda un filo la frenata o anticipa leggermente il gas per più scorrevolezza.")
        ));
        list.add(new Entry("Throttle oscillation", "Coach",
                "Oscillazioni rapide del pedale gas. Sintomo di trazione precaria o modulazione non lineare.",
                "",
                List.of("Oscillazione acceleratore", "Throttle dithering"),
                bulletTips("Usa mappa gas più progressiva; lavora su TC/assetto se avviene in trazione.")
        ));
        list.add(new Entry("Brake stomp", "Coach",
                "Picco rapido e corto di pressione freno. Può destabilizzare l’auto in ingresso.",
                "",
                List.of("Brake spike", "Brake jab"),
                bulletTips("Modula più progressivo; se ricorrente, rivedi bump/rebound anteriori e pressione freno.")
        ));
        list.add(new Entry("Inversioni sterzo per minuto", "Coach",
                "Numero di cambi di segno dello sterzo/minuto. Alto = guida nervosa o bilancio instabile.",
                "1/min",
                List.of("Steer reversals/min", "Reversals per minute"),
                bulletTips("Lavora su traiettoria più pulita; se l’auto “galleggia”, ammorbidisci leggermente il posteriore.")
        ));
        list.add(new Entry("FFB clipping", "Coach",
                "Saturazione della forza al volante: perdi informazione tattile sul limite.",
                "",
                List.of("Force Feedback clipping", "FFB sat"),
                bulletTips("Riduci il gain o aumenta filtro; evita carichi eccessivi sull’anteriore in ingresso.")
        ));
        list.add(new Entry("Seat force / Kerb", "Coach",
                "Forze sedile/urti sui cordoli. Picchi elevati indicano impatti/kerb aggressivi.",
                "",
                List.of("Seat Force", "Seat Left/Right/Rear"),
                bulletTips("Evita cordoli alti a ruote sterzate; se necessario, irrigidisci leggermente il rebound.")
        ));
        list.add(new Entry("Profilo veicolo (Categoria/Trammissione/Powertrain)", "Coach",
                "Classificazione euristica del veicolo: Formula/Prototype/GT/Road, RWD/FWD/AWD, NA/Turbo/Hybrid.",
                "",
                List.of("Vehicle profile", "Category / Drivetrain / Powertrain"),
                bulletTips("Adatta i target termici e i consigli in base alla categoria identificata.")
        ));
        list.add(new Entry("Finestra temperatura gomme (target veicolo)", "Pneumatici",
                "Intervallo di temperatura core ideale variabile per categoria (Formula > GT > Road).",
                "°C",
                List.of("Tyre temp window", "Target tyre temp"),
                bulletTips("Se sei sotto target: guida più aggressivo o riduci leggermente pressioni.",
                        "Se sei sopra target: guida più rotonda, alza un filo pressioni o più carico aero.")
        ));
        list.add(new Entry("Finestra temperatura freni (target veicolo)", "Freni",
                "Intervallo di temperatura freni ideale (carbonio molto più alto delle acciaio).",
                "°C",
                List.of("Brake temp window", "Target brake temp"),
                bulletTips("Sotto target: chiudi leggermente i ducts; Sopra target: apri ducts o fai cooling lap.")
        ));
        list.add(new Entry("Limitatore (colpi)", "Coach",
                "Tempo passato a limitatore. Troppo frequente indica rapporti troppo corti o eccesso di drag.",
                "",
                List.of("Limiter hits", "Engine limiter"),
                bulletTips("Allunga rapporto finale o riduci leggermente l’ala posteriore se l’auto resta stabile.")
        ));

        // --- COACH (già presenti) ---
        list.add(new Entry("Punto di corda (Apex)", "Coach",
                "Punto più interno della curva attraversato dall'auto: anticipato, ritardato o standard.", "",
                List.of("Apex", "Punto corda"),
                bulletTips("Apex anticipato: uscita rapida su rettilineo corto.", "Apex ritardato: massimizza accelerazione su rettilineo lungo.")
        ));
        list.add(new Entry("Trail Braking", "Coach",
                "Rilascio progressivo del freno dentro la curva per trasferire carico all'avantreno.", "",
                List.of("Frenata in appoggio", "Trail"),
                bulletTips("Se sottosterzi in ingresso: aumenta leggermente il trail (rilascio più graduale).",
                        "Se l'auto diventa nervosa: riduci il trail o sposta bias avanti.")
        ));
        list.add(new Entry("Rotazione a centro curva", "Coach",
                "Capacità dell'auto di ruotare nel medio curva senza sottosterzo/sovrasterzo eccessivo.", "",
                List.of("Mid-corner Rotation"),
                bulletTips("Migliora con: più camber negativo davanti, ARB posteriore più rigida, più rake.",
                        "Se sovrasterza: riduci ARB posteriore o aumenta toe-in posteriore.")
        ));
        list.add(new Entry("Uscita di curva (Traction)", "Coach",
                "Gestione trazione in uscita per massimizzare accelerazione senza pattinamento.", "",
                List.of("Traction", "Wheelspin"),
                bulletTips("Migliora con: più toe-in posteriore, meno precarico diff in coast, TC leggermente più alto.",
                        "Evita eccesso TC se taglia troppa potenza.")
        ));
        list.add(new Entry("Gestione gomme (Tyre management)", "Coach",
                "Strategie per limitare usura/overheating e mantenere la finestra di temperatura.", "",
                List.of("Tyre Temp", "Usura gomme"),
                bulletTips("Se surriscaldi: alza pressioni a freddo con cautela o riduci camber/drag; guida più rotonda.",
                        "Se non vai in temperatura: riduci leggermente pressioni o guida più aggressivo sugli input.")
        ));
        return list;
    }
}
