package org.simulator.telemetrycoachFX;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.simulator.ui.DataController;
import org.simulator.ui.ChartStyles;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import org.simulator.ui.settings.SettingsDialog;
import org.simulator.ui.settings.UiSettings;
import org.simulator.ui.ChartStyles;

public class TelemetryCoachFX extends Application {

    private final DataController data = new DataController();

    @Override
    public void start(Stage stage) {
        stage.setTitle("Telemetry Coach");

        Image appIcon = safeImage("/assets/app_logo.png");
        if (appIcon != null) stage.getIcons().setAll(appIcon);

        AppUI appUI = new AppUI(data);
        ImportService importer = new ImportService(data, appUI);

        final java.util.concurrent.atomic.AtomicReference<WelcomeView> welcomeRef = new java.util.concurrent.atomic.AtomicReference<>();
        WelcomeView welcome = new WelcomeView(
                stage,
                file -> importer.importFromWelcome(file, stage, welcomeRef.get()),
                stage::close
        );
        welcomeRef.set(welcome);

        IntroVideoView intro = new IntroVideoView(
                stage,
                "/assets/intro.mp4",
                () -> {
                    // 👉 invece di rimpiazzare la root con 'welcome',
                    //    rimpiazza il CENTRO del guscio
                    BorderPane shell = (BorderPane) stage.getScene().getRoot();
                    shell.setCenter(welcome.getRoot());
                    stage.setMaximized(true);
                }
        );

        // 🔸 crea SUBITO il "guscio" con MenuBar e metti l'INTRO al centro
        BorderPane shell = wrapWithShell(intro.getRoot(), stage);

        // UNICA scena: il root è SEMPRE il "guscio"
        Scene scene = new Scene(shell, 1500, 940);
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
        // Avvio standard JavaFX
        Application.launch(TelemetryCoachFX.class, args);
        // oppure semplicemente: launch(args);
    }
    /** Crea un BorderPane con MenuBar in alto e il 'content' al centro. */
    private BorderPane wrapWithShell(Node content, Stage stage){
        BorderPane shell = new BorderPane(content);
        shell.setTop(buildPersonalizeMenu(stage));
        return shell;
    }

    /** Menu "Personalizza" (apre il pannello impostazioni) + (facoltativo) toggle rapidi. */
    private MenuBar buildPersonalizeMenu(Stage stage){
        Menu mPers = new Menu("Personalizza");
        MenuItem open = new MenuItem("Apri pannello…");
        open.setOnAction(e -> SettingsDialog.show(stage));
        mPers.getItems().add(open);
        return new MenuBar(mPers);
    }


}
