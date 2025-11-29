package org.simulator.ui.settings;

import javafx.scene.Scene;
import javafx.scene.paint.Color;

public class ThemeManager {

    public static void applyTheme(Scene scene, AppTheme theme) {
        if (scene == null) return;
        scene.getStylesheets().clear();

        String cssPath = switch (theme) {
            case DARK_BLUE -> "/css/dark_blue.css";
            case LIGHT -> "/css/light.css";
            case RACING -> "/css/racing.css";
            default -> "/css/classic.css";
        };

        if (cssPath != null) {
            var url = ThemeManager.class.getResource(cssPath);
            if (url != null) {
                scene.getStylesheets().add(url.toExternalForm());
            } else {
                System.out.println("Attenzione: File CSS non trovato: " + cssPath);
            }
        }
    }

    public static ThemePalette getPalette(AppTheme theme) {
        return switch (theme) {
            case DARK_BLUE -> new ThemePalette(
                    // PRINCIPALE
                    Color.web("#00e5ff"), Color.web("#2979ff"), Color.web("#ff1744"),
                    Color.web("#00e676"), Color.web("#ffea00"), Color.web("#d500f9"),
                    Color.web("#aa00ff"), Color.web("#00b0ff"), Color.web("#1de9b6"),
                    // COMPARATA
                    Color.web("#ff9100"), Color.web("#ea80fc"), Color.web("#ff80ab"),
                    Color.web("#b9f6ca"), Color.web("#ffd740"), Color.web("#90caf9"),
                    Color.web("#ce93d8"), Color.web("#80deea"), Color.web("#69f0ae"),
                    // TRACCIATO (Chiaro su sfondo scuro)
                    Color.web("#cbd5e1")
            );
            case LIGHT -> new ThemePalette(
                    // PRINCIPALE
                    Color.web("#0f766e"), Color.web("#0369a1"), Color.web("#dc2626"),
                    Color.web("#15803d"), Color.web("#b45309"), Color.web("#4338ca"),
                    Color.web("#7e22ce"), Color.web("#0e7490"), Color.web("#15803d"),
                    // COMPARATA
                    Color.web("#f59e0b"), Color.web("#8b5cf6"), Color.web("#f43f5e"),
                    Color.web("#84cc16"), Color.web("#eab308"), Color.web("#6366f1"),
                    Color.web("#d946ef"), Color.web("#06b6d4"), Color.web("#84cc16"),
                    // TRACCIATO (Scuro su sfondo chiaro)
                    Color.web("#1f2937")
            );
            case RACING -> new ThemePalette(
                    // PRINCIPALE
                    Color.web("#dc2626"), Color.web("#3b82f6"), Color.web("#facc15"),
                    Color.web("#10b981"), Color.web("#f97316"), Color.web("#d946ef"),
                    Color.web("#8b5cf6"), Color.web("#06b6d4"), Color.web("#10b981"),
                    // COMPARATA
                    Color.web("#e2e8f0"), Color.web("#22d3ee"), Color.web("#f472b6"),
                    Color.web("#4ade80"), Color.web("#fcd34d"), Color.web("#a78bfa"),
                    Color.web("#c084fc"), Color.web("#67e8f9"), Color.web("#4ade80"),
                    // TRACCIATO (Bianco Ghiaccio su asfalto nero)
                    Color.web("#cbd5e1")
            );
            default -> new ThemePalette( // CLASSIC
                    Color.web("#f97316"), Color.web("#1d4ed8"), Color.web("#ef4444"),
                    Color.web("#10b981"), Color.web("#f59e0b"), Color.web("#6366f1"),
                    Color.web("#8b5cf6"), Color.web("#06b6d4"), Color.web("#10b981"),
                    // COMPARATA
                    Color.web("#7c2d12"), Color.web("#1e3a8a"), Color.web("#7f1d1d"),
                    Color.web("#064e3b"), Color.web("#78350f"), Color.web("#312e81"),
                    Color.web("#4c1d95"), Color.web("#164e63"), Color.web("#064e3b"),
                    // TRACCIATO (Standard)
                    Color.web("#333333")
            );
        };
    }

    public record ThemePalette(
            Color speed, Color throttle, Color brake, Color clutch,
            Color steer, Color rpm, Color ffb, Color seat, Color pedalForce,
            Color cmpSpeed, Color cmpThrottle, Color cmpBrake, Color cmpClutch,
            Color cmpSteer, Color cmpRpm, Color cmpFfb, Color cmpSeat, Color cmpPedalForce,
            Color trackMap
    ) {}
}