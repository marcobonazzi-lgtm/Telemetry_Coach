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
    private final BooleanProperty wTyreTemp  = new SimpleBooleanProperty(true);
    private final BooleanProperty wTyrePress = new SimpleBooleanProperty(true);
    private final BooleanProperty wSuspension = new SimpleBooleanProperty(true);
    private final BooleanProperty wBrakes    = new SimpleBooleanProperty(true);
    private final BooleanProperty wDamage    = new SimpleBooleanProperty(true);
    private final BooleanProperty wPedals    = new SimpleBooleanProperty(true);


    // ===== Caricamento =====
    private void load() {
        useDefaultColors.set(getBool("useDefaultColors", true));

        colorSpeed.set      (fromHex(prefs.get("colorSpeed",      toHex(colorSpeed.get()))));
        colorThrottle.set   (fromHex(prefs.get("colorThrottle",   toHex(colorThrottle.get()))));
        colorBrake.set      (fromHex(prefs.get("colorBrake",      toHex(colorBrake.get()))));
        colorClutch.set     (fromHex(prefs.get("colorClutch",     toHex(colorClutch.get()))));
        colorSteer.set      (fromHex(prefs.get("colorSteer",      toHex(colorSteer.get()))));
        colorRpm.set        (fromHex(prefs.get("colorRpm",        toHex(colorRpm.get()))));
        colorFfb.set        (fromHex(prefs.get("colorFfb",        toHex(colorFfb.get()))));
        colorSeat.set       (fromHex(prefs.get("colorSeat",       toHex(colorSeat.get()))));
        colorPedalForce.set (fromHex(prefs.get("colorPedalForce", toHex(colorPedalForce.get()))));

        // Ghost


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

        wheelImagePath.set(prefs.get("wheelImagePath", wheelImagePath.get()));

        // Timeline (nuove chiavi con fallback alle vecchie)
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

        // Legacy allineate
        showWheel.set     (prefs.getBoolean("showWheel",     showWheelTL.get()));
        showPedals.set    (prefs.getBoolean("showPedals",    showPedalsTL.get()));
        showFFB.set       (prefs.getBoolean("showFFB",       showFfbTL.get()));
        showTyres.set     (prefs.getBoolean("showTyres",     showTyresTL.get()));
        showBrakes.set    (prefs.getBoolean("showBrakes",    showBrakesTL.get()));
        showSeat.set      (prefs.getBoolean("showSeat",      showSeatTL.get()));
        showCoach.set     (prefs.getBoolean("showCoach",     showCoachTL.get()));
        showPedalForce.set(prefs.getBoolean("showPedalForce", true));

        // Analysis / AllLaps
        wTyreTemp.set (getBool("wTyreTemp",  true));
        wTyrePress.set(getBool("wTyrePress", true));
        wBrakes.set   (getBool("wBrakes",    true));
        wDamage.set   (getBool("wDamage",    true));
        wPedals.set   (getBool("wPedals",    true));
    }

    // ===== Salvataggio automatico =====
    private void attachAutoSave() {
        useDefaultColors.addListener((o, ov, nv) -> prefs.putBoolean("useDefaultColors", nv));

        addAutoSave(colorSpeed,      "colorSpeed");
        addAutoSave(colorThrottle,   "colorThrottle");
        addAutoSave(colorBrake,      "colorBrake");
        addAutoSave(colorClutch,     "colorClutch");
        addAutoSave(colorSteer,      "colorSteer");
        addAutoSave(colorRpm,        "colorRpm");
        addAutoSave(colorFfb,        "colorFfb");
        addAutoSave(colorSeat,       "colorSeat");
        addAutoSave(colorPedalForce, "colorPedalForce");

        // Ghost
        addAutoSave(ghostColorA, "ghostColorA");
        addAutoSave(ghostColorB, "ghostColorB");
        addAutoSave(ghostColorC, "ghostColorC");
        ghostOpacity.addListener((o, ov, nv) -> prefs.putDouble("ghostOpacity", nv.doubleValue()));

        // Comparata
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

        // Timeline
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

        // legacy (compat)
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

    }
    public BooleanProperty wSuspensionProperty() { return wSuspension; }
    public boolean isWSuspension() { return wSuspension.get(); }
    public void setWSuspension(boolean v) { wSuspension.set(v); }
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

        colorSpeed.set(Color.web("#f97316"));
        colorThrottle.set(Color.web("#1d4ed8"));
        colorBrake.set(Color.web("#ef4444"));
        colorClutch.set(Color.web("#10b981"));
        colorSteer.set(Color.web("#f59e0b"));
        colorRpm.set(Color.web("#6366f1"));
        colorFfb.set(Color.web("#8b5cf6"));
        colorSeat.set(Color.web("#06b6d4"));
        colorPedalForce.set(Color.web("#10b981"));

        // Ghost
        ghostColorA.set(Color.web("#64748b"));
        ghostColorB.set(Color.web("#94a3b8"));
        ghostColorC.set(Color.web("#cbd5e1"));
        ghostOpacity.set(0.55);

        // Comparata (più “scure” rispetto ai colori base per distinguere)
        cmpColorSpeed.set(Color.web("#c2410c"));
        cmpColorThrottle.set(Color.web("#0e7490"));
        cmpColorBrake.set(Color.web("#b91c1c"));
        cmpColorClutch.set(Color.web("#047857"));
        cmpColorSteer.set(Color.web("#a16207"));
        cmpColorRpm.set(Color.web("#4338ca"));
        cmpColorFfb.set(Color.web("#6d28d9"));
        cmpColorSeat.set(Color.web("#0e7490"));
        cmpColorPedalForce.set(Color.web("#047857"));

        wheelImagePath.set("/assets/wheel.png");

        showWheelTL.set(true); showSpeedTL.set(true); showRpmTL.set(true);
        showPedalsTL.set(true); showFfbTL.set(true); showTyresTL.set(true);
        showBrakesTL.set(true); showSeatTL.set(true); showCoachTL.set(true);
        showSuspTL.set(true);

        wTyreTemp.set(true); wTyrePress.set(true); wBrakes.set(true);

    }

    // ===== Getters (properties) =====
    public BooleanProperty useDefaultColorsProperty(){ return useDefaultColors; }
    public ObjectProperty<Color> colorSpeedProperty()      { return colorSpeed; }
    public ObjectProperty<Color> colorThrottleProperty()   { return colorThrottle; }
    public ObjectProperty<Color> colorBrakeProperty()      { return colorBrake; }
    public ObjectProperty<Color> colorClutchProperty()     { return colorClutch; }
    public ObjectProperty<Color> colorSteerProperty()      { return colorSteer; }
    public ObjectProperty<Color> colorRpmProperty()        { return colorRpm; }
    public ObjectProperty<Color> colorFfbProperty()        { return colorFfb; }
    public ObjectProperty<Color> colorSeatProperty()       { return colorSeat; }
    public ObjectProperty<Color> colorPedalForceProperty() { return colorPedalForce; }

    // Ghost
    public ObjectProperty<Color> ghostColorAProperty(){ return ghostColorA; } // GHOST 1
    public ObjectProperty<Color> ghostColorBProperty(){ return ghostColorB; } // GHOST 2
    public ObjectProperty<Color> ghostColorCProperty(){ return ghostColorC; } // GHOST 3
    public DoubleProperty        ghostOpacityProperty(){ return ghostOpacity; }

    // Comparata
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

    // Timeline (nuove)
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

    // Legacy (compat)
    public BooleanProperty showWheelProperty()      { return showWheel; }
    public BooleanProperty showPedalsProperty()     { return showPedals; }
    public BooleanProperty showFFBProperty()        { return showFFB; }
    public BooleanProperty showTyresProperty()      { return showTyres; }
    public BooleanProperty showBrakesProperty()     { return showBrakes; }
    public BooleanProperty showSeatProperty()       { return showSeat; }
    public BooleanProperty showCoachProperty()      { return showCoach; }
    public BooleanProperty showPedalForceProperty() { return showPedalForce; }

    // Analysis / AllLaps
    public BooleanProperty wTyreTempProperty()  { return wTyreTemp; }
    public BooleanProperty wTyrePressProperty() { return wTyrePress; }
    public BooleanProperty wBrakesProperty()    { return wBrakes; }
    public BooleanProperty wDamageProperty()    { return wDamage; }
    public BooleanProperty wPedalsProperty()    { return wPedals; }

}
