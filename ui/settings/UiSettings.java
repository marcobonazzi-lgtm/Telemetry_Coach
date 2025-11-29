package org.simulator.ui.settings;

import javafx.beans.property.*;
import javafx.scene.paint.Color;
import java.util.prefs.Preferences;

/** Impostazioni globali (singleton) con persistenza via Preferences. */
public final class UiSettings {

    // ===== Singleton =====
    private static final UiSettings INSTANCE = new UiSettings();
    public static UiSettings get() { return INSTANCE; }
    private UiSettings() { load(); attachAutoSave(); }

    // ===== Preferences =====
    private final Preferences prefs = Preferences.userNodeForPackage(UiSettings.class);

    // ===== TEMA =====
    private final ObjectProperty<AppTheme> currentTheme = new SimpleObjectProperty<>(AppTheme.CLASSIC);

    // ===== Colori grafici (Sessione attuale) =====
    private final BooleanProperty       useDefaultColors = new SimpleBooleanProperty(true);
    private final ObjectProperty<Color> colorSpeed       = new SimpleObjectProperty<>(Color.web("#f97316"));
    private final ObjectProperty<Color> colorThrottle    = new SimpleObjectProperty<>(Color.web("#1d4ed8"));
    private final ObjectProperty<Color> colorBrake       = new SimpleObjectProperty<>(Color.web("#ef4444"));
    private final ObjectProperty<Color> colorClutch      = new SimpleObjectProperty<>(Color.web("#10b981"));
    private final ObjectProperty<Color> colorSteer       = new SimpleObjectProperty<>(Color.web("#f59e0b"));
    private final ObjectProperty<Color> colorRpm         = new SimpleObjectProperty<>(Color.web("#6366f1"));
    private final ObjectProperty<Color> colorFfb         = new SimpleObjectProperty<>(Color.web("#8b5cf6"));
    private final ObjectProperty<Color> colorSeat        = new SimpleObjectProperty<>(Color.web("#06b6d4"));
    private final ObjectProperty<Color> colorPedalForce  = new SimpleObjectProperty<>(Color.web("#10b981"));

    // ===== Ghost (multi-serie) =====
    private final ObjectProperty<Color> ghostColorA = new SimpleObjectProperty<>(Color.web("#64748b")); // GHOST 1
    private final ObjectProperty<Color> ghostColorB = new SimpleObjectProperty<>(Color.web("#94a3b8")); // GHOST 2
    private final ObjectProperty<Color> ghostColorC = new SimpleObjectProperty<>(Color.web("#cbd5e1")); // GHOST 3
    private final DoubleProperty        ghostOpacity = new SimpleDoubleProperty(0.55);

    // ===== Colori “Sessione comparata” (stessa mappa della sessione attuale) =====
    private final BooleanProperty       useDefaultCmpColors = new SimpleBooleanProperty(true);
    private final ObjectProperty<Color> cmpColorSpeed       = new SimpleObjectProperty<>(Color.web("#c2410c"));
    private final ObjectProperty<Color> cmpColorThrottle    = new SimpleObjectProperty<>(Color.web("#0e7490"));
    private final ObjectProperty<Color> cmpColorBrake       = new SimpleObjectProperty<>(Color.web("#b91c1c"));
    private final ObjectProperty<Color> cmpColorClutch      = new SimpleObjectProperty<>(Color.web("#047857"));
    private final ObjectProperty<Color> cmpColorSteer       = new SimpleObjectProperty<>(Color.web("#a16207"));
    private final ObjectProperty<Color> cmpColorRpm         = new SimpleObjectProperty<>(Color.web("#4338ca"));
    private final ObjectProperty<Color> cmpColorFfb         = new SimpleObjectProperty<>(Color.web("#6d28d9"));
    private final ObjectProperty<Color> cmpColorSeat        = new SimpleObjectProperty<>(Color.web("#0e7490"));
    private final ObjectProperty<Color> cmpColorPedalForce  = new SimpleObjectProperty<>(Color.web("#047857"));

    // ===== Timeline =====
    private final StringProperty wheelImagePath = new SimpleStringProperty("/assets/wheel.png");
    private final BooleanProperty showWheelTL  = new SimpleBooleanProperty(true);
    private final BooleanProperty showSpeedTL  = new SimpleBooleanProperty(true);
    private final BooleanProperty showRpmTL    = new SimpleBooleanProperty(true);
    private final BooleanProperty showPedalsTL = new SimpleBooleanProperty(true);
    private final BooleanProperty showFfbTL    = new SimpleBooleanProperty(true);
    private final BooleanProperty showTyresTL  = new SimpleBooleanProperty(true);
    private final BooleanProperty showBrakesTL = new SimpleBooleanProperty(true);
    private final BooleanProperty showSeatTL   = new SimpleBooleanProperty(true);
    private final BooleanProperty showCoachTL  = new SimpleBooleanProperty(true);
    private final BooleanProperty showSuspTL   = new SimpleBooleanProperty(true);

    // NUOVO: Widget G-Force
    private final BooleanProperty showGForceTL = new SimpleBooleanProperty(true);

    // Legacy (compat)
    private final BooleanProperty showWheel      = new SimpleBooleanProperty(true);
    private final BooleanProperty showPedals     = new SimpleBooleanProperty(true);
    private final BooleanProperty showFFB        = new SimpleBooleanProperty(true);
    private final BooleanProperty showTyres      = new SimpleBooleanProperty(true);
    private final BooleanProperty showBrakes     = new SimpleBooleanProperty(true);
    private final BooleanProperty showSeat       = new SimpleBooleanProperty(true);
    private final BooleanProperty showCoach      = new SimpleBooleanProperty(true);
    private final BooleanProperty showPedalForce = new SimpleBooleanProperty(true);

    // ===== Altri pannelli (Analysis / AllLaps) =====
    private final BooleanProperty wTyreTemp   = new SimpleBooleanProperty(true);
    private final BooleanProperty wTyrePress  = new SimpleBooleanProperty(true);
    private final BooleanProperty wSuspension = new SimpleBooleanProperty(true); // <--- Campo esistente
    private final BooleanProperty wBrakes     = new SimpleBooleanProperty(true);
    private final BooleanProperty wDamage     = new SimpleBooleanProperty(true);
    private final BooleanProperty wPedals     = new SimpleBooleanProperty(true);

    // ===== Settings Circuito (Marker, Ideal Line, DRS) =====
    // Curve Markers
    private final BooleanProperty showCurveMarkers = new SimpleBooleanProperty(true);
    private final DoubleProperty  curveMarkerSize  = new SimpleDoubleProperty(9.0); // Default 9.0
    private final BooleanProperty showCurvePopup   = new SimpleBooleanProperty(true); // Popup al click

    // Ideal Line
    private final ObjectProperty<Color> idealLineColor   = new SimpleObjectProperty<>(Color.web("#FFD700")); // Gold
    private final DoubleProperty        idealLineWidth   = new SimpleDoubleProperty(2.0);
    private final DoubleProperty        idealLineOpacity = new SimpleDoubleProperty(0.65);

    // DRS Zones
    private final ObjectProperty<Color> drsZoneColor     = new SimpleObjectProperty<>(Color.CYAN);
    private final DoubleProperty        drsZoneWidth     = new SimpleDoubleProperty(32.0);
    private final DoubleProperty        drsZoneOpacity   = new SimpleDoubleProperty(0.5);


    // ===== Caricamento =====
    private void load() {
        // Carica il tema salvato (default CLASSIC)
        String themeName = prefs.get("appTheme", AppTheme.CLASSIC.name());
        try {
            currentTheme.set(AppTheme.valueOf(themeName));
        } catch (IllegalArgumentException e) {
            currentTheme.set(AppTheme.CLASSIC);
        }

        useDefaultColors.set(getBool("useDefaultColors", true));
        useDefaultCmpColors.set(getBool("useDefaultCmpColors", true));

        colorSpeed.set      (fromHex(prefs.get("colorSpeed",      toHex(colorSpeed.get()))));
        colorThrottle.set   (fromHex(prefs.get("colorThrottle",   toHex(colorThrottle.get()))));
        colorBrake.set      (fromHex(prefs.get("colorBrake",      toHex(colorBrake.get()))));
        colorClutch.set     (fromHex(prefs.get("colorClutch",     toHex(colorClutch.get()))));
        colorSteer.set      (fromHex(prefs.get("colorSteer",      toHex(colorSteer.get()))));
        colorRpm.set        (fromHex(prefs.get("colorRpm",        toHex(colorRpm.get()))));
        colorFfb.set        (fromHex(prefs.get("colorFfb",        toHex(colorFfb.get()))));
        colorSeat.set       (fromHex(prefs.get("colorSeat",       toHex(colorSeat.get()))));
        colorPedalForce.set (fromHex(prefs.get("colorPedalForce", toHex(colorPedalForce.get()))));

        // Comparata
        cmpColorSpeed.set      (fromHex(prefs.get("cmpColorSpeed",      toHex(cmpColorSpeed.get()))));
        cmpColorThrottle.set   (fromHex(prefs.get("cmpColorThrottle",   toHex(cmpColorThrottle.get()))));
        cmpColorBrake.set      (fromHex(prefs.get("cmpColorBrake",      toHex(cmpColorBrake.get()))));
        cmpColorClutch.set     (fromHex(prefs.get("cmpColorClutch",     toHex(cmpColorClutch.get()))));
        cmpColorSteer.set      (fromHex(prefs.get("cmpColorSteer",      toHex(cmpColorSteer.get()))));
        cmpColorRpm.set        (fromHex(prefs.get("cmpColorRpm",        toHex(cmpColorRpm.get()))));
        cmpColorFfb.set        (fromHex(prefs.get("cmpColorFfb",        toHex(cmpColorFfb.get()))));
        cmpColorSeat.set       (fromHex(prefs.get("cmpColorSeat",       toHex(cmpColorSeat.get()))));
        cmpColorPedalForce.set (fromHex(prefs.get("cmpColorPedalForce", toHex(cmpColorPedalForce.get()))));

        // Se usa i colori di default, forza l'applicazione del tema caricato all'avvio
        if (useDefaultColors.get()) applyMainPaletteForTheme(currentTheme.get());
        if (useDefaultCmpColors.get()) applyCmpPaletteForTheme(currentTheme.get());

        wheelImagePath.set(prefs.get("wheelImagePath", wheelImagePath.get()));

        // Timeline
        showWheelTL.set (prefs.getBoolean("showWheelTL",  prefs.getBoolean("showWheel",  true)));
        showSpeedTL.set (prefs.getBoolean("showSpeedTL",  true));
        showRpmTL.set   (prefs.getBoolean("showRpmTL",    true));
        showPedalsTL.set(prefs.getBoolean("showPedalsTL", prefs.getBoolean("showPedals", true)));
        showFfbTL.set   (prefs.getBoolean("showFfbTL",    prefs.getBoolean("showFFB",    true)));
        showTyresTL.set (prefs.getBoolean("showTyresTL",  prefs.getBoolean("showTyres",  true)));
        showBrakesTL.set(prefs.getBoolean("showBrakesTL", prefs.getBoolean("showBrakes", true)));
        showSeatTL.set  (prefs.getBoolean("showSeatTL",   prefs.getBoolean("showSeat",   true)));
        showCoachTL.set (prefs.getBoolean("showCoachTL",  prefs.getBoolean("showCoach",  true)));
        showSuspTL.set  (prefs.getBoolean("showSuspTL",   true));

        // NUOVO: Caricamento G-Force
        showGForceTL.set(prefs.getBoolean("showGForceTL", true));

        // Legacy allineate
        showWheel.set     (prefs.getBoolean("showWheel",     showWheelTL.get()));
        showPedals.set    (prefs.getBoolean("showPedals",    showPedalsTL.get()));
        showFFB.set       (prefs.getBoolean("showFFB",       showFfbTL.get()));
        showTyres.set     (prefs.getBoolean("showTyres",     showTyresTL.get()));
        showBrakes.set    (prefs.getBoolean("showBrakes",    showBrakesTL.get()));
        showSeat.set      (prefs.getBoolean("showSeat",      showSeatTL.get()));
        showCoach.set     (prefs.getBoolean("showCoach",     showCoachTL.get()));
        showPedalForce.set(prefs.getBoolean("showPedalForce", true));

        // Analysis
        wTyreTemp.set (getBool("wTyreTemp",  true));
        wTyrePress.set(getBool("wTyrePress", true));
        wBrakes.set   (getBool("wBrakes",    true));
        wDamage.set   (getBool("wDamage",    true));
        wPedals.set   (getBool("wPedals",    true));
        wSuspension.set(getBool("wSuspension", true));

        // Circuito
        showCurveMarkers.set(prefs.getBoolean("showCurveMarkers", true));
        curveMarkerSize.set(prefs.getDouble("curveMarkerSize", 9.0));
        showCurvePopup.set(prefs.getBoolean("showCurvePopup", true));

        idealLineColor.set(fromHex(prefs.get("idealLineColor", toHex(idealLineColor.get()))));
        idealLineWidth.set(prefs.getDouble("idealLineWidth", 2.0));
        idealLineOpacity.set(prefs.getDouble("idealLineOpacity", 0.65));

        drsZoneColor.set(fromHex(prefs.get("drsZoneColor", toHex(drsZoneColor.get()))));
        drsZoneWidth.set(prefs.getDouble("drsZoneWidth", 32.0));
        drsZoneOpacity.set(prefs.getDouble("drsZoneOpacity", 0.5));
    }

    // ===== Salvataggio automatico =====
    private void attachAutoSave() {
        // Salva il tema e applica colori se i flag sono attivi
        currentTheme.addListener((o, ov, nv) -> {
            if (nv != null) {
                prefs.put("appTheme", nv.name());
                if (useDefaultColors.get()) applyMainPaletteForTheme(nv);
                if (useDefaultCmpColors.get()) applyCmpPaletteForTheme(nv);

                if (nv == AppTheme.DARK_BLUE || nv == AppTheme.RACING) ghostOpacity.set(0.70);
                else ghostOpacity.set(0.55);
            }
        });

        // Toggle Default Main
        useDefaultColors.addListener((o, ov, nv) -> {
            if (nv) applyMainPaletteForTheme(currentTheme.get());
            prefs.putBoolean("useDefaultColors", nv);
        });

        // Toggle Default Cmp
        useDefaultCmpColors.addListener((o, ov, nv) -> {
            if (nv) applyCmpPaletteForTheme(currentTheme.get());
            prefs.putBoolean("useDefaultCmpColors", nv);
        });

        addAutoSave(colorSpeed,      "colorSpeed");
        addAutoSave(colorThrottle,   "colorThrottle");
        addAutoSave(colorBrake,      "colorBrake");
        addAutoSave(colorClutch,     "colorClutch");
        addAutoSave(colorSteer,      "colorSteer");
        addAutoSave(colorRpm,        "colorRpm");
        addAutoSave(colorFfb,        "colorFfb");
        addAutoSave(colorSeat,       "colorSeat");
        addAutoSave(colorPedalForce, "colorPedalForce");

        addAutoSave(ghostColorA, "ghostColorA");
        addAutoSave(ghostColorB, "ghostColorB");
        addAutoSave(ghostColorC, "ghostColorC");
        ghostOpacity.addListener((o, ov, nv) -> prefs.putDouble("ghostOpacity", nv.doubleValue()));

        addAutoSave(cmpColorSpeed,      "cmpColorSpeed");
        addAutoSave(cmpColorThrottle,   "cmpColorThrottle");
        addAutoSave(cmpColorBrake,      "cmpColorBrake");
        addAutoSave(cmpColorClutch,     "cmpColorClutch");
        addAutoSave(cmpColorSteer,      "cmpColorSteer");
        addAutoSave(cmpColorRpm,        "cmpColorRpm");
        addAutoSave(cmpColorFfb,        "cmpColorFfb");
        addAutoSave(cmpColorSeat,       "cmpColorSeat");
        addAutoSave(cmpColorPedalForce, "cmpColorPedalForce");

        wheelImagePath.addListener((o, ov, nv) -> { if (nv != null) prefs.put("wheelImagePath", nv); });

        showWheelTL .addListener((o, ov, nv) -> prefs.putBoolean("showWheelTL",  nv));
        showSpeedTL .addListener((o, ov, nv) -> prefs.putBoolean("showSpeedTL",  nv));
        showRpmTL   .addListener((o, ov, nv) -> prefs.putBoolean("showRpmTL",    nv));
        showPedalsTL.addListener((o, ov, nv) -> prefs.putBoolean("showPedalsTL", nv));
        showFfbTL   .addListener((o, ov, nv) -> prefs.putBoolean("showFfbTL",    nv));
        showTyresTL .addListener((o, ov, nv) -> prefs.putBoolean("showTyresTL",  nv));
        showBrakesTL.addListener((o, ov, nv) -> prefs.putBoolean("showBrakesTL", nv));
        showSeatTL  .addListener((o, ov, nv) -> prefs.putBoolean("showSeatTL",   nv));
        showCoachTL .addListener((o, ov, nv) -> prefs.putBoolean("showCoachTL",  nv));
        showSuspTL  .addListener((o, ov, nv) -> prefs.putBoolean("showSuspTL",   nv));
        showGForceTL.addListener((o, ov, nv) -> prefs.putBoolean("showGForceTL", nv));

        showWheel.addListener     ((o, ov, nv) -> prefs.putBoolean("showWheel",     nv));
        showPedals.addListener    ((o, ov, nv) -> prefs.putBoolean("showPedals",    nv));
        showFFB.addListener       ((o, ov, nv) -> prefs.putBoolean("showFFB",       nv));
        showTyres.addListener     ((o, ov, nv) -> prefs.putBoolean("showTyres",     nv));
        showBrakes.addListener    ((o, ov, nv) -> prefs.putBoolean("showBrakes",    nv));
        showSeat.addListener      ((o, ov, nv) -> prefs.putBoolean("showSeat",      nv));
        showCoach.addListener     ((o, ov, nv) -> prefs.putBoolean("showCoach",     nv));
        showPedalForce.addListener((o, ov, nv) -> prefs.putBoolean("showPedalForce", nv));

        wTyreTemp .addListener((o, ov, nv) -> prefs.putBoolean("wTyreTemp",  nv));
        wTyrePress.addListener((o, ov, nv) -> prefs.putBoolean("wTyrePress", nv));
        wBrakes   .addListener((o, ov, nv) -> prefs.putBoolean("wBrakes",    nv));
        wDamage   .addListener((o, ov, nv) -> prefs.putBoolean("wDamage",    nv));
        wPedals   .addListener((o, ov, nv) -> prefs.putBoolean("wPedals",    nv));
        wSuspension.addListener   ((o, ov, nv) -> prefs.putBoolean("wSuspension", nv));

        showCurveMarkers.addListener((o, ov, nv) -> prefs.putBoolean("showCurveMarkers", nv));
        curveMarkerSize.addListener((o, ov, nv) -> prefs.putDouble("curveMarkerSize", nv.doubleValue()));
        showCurvePopup.addListener((o, ov, nv) -> prefs.putBoolean("showCurvePopup", nv));

        addAutoSave(idealLineColor, "idealLineColor");
        idealLineWidth.addListener((o, ov, nv) -> prefs.putDouble("idealLineWidth", nv.doubleValue()));
        idealLineOpacity.addListener((o, ov, nv) -> prefs.putDouble("idealLineOpacity", nv.doubleValue()));

        addAutoSave(drsZoneColor, "drsZoneColor");
        drsZoneWidth.addListener((o, ov, nv) -> prefs.putDouble("drsZoneWidth", nv.doubleValue()));
        drsZoneOpacity.addListener((o, ov, nv) -> prefs.putDouble("drsZoneOpacity", nv.doubleValue()));
    }

    private void applyMainPaletteForTheme(AppTheme theme) {
        ThemeManager.ThemePalette p = ThemeManager.getPalette(theme);
        colorSpeed.set(p.speed());
        colorThrottle.set(p.throttle());
        colorBrake.set(p.brake());
        colorClutch.set(p.clutch());
        colorSteer.set(p.steer());
        colorRpm.set(p.rpm());
        colorFfb.set(p.ffb());
        colorSeat.set(p.seat());
        colorPedalForce.set(p.pedalForce());
        trackMapColor.set(p.trackMap());
    }

    private void applyCmpPaletteForTheme(AppTheme theme) {
        ThemeManager.ThemePalette p = ThemeManager.getPalette(theme);
        cmpColorSpeed.set(p.cmpSpeed());
        cmpColorThrottle.set(p.cmpThrottle());
        cmpColorBrake.set(p.cmpBrake());
        cmpColorClutch.set(p.cmpClutch());
        cmpColorSteer.set(p.cmpSteer());
        cmpColorRpm.set(p.cmpRpm());
        cmpColorFfb.set(p.cmpFfb());
        cmpColorSeat.set(p.cmpSeat());
        cmpColorPedalForce.set(p.cmpPedalForce());
    }

    private void addAutoSave(ObjectProperty<Color> p, String key) {
        p.addListener((o, ov, nv) -> prefs.put(key, toHex(nv)));
    }

    private boolean getBool(String key, boolean def) {
        try { return prefs.getBoolean(key, def); } catch (Exception e) { return def; }
    }

    private static String toHex(Color c) {
        if (c == null) return "#000000";
        return String.format("#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
    private static Color fromHex(String hex) {
        try { return Color.web(hex); } catch (Exception e) { return Color.BLACK; }
    }

    // ===== Reset =====
    public void resetToDefaults() {
        useDefaultColors.set(true);
        useDefaultCmpColors.set(true);

        applyMainPaletteForTheme(currentTheme.get());
        applyCmpPaletteForTheme(currentTheme.get());

        ghostColorA.set(Color.web("#64748b"));
        ghostColorB.set(Color.web("#94a3b8"));
        ghostColorC.set(Color.web("#cbd5e1"));
        ghostOpacity.set(0.55);

        wheelImagePath.set("/assets/wheel.png");

        showWheelTL.set(true); showSpeedTL.set(true); showRpmTL.set(true);
        showPedalsTL.set(true); showFfbTL.set(true); showTyresTL.set(true);
        showBrakesTL.set(true); showSeatTL.set(true); showCoachTL.set(true);
        showSuspTL.set(true);
        showGForceTL.set(true);

        wTyreTemp.set(true); wTyrePress.set(true); wBrakes.set(true);
        wDamage.set(true); wPedals.set(true); wSuspension.set(true);

        showCurveMarkers.set(true);
        curveMarkerSize.set(9.0);
        showCurvePopup.set(true);

        idealLineColor.set(Color.web("#FFD700"));
        idealLineWidth.set(2.0);
        idealLineOpacity.set(0.65);
        trackMapColor.set(ThemeManager.getPalette(currentTheme.get()).trackMap());
        drsZoneColor.set(Color.CYAN);
        drsZoneWidth.set(32.0);
        drsZoneOpacity.set(0.5);
    }

    // ===== Getters (properties) =====
    public ObjectProperty<AppTheme> currentThemeProperty() { return currentTheme; }
    public AppTheme getCurrentTheme() { return currentTheme.get(); }

    public BooleanProperty useDefaultColorsProperty(){ return useDefaultColors; }
    public BooleanProperty useDefaultCmpColorsProperty(){ return useDefaultCmpColors; }

    public ObjectProperty<Color> colorSpeedProperty()      { return colorSpeed; }
    public ObjectProperty<Color> colorThrottleProperty()   { return colorThrottle; }
    public ObjectProperty<Color> colorBrakeProperty()      { return colorBrake; }
    public ObjectProperty<Color> colorClutchProperty()     { return colorClutch; }
    public ObjectProperty<Color> colorSteerProperty()      { return colorSteer; }
    public ObjectProperty<Color> colorRpmProperty()        { return colorRpm; }
    public ObjectProperty<Color> colorFfbProperty()        { return colorFfb; }
    public ObjectProperty<Color> colorSeatProperty()       { return colorSeat; }
    public ObjectProperty<Color> colorPedalForceProperty() { return colorPedalForce; }

    public ObjectProperty<Color> ghostColorAProperty(){ return ghostColorA; }
    public ObjectProperty<Color> ghostColorBProperty(){ return ghostColorB; }
    public ObjectProperty<Color> ghostColorCProperty(){ return ghostColorC; }
    public DoubleProperty        ghostOpacityProperty(){ return ghostOpacity; }

    public ObjectProperty<Color> cmpColorSpeedProperty()      { return cmpColorSpeed; }
    public ObjectProperty<Color> cmpColorThrottleProperty()   { return cmpColorThrottle; }
    public ObjectProperty<Color> cmpColorBrakeProperty()      { return cmpColorBrake; }
    public ObjectProperty<Color> cmpColorClutchProperty()     { return cmpColorClutch; }
    public ObjectProperty<Color> cmpColorSteerProperty()      { return cmpColorSteer; }
    public ObjectProperty<Color> cmpColorRpmProperty()        { return cmpColorRpm; }
    public ObjectProperty<Color> cmpColorFfbProperty()        { return cmpColorFfb; }
    public ObjectProperty<Color> cmpColorSeatProperty()       { return cmpColorSeat; }
    public ObjectProperty<Color> cmpColorPedalForceProperty() { return cmpColorPedalForce; }

    public StringProperty wheelImagePathProperty(){ return wheelImagePath; }

    public BooleanProperty showWheelTLProperty()  { return showWheelTL; }
    public BooleanProperty showSpeedTLProperty()  { return showSpeedTL; }
    public BooleanProperty showRpmTLProperty()    { return showRpmTL; }
    public BooleanProperty showPedalsTLProperty() { return showPedalsTL; }
    public BooleanProperty showFfbTLProperty()    { return showFfbTL; }
    public BooleanProperty showTyresTLProperty()  { return showTyresTL; }
    public BooleanProperty showBrakesTLProperty() { return showBrakesTL; }
    public BooleanProperty showSeatTLProperty()   { return showSeatTL; }
    public BooleanProperty showCoachTLProperty()  { return showCoachTL; }
    public BooleanProperty showSuspTLProperty()   { return showSuspTL; }
    public BooleanProperty showGForceTLProperty() { return showGForceTL; }

    public BooleanProperty showWheelProperty()      { return showWheel; }
    public BooleanProperty showPedalsProperty()     { return showPedals; }
    public BooleanProperty showFFBProperty()        { return showFFB; }
    public BooleanProperty showTyresProperty()      { return showTyres; }
    public BooleanProperty showBrakesProperty()     { return showBrakes; }
    public BooleanProperty showSeatProperty()       { return showSeat; }
    public BooleanProperty showCoachProperty()      { return showCoach; }
    public BooleanProperty showPedalForceProperty() { return showPedalForce; }
    public ObjectProperty<Color> trackMapColorProperty() { return trackMapColor; }
    public BooleanProperty wTyreTempProperty()  { return wTyreTemp; }
    public BooleanProperty wTyrePressProperty() { return wTyrePress; }
    public BooleanProperty wBrakesProperty()    { return wBrakes; }
    public BooleanProperty wDamageProperty()    { return wDamage; }
    public BooleanProperty wPedalsProperty()    { return wPedals; }
    public BooleanProperty wSuspensionProperty() { return wSuspension; } // <--- ECCOLO!
    private final ObjectProperty<Color> trackMapColor = new SimpleObjectProperty<>(Color.BLACK);
    public BooleanProperty showCurveMarkersProperty() { return showCurveMarkers; }
    public DoubleProperty  curveMarkerSizeProperty()  { return curveMarkerSize; }
    public BooleanProperty showCurvePopupProperty()   { return showCurvePopup; }

    public ObjectProperty<Color> idealLineColorProperty()   { return idealLineColor; }
    public DoubleProperty        idealLineWidthProperty()   { return idealLineWidth; }
    public DoubleProperty        idealLineOpacityProperty() { return idealLineOpacity; }

    public ObjectProperty<Color> drsZoneColorProperty()     { return drsZoneColor; }
    public DoubleProperty        drsZoneWidthProperty()     { return drsZoneWidth; }
    public DoubleProperty        drsZoneOpacityProperty()   { return drsZoneOpacity; }
}