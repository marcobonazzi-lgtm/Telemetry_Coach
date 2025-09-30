package org.simulator.setup.setup_advisor;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.*;

final class SetupAdvisorPaneBuilder {
    private SetupAdvisorPaneBuilder(){}

    static Node buildSetupPane(List<SetupAdvisor.Recommendation> recs){
        VBox root = new VBox(8);
        root.setStyle("-fx-background-color:#0f172a; -fx-background-radius:10;");
        root.setPadding(new Insets(10));

        CheckBox cbTyres = new CheckBox(SetupAdvisor.UiCategory.TYRES.title);
        CheckBox cbBrakes = new CheckBox(SetupAdvisor.UiCategory.BRAKES.title);
        CheckBox cbDiffPow = new CheckBox(SetupAdvisor.UiCategory.DIFF_POWER.title);
        CheckBox cbSusp = new CheckBox(SetupAdvisor.UiCategory.SUSPENSION.title);
        CheckBox cbAero = new CheckBox(SetupAdvisor.UiCategory.AERO.title);
        CheckBox cbElec = new CheckBox(SetupAdvisor.UiCategory.ELECTRONICS.title);
        CheckBox cbChassis = new CheckBox(SetupAdvisor.UiCategory.CHASSIS.title);
        CheckBox cbDmg = new CheckBox(SetupAdvisor.UiCategory.DAMAGE.title);
        CheckBox cbOther = new CheckBox(SetupAdvisor.UiCategory.OTHER.title);

        cbTyres.setSelected(true); cbBrakes.setSelected(true); cbDiffPow.setSelected(true); cbSusp.setSelected(true);
        cbAero.setSelected(true); cbElec.setSelected(true); cbChassis.setSelected(true); cbDmg.setSelected(true); cbOther.setSelected(true);
        String cbStyle = "-fx-text-fill:#cbd5e1;";
        for (CheckBox c : List.of(cbTyres,cbBrakes,cbDiffPow,cbSusp,cbAero,cbElec,cbChassis,cbDmg,cbOther)) c.setStyle(cbStyle);

        HBox filters = new HBox(10, cbTyres, cbBrakes, cbDiffPow, cbSusp, cbAero, cbElec, cbChassis, cbDmg, cbOther);
        root.getChildren().add(filters);

        VBox list = new VBox(6);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        root.getChildren().add(scroll);

        Runnable render = () -> {
            Set<SetupAdvisor.UiCategory> enabled = new LinkedHashSet<>();
            if (cbTyres.isSelected()) enabled.add(SetupAdvisor.UiCategory.TYRES);
            if (cbBrakes.isSelected()) enabled.add(SetupAdvisor.UiCategory.BRAKES);
            if (cbDiffPow.isSelected()) enabled.add(SetupAdvisor.UiCategory.DIFF_POWER);
            if (cbSusp.isSelected()) enabled.add(SetupAdvisor.UiCategory.SUSPENSION);
            if (cbAero.isSelected()) enabled.add(SetupAdvisor.UiCategory.AERO);
            if (cbElec.isSelected()) enabled.add(SetupAdvisor.UiCategory.ELECTRONICS);
            if (cbChassis.isSelected()) enabled.add(SetupAdvisor.UiCategory.CHASSIS);
            if (cbDmg.isSelected()) enabled.add(SetupAdvisor.UiCategory.DAMAGE);
            if (cbOther.isSelected()) enabled.add(SetupAdvisor.UiCategory.OTHER);

            List<SetupAdvisor.Recommendation> filtered = SetupAdvisor.filterByUiCategories(recs, enabled);

            Map<SetupAdvisor.UiCategory, List<SetupAdvisor.Recommendation>> grouped = new LinkedHashMap<>();
            for (SetupAdvisor.UiCategory c : SetupAdvisor.UiCategory.values()) grouped.put(c, new ArrayList<>());
            for (SetupAdvisor.Recommendation r : filtered) grouped.get(SetupAdvisor.uiCategoryOf(r)).add(r);

            List<Node> nodes = new ArrayList<>();
            for (var e : grouped.entrySet()){
                var tips = e.getValue();
                if (tips.isEmpty()) continue;

                Label header = new Label(e.getKey().title);
                header.setStyle("-fx-text-fill:#cbd5e1; -fx-font-weight:700;");
                nodes.add(header);

                for (SetupAdvisor.Recommendation r : tips){
                    Label lab = new Label("• [" + r.sev() + "] " + (r.area()==null?"":(r.area()+": ")) + r.message());
                    lab.setWrapText(true);
                    lab.setStyle("-fx-text-fill:#e5e7eb; -fx-opacity:.95;");
                    nodes.add(lab);
                }
            }
            if (nodes.isEmpty()){
                nodes.add(new Label("Nessun consiglio nelle categorie selezionate."));
            }
            list.getChildren().setAll(nodes);
        };

        cbTyres.setOnAction(e -> render.run());
        cbBrakes.setOnAction(e -> render.run());
        cbDiffPow.setOnAction(e -> render.run());
        cbSusp.setOnAction(e -> render.run());
        cbAero.setOnAction(e -> render.run());
        cbElec.setOnAction(e -> render.run());
        cbChassis.setOnAction(e -> render.run());
        cbDmg.setOnAction(e -> render.run());
        cbOther.setOnAction(e -> render.run());

        render.run();
        return root;
    }
}
