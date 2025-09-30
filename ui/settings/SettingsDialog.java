package org.simulator.ui.settings;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
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

        // ---------- TAB: COLORI GRAFICI (Sessione attuale + Ghost + Comparata) ----------
        CheckBox useDef = new CheckBox("Usa colori di default JavaFX");
        useDef.selectedProperty().bindBidirectional(s.useDefaultColorsProperty());

        GridPane baseGrid = new GridPane();
        baseGrid.setHgap(12); baseGrid.setVgap(8); baseGrid.setPadding(new Insets(10));

        int r = 0;
        List<ColorPicker> allPickers = new ArrayList<>();

        baseGrid.add(new Label("Speed"), 0, r);
        ColorPicker cSpeed = bindPicker(s.colorSpeedProperty()); allPickers.add(cSpeed);
        baseGrid.add(cSpeed, 1, r++);

        baseGrid.add(new Label("Throttle"), 0, r);
        ColorPicker cThr = bindPicker(s.colorThrottleProperty()); allPickers.add(cThr);
        baseGrid.add(cThr, 1, r++);

        baseGrid.add(new Label("Brake"), 0, r);
        ColorPicker cBr = bindPicker(s.colorBrakeProperty()); allPickers.add(cBr);
        baseGrid.add(cBr, 1, r++);

        baseGrid.add(new Label("Clutch"), 0, r);
        ColorPicker cCl = bindPicker(s.colorClutchProperty()); allPickers.add(cCl);
        baseGrid.add(cCl, 1, r++);

        baseGrid.add(new Label("Steer"), 0, r);
        ColorPicker cSt = bindPicker(s.colorSteerProperty()); allPickers.add(cSt);
        baseGrid.add(cSt, 1, r++);

        baseGrid.add(new Label("RPM"), 0, r);
        ColorPicker cRpm = bindPicker(s.colorRpmProperty()); allPickers.add(cRpm);
        baseGrid.add(cRpm, 1, r++);

        baseGrid.add(new Label("FFB"), 0, r);
        ColorPicker cFfb = bindPicker(s.colorFfbProperty()); allPickers.add(cFfb);
        baseGrid.add(cFfb, 1, r++);

        baseGrid.add(new Label("Seat"), 0, r);
        ColorPicker cSeat = bindPicker(s.colorSeatProperty()); allPickers.add(cSeat);
        baseGrid.add(cSeat, 1, r++);

        baseGrid.add(new Label("Pedal force"), 0, r);
        ColorPicker cPF = bindPicker(s.colorPedalForceProperty()); allPickers.add(cPF);
        baseGrid.add(cPF, 1, r++);

        // GHOST dentro "Colori grafici"
        Separator sepGhost = new Separator();
        sepGhost.setPadding(new Insets(6,0,6,0));

        GridPane ghostGrid = new GridPane();
        ghostGrid.setHgap(12); ghostGrid.setVgap(8); ghostGrid.setPadding(new Insets(4,10,10,10));
        int rg = 0;


        // Colori "Sessione comparata"
        Separator sepCmp = new Separator();
        sepCmp.setPadding(new Insets(6,0,6,0));

        GridPane cmpGrid = new GridPane();
        cmpGrid.setHgap(12); cmpGrid.setVgap(8); cmpGrid.setPadding(new Insets(4,10,10,10));
        int rc = 0;

        cmpGrid.add(header("Colori “Sessione comparata”"), 0, rc++, 2, 1);

        cmpGrid.add(new Label("Speed"), 0, rc); ColorPicker cCSpeed = bindPicker(s.cmpColorSpeedProperty()); cmpGrid.add(cCSpeed, 1, rc++);
        cmpGrid.add(new Label("Throttle"), 0, rc); ColorPicker cCThr = bindPicker(s.cmpColorThrottleProperty()); cmpGrid.add(cCThr, 1, rc++);
        cmpGrid.add(new Label("Brake"), 0, rc); ColorPicker cCBr = bindPicker(s.cmpColorBrakeProperty()); cmpGrid.add(cCBr, 1, rc++);
        cmpGrid.add(new Label("Clutch"), 0, rc); ColorPicker cCCl = bindPicker(s.cmpColorClutchProperty()); cmpGrid.add(cCCl, 1, rc++);
        cmpGrid.add(new Label("Steer"), 0, rc); ColorPicker cCSt = bindPicker(s.cmpColorSteerProperty()); cmpGrid.add(cCSt, 1, rc++);
        cmpGrid.add(new Label("RPM"), 0, rc); ColorPicker cCRpm = bindPicker(s.cmpColorRpmProperty()); cmpGrid.add(cCRpm, 1, rc++);
        cmpGrid.add(new Label("FFB"), 0, rc); ColorPicker cCFfb = bindPicker(s.cmpColorFfbProperty()); cmpGrid.add(cCFfb, 1, rc++);
        cmpGrid.add(new Label("Seat"), 0, rc); ColorPicker cCSeat = bindPicker(s.cmpColorSeatProperty()); cmpGrid.add(cCSeat, 1, rc++);
        cmpGrid.add(new Label("Pedal force"), 0, rc); ColorPicker cCPF = bindPicker(s.cmpColorPedalForceProperty()); cmpGrid.add(cCPF, 1, rc++);

        // abilita/disabilita pickers base quando uso default
        Runnable togglePickers = () -> {
            boolean dis = useDef.isSelected();
            for (var p : allPickers) p.setDisable(dis);
        };
        useDef.selectedProperty().addListener((o,ov,nv) -> {
            togglePickers.run();
            ChartStyles.reapplyAll();
        });
        Platform.runLater(togglePickers);

        // ogni cambio colore → restyle
        List<ColorPicker> toWatch = new ArrayList<>();
        toWatch.addAll(allPickers);
        toWatch.addAll(List.of(cCSpeed,cCThr,cCBr,cCCl,cCSt,cCRpm,cCFfb,cCSeat,cCPF));
        toWatch.forEach(p -> p.valueProperty().addListener((o,ov,nv) -> ChartStyles.reapplyAll()));


        Button reset = new Button("Ripristina default JavaFX");
        reset.setOnAction(e -> {
            s.resetToDefaults();
            ChartStyles.reapplyAll();
        });

        VBox colorsPage = new VBox(10,
                useDef,
                new Separator(),
                baseGrid,
                sepGhost,
                ghostGrid,
                sepCmp,
                cmpGrid,
                reset
        );
        colorsPage.setPadding(new Insets(8));
        ScrollPane colorsScroll = new ScrollPane(colorsPage);
        colorsScroll.setFitToWidth(true);

        // ---------- TAB: TIMELINE (come tua versione) ----------
        TilePane wheelGallery = new TilePane();
        wheelGallery.setHgap(12); wheelGallery.setVgap(12);
        wheelGallery.setPrefColumns(4);
        String[] candidates = {
                "/assets/wheel.png","/assets/wheel1.png","/assets/wheel2.png",
                "/assets/wheel3.png","/assets/wheel4.png","/assets/wheel5.png"
        };
        ToggleGroup tg = new ToggleGroup();
        List<ToggleButton> wheelButtons = new ArrayList<>();
        for (String path : candidates){
            Image img = safeRes(path);
            if (img == null) continue;
            ImageView iv = new ImageView(img); iv.setPreserveRatio(true); iv.setFitWidth(120);
            ToggleButton tb = new ToggleButton(); tb.setGraphic(iv); tb.setUserData(path);
            tb.setFocusTraversable(false); tb.setStyle("-fx-background-color: transparent; -fx-padding: 4;");
            tb.setToggleGroup(tg);
            if (path.equals(s.wheelImagePathProperty().get())) tb.setSelected(true);
            wheelButtons.add(tb); wheelGallery.getChildren().add(tb);
        }
        tg.selectedToggleProperty().addListener((o,ov,nv) -> {
            if (nv != null) s.wheelImagePathProperty().set(nv.getUserData().toString());
        });
        s.wheelImagePathProperty().addListener((o,ov,nv) -> {
            ToggleButton match = null;
            for (ToggleButton tb : wheelButtons) if (tb.getUserData().equals(nv)) { match = tb; break; }
            if (match != null) tg.selectToggle(match); else tg.selectToggle(null);
        });

        TextField wheelPath = new TextField(s.wheelImagePathProperty().get());
        wheelPath.textProperty().bindBidirectional(s.wheelImagePathProperty());

        Button browse = new Button("Sfoglia immagine…");
        browse.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Immagini", "*.png","*.jpg","*.jpeg"));
            File f = fc.showOpenDialog(owner);
            if (f != null) s.wheelImagePathProperty().set(f.toURI().toString());
        });

        GridPane tlGrid = new GridPane();
        tlGrid.setHgap(12); tlGrid.setVgap(8); tlGrid.setPadding(new Insets(10));
        tlGrid.add(new Label("Modello volante (anteprima):"), 0, 0);
        tlGrid.add(wheelGallery, 0, 1, 3, 1);
        tlGrid.add(new Label("Oppure percorso custom:"), 0, 2);
        tlGrid.add(wheelPath, 1, 2);
        tlGrid.add(browse, 2, 2);

        VBox tlChecks = new VBox(8,
                header("Widget Timeline"),
                bindCheck("Volante",      s.showWheelTLProperty()),
                bindCheck("Velocità",     s.showSpeedTLProperty()),
                bindCheck("Giri (RPM)",   s.showRpmTLProperty()),
                bindCheck("Pedali",       s.showPedalsTLProperty()),
                bindCheck("FFB",          s.showFfbTLProperty()),
                bindCheck("Gomme",        s.showTyresTLProperty()),
                bindCheck("Freni",        s.showBrakesTLProperty()),
                bindCheck("Sedile",       s.showSeatTLProperty()),
                bindCheck("Sospensioni",  s.showSuspTLProperty()),
                bindCheck("Coach",        s.showCoachTLProperty())
        );
        tlChecks.setPadding(new Insets(0,10,10,10));

        VBox tlPage = new VBox(10, tlGrid, new Separator(), tlChecks);
        tlPage.setPadding(new Insets(6, 6, 10, 6));
        ScrollPane sp = new ScrollPane(tlPage);
        sp.setFitToWidth(true); sp.setPannable(true);
        VBox tabTimeline = new VBox(sp);

        // ---------- TAB: ALTRI PANNELLI ----------
        VBox tabAnalysis = new VBox(10,
                header("Widget disponibili (Analysis / AllLaps)"),
                bindCheck("Pneumatici (T)", s.wTyreTempProperty()),
                bindCheck("Pressioni",      s.wTyrePressProperty()),
                bindCheck("Sospensioni",      s.wSuspensionProperty()),
                bindCheck("Freni",          s.wBrakesProperty()),
                bindCheck("Danni",          s.wDamageProperty()),
                bindCheck("Pedali",         s.wPedalsProperty())
        );
        tabAnalysis.setPadding(new Insets(10));

        // ---------- DIALOG ----------
        TabPane tp = new TabPane(
                new Tab("Colori grafici", colorsScroll),
                new Tab("Timeline",       tabTimeline),
                new Tab("Altri pannelli", tabAnalysis)
        );
        tp.getTabs().forEach(t -> t.setClosable(false));

        BorderPane root = new BorderPane(tp);
        root.setPadding(new Insets(6));

        Stage st = new Stage();
        st.initOwner(owner);
        st.initModality(Modality.NONE);
        st.setTitle("Personalizzazione");
        st.setScene(new Scene(root, 840, 600));
        st.show();
    }

    // ---------- helpers ----------
    private static Label header(String txt){
        Label l = new Label(txt);
        l.setStyle("-fx-font-weight: bold; -fx-opacity: .9;");
        return l;
    }
    private static ColorPicker bindPicker(javafx.beans.property.ObjectProperty<Color> p){
        ColorPicker cp = new ColorPicker(p.get());
        cp.valueProperty().bindBidirectional(p);
        return cp;
    }
    private static CheckBox bindCheck(String label, javafx.beans.property.BooleanProperty p){
        CheckBox cb = new CheckBox(label);
        cb.selectedProperty().bindBidirectional(p);
        cb.setDisable(false);
        return cb;
    }
    private static Image safeRes(String path){
        try{
            var url = SettingsDialog.class.getResource(path);
            return (url == null) ? null : new Image(url.toExternalForm(), 120, 0, true, true);
        }catch(Exception e){ return null; }
    }
}
