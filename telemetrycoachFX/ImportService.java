package org.simulator.telemetrycoachFX;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.simulator.canale.Channel;
import org.simulator.ui.DataController;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class ImportService {

    private final DataController data;
    private final AppUI appUI;

    ImportService(DataController data, AppUI appUI) {
        this.data = data;
        this.appUI = appUI;
    }

    // chiamato dal WelcomeView
    void importFromWelcome(File f, Stage stage, WelcomeView welcomeView) {
        if (f == null) return;
        welcomeView.showLoading(true);

        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() {
                try {
                    Map<String, Channel> mapping = new HashMap<>();
                    data.load(f.toPath(), mapping);
                    return true;
                } catch (Exception ex) {
                    updateMessage(ex.getMessage());
                    return false;
                }
            }
        };

        task.setOnSucceeded(ev -> {
            welcomeView.showLoading(false);
            if (task.getValue()) {
                appUI.init(stage, this::chooseAndLoadInsideApp);
                appUI.populateAfterLoad(f);
                BorderPane shell = (BorderPane) stage.getScene().getRoot();
                shell.setCenter(appUI.getRoot());

            } else {
                String msg = (task.getMessage() == null || task.getMessage().isBlank())
                        ? "Errore durante il caricamento del CSV."
                        : task.getMessage();
                new Alert(Alert.AlertType.ERROR, msg).showAndWait();
            }
        });
        task.setOnFailed(ev -> {
            welcomeView.showLoading(false);
            new Alert(Alert.AlertType.ERROR, "Errore durante il caricamento del CSV.").showAndWait();
        });

        new Thread(task, "csv-loader").start();
    }

    // chiamato dal pulsante in-app
    void chooseAndLoadInsideApp(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        if (!loadCsv(f.toPath())) return;
        appUI.populateAfterLoad(f);
    }

    private boolean loadCsv(Path p) {
        try {
            Map<String, Channel> mapping = new HashMap<>();
            data.load(p, mapping);
            return true;
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Errore caricamento CSV: " + ex.getMessage()).showAndWait();
            return false;
        }
    }
}
