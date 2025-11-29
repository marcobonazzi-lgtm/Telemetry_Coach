package org.simulator.widget.oggetti3D;

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
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class ShockAbsorber3D extends Group {

    private final Group root3D = new Group();
    private final Group springGroup = new Group();
    private final Group staticGroup = new Group();

    private final Scale springScale = new Scale(1, 1, 1);
    private final PhongMaterial matSpring = new PhongMaterial();

    public ShockAbsorber3D(String modelResourcePath, double w, double h) {
        SubScene subScene = new SubScene(root3D, w, h, true, javafx.scene.SceneAntialiasing.BALANCED);
        subScene.setFill(Color.TRANSPARENT);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        // --- MODIFICA CAMERA ---
        // Era -11. Portandola a -14 allontaniamo il punto di vista.
        // Risultato: L'oggetto appare più piccolo ("Zoom Out") nel riquadro.
        camera.setTranslateZ(-25);
        camera.setTranslateY(0);
        subScene.setCamera(camera);

        PointLight light = new PointLight(Color.WHITE);
        light.setTranslateX(-5);
        light.setTranslateY(-5);
        light.setTranslateZ(-10);
        root3D.getChildren().add(light);

        root3D.getTransforms().addAll(
                new Rotate(-5, Rotate.X_AXIS),
                new Rotate(10, Rotate.Y_AXIS)
        );

        PhongMaterial matBody = new PhongMaterial(Color.rgb(200, 200, 200));
        matBody.setSpecularColor(Color.WHITE);

        matSpring.setSpecularColor(Color.TRANSPARENT);
        matSpring.setDiffuseColor(Color.GRAY);

        loadModel(modelResourcePath, matBody);

        springGroup.getTransforms().add(springScale);
        root3D.getChildren().addAll(staticGroup, springGroup);
        this.getChildren().add(subScene);
    }

    public void updateState(double compressionFactor, Color statusColor) {
        compressionFactor = Math.max(0, Math.min(1, compressionFactor));
        double scale = 1.0 - (compressionFactor * 0.5);
        springScale.setY(scale);
        matSpring.setDiffuseColor(statusColor);
    }

    private void loadModel(String resourcePath, PhongMaterial matBody) {
        if (getClass().getResource(resourcePath) == null) {
            System.err.println("ERRORE: Risorsa non trovata: " + resourcePath);
            return;
        }

        try {
            ObjParser parser = new ObjParser(resourcePath);
            for (String groupName : parser.meshes.keySet()) {
                TriangleMesh mesh = parser.meshes.get(groupName);
                MeshView mv = new MeshView(mesh);
                mv.setCullFace(CullFace.NONE);

                String lowerName = groupName.toLowerCase();
                if (lowerName.contains("helix") || lowerName.contains("spring") || lowerName.contains("molla")) {
                    mv.setMaterial(matSpring);
                    springGroup.getChildren().add(mv);
                } else {
                    mv.setMaterial(matBody);
                    staticGroup.getChildren().add(mv);
                }
            }

            double midY = (parser.minY + parser.maxY) / 2.0;
            Translate centerTr = new Translate(0, -midY, 0);
            springGroup.getTransforms().add(centerTr);
            staticGroup.getTransforms().add(centerTr);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class ObjParser {
        Map<String, TriangleMesh> meshes = new HashMap<>();
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

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
                    float yCoord = Float.parseFloat(p[2]);
                    v.add(new float[]{Float.parseFloat(p[1]), yCoord, Float.parseFloat(p[3])});
                    if(yCoord < minY) minY = yCoord;
                    if(yCoord > maxY) maxY = yCoord;
                } else if (line.startsWith("g ") || line.startsWith("o ")) {
                    currentGroup = line.substring(2).trim();
                } else if (line.startsWith("f ")) {
                    faces.putIfAbsent(currentGroup, new ArrayList<>());
                    String[] p = line.split("\\s+");
                    for (int i = 2; i < p.length - 1; i++) {
                        faces.get(currentGroup).add(parseIdx(p[1]));
                        faces.get(currentGroup).add(parseIdx(p[i]));
                        faces.get(currentGroup).add(parseIdx(p[i+1]));
                    }
                }
            }
            r.close();

            for (String g : faces.keySet()) {
                TriangleMesh m = new TriangleMesh();
                m.getTexCoords().addAll(0, 0);
                float[] points = new float[v.size() * 3];
                for (int i = 0; i < v.size(); i++) {
                    points[i*3] = v.get(i)[0];
                    points[i*3+1] = v.get(i)[1];
                    points[i*3+2] = v.get(i)[2];
                }
                m.getPoints().addAll(points);
                int[] faceArr = new int[faces.get(g).size() * 2];
                for (int i = 0; i < faces.get(g).size(); i++) {
                    faceArr[i*2] = faces.get(g).get(i);
                    faceArr[i*2+1] = 0;
                }
                m.getFaces().addAll(faceArr);
                meshes.put(g, m);
            }
        }
        int parseIdx(String s) { return Integer.parseInt(s.split("/")[0]) - 1; }
    }
}