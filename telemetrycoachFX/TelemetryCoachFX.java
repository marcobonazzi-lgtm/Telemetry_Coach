package org.simulator.telemetrycoachFX;

import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.simulator.ui.DataController;
import org.simulator.ui.export.ExportPdfDialog;
import org.simulator.ui.export.ExportOptions;
import org.simulator.ui.settings.SettingsDialog;
import org.simulator.ui.settings.ThemeManager;
import org.simulator.ui.settings.UiSettings;
import javafx.scene.control.*;
import org.simulator.ui.track_view.TrackView;

public class TelemetryCoachFX extends Application {

    private final DataController data = new DataController();

    @Override
    public void start(Stage stage) {
        stage.setTitle("Telemetry Coach");

        Image appIcon = safeImage("/assets/app_logo.png");
        if (appIcon != null) stage.getIcons().setAll(appIcon);

        final java.util.concurrent.atomic.AtomicReference<WelcomeView> welcomeRef = new java.util.concurrent.atomic.AtomicReference<>();

        Runnable showWelcomeAction = () -> {
            if (welcomeRef.get() != null) {
                BorderPane shell = (BorderPane) stage.getScene().getRoot();
                shell.setCenter(welcomeRef.get().getRoot());
                stage.setTitle("Telemetry Coach");
            }
        };

        AppUI appUI = new AppUI(data, showWelcomeAction);
        ImportService importer = new ImportService(data, appUI);

        WelcomeView welcome = new WelcomeView(
                stage,
                file -> importer.importFromWelcome(file, stage, welcomeRef.get()),
                stage::close,
                (trackId, cat) -> {
                    TrackView previewView = new TrackView(data);
                    previewView.setOnBackAction(showWelcomeAction);
                    previewView.setPreviewMode(trackId, cat);
                    BorderPane shell = (BorderPane) stage.getScene().getRoot();
                    shell.setCenter(previewView.getRoot());
                    stage.setTitle("Anteprima Circuito: " + trackId);
                }
        );
        welcomeRef.set(welcome);

        IntroVideoView intro = new IntroVideoView(
                stage,
                "/assets/intro.mp4",
                () -> {
                    BorderPane shell = (BorderPane) stage.getScene().getRoot();
                    shell.setCenter(welcome.getRoot());
                    stage.setMaximized(true);
                }
        );

        BorderPane shell = wrapWithShell(intro.getRoot(), stage);
        Scene scene = new Scene(shell, 1500, 940);

        // --- GESTIONE TEMA ---
        // 1. Applica il tema salvato all'avvio
        ThemeManager.applyTheme(scene, UiSettings.get().getCurrentTheme());

        // 2. Ascolta i cambiamenti di tema e aggiorna la scena live
        UiSettings.get().currentThemeProperty().addListener((o, ov, nv) -> {
            ThemeManager.applyTheme(scene, nv);
        });
        // ---------------------

        stage.setScene(scene);
        stage.setFullScreenExitHint("");
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.F11) stage.setFullScreen(!stage.isFullScreen()); });

        stage.setMaximized(true);
        stage.show();
    }

    private Image safeImage(String path) {
        try {
            var url = getClass().getResource(path);
            if (url == null) return null;
            return new Image(url.toExternalForm());
        } catch (Exception ex) { return null; }
    }

    public static void main(String[] args) {
        Application.launch(TelemetryCoachFX.class, args);
    }

    private BorderPane wrapWithShell(Node content, Stage stage){
        BorderPane shell = new BorderPane(content);
        shell.setTop(buildPersonalizeMenu(stage));
        return shell;
    }

    private MenuBar buildPersonalizeMenu(Stage stage){
        Menu mExport = new Menu("Esporta in PDF");
        MenuItem openExport = new MenuItem("Apri selezione…");
        openExport.setOnAction(e -> {
            if (data.getLaps() == null || data.getLaps().isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION, "Importa un CSV prima di esportare.").showAndWait();
                return;
            }
            ExportPdfDialog.show(stage, data, (ExportOptions opts) -> {
                System.out.println("[Export] Scelte utente: " + opts);
            });
        });
        mExport.getItems().add(openExport);

        Menu mPers = new Menu("Personalizza");
        MenuItem open = new MenuItem("Apri pannello…");
        open.setOnAction(ev -> SettingsDialog.show(stage));
        mPers.getItems().add(open);

        return new MenuBar(mExport, mPers);
    }
}