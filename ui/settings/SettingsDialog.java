package org.simulator.ui.settings;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import org.simulator.ui.ChartStyles;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class SettingsDialog {
    private SettingsDialog(){}

    public static void show(Stage owner){
        UiSettings s = UiSettings.get();

        // =================================================================================
        // 1. SELETTORE TEMA MODERNO (CARD)
        // =================================================================================
        Label lblTheme = new Label("Seleziona il Tema dell'Applicazione");
        lblTheme.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Usiamo un FlowPane o TilePane per le card se vogliamo che vadano a capo,
        // ma per 4 card un HBox centrato va bene.
        HBox themeCardsBox = new HBox(20);
        themeCardsBox.setAlignment(Pos.CENTER);
        themeCardsBox.setPadding(new Insets(15, 0, 25, 0));

        ToggleGroup themeGroup = new ToggleGroup();

        for (AppTheme theme : AppTheme.values()) {
            ToggleButton card = createThemeCard(theme, s.currentThemeProperty().get());
            card.setToggleGroup(themeGroup);
            card.setOnAction(e -> s.currentThemeProperty().set(theme));

            if (s.currentThemeProperty().get() == theme) card.setSelected(true);

            themeCardsBox.getChildren().add(card);
        }

        s.currentThemeProperty().addListener((o, ov, nv) -> {
            themeCardsBox.getChildren().forEach(n -> {
                if (n instanceof ToggleButton tb && tb.getUserData() == nv) tb.setSelected(true);
            });
        });

        // =================================================================================
        // 2. SEZIONE COLORI (RESPONSIVE)
        // =================================================================================

        // --- COLONNA SINISTRA: SESSIONE ATTUALE ---
        Label lblMain = new Label("Colori Sessione Attuale");
        lblMain.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: -fx-accent;");

        CheckBox useDefMain = new CheckBox("Usa default tema (Main)");
        useDefMain.selectedProperty().bindBidirectional(s.useDefaultColorsProperty());

        GridPane gridMain = new GridPane();
        gridMain.setHgap(15); gridMain.setVgap(12);

        // FIX: Constraints per allargare la griglia al 100% del VBox padre
        ColumnConstraints colLabel = new ColumnConstraints();
        colLabel.setPercentWidth(40); // 40% spazio per le scritte
        ColumnConstraints colPicker = new ColumnConstraints();
        colPicker.setPercentWidth(60); // 60% spazio per i color picker
        gridMain.getColumnConstraints().addAll(colLabel, colPicker);

        List<ColorPicker> pickersMain = new ArrayList<>();
        int r=0;
        addPicker(gridMain, "Speed", s.colorSpeedProperty(), pickersMain, r++);
        addPicker(gridMain, "Throttle", s.colorThrottleProperty(), pickersMain, r++);
        addPicker(gridMain, "Brake", s.colorBrakeProperty(), pickersMain, r++);
        addPicker(gridMain, "Clutch", s.colorClutchProperty(), pickersMain, r++);
        addPicker(gridMain, "Steer", s.colorSteerProperty(), pickersMain, r++);
        addPicker(gridMain, "RPM", s.colorRpmProperty(), pickersMain, r++);
        addPicker(gridMain, "FFB", s.colorFfbProperty(), pickersMain, r++);
        addPicker(gridMain, "Seat", s.colorSeatProperty(), pickersMain, r++);
        addPicker(gridMain, "Pedal F.", s.colorPedalForceProperty(), pickersMain, r++);

        VBox leftCol = new VBox(12, lblMain, useDefMain, new Separator(), gridMain);
        leftCol.getStyleClass().add("card-panel");
        leftCol.setPadding(new Insets(20));

        // FIX: VBox deve crescere orizzontalmente
        HBox.setHgrow(leftCol, Priority.ALWAYS);
        leftCol.setMaxWidth(Double.MAX_VALUE);

        // --- COLONNA DESTRA: SESSIONE COMPARATA ---
        Label lblCmp = new Label("Colori Sessione Comparata (Ghost)");
        lblCmp.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: -fx-accent;");

        CheckBox useDefCmp = new CheckBox("Usa default tema (Ghost)");
        useDefCmp.selectedProperty().bindBidirectional(s.useDefaultCmpColorsProperty());

        GridPane gridCmp = new GridPane();
        gridCmp.setHgap(15); gridCmp.setVgap(12);

        // FIX: Stessi constraints anche qui
        gridCmp.getColumnConstraints().addAll(colLabel, colPicker);

        List<ColorPicker> pickersCmp = new ArrayList<>();
        int rc=0;
        addPicker(gridCmp, "Speed", s.cmpColorSpeedProperty(), pickersCmp, rc++);
        addPicker(gridCmp, "Throttle", s.cmpColorThrottleProperty(), pickersCmp, rc++);
        addPicker(gridCmp, "Brake", s.cmpColorBrakeProperty(), pickersCmp, rc++);
        addPicker(gridCmp, "Clutch", s.cmpColorClutchProperty(), pickersCmp, rc++);
        addPicker(gridCmp, "Steer", s.cmpColorSteerProperty(), pickersCmp, rc++);
        addPicker(gridCmp, "RPM", s.cmpColorRpmProperty(), pickersCmp, rc++);
        addPicker(gridCmp, "FFB", s.cmpColorFfbProperty(), pickersCmp, rc++);
        addPicker(gridCmp, "Seat", s.cmpColorSeatProperty(), pickersCmp, rc++);
        addPicker(gridCmp, "Pedal F.", s.cmpColorPedalForceProperty(), pickersCmp, rc++);

        VBox rightCol = new VBox(12, lblCmp, useDefCmp, new Separator(), gridCmp);
        rightCol.getStyleClass().add("card-panel");
        rightCol.setPadding(new Insets(20));

        // FIX: VBox cresce orizzontalmente
        HBox.setHgrow(rightCol, Priority.ALWAYS);
        rightCol.setMaxWidth(Double.MAX_VALUE);

        // SYNC E LISTENERS
        Runnable updatePickersState = () -> {
            boolean disMain = s.useDefaultColorsProperty().get();
            pickersMain.forEach(p -> p.setDisable(disMain));
            boolean disCmp = s.useDefaultCmpColorsProperty().get();
            pickersCmp.forEach(p -> p.setDisable(disCmp));
        };
        s.useDefaultColorsProperty().addListener((o,ov,nv) -> { updatePickersState.run(); ChartStyles.reapplyAll(); });
        s.useDefaultCmpColorsProperty().addListener((o,ov,nv) -> { updatePickersState.run(); ChartStyles.reapplyAll(); });
        Platform.runLater(updatePickersState);

        List<ColorPicker> allP = new ArrayList<>(pickersMain); allP.addAll(pickersCmp);
        allP.forEach(cp -> cp.valueProperty().addListener((o,ov,nv) -> ChartStyles.reapplyAll()));

        // LAYOUT PRINCIPALE COLORI
        HBox colorsLayout = new HBox(25, leftCol, rightCol);
        colorsLayout.setAlignment(Pos.TOP_CENTER);
        // FIX: Assicura che l'HBox riempia la larghezza disponibile
        colorsLayout.setMaxWidth(Double.MAX_VALUE);

        Button reset = new Button("Ripristina impostazioni iniziali (Tema & Colori)");
        reset.getStyleClass().add("button-secondary");
        reset.setMaxWidth(Double.MAX_VALUE); // Bottone largo
        reset.setOnAction(e -> { s.resetToDefaults(); ChartStyles.reapplyAll(); });

        VBox colorsPage = new VBox(25, lblTheme, themeCardsBox, new Separator(), colorsLayout, new Separator(), reset);
        colorsPage.setPadding(new Insets(25));

        ScrollPane colorsScroll = new ScrollPane(colorsPage);
        colorsScroll.setFitToWidth(true); // FONDAMENTALE: Forza il contenuto ad adattarsi alla larghezza
        colorsScroll.setStyle("-fx-background-color: transparent;");

        // =================================================================================
        // TAB 2: TIMELINE (Codice invariato ma ottimizzato padding)
        // =================================================================================
        TilePane wheelGallery = new TilePane();
        wheelGallery.setHgap(15); wheelGallery.setVgap(15); wheelGallery.setPrefColumns(4);
        String[] candidates = { "/assets/wheel.png","/assets/wheel1.png","/assets/wheel2.png", "/assets/wheel3.png","/assets/wheel4.png","/assets/wheel5.png" };
        ToggleGroup tgW = new ToggleGroup();
        for (String path : candidates){
            Image img = safeRes(path);
            if (img == null) continue;
            ImageView iv = new ImageView(img); iv.setPreserveRatio(true); iv.setFitWidth(140);
            ToggleButton tb = new ToggleButton(); tb.setGraphic(iv); tb.setUserData(path);
            tb.setStyle("-fx-background-color: transparent; -fx-padding: 6; -fx-border-color: transparent;");
            tb.selectedProperty().addListener((o,ov,nv)-> {
                if(nv) tb.setStyle("-fx-background-color: -fx-accent; -fx-background-radius: 8;");
                else tb.setStyle("-fx-background-color: transparent;");
            });
            tb.setToggleGroup(tgW);
            if (path.equals(s.wheelImagePathProperty().get())) tb.setSelected(true);
            wheelGallery.getChildren().add(tb);
        }
        tgW.selectedToggleProperty().addListener((o,ov,nv) -> { if (nv != null) s.wheelImagePathProperty().set(nv.getUserData().toString()); });

        TextField wheelPath = new TextField(s.wheelImagePathProperty().get());
        wheelPath.textProperty().bindBidirectional(s.wheelImagePathProperty());
        Button browse = new Button("Sfoglia...");
        browse.setOnAction(ev -> { FileChooser fc = new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Immagini", "*.png","*.jpg")); File f = fc.showOpenDialog(owner); if (f != null) s.wheelImagePathProperty().set(f.toURI().toString()); });

        VBox tlBox = new VBox(15,
                new Label("Modello Volante (Anteprima)"), wheelGallery,
                new HBox(10, new Label("Path Custom:"), wheelPath, browse),
                new Separator(),
                header("Widget Attivi"),
                new TilePane(10,10,
                        bindCheck("Volante", s.showWheelTLProperty()), bindCheck("Velocità", s.showSpeedTLProperty()),
                        bindCheck("RPM", s.showRpmTLProperty()), bindCheck("Pedali", s.showPedalsTLProperty()),
                        bindCheck("FFB", s.showFfbTLProperty()), bindCheck("Gomme", s.showTyresTLProperty()),
                        bindCheck("G-Force", s.showGForceTLProperty()), bindCheck("Sedile", s.showSeatTLProperty()),
                        bindCheck("Sospensioni", s.showSuspTLProperty()), bindCheck("Coach", s.showCoachTLProperty())
                )
        );
        tlBox.setPadding(new Insets(25));
        ScrollPane sp = new ScrollPane(tlBox); sp.setFitToWidth(true); sp.setStyle("-fx-background-color: transparent;");

        // =================================================================================
        // TAB 3: CIRCUITO
        // =================================================================================
        GridPane circGrid = new GridPane();
        circGrid.setHgap(20); circGrid.setVgap(15); circGrid.setPadding(new Insets(25));
        int cr = 0;
        circGrid.add(header("Marker Curve"), 0, cr++, 2, 1);
        circGrid.add(bindCheck("Mostra Marker", s.showCurveMarkersProperty()), 0, cr);
        circGrid.add(bindCheck("Popup Dettagli", s.showCurvePopupProperty()), 1, cr++);
        circGrid.add(new Label("Dimensione:"), 0, cr);
        circGrid.add(createSliderWithLabel(s.curveMarkerSizeProperty(), 4, 20), 1, cr++);

        circGrid.add(new Separator(), 0, cr++, 2, 1);
        circGrid.add(header("Ideal Line"), 0, cr++, 2, 1);
        circGrid.add(new Label("Colore:"), 0, cr); circGrid.add(bindPicker(s.idealLineColorProperty()), 1, cr++);
        circGrid.add(new Label("Spessore:"), 0, cr); circGrid.add(createSliderWithLabel(s.idealLineWidthProperty(), 1, 10), 1, cr++);
        circGrid.add(new Label("Opacità:"), 0, cr); circGrid.add(createSliderWithLabel(s.idealLineOpacityProperty(), 0.1, 1), 1, cr++);

        circGrid.add(new Separator(), 0, cr++, 2, 1);
        circGrid.add(header("DRS Zones"), 0, cr++, 2, 1);
        circGrid.add(new Label("Colore:"), 0, cr); circGrid.add(bindPicker(s.drsZoneColorProperty()), 1, cr++);
        circGrid.add(new Label("Spessore:"), 0, cr); circGrid.add(createSliderWithLabel(s.drsZoneWidthProperty(), 5, 50), 1, cr++);
        circGrid.add(new Label("Opacità:"), 0, cr); circGrid.add(createSliderWithLabel(s.drsZoneOpacityProperty(), 0.1, 1), 1, cr++);

        ScrollPane circScroll = new ScrollPane(circGrid); circScroll.setFitToWidth(true); circScroll.setStyle("-fx-background-color: transparent;");

        // =================================================================================
        // TAB 4: ALTRI PANNELLI
        // =================================================================================
        VBox tabAnalysis = new VBox(15,
                header("Widget Analysis View"),
                new TilePane(10,10,
                        bindCheck("Pneumatici (T)", s.wTyreTempProperty()), bindCheck("Pressioni", s.wTyrePressProperty()),
                        bindCheck("Sospensioni", s.wSuspensionProperty()), bindCheck("Freni", s.wBrakesProperty()),
                        bindCheck("Danni", s.wDamageProperty()), bindCheck("Pedali", s.wPedalsProperty())
                )
        );
        tabAnalysis.setPadding(new Insets(25));

        // MAIN PANE
        TabPane tp = new TabPane(
                new Tab("Colori & Tema", colorsScroll),
                new Tab("Timeline", sp),
                new Tab("Circuito", circScroll),
                new Tab("Altri Widget", tabAnalysis)
        );
        tp.getTabs().forEach(t -> t.setClosable(false));

        BorderPane rootBase = new BorderPane(tp);

        Stage st = new Stage();
        st.initOwner(owner);
        st.initModality(Modality.NONE);
        st.setTitle("Personalizzazione");

        // Finestra Resizable e leggermente più grande
        Scene scene = new Scene(rootBase, 1100, 800);

        ThemeManager.applyTheme(scene, s.getCurrentTheme());
        s.currentThemeProperty().addListener((o, ov, nv) -> ThemeManager.applyTheme(scene, nv));

        st.setScene(scene);
        st.setResizable(true); // Permetti resize
        st.show();
    }

    // HELPER: CARD TEMA GIGANTI
    private static ToggleButton createThemeCard(AppTheme theme, AppTheme current) {
        String accentColor = switch (theme) {
            case DARK_BLUE -> "#3b82f6";
            case LIGHT -> "#0d9488";
            case RACING -> "#dc2626";
            default -> "#f97316";
        };

        Label lbl = new Label(theme.toString());
        lbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        StackPane header = new StackPane(lbl);
        header.setStyle("-fx-background-color: " + accentColor + "; -fx-background-radius: 8 8 0 0; -fx-padding: 8;");
        header.setAlignment(Pos.CENTER);

        StackPane body = new StackPane();
        body.setPrefSize(220, 130);
        body.setStyle("-fx-background-color: #222; -fx-background-radius: 0 0 8 8;");

        String imgPath = "/assets/theme_preview_" + theme.name() + ".png";
        Image img = safeRes(imgPath);
        if (img != null) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(220); iv.setFitHeight(130);
            iv.setPreserveRatio(false);
            Rectangle clip = new Rectangle(220, 130);
            clip.setArcWidth(0); clip.setArcHeight(0);
            iv.setClip(clip);
            body.getChildren().add(iv);
        } else {
            Label missing = new Label("Anteprima");
            missing.setStyle("-fx-text-fill: white; -fx-opacity: 0.3; -fx-font-size: 18px;");
            body.getChildren().add(missing);
        }

        VBox card = new VBox(header, body);
        card.setStyle("-fx-background-radius: 8; -fx-border-color: #666; -fx-border-width: 1; -fx-border-radius: 8; -fx-cursor: hand;");

        ToggleButton btn = new ToggleButton();
        btn.setGraphic(card);
        btn.setUserData(theme);
        btn.setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-border-color: transparent;");

        btn.selectedProperty().addListener((o,ov,nv) -> {
            if (nv) {
                card.setStyle("-fx-background-radius: 8; -fx-border-color: " + accentColor + "; -fx-border-width: 4; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, " + accentColor + ", 20, 0.3, 0, 0);");
            } else {
                card.setStyle("-fx-background-radius: 8; -fx-border-color: #666; -fx-border-width: 1; -fx-border-radius: 8; -fx-opacity: 0.7;");
            }
        });

        if (theme == current) btn.setSelected(true);
        else card.setStyle("-fx-background-radius: 8; -fx-border-color: #666; -fx-border-width: 1; -fx-border-radius: 8; -fx-opacity: 0.7;");

        return btn;
    }

    private static void addPicker(GridPane g, String label, javafx.beans.property.ObjectProperty<Color> p, List<ColorPicker> list, int row) {
        Label l = new Label(label);
        // FIX: Label si adatta, ColorPicker si allarga
        g.add(l, 0, row);

        ColorPicker cp = new ColorPicker(p.get());
        cp.valueProperty().bindBidirectional(p);
        cp.getStyleClass().add(ColorPicker.STYLE_CLASS_BUTTON);

        // FIX: ColorPicker prende tutto lo spazio
        cp.setMaxWidth(Double.MAX_VALUE);
        cp.setStyle("-fx-color-label-visible: false; -fx-background-radius: 4;");

        list.add(cp);
        g.add(cp, 1, row);
    }

    private static Label header(String txt){ Label l = new Label(txt); l.setStyle("-fx-font-weight: bold; -fx-opacity: .9; -fx-font-size: 14px; -fx-text-fill: -fx-accent;"); return l; }
    private static ColorPicker bindPicker(javafx.beans.property.ObjectProperty<Color> p){ ColorPicker cp = new ColorPicker(p.get()); cp.valueProperty().bindBidirectional(p); return cp; }
    private static CheckBox bindCheck(String label, javafx.beans.property.BooleanProperty p){ CheckBox cb = new CheckBox(label); cb.selectedProperty().bindBidirectional(p); return cb; }
    private static HBox createSliderWithLabel(javafx.beans.property.DoubleProperty prop, double min, double max) {
        Slider sl = new Slider(min, max, prop.get());
        sl.valueProperty().bindBidirectional(prop);
        sl.setPrefWidth(200);
        Label lbl = new Label();
        lbl.textProperty().bind(prop.asString("%.1f"));
        lbl.setMinWidth(40);
        lbl.setAlignment(Pos.CENTER_RIGHT);
        return new HBox(10, sl, lbl);
    }
    private static Image safeRes(String path){ try{ var url = SettingsDialog.class.getResource(path); return (url == null) ? null : new Image(url.toExternalForm()); }catch(Exception e){ return null; } }
}