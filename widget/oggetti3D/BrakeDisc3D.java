package org.simulator.widget.oggetti3D;

import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SubScene;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

public class BrakeDisc3D extends Group {

    private final Group root3D = new Group();
    private final Group discGroup = new Group();
    private final Group caliperGroup = new Group();

    private final PhongMaterial matDisc = new PhongMaterial();
    private final PhongMaterial matCaliper = new PhongMaterial();

    // --- COLORI TERMICI ---
    private static final Color COL_COLD = Color.rgb(100, 100, 105);
    private static final Color COL_WARM = Color.rgb(200, 100, 0);
    private static final Color COL_HOT  = Color.rgb(255, 50, 0);

    public BrakeDisc3D(String modelResourcePath, double w, double h) {
        SubScene subScene = new SubScene(root3D, w, h, true, javafx.scene.SceneAntialiasing.BALANCED);
        subScene.setFill(Color.TRANSPARENT);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-17);
        camera.setTranslateY(0);
        subScene.setCamera(camera);

        AmbientLight ambient = new AmbientLight(Color.rgb(160, 160, 160));
        root3D.getChildren().add(ambient);

        PointLight light = new PointLight(Color.WHITE);
        light.setTranslateX(-15);
        light.setTranslateY(-15);
        light.setTranslateZ(-20);
        root3D.getChildren().add(light);

        PointLight fillLight = new PointLight(Color.rgb(120, 120, 140));
        fillLight.setTranslateX(15);
        fillLight.setTranslateY(10);
        fillLight.setTranslateZ(-10);
        root3D.getChildren().add(fillLight);

        root3D.getTransforms().addAll(
                new Rotate(90, Rotate.Y_AXIS),
                new Rotate(0, Rotate.X_AXIS),
                new Rotate(0, Rotate.Z_AXIS)
        );

        // --- COLORE PINZA: ROSSO SPORTIVO ---
        matCaliper.setDiffuseColor(Color.web("#D10000")); // Rosso scuro intenso
        matCaliper.setSpecularColor(Color.rgb(200, 200, 200)); // Riflesso metallico

        matDisc.setDiffuseColor(COL_COLD);
        matDisc.setSpecularColor(Color.WHITE);

        loadModel(modelResourcePath);

        root3D.getChildren().addAll(discGroup, caliperGroup);
        this.getChildren().add(subScene);
    }

    public void updateHeat(double temp, double minOpt, double maxOpt) {
        if (Double.isNaN(temp)) return;

        double startHeat = 150.0;
        double midHeat   = 400.0;
        double maxHeat   = 700.0;

        Color finalColor;
        double glowFactor = 0.0;

        if (temp <= startHeat) {
            finalColor = COL_COLD;
        }
        else if (temp <= midHeat) {
            double range = midHeat - startHeat;
            double f = (temp - startHeat) / range;
            finalColor = COL_COLD.interpolate(COL_WARM, f);
        }
        else {
            double range = maxHeat - midHeat;
            double f = Math.min(1.0, (temp - midHeat) / range);
            finalColor = COL_WARM.interpolate(COL_HOT, f);
            glowFactor = f;
        }

        if (glowFactor > 0.1) {
            matDisc.setSpecularColor(Color.gray(0.8 - (glowFactor * 0.6)));
        } else {
            matDisc.setSpecularColor(Color.WHITE);
        }

        matDisc.setDiffuseColor(finalColor);
    }

    private void loadModel(String resourcePath) {
        if (getClass().getResource(resourcePath) == null) return;

        try {
            ObjParser parser = new ObjParser(resourcePath);

            for (String groupName : parser.meshes.keySet()) {
                TriangleMesh mesh = parser.meshes.get(groupName);
                MeshView mv = new MeshView(mesh);
                mv.setCullFace(CullFace.NONE);

                if (groupName.equalsIgnoreCase("Break01")) {
                    mv.setMaterial(matCaliper);
                    caliperGroup.getChildren().add(mv);
                } else {
                    mv.setMaterial(matDisc);
                    discGroup.getChildren().add(mv);
                }
            }

            double midX = (parser.minX + parser.maxX) / 2.0;
            double midY = (parser.minY + parser.maxY) / 2.0;
            double midZ = (parser.minZ + parser.maxZ) / 2.0;
            Translate centerTr = new Translate(-midX, -midY, -midZ);

            discGroup.getTransforms().add(centerTr);
            caliperGroup.getTransforms().add(centerTr);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class ObjParser {
        Map<String, TriangleMesh> meshes = new HashMap<>();
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        ObjParser(String resourcePath) throws Exception {
            InputStream is = getClass().getResourceAsStream(resourcePath);
            if (is == null) throw new java.io.FileNotFoundException("Resource not found: " + resourcePath);

            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            List<float[]> v = new ArrayList<>();
            Map<String, List<Integer>> faces = new HashMap<>();
            String currentGroup = "default";

            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("v ")) {
                    String[] p = line.split("\\s+");
                    v.add(new float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])});
                    minX = Math.min(minX, v.get(v.size()-1)[0]); maxX = Math.max(maxX, v.get(v.size()-1)[0]);
                    minY = Math.min(minY, v.get(v.size()-1)[1]); maxY = Math.max(maxY, v.get(v.size()-1)[1]);
                    minZ = Math.min(minZ, v.get(v.size()-1)[2]); maxZ = Math.max(maxZ, v.get(v.size()-1)[2]);
                } else if (line.startsWith("g ") || line.startsWith("o ")) currentGroup = line.substring(2).trim();
                else if (line.startsWith("f ")) {
                    faces.putIfAbsent(currentGroup, new ArrayList<>());
                    String[] p = line.split("\\s+");
                    for (int i = 2; i < p.length - 1; i++) {
                        faces.get(currentGroup).add(Integer.parseInt(p[1].split("/")[0]) - 1);
                        faces.get(currentGroup).add(Integer.parseInt(p[i].split("/")[0]) - 1);
                        faces.get(currentGroup).add(Integer.parseInt(p[i+1].split("/")[0]) - 1);
                    }
                }
            }
            r.close();
            for (String g : faces.keySet()) {
                TriangleMesh m = new TriangleMesh();
                m.getTexCoords().addAll(0, 0);
                float[] points = new float[v.size() * 3];
                for (int i = 0; i < v.size(); i++) {
                    points[i*3] = v.get(i)[0]; points[i*3+1] = v.get(i)[1]; points[i*3+2] = v.get(i)[2];
                }
                m.getPoints().addAll(points);
                int[] faceArr = new int[faces.get(g).size() * 2];
                for (int i = 0; i < faces.get(g).size(); i++) { faceArr[i*2] = faces.get(g).get(i); faceArr[i*2+1] = 0; }
                m.getFaces().addAll(faceArr);
                meshes.put(g, m);
            }
        }
    }
}