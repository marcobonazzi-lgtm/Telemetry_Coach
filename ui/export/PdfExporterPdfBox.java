package org.simulator.ui.export;

import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.coach.Coach;
import org.simulator.ui.DataController;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.simulator.ui.export.LapResolver.resolveLaps;

// ==== INTEGRAZIONI (coach / setup / circuito) ====
import org.simulator.setup.setup_advisor.SetupAdvisor;
import org.simulator.setup.setup_advisor.SetupAdvisor.DriverStyle;
import org.simulator.setup.setup_advisor.SetupAdvisor.Recommendation;
import org.simulator.setup.setup_advisor.SetupAdvisor.Severity;
import org.simulator.setup.setup_advisor.SetupAdvisor.Assessment;
import org.simulator.setup.setup_advisor.VehicleTraits;
import org.simulator.tracks.SessionPreamble;
import org.simulator.tracks.StaticTrackDB;
import org.simulator.tracks.TrackInfo;

public class PdfExporterPdfBox implements PdfExporter {

    // Sottotitolo cover (veicolo + tracciato)
    private String reportSubtitle;

    // Layout
    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float MARGIN = 36f; // 0.5"
    private static final float CONTENT_WIDTH = PAGE_SIZE.getWidth() - 2 * MARGIN;

    // Stile tabella
    private static final float GRID_THICKNESS = 0.5f;
    // Canale | Lap | Media | Max | Unità
    private static final float[] COLS_TABLE = new float[]{0.38f, 0.12f, 0.18f, 0.18f, 0.14f};
    private static final String APP_LOGO_PATH = "C:\\Users\\addir\\Desktop\\TESI\\TELEMETRY_COACH\\src\\main\\resources\\assets\\app_logo.png";
    private static final String BG_IMAGE_PATH  = "C:\\Users\\addir\\Desktop\\TESI\\TELEMETRY_COACH\\src\\main\\resources\\assets\\assetto_corsa_bg.png";

    private static class SectionRef {
        final String title;
        final PDPage page;
        SectionRef(String title, PDPage page){ this.title = title; this.page = page; }
    }
    private record ImgItem(String caption, File file) {}
    private static class Row {
        final String c, lap, mean, max, unit;
        Row(String c, String lap, String mean, String max, String unit){ this.c=c; this.lap=lap; this.mean=mean; this.max=max; this.unit=unit; }
    }

    @Override
    public void export(Window owner, DataController data, ExportOptions options) throws Exception {
        // --- Sottotitolo: [veicolo] [tracciato] ---
        try {
            var preSubtitle = SessionPreamble.parse(data.getCsvPath());

            // ---- veicolo ----
            String carName = null;
            try { carName = (String) SessionPreamble.class.getField("car").get(preSubtitle); } catch (Throwable __) {}
            if (carName == null) { try { carName = (String) SessionPreamble.class.getMethod("car").invoke(preSubtitle); } catch (Throwable __) {} }
            if (carName == null) { try { carName = (String) SessionPreamble.class.getMethod("getCar").invoke(preSubtitle); } catch (Throwable __) {} }
            if (carName == null) { try { carName = (String) SessionPreamble.class.getMethod("vehicle").invoke(preSubtitle); } catch (Throwable __) {} }
            if (carName == null) { try { carName = (String) SessionPreamble.class.getMethod("getVehicle").invoke(preSubtitle); } catch (Throwable __) {} }
            // fallback CSV
            if (carName == null || carName.isBlank()) {
                carName = tryReadVehicleFromCsv(data.getCsvPath());
            }

            // ---- utente/pilota ----
            String userName = null;
            try { userName = (String) SessionPreamble.class.getField("driver").get(preSubtitle); } catch (Throwable __) {}
            if (userName == null) { try { userName = (String) SessionPreamble.class.getMethod("driver").invoke(preSubtitle); } catch (Throwable __) {} }
            if (userName == null) { try { userName = (String) SessionPreamble.class.getMethod("getDriver").invoke(preSubtitle); } catch (Throwable __) {} }
            if (userName == null) { try { userName = (String) SessionPreamble.class.getMethod("user").invoke(preSubtitle); } catch (Throwable __) {} }
            if (userName == null) { try { userName = (String) SessionPreamble.class.getMethod("getUser").invoke(preSubtitle); } catch (Throwable __) {} }

            // sottotitolo finale
            String left  = (carName == null || carName.isBlank()) ? "Veicolo" : carName.trim();
            String right = (userName == null || userName.isBlank()) ? "" : userName.trim();
            reportSubtitle = right.isBlank() ? left : (left + " — " + right);

        } catch (Throwable __) {
            reportSubtitle = null;
        }

        // 1) Path di output
        FileChooser fc = new FileChooser();
        fc.setTitle("Salva PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File outPdf = fc.showSaveDialog(owner);
        if (outPdf == null) return;

        // 2) Dati & grafici
        List<Lap> lapsAll = data.getLaps();
        List<Lap> lapsSel = resolveLaps(lapsAll, options);

        File work = java.nio.file.Files.createTempDirectory("tc_pdfbox_").toFile();
        work.deleteOnExit();
        File chartsDir = new File(work, "charts"); chartsDir.mkdirs();

        // Generazione Grafici Standard
        File speedOverlay = ChartGenerator.speedOverlay(chartsDir, lapsSel);
        File ggDiagram    = ChartGenerator.ggDiagram(chartsDir, lapsSel);
        File brakeTemps   = ChartGenerator.brakeTemps(chartsDir, lapsSel);
        File tireTemps    = ChartGenerator.tireTemps(chartsDir, lapsSel);

        // NEW: Generazione condizionale Delta Time
        File deltaTimeChart = null;
        if (options.includeDeltaChart()) {
            try {
                deltaTimeChart = ChartGenerator.deltaTime(chartsDir, lapsSel);
            } catch (Exception e) {
                System.err.println("Impossibile generare grafico Delta: " + e.getMessage());
            }
        }

        var lapStats = StatsCalculator.perLap(lapsSel);

        // 3) Costruzione PDF
        List<SectionRef> sections = new ArrayList<>();
        try (PDDocument doc = new PDDocument()) {
            // pagina ToC (riempita alla fine)
            PDPage tocPage = new PDPage(PAGE_SIZE);
            doc.addPage(tocPage);

            // SEZIONE GRAFICI
            List<ImgItem> chartItems = new ArrayList<>();
            chartItems.add(new ImgItem("Velocità vs Distanza (overlay)", speedOverlay));

            // Inserisci Delta Time subito dopo la velocità (se esiste)
            if (deltaTimeChart != null) {
                chartItems.add(new ImgItem("Delta Time (vs Best)", deltaTimeChart));
            }

            chartItems.add(new ImgItem("G-G Diagram", ggDiagram));
            chartItems.add(new ImgItem("Temperature freni - media per lap", brakeTemps));
            chartItems.add(new ImgItem("Temperature pneumatici - media per lap", tireTemps));

            sections.add(new SectionRef("Grafici principali", addChartsPages(doc, chartItems)));

            // TABELLE CANALI PRIMARI/SECONDARI
            Set<Channel> present = detectPresentChannels(lapsSel);
            List<Channel> primary = RaceEngineerChannels.PRIMARY.stream().filter(present::contains).collect(Collectors.toList());
            List<Channel> secondary = RaceEngineerChannels.SECONDARY.stream().filter(present::contains).collect(Collectors.toList());

            if (options.includePrimaryTable() && !primary.isEmpty()) {
                sections.add(new SectionRef("Tabella canali primari",
                        addChannelsTable(doc, "Tabella canali primari", primary, lapsSel)));
            }
            if (options.includeSecondaryTable() && !secondary.isEmpty()) {
                sections.add(new SectionRef("Tabella canali secondari",
                        addChannelsTable(doc, "Tabella canali secondari", secondary, lapsSel)));
            }

            // ANALISI PER GIRO
            PDPage firstLapPage = addPerLapPages(doc, lapsSel, lapStats, chartsDir, data, options);
            if (firstLapPage != null) {
                sections.add(new SectionRef("Analisi per giro", firstLapPage));
            }

            // COACHING SESSIONE
            if (options.coachingScope() == CoachingScope.FULL_SESSION) {
                sections.add(new SectionRef("Coaching - Sessione",
                        addCoachTablePage(doc, "Coaching - Sessione (giri selezionati)", coachNotesForSessionList(lapsSel, data))));
            }

            // CIRCUITO
            if (options.includeCircuit()) {
                sections.add(new SectionRef("Circuito",
                        addCircuitPage(doc, data, options)));
            }

            // SETUP
            if (options.includeSetup()) {
                sections.add(new SectionRef("Setup",
                        addSetupTablePage(doc, "Setup", data, options)));
            }

            // TOC + OUTLINE + NUMERI PAGINA
            writeTocAndOutline(doc, tocPage, options, lapsSel, sections);
            addPageNumbers(doc);

            // Logo e Sfondo
            File appLogo = new File(APP_LOGO_PATH);
            if (appLogo.exists()) {
                stampLogoTopRightAllPages(doc, appLogo, 56f);
            }

            File bgFile = new File(BG_IMAGE_PATH);
            if (bgFile.exists()) {
                applyBackgroundToAllPages(doc, bgFile, 0.20f);
            }

            // Salva
            doc.save(outPdf);
        }
    }

    /* ========================== PAGINE =========================== */

    private PDPage addChartsPages(PDDocument doc, List<ImgItem> items) throws Exception {
        PDPage first = null;
        int i = 0;
        while (i < items.size()) {
            PDPage page = new PDPage(PAGE_SIZE);
            if (first == null) first = page;
            doc.addPage(page);

            // precarico immagini
            List<ImgItem> chunk = items.subList(i, Math.min(i + 2, items.size()));
            List<PDImageXObject> imgs = new ArrayList<>();
            for (ImgItem it : chunk) imgs.add(loadImage(doc, it.file));

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PAGE_SIZE.getHeight() - MARGIN;
                y = drawHeading(cs, "Grafici principali", 18, y);

                float imgWidth = CONTENT_WIDTH;
                float maxImgHeight = (PAGE_SIZE.getHeight() - 2 * MARGIN - 60) / 2f;

                for (int k = 0; k < chunk.size(); k++) {
                    ImgItem it = chunk.get(k);
                    PDImageXObject im = imgs.get(k);
                    y -= 10;
                    if (im != null) {
                        float scale = Math.min(imgWidth / im.getWidth(), maxImgHeight / im.getHeight());
                        float w = im.getWidth() * scale;
                        float h = im.getHeight() * scale;
                        float x = MARGIN + (CONTENT_WIDTH - w) / 2f;
                        y -= h;
                        cs.drawImage(im, x, y, w, h);
                        y -= 12;
                    }
                    y = drawSmall(cs, it.caption, y);
                    y -= 12;
                }
            }
            i += 2;
        }
        return first;
    }

    private PDPage addPerLapPages(PDDocument doc, List<Lap> lapsSel,
                                  List<StatsCalculator.LapStats> stats,
                                  File chartsDir, DataController data, ExportOptions opts) throws Exception {
        if (lapsSel == null || lapsSel.isEmpty()) return null;
        Map<Integer, StatsCalculator.LapStats> byIdx = new LinkedHashMap<>();
        for (var st : stats) byIdx.put(st.lapIndex, st);

        PDPage first = null;
        for (Lap lap : lapsSel) {
            var st = byIdx.get(lap.index);
            PDImageXObject tbImage = loadImage(doc, ChartGenerator.throttleBrake(chartsDir, lap));

            PDPage page = new PDPage(PAGE_SIZE);
            if (first == null) first = page;
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PAGE_SIZE.getHeight() - MARGIN;
                y = drawHeading(cs, "Giro " + lap.index, 18, y);

                if (st != null) {
                    y = drawKeyVal(cs, "Lap time [s]", fmt(st.lapTime), y);
                    y = drawKeyVal(cs, "Vmax [km/h]", fmt1(st.vmax), y);
                    y = drawKeyVal(cs, "Vavg [km/h]", fmt1(st.vavg), y);
                    y = drawKeyVal(cs, "Lateral G max [g]", fmt2(st.latGmax), y);
                    y = drawKeyVal(cs, "Long G min (fr) [g]", fmt2(st.longGmin), y);
                    y = drawKeyVal(cs, "Long G max (tr) [g]", fmt2(st.longGmax), y);
                    y = drawKeyVal(cs, "Throttle medio [%]", fmt1(st.throttleMean), y);
                    y = drawKeyVal(cs, "Brake duty [%]", fmt1(st.brakeDuty), y);
                }

                y -= 12;
                if (tbImage != null) {
                    float imgW = CONTENT_WIDTH;
                    float imgH = (PAGE_SIZE.getHeight() - 2 * MARGIN - 320);
                    float scale = Math.min(imgW / tbImage.getWidth(), imgH / tbImage.getHeight());
                    float w = tbImage.getWidth() * scale;
                    float h = tbImage.getHeight() * scale;
                    float x = MARGIN + (CONTENT_WIDTH - w) / 2f;
                    y -= h;
                    cs.drawImage(tbImage, x, y, w, h);
                    y -= 10;
                    y = drawSmall(cs, "Throttle/Brake vs Distanza", y);
                }

                // Coach per lap in tabella (wrapping)
                if (opts.coachingScope() == CoachingScope.SELECTED_LAPS) {
                    y -= 16;
                    y = drawHeading(cs, "Coaching - Giro " + lap.index, 14, y);
                    List<String> notes = coachNotesForLapList(lap.index, data);
                    y = drawNotesTableInlineWrapped(cs, y, notes);
                }
            }
        }
        return first;
    }

    /* ====================== PAGINE SPECIALI A TABELLA ======================= */

    private PDPage addCoachTablePage(PDDocument doc, String title, List<String> notes) throws Exception {
        PDPage first = null;
        float baseRowH = 18f, headerH = 22f;
        float[] widths = new float[]{ CONTENT_WIDTH * 0.10f, CONTENT_WIDTH * 0.90f };
        int fontSize = 10;

        int idx = 0;
        do {
            PDPage page = new PDPage(PAGE_SIZE);
            if (first == null) first = page;
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PAGE_SIZE.getHeight() - MARGIN;
                y = drawHeading(cs, title, 18, y);

                float x = MARGIN;
                drawTableHeader(cs, new String[]{"#", "Nota"}, x, y, widths, headerH);
                y -= headerH;

                while (idx < (notes==null?0:notes.size())) {
                    String[] cols = new String[]{ Integer.toString(idx+1), notes.get(idx) };
                    float rh = measureRowHeightWrapped(cols, widths, baseRowH, fontSize);
                    if (y - rh < MARGIN + 20) break;
                    drawTableRowWrapped(cs, cols, x, y, widths, baseRowH, fontSize, (idx % 2)==0);
                    y -= rh;
                    idx++;
                }

                if (idx==0 && (notes==null || notes.isEmpty())) {
                    drawTableRowWrapped(cs, new String[]{"-", "Nessuna nota disponibile"}, x, y, widths, baseRowH, fontSize, true);
                    idx = 1; // per uscire
                }
            }
        } while (idx < (notes==null?0:notes.size()));
        return first;
    }

    private PDPage addSetupTablePage(PDDocument doc, String title, DataController data, ExportOptions opts) throws Exception {
        List<Lap> laps = data.getLaps();
        Assessment assess = SetupAdvisor.analyzeStyleDetailed(laps);
        DriverStyle style = assess.primary();
        List<Recommendation> recs = Optional.ofNullable(SetupAdvisor.forSession(laps, style)).orElse(List.of());

        // ordina per severita' e area
        recs = new ArrayList<>(recs);
        recs.sort((a,b) -> {
            int s = Integer.compare(sevRank(b.sev()), sevRank(a.sev()));
            if (s!=0) return s;
            String aa = a.area()==null? "" : a.area();
            String bb = b.area()==null? "" : b.area();
            return aa.compareToIgnoreCase(bb);
        });

        PDPage first = null;
        float baseRowH = 18f, headerH = 22f;
        float[] widths = new float[]{ CONTENT_WIDTH * 0.28f, CONTENT_WIDTH * 0.18f, CONTENT_WIDTH * 0.54f };
        int fontSize = 10;
        int idx = 0;

        do {
            PDPage page = new PDPage(PAGE_SIZE);
            if (first == null) first = page;
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PAGE_SIZE.getHeight() - MARGIN;
                y = drawHeading(cs, title, 18, y);

                // breve intestazione stile
                y = drawKeyVal(cs, "Stile guida prevalente", String.valueOf(style), y);

                float x = MARGIN;
                drawTableHeader(cs, new String[]{"Area", "Severita'", "Messaggio"}, x, y, widths, headerH);
                y -= headerH;

                while (idx < recs.size()) {
                    Recommendation r = recs.get(idx);
                    String sev = r.sev()==null ? "" : switch (r.sev()){
                        case HIGH -> "[ALTA]";
                        case MEDIUM -> "[MEDIA]";
                        default -> "[BASSA]";
                    };
                    String area = (r.area()==null || r.area().isBlank()) ? "Generale" : r.area();
                    String[] cols = new String[]{ area, sev, r.message() };

                    float rh = measureRowHeightWrapped(cols, widths, baseRowH, fontSize);
                    if (y - rh < MARGIN + 20) break;

                    drawTableRowWrapped(cs, cols, x, y, widths, baseRowH, fontSize, (idx % 2)==0);
                    y -= rh;
                    idx++;
                }

                if (recs.isEmpty()) {
                    drawTableRowWrapped(cs, new String[]{ "-", "-", "Nessuna raccomandazione rilevante" }, x, y, widths, baseRowH, fontSize, true);
                    idx = 1;
                }
            }
        } while (idx < recs.size());

        return first;
    }
    private PDPage addCircuitPage(PDDocument doc, DataController data, ExportOptions opts) throws Exception {
        var pre = SessionPreamble.parse(data.getCsvPath());
        String baseId = (pre == null || pre.venue == null) ? null : pre.venue.toLowerCase(Locale.ROOT);
        TrackInfo base = (baseId == null ? null : StaticTrackDB.get(baseId));

        VehicleTraits traits = VehicleTraits.detect(data.getLaps());
        String catKey = switch (traits.category) {
            case FORMULA -> "FORMULA";
            case PROTOTYPE -> "PROTOTYPE";
            case GT -> "GT";
            case ROAD -> "ROAD";
            default -> "OTHER";
        };

        PDPage firstPage = new PDPage(PAGE_SIZE);
        doc.addPage(firstPage);

        if (base == null) {
            try (PDPageContentStream cs = new PDPageContentStream(doc, firstPage)) {
                float y = PAGE_SIZE.getHeight() - MARGIN;
                y = drawHeading(cs, "Circuito", 18, y);
                y = drawKeyVal(cs, "Layout / Variante", "N/D", y);
                y = drawKeyVal(cs, "Lunghezza [km]", "n/d", y);
                y = drawKeyVal(cs, "Tipologia veicolo", traits.category.toString(), y);
                y = drawHeading(cs, "Note personali", 14, y - 10);
                y = drawBullet(cs, loadCircuitNotes(data), y);
            }
            return firstPage;
        }

        // ---------- selezione variante robusta ----------
        TrackInfo use = null;
        String chosen = (opts == null) ? null : opts.trackVariantId();
        use = firstNonNull(
                tryVariant(baseId, chosen),
                tryVariant(baseId, prefixed(baseId, chosen))
        );
        if (use == null) {
            String def = StaticTrackDB.getDefaultVariantId(baseId);
            use = firstNonNull(
                    tryVariant(baseId, def),
                    tryVariant(baseId, prefixed(baseId, def))
            );
        }
        if (use == null || use.turns == null || use.turns.isEmpty()) {
            use = firstVariantWithTurns(baseId);
        }
        if (use == null) use = base;

        // --------- dati per la stampa ----------
        String layoutName = (use.displayName != null && !use.displayName.isBlank())
                ? use.displayName : base.displayName;
        double lengthKm = (use.lengthKm > 0) ? use.lengthKm : base.lengthKm;
        List<TrackInfo.Turn> turns = (use.turns != null) ? use.turns : List.of();

        File img = null;
        if (use.imageResource != null && !use.imageResource.isBlank()) {
            img = imageFromJsonPath(use.imageResource);
        }
        if (img == null) {
            img = resolveImageFromHintOrId(base.imageResource, base.id);
        }

        // ------------- rendering con paginazione -------------
        PDPage currentPage = firstPage;
        PDPageContentStream cs = new PDPageContentStream(doc, currentPage);
        float y = PAGE_SIZE.getHeight() - MARGIN;

        // intestazione prima pagina
        y = drawHeading(cs, "Circuito", 18, y);
        y = drawKeyVal(cs, "Layout / Variante", layoutName, y);
        y = drawKeyVal(cs, "Lunghezza [km]", lengthKm > 0 ? String.format(Locale.US, "%.3f", lengthKm) : "n/d", y);
        y = drawKeyVal(cs, "Tipologia veicolo", traits.category.toString(), y);

        if (img != null) {
            PDImageXObject im = loadImage(doc, img);
            if (im != null) {
                float maxW = CONTENT_WIDTH, maxH = 220f;
                float scale = Math.min(maxW / im.getWidth(), maxH / im.getHeight());
                float w = im.getWidth() * scale, h = im.getHeight() * scale;
                float x = MARGIN + (CONTENT_WIDTH - w) / 2f;
                y -= h + 8;
                cs.drawImage(im, x, y, w, h);
                y -= 10;
            }
        }

        // tabella curve — può andare su più pagine
        final float x0 = MARGIN;
        final float[] widths = new float[]{
                CONTENT_WIDTH * 0.10f,
                CONTENT_WIDTH * 0.24f,
                CONTENT_WIDTH * 0.16f,
                CONTENT_WIDTH * 0.18f,
                CONTENT_WIDTH * 0.32f
        };
        final float baseRowH = 18f, headerH = 22f;
        final int fontSize = 10;

        y = drawHeading(cs, "Curve – " + layoutName, 14, y);
        drawTableHeader(cs, new String[]{"T", "Nome", "Marcia", "vMin [km/h]", "Note"}, x0, y, widths, headerH);
        y -= headerH;

        if (turns.isEmpty()) {
            drawTableRowWrapped(cs, new String[]{"-", "-", "-", "-", "Nessun dato disponibile"},
                    x0, y, widths, baseRowH, fontSize, true);
            y -= baseRowH;
        } else {
            for (int i = 0; i < turns.size(); i++) {
                TrackInfo.Turn t = turns.get(i);
                TrackInfo.Advice adv = (t.adviceByVehicle == null) ? null : t.adviceByVehicle.get(catKey);
                String gear = (adv != null && adv.gear != null) ? String.valueOf(adv.gear) : "-";
                String vmin = "-";
                if (adv != null && adv.vMinIdealKmh != null) {
                    vmin = String.format(Locale.US, "%.0f", adv.vMinIdealKmh);
                    if (adv.vRangeKmh != null)
                        vmin += " +/-" + String.format(Locale.US, "%.0f", adv.vRangeKmh);
                }
                String note = (adv != null && adv.note != null) ? adv.note : "";

                String[] row = new String[]{
                        (t.number > 0 ? "T" + t.number : "-"),
                        (t.name == null || t.name.isBlank()) ? "-" : t.name,
                        gear, vmin, note
                };
                float rh = measureRowHeightWrapped(row, widths, baseRowH, fontSize);

                if (y - rh < MARGIN + 20) {
                    cs.close();
                    currentPage = new PDPage(PAGE_SIZE);
                    doc.addPage(currentPage);
                    cs = new PDPageContentStream(doc, currentPage);
                    y = PAGE_SIZE.getHeight() - MARGIN;

                    y = drawHeading(cs, "Circuito (contin.)", 18, y);
                    y = drawHeading(cs, "Curve – " + layoutName, 14, y);
                    drawTableHeader(cs, new String[]{"T", "Nome", "Marcia", "vMin [km/h]", "Note"},
                            x0, y, widths, headerH);
                    y -= headerH;
                }

                drawTableRowWrapped(cs, row, x0, y, widths, baseRowH, fontSize, ((i % 2) == 0));
                y -= rh;
            }
        }

        // note personali, con possibile nuova pagina
        if (opts.includeCircuitNotes()) {
            float minNotes = 22f + 22f + 2 * baseRowH;
            if (y < MARGIN + minNotes) {
                cs.close();
                currentPage = new PDPage(PAGE_SIZE);
                doc.addPage(currentPage);
                cs = new PDPageContentStream(doc, currentPage);
                y = PAGE_SIZE.getHeight() - MARGIN;
                y = drawHeading(cs, "Circuito (contin.)", 18, y);
            }

            y = drawHeading(cs, "Note personali", 14, y);
            y = drawNotesTableInlineWrapped(cs, y,
                    Arrays.asList(loadCircuitNotes(data).split("\\r?\\n")));
        }

        cs.close();
        return firstPage;
    }

    // ---------- helpers ----------
    private static String prefixed(String baseId, String varId) {
        if (varId == null || varId.isBlank()) return null;
        return baseId + "_" + varId;
    }
    private TrackInfo tryVariant(String baseId, String variantId) {
        if (baseId == null || variantId == null || variantId.isBlank()) return null;
        try { return StaticTrackDB.getVariant(baseId, variantId); }
        catch (Throwable __) { return null; }
    }
    private TrackInfo firstNonNull(TrackInfo... items) {
        for (TrackInfo t : items) if (t != null) return t;
        return null;
    }
    private TrackInfo firstVariantWithTurns(String baseId) {
        try {
            List<String> ids = StaticTrackDB.getVariantIds(baseId);
            if (ids != null) {
                for (String id : ids) {
                    TrackInfo v = tryVariant(baseId, id);
                    if (v == null && id != null && id.startsWith(baseId + "_"))
                        v = tryVariant(baseId, id.substring((baseId + "_").length()));
                    if (v == null)
                        v = tryVariant(baseId, prefixed(baseId, id));
                    if (v != null && v.turns != null && !v.turns.isEmpty()) return v;
                }
            }
        } catch (Throwable __) {}
        return null;
    }
    /* =============== TABELLE GENERICHE INLINE (per-lap / note) ================= */

    private float drawNotesTableInlineWrapped(PDPageContentStream cs, float y, List<String> notes) throws Exception {
        float baseRowH = 18f, headerH = 22f;
        float x = MARGIN;
        float[] widths = new float[]{ CONTENT_WIDTH * 0.10f, CONTENT_WIDTH * 0.90f };
        int fontSize = 10;

        drawTableHeader(cs, new String[]{"#", "Nota"}, x, y, widths, headerH);
        y -= headerH;

        if (notes == null || notes.isEmpty()) {
            drawTableRowWrapped(cs, new String[]{ "-", "Nessuna nota disponibile" }, x, y, widths, baseRowH, fontSize, true);
            return y - baseRowH;
        }

        for (int i = 0; i < notes.size(); i++) {
            float rh = measureRowHeightWrapped(new String[]{Integer.toString(i+1), notes.get(i)}, widths, baseRowH, fontSize);
            if (y - rh < MARGIN + 20) break; // inline: non pagino
            drawTableRowWrapped(cs, new String[]{ Integer.toString(i+1), notes.get(i) }, x, y, widths, baseRowH, fontSize, (i%2)==0);
            y -= rh;
        }
        return y;
    }

    /* ======================== TABELLE CANALI ========================= */

    private PDPage addChannelsTable(PDDocument doc, String title, List<Channel> channels, List<Lap> laps) throws Exception {
        PDPage page = new PDPage(PAGE_SIZE);
        doc.addPage(page);

        // Prepara dati — ORDINAMENTO: per LAP, poi per CANALE
        List<Row> rows = new ArrayList<>();
        for (Lap l : laps) {
            for (Channel c : channels) {
                double mean = mean(l, c);
                double max  = max(l, c);
                rows.add(new Row(channelLabel(c), Integer.toString(l.index), fmt2(mean), fmt2(max), unitOf(c)));
            }
        }

        float rowH = 18f;
        float headerH = 22f;
        float y = PAGE_SIZE.getHeight() - MARGIN;

        PDPage currentPage = page;
        PDPageContentStream cs = new PDPageContentStream(doc, currentPage);

        y = drawHeading(cs, title, 18, y);

        int from = 0;
        while (from < rows.size()) {
            if (y < MARGIN + 120) {
                cs.close();
                currentPage = new PDPage(PAGE_SIZE);
                doc.addPage(currentPage);
                cs = new PDPageContentStream(doc, currentPage);
                y = PAGE_SIZE.getHeight() - MARGIN;
            }

            float[] widths = new float[]{
                    CONTENT_WIDTH * COLS_TABLE[0],
                    CONTENT_WIDTH * COLS_TABLE[1],
                    CONTENT_WIDTH * COLS_TABLE[2],
                    CONTENT_WIDTH * COLS_TABLE[3],
                    CONTENT_WIDTH * COLS_TABLE[4]
            };
            float x = MARGIN;
            drawTableHeader(cs, new String[]{"Canale", "Lap", "Media", "Max", "Unità"}, x, y, widths, headerH);
            y -= headerH;

            int fits = (int) Math.floor((y - MARGIN) / rowH) - 1;
            fits = Math.max(1, fits);
            int to = Math.min(rows.size(), from + fits);

            for (int i = from; i < to; i++) {
                Row r = rows.get(i);
                // valori brevi: riga standard (no wrapping)
                drawTableRow(cs, new String[]{r.c, r.lap, r.mean, r.max, r.unit}, x, y, widths, rowH, (i % 2) == 0);
                y -= rowH;
            }
            from = to;
            y -= 6;
        }
        cs.close();

        return page;
    }

    private void drawTableHeader(PDPageContentStream cs, String[] cols, float x, float y, float[] w, float h) throws Exception {
        // background
        cs.setNonStrokingColor(240/255f,240/255f,240/255f);
        cs.addRect(x, y - h, sum(w), h);
        cs.fill();

        // linea basso
        cs.setStrokingColor(200/255f,200/255f,200/255f);
        cs.setLineWidth(GRID_THICKNESS);
        cs.moveTo(x, y - h);
        cs.lineTo(x + sum(w), y - h);
        cs.stroke();

        // testo
        float cx = x + 4;
        for (int i = 0; i < cols.length; i++) {
            cs.beginText();
            cs.setNonStrokingColor(0,0,0);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
            cs.newLineAtOffset(cx, y - h + 5);
            cs.showText(sanitizePdfText(cols[i]));
            cs.endText();
            cx += w[i];
        }
    }

    private void drawTableRow(PDPageContentStream cs, String[] cols, float x, float y, float[] w, float h, boolean zebra) throws Exception {
        if (zebra) {
            cs.setNonStrokingColor(250/255f,250/255f,250/255f);
            cs.addRect(x, y - h, sum(w), h);
            cs.fill();
        }
        cs.setStrokingColor(230/255f,230/255f,230/255f);
        cs.setLineWidth(GRID_THICKNESS);
        float vx = x;
        for (float ww : w) {
            cs.moveTo(vx, y - h);
            cs.lineTo(vx, y);
            cs.stroke();
            vx += ww;
        }
        cs.moveTo(x, y - h);
        cs.lineTo(x + sum(w), y - h);
        cs.stroke();

        float cx = x + 4;
        for (int i = 0; i < cols.length; i++) {
            cs.beginText();
            cs.setNonStrokingColor(0,0,0);
            cs.setFont(PDType1Font.HELVETICA, 10);
            cs.newLineAtOffset(cx, y - h + 4);
            cs.showText(trimToFit(cols[i], w[i] - 8, 10));
            cs.endText();
            cx += w[i];
        }
    }

    /* ---------- Righe WRAPPED (note sempre visibili) ---------- */

    private void drawTableRowWrapped(PDPageContentStream cs, String[] cols, float x, float y, float[] w,
                                     float baseRowH, int fontSize, boolean zebra) throws Exception {
        float h = measureRowHeightWrapped(cols, w, baseRowH, fontSize);

        if (zebra) {
            cs.setNonStrokingColor(250/255f,250/255f,250/255f);
            cs.addRect(x, y - h, sum(w), h);
            cs.fill();
        }
        cs.setStrokingColor(230/255f,230/255f,230/255f);
        cs.setLineWidth(GRID_THICKNESS);
        float vx = x;
        for (float ww : w) {
            cs.moveTo(vx, y - h);
            cs.lineTo(vx, y);
            cs.stroke();
            vx += ww;
        }
        cs.moveTo(x, y - h);
        cs.lineTo(x + sum(w), y - h);
        cs.stroke();

        // testo per colonna, suddiviso in righe
        float cx = x + 4;
        for (int i = 0; i < cols.length; i++) {
            List<String> lines = wrapLines(sanitizePdfText(cols[i]), w[i] - 8, fontSize);
            float ty = y - baseRowH + 4; // baseline prima riga
            for (String line : lines) {
                cs.beginText();
                cs.setNonStrokingColor(0,0,0);
                cs.setFont(PDType1Font.HELVETICA, fontSize);
                cs.newLineAtOffset(cx, ty);
                cs.showText(line);
                cs.endText();
                ty -= baseRowH; // vai a capo nella cella
            }
            cx += w[i];
        }
    }

    private float measureRowHeightWrapped(String[] cols, float[] w, float baseRowH, int fontSize) {
        int maxLines = 1;
        for (int i = 0; i < cols.length; i++) {
            List<String> lines = wrapLines(sanitizePdfText(cols[i]), w[i] - 8, fontSize);
            maxLines = Math.max(maxLines, Math.max(1, lines.size()));
        }
        return baseRowH * maxLines;
    }

    private List<String> wrapLines(String text, float maxWidth, int fontSize){
        if (text == null) return List.of("");
        float approxCharW = 0.5f * fontSize; // Helvetica approx
        int maxChars = Math.max(1, (int)(maxWidth / approxCharW));

        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            int add = (line.length()==0 ? w.length() : w.length()+1);
            if (line.length() + add > maxChars) {
                if (!line.isEmpty()) { lines.add(line.toString()); line.setLength(0); }
                // parola piu' lunga della riga: spezza
                while (w.length() > maxChars) {
                    lines.add(w.substring(0, maxChars));
                    w = w.substring(maxChars);
                }
                line.append(w);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(w);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        if (lines.isEmpty()) lines.add("");
        return lines;
    }


    /* ====================== TOC + OUTLINE ======================= */
    private void writeTocAndOutline(PDDocument doc, PDPage tocPage, ExportOptions opts, List<Lap> lapsSel, List<SectionRef> sections) throws Exception {
        PDDocumentOutline outline = new PDDocumentOutline();
        doc.getDocumentCatalog().setDocumentOutline(outline);
        for (SectionRef s : sections) {
            PDOutlineItem it = new PDOutlineItem();
            it.setTitle(s.title);
            it.setDestination(s.page);
            outline.addLast(it);
        }
        outline.openNode();

        try (PDPageContentStream cs = new PDPageContentStream(doc, tocPage)) {
            float y = PAGE_SIZE.getHeight() - MARGIN;
// LOGO in alto a destra (solo prima pagina)
            PDImageXObject logo = loadImage(doc, new File(APP_LOGO_PATH));
            if (logo != null) {
                float max = 56f; // lato massimo
                float scale = Math.min(max / logo.getWidth(), max / logo.getHeight());
                float w = logo.getWidth() * scale;
                float h = logo.getHeight() * scale;
                float x = PAGE_SIZE.getWidth() - MARGIN - w;
                float yTop = PAGE_SIZE.getHeight() - MARGIN;
                cs.drawImage(logo, x, yTop - h, w, h);
            }

            y = drawHeading(cs, "Telemetry Report", 24, y);
            String date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());
            String sub = (reportSubtitle == null || reportSubtitle.isBlank()) ? date : (reportSubtitle + " — " + date);

            y = drawText(cs, sub, 12, y - 6);

            y = drawHeading(cs, "Panoramica", 16, y - 18);
            y = drawBullet(cs, "Modalità giri: " + opts.lapSelection(), y);
            if (!lapsSel.isEmpty())  y = drawBullet(cs, "Giri inclusi: " + lapList(lapsSel), y);
            if (opts.includeCircuit()) y = drawBullet(cs, "Circuito: " + (opts.includeCircuitNotes() ? "con note" : "senza note"), y);
            if (opts.coachingScope() != CoachingScope.NONE) y = drawBullet(cs, "Coaching: " + opts.coachingScope(), y);
            if (opts.includeSetup()) y = drawBullet(cs, "Setup: incluso (sessione completa)", y);

            y = drawHeading(cs, "Indice", 16, y - 16);
            for (SectionRef sref : sections) {
                y = drawLinkBullet(doc, tocPage, cs, sref.title, sref.page, y);
            }
        }
    }
    private float drawLinkBullet(PDDocument doc, PDPage page, PDPageContentStream cs, String text, PDPage target, float y) throws Exception {
        // testo
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 12);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(sanitizePdfText("- " + text));
        cs.endText();

        // link
        PDAnnotationLink link = new PDAnnotationLink();
        PDPageXYZDestination dest = new PDPageXYZDestination();
        dest.setPage(target);
        dest.setTop((int)(PAGE_SIZE.getHeight() - MARGIN));
        PDActionGoTo action = new PDActionGoTo();
        action.setDestination(dest);
        link.setAction(action);

        PDBorderStyleDictionary border = new PDBorderStyleDictionary();
        border.setWidth(0);
        link.setBorderStyle(border);

        float height = 14f;
        link.setRectangle(new PDRectangle(MARGIN, y - 2, CONTENT_WIDTH, height));
        page.getAnnotations().add(link);

        return y - 16;
    }

    private void addPageNumbers(PDDocument doc) throws Exception {
        int n = doc.getNumberOfPages();
        for (int i = 0; i < n; i++) {
            PDPage p = doc.getPage(i);
            String label = String.format("%d / %d", i+1, n);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p, PDPageContentStream.AppendMode.APPEND, true, true)) {
                float textWidth = label.length() * 6.0f; // approx
                float x = PAGE_SIZE.getWidth() / 2f - textWidth / 2f;
                float y = MARGIN / 2f;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 9);
                cs.newLineAtOffset(x, y);
                cs.showText(sanitizePdfText(label));
                cs.endText();
            }
        }
    }

    /* ============================ DRAW =========================== */

    private float drawHeading(PDPageContentStream cs, String text, int size, float y) throws Exception {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, size);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(sanitizePdfText(text));
        cs.endText();
        return y - (size + 6);
    }

    private float drawText(PDPageContentStream cs, String text, int size, float y) throws Exception {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, size);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(sanitizePdfText(text));
        cs.endText();
        return y - (size + 4);
    }

    private float drawSmall(PDPageContentStream cs, String text, float y) throws Exception {
        return drawText(cs, text, 10, y);
    }

    private float drawBullet(PDPageContentStream cs, String text, float y) throws Exception {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 12);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(sanitizePdfText("- " + text));
        cs.endText();
        return y - 16;
    }

    private float drawKeyVal(PDPageContentStream cs, String key, String val, float y) throws Exception {
        float split = MARGIN + CONTENT_WIDTH * 0.7f;
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 12);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(sanitizePdfText(key));
        cs.endText();

        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
        cs.newLineAtOffset(split, y);
        cs.showText(sanitizePdfText(val));
        cs.endText();
        return y - 16;
    }

    /* ============================ UTILS =========================== */

    private PDImageXObject loadImage(PDDocument doc, File f){
        if (f == null || !f.exists() || f.length()==0) return null;
        try {
            BufferedImage bi = ImageIO.read(f);
            if (bi == null) return null;
            return LosslessFactory.createFromImage(doc, bi);
        } catch (Exception e){
            return null;
        }
    }

    // (facoltativo, utile come fallback quando manca l'hint image dal JSON)
    private File findTrackImage(String id){
        if (id == null || id.isBlank()) return null;

        final File dirWin = new File("C:\\\\Users\\\\addir\\\\Desktop\\\\TESI\\\\TELEMETRY_COACH\\\\src\\\\main\\\\resources\\\\assets\\\\tracks");
        final File dirUnix = new File("src/main/resources/assets/tracks");
        File dir = dirWin.exists() ? dirWin : (dirUnix.exists() ? dirUnix : null);
        if (dir == null || !dir.isDirectory()) return null;

        String normId = normalizeId(id);
        File[] imgs = dir.listFiles(f -> {
            String n = f.getName().toLowerCase(Locale.ROOT);
            return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
        });
        if (imgs == null || imgs.length == 0) return null;

        File best = null; int bestScore = -1;
        for (File f : imgs){
            String base = stripExt(f.getName());
            String normBase = normalizeId(base);
            int score = 0;
            if (normBase.equals(normId)) score = 100;
            if (normBase.equals(normId + "official")) score = Math.max(score, 95);
            if (normBase.equals(normId + "gp"))       score = Math.max(score, 92);
            if (normBase.equals(normId + "national")) score = Math.max(score, 92);
            if (normBase.equals(normId + "international")) score = Math.max(score, 92);
            if (normBase.equals(normId + "club"))     score = Math.max(score, 90);
            if (normBase.equals(normId + "classic"))  score = Math.max(score, 90);
            if (normBase.startsWith(normId) && !normBase.equals(normId)) score = Math.max(score, 85);
            if (score == 0 && normBase.contains(normId)) score = 70;
            if (score > bestScore) { bestScore = score; best = f; }
        }
        return best;
    }

    private String normalizeId(String s){
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }


    private String lapList(List<Lap> laps){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < laps.size(); i++){
            sb.append(laps.get(i).index);
            if (i < laps.size()-1) sb.append(", ");
        }
        return sb.toString();
    }

    private String fmt(double v){ return Double.isNaN(v) ? "n/d" : String.format(java.util.Locale.US, "%.3f", v); }
    private String fmt1(double v){ return Double.isNaN(v) ? "n/d" : String.format(java.util.Locale.US, "%.1f", v); }
    private String fmt2(double v){ return Double.isNaN(v) ? "n/d" : String.format(java.util.Locale.US, "%.2f", v); }

    private static double mean(Lap l, Channel c){
        var s = l.samples;
        if (s == null || s.isEmpty()) return Double.NaN;
        return s.stream().mapToDouble(sm -> v(sm, c)).filter(d -> !Double.isNaN(d)).average().orElse(Double.NaN);
    }
    private static double max(Lap l, Channel c){
        var s = l.samples;
        if (s == null || s.isEmpty()) return Double.NaN;
        return s.stream().mapToDouble(sm -> v(sm, c)).filter(d -> !Double.isNaN(d)).max().orElse(Double.NaN);
    }
    private static double v(Sample s, Channel c){
        if (s == null || s.values()==null) return Double.NaN;
        Double x = s.values().get(c);
        return x==null ? Double.NaN : x;
    }

    private static Set<Channel> detectPresentChannels(List<Lap> laps){
        if (laps == null) return Set.of();
        LinkedHashSet<Channel> set = new LinkedHashSet<>();
        for (Lap l : laps){
            if (l == null || l.samples == null) continue;
            for (Sample s : l.samples){
                if (s == null || s.values() == null) continue;
                set.addAll(s.values().keySet());
            }
        }
        return set;
    }

    private String channelLabel(Channel c){ return c.name().replace('_',' '); }

    private String unitOf(Channel c){
        switch (c){
            case SPEED: return "km/h";
            case ENGINE_RPM:
            case WHEEL_ANGULAR_SPEED_FL:
            case WHEEL_ANGULAR_SPEED_FR:
            case WHEEL_ANGULAR_SPEED_RL:
            case WHEEL_ANGULAR_SPEED_RR: return "rpm";
            case THROTTLE:
            case BRAKE:
            case BRAKE_BIAS: return "%";
            case STEER_ANGLE: return "deg";
            case CG_ACCEL_LATERAL:
            case CG_ACCEL_LONGITUDINAL: return "g";
            case TIRE_PRESSURE_FL:
            case TIRE_PRESSURE_FR:
            case TIRE_PRESSURE_RL:
            case TIRE_PRESSURE_RR: return "bar";
            case TIRE_TEMP_INNER_FL: case TIRE_TEMP_MIDDLE_FL: case TIRE_TEMP_OUTER_FL:
            case TIRE_TEMP_INNER_FR: case TIRE_TEMP_MIDDLE_FR: case TIRE_TEMP_OUTER_FR:
            case TIRE_TEMP_INNER_RL: case TIRE_TEMP_MIDDLE_RL: case TIRE_TEMP_OUTER_RL:
            case TIRE_TEMP_INNER_RR: case TIRE_TEMP_MIDDLE_RR: case TIRE_TEMP_OUTER_RR:
            case BRAKE_TEMP_FL: case BRAKE_TEMP_FR: case BRAKE_TEMP_RL: case BRAKE_TEMP_RR:
                return "°C";
            case TIRE_LOAD_FL: case TIRE_LOAD_FR: case TIRE_LOAD_RL: case TIRE_LOAD_RR: return "N";
            case RIDE_HEIGHT_FL: case RIDE_HEIGHT_FR: case RIDE_HEIGHT_RL: case RIDE_HEIGHT_RR:
            case SUSP_TRAVEL_FL: case SUSP_TRAVEL_FR: case SUSP_TRAVEL_RL: case SUSP_TRAVEL_RR:
                return "mm";
            default: return "-";
        }
    }

    // --- missing helpers fixed ---

    private float sum(float[] arr){
        float s = 0f; for (float v : arr) s += v; return s;
    }

    private String trimToFit(String text, float maxWidth, int fontSize){
        String safe = sanitizePdfText(text);
        if (safe == null) return "";
        float approxCharW = 0.5f * fontSize; // Helvetica approx
        int maxChars = Math.max(1, (int)(maxWidth / approxCharW));
        if (safe.length() <= maxChars) return safe;
        if (maxChars <= 3) return safe.substring(0, Math.min(3, safe.length()));
        return safe.substring(0, maxChars - 3) + "...";
    }

    // --- integrazioni reali (coach / circuito / setup) ---

    private List<String> coachNotesForLapList(int lapIndex, DataController data){
        try {
            if (data == null) return List.of();
            Lap lap = null;
            for (Lap l : data.getLaps()){
                if (l != null && l.index == lapIndex){ lap = l; break; }
            }
            if (lap == null) return List.of();
            List<String> notes = Coach.generateNotes(lap);
            return notes == null ? List.of() : notes;
        } catch (Throwable t){
            return List.of();
        }
    }
    private String coachNotesForLap(int lapIndex, DataController data){ // legacy
        List<String> notes = coachNotesForLapList(lapIndex, data);
        if (notes.isEmpty()) return "_Nessuna nota disponibile per questo giro._";
        StringBuilder sb = new StringBuilder();
        for (String n : notes) sb.append("- ").append(n).append('\n');
        return sb.toString().trim();
    }
    private List<String> coachNotesForSessionList(List<Lap> laps, DataController data){
        try {
            List<String> notes = Coach.generateSessionNotes(laps==null? List.of() : laps);
            return notes == null ? List.of() : notes;
        } catch (Throwable t){
            return List.of();
        }
    }


    private String loadCircuitNotes(DataController data){
        try {
            var pre = SessionPreamble.parse(data.getCsvPath());
            String id = pre.venue==null? "unknown" : pre.venue.toLowerCase(Locale.ROOT);

            VehicleTraits traits = VehicleTraits.detect(data.getLaps());
            String catKey = switch (traits.category){
                case FORMULA -> "FORMULA";
                case PROTOTYPE -> "PROTOTYPE";
                case GT -> "GT";
                case ROAD -> "ROAD";
                default -> "OTHER";
            };
            String key = id + "__" + catKey;
            Path dir = Path.of(System.getProperty("user.home"), ".telemetrycoach", "notes");
            Path f = dir.resolve(key.replaceAll("[^a-zA-Z0-9._-]","_") + ".txt");
            if (Files.exists(f)) {
                return Files.readString(f, StandardCharsets.UTF_8);
            }
            return "_Nessuna nota salvata._";
        } catch (Exception e){
            return "_Nessuna nota salvata._";
        }
    }

    private static int sevRank(Severity s){
        return switch (s){
            case HIGH -> 3;
            case MEDIUM -> 2;
            default -> 1;
        };
    }

    /** Converte caratteri non WinAnsi in ASCII sicuro per Helvetica base */
    private String sanitizePdfText(String s){
        if (s == null) return "";
        String out = s;
        out = out.replace("→", "->")
                .replace("←", "<-")
                .replace("↔", "<->")
                .replace("±", "+/-")
                .replace("–", "-")
                .replace("—", "-")
                .replace("•", "-")
                .replace("·", ".")
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("„", "\"")
                .replace("‘", "'")
                .replace("’", "'")
                .replace("…", "...");
        StringBuilder sb = new StringBuilder(out.length());
        for (int i = 0; i < out.length(); i++){
            char c = out.charAt(i);
            if (c >= 32 && c <= 126 || c == '\n' || c == '\r' || c == '\t' || c == '°') {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }
        return sb.toString();
    }


    /** Se il JSON fornisce un path immagine (es. "/assets/tracks/silverstone_GP.png"),
     *  prova prima con quello; in fallback cerca per euristiche sul solo id. */
    private File resolveImageFromHintOrId(String imageResourceHint, String id){
        File byHint = (imageResourceHint == null || imageResourceHint.isBlank())
                ? null
                : imageFromJsonPath(imageResourceHint);
        return byHint != null ? byHint : findTrackImage(id);
    }

    /** Converte un path JSON tipo "/assets/tracks/silverstone_GP.png" nel path fisico usato dal progetto. */
    private File imageFromJsonPath(String jsonPath){
        // normalizza nome file
        String fileName = jsonPath.replace('\\','/').substring(jsonPath.replace('\\','/').lastIndexOf('/')+1);

        // cartella che usi tu su Windows
        File dirWin = new File("C:\\Users\\addir\\Desktop\\TESI\\TELEMETRY_COACH\\src\\main\\resources\\assets\\tracks");
        if (dirWin.isDirectory()) {
            File f = new File(dirWin, fileName);
            if (f.exists()) return f;
        }

        // fallback per run unix-like / build
        File dirUnix = new File("src/main/resources/assets/tracks");
        if (dirUnix.isDirectory()) {
            File f = new File(dirUnix, fileName);
            if (f.exists()) return f;
        }
        return null;
    }
    private String stripExt(String name){
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return (i > 0) ? name.substring(0, i) : name;
    }
    private String tryReadVehicleFromCsv(Path csvPath) {
        if (csvPath == null) return null;
        try (BufferedReader br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            // leggi l’header
            String header = br.readLine();
            if (header == null) return null;

            // scegli il separatore (virgola o punto e virgola)
            String sep = header.contains(";") ? ";" : ",";
            String[] cols = header.split(sep, -1);

            // trova la colonna "car", "vehicle", ecc.
            int idx = -1;
            String[] candidates = { "car", "vehicle", "car_name", "carname", "model", "veicolo", "nomeveicolo" };
            for (int i = 0; i < cols.length && idx < 0; i++) {
                String c = cols[i].trim().replace("\"", "").toLowerCase(Locale.ROOT);
                for (String cand : candidates)
                    if (c.equals(cand)) { idx = i; break; }
            }

            // leggi la prima riga dati
            String first = br.readLine();
            if (idx >= 0 && first != null) {
                String[] vals = first.split(sep, -1);
                if (idx < vals.length) {
                    String v = vals[idx].replace("\"", "").trim();
                    if (!v.isBlank()) return v;
                }
            }

            // fallback: deduci dal nome file
            String fileName = csvPath.getFileName().toString();
            String guess = fileName.replace(".csv", "").replace('_', ' ').trim();
            return guess.isBlank() ? null : guess;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyBackgroundToAllPages(PDDocument doc, File bgFile, float opacity) throws Exception {
        PDImageXObject bg = loadImage(doc, bgFile);
        if (bg == null) return;

        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            PDPage page = doc.getPage(i);
            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.PREPEND, true, true)) {

                // Opacità bassa per non disturbare la lettura
                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(opacity);
                gs.setStrokingAlphaConstant(opacity);
                cs.saveGraphicsState();
                cs.setGraphicsStateParameters(gs);

                // "cover" mantenendo il rapporto, centrato
                float pw = PAGE_SIZE.getWidth(), ph = PAGE_SIZE.getHeight();
                float scale = Math.max(pw / bg.getWidth(), ph / bg.getHeight());
                float w = bg.getWidth() * scale, h = bg.getHeight() * scale;
                float x = (pw - w) / 2f, y = (ph - h) / 2f;

                cs.drawImage(bg, x, y, w, h);
                cs.restoreGraphicsState();
            }
        }
    }
    private void stampLogoTopRightAllPages(PDDocument doc, File logoFile, float maxSize) throws Exception {
        PDImageXObject logo = loadImage(doc, logoFile);
        if (logo == null) return;

        final float margin = MARGIN; // usa lo stesso margine del layout
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            PDPage page = doc.getPage(i);
            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                float scale = Math.min(maxSize / logo.getWidth(), maxSize / logo.getHeight());
                float w = logo.getWidth() * scale;
                float h = logo.getHeight() * scale;

                float x = PAGE_SIZE.getWidth() - margin - w;
                float y = PAGE_SIZE.getHeight() - margin - h;

                // L'header dei contenuti parte a sinistra del margine, quindi qui restiamo nell'area di margine
                cs.drawImage(logo, x, y, w, h);
            }
        }
    }



}