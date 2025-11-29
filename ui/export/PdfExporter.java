package org.simulator.ui.export;

import javafx.stage.Window;
import org.simulator.ui.DataController;

public interface PdfExporter {
    void export(Window owner, DataController data, ExportOptions options) throws Exception;
}
