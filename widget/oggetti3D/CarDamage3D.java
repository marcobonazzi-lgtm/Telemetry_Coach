package org.simulator.widget.oggetti3D;

import javafx.geometry.Bounds;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SubScene;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import java.util.HashMap;
import java.util.Map;

public class CarDamage3D extends Group {

    private static final String MODEL_PATH = "/Skyline_R32.obj";

    // --- COLORI DANNO ---
    private static final Color COL_SAFE      = Color.WHITESMOKE;
    private static final Color COL_WARN      = Color.ORANGE;
    private static final Color COL_CRIT      = Color.rgb(255, 30, 0); // Rosso Fuoco

    // --- COLORI MATERIALI SPECIALI ---
    private static final Color COL_WHEEL     = Color.rgb(20, 20, 20);
    private static final Color COL_ROOF      = Color.rgb(10, 10, 10); // Tetto Nero
    private static final Color COL_GLASS     = Color.rgb(180, 220, 240, 0.6); // Vetro Azzurrino

    private final Map<String, CarZone> partZoneMap = new HashMap<>();
    private final Map<String, PhongMaterial> materialsMap = new HashMap<>();
    private final SubScene subScene;

    // Aggiunti ROOF e GLASS alle zone
    private enum CarZone { FRONT, REAR, LEFT, RIGHT, CENTER, WHEEL, ROOF, GLASS }

    public CarDamage3D(double w, double h) {
        Group root3D = new Group();

        this.subScene = new SubScene(root3D, w, h, true, javafx.scene.SceneAntialiasing.BALANCED);
        this.subScene.setFill(Color.TRANSPARENT);

        // --- 1. CAMERA ---
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-35);
        camera.setNearClip(0.1);
        camera.setFarClip(1000.0);
        camera.setFieldOfView(40); // FOV stretto per appiattire la prospettiva (look 2D)
        this.subScene.setCamera(camera);

        // --- 2. LUCI ---
        AmbientLight ambient = new AmbientLight(Color.rgb(180, 180, 180));
        root3D.getChildren().add(ambient);

        PointLight light = new PointLight(Color.WHITE);
        light.setTranslateX(0);
        light.setTranslateY(-30);
        light.setTranslateZ(-50);
        root3D.getChildren().add(light);

        // --- 3. MODELLO ---
        Group carModelGroup = new Group();

        // Rotazione per vista laterale perfetta (Muso a destra)
        carModelGroup.getTransforms().add(new Rotate(-90, Rotate.X_AXIS));
        carModelGroup.getTransforms().add(new Rotate(90, Rotate.Y_AXIS));

        loadModelAndAnalyze(MODEL_PATH, carModelGroup);

        root3D.getChildren().add(carModelGroup);
        this.getChildren().add(this.subScene);
    }

    public SubScene getSubScene() { return this.subScene; }

    public void updateDamage(double front, double rear, double left, double right) {
        for (Map.Entry<String, CarZone> entry : partZoneMap.entrySet()) {
            String meshName = entry.getKey();
            CarZone zone = entry.getValue();
            PhongMaterial mat = materialsMap.get(meshName);
            if (mat == null) continue;

            // Se è una parte speciale (Ruote, Tetto, Vetri), NON cambiamo colore col danno
            if (zone == CarZone.WHEEL || zone == CarZone.ROOF || zone == CarZone.GLASS) {
                continue;
            }

            double dmg = 0;
            switch (zone) {
                case FRONT: dmg = front; break;
                case REAR:  dmg = rear;  break;
                case LEFT:  dmg = left;  break;
                case RIGHT: dmg = right; break;
                case CENTER: dmg = Math.max(left, right); break;
                default: break;
            }

            applyBodyColor(mat, dmg);
        }
    }

    private void applyBodyColor(PhongMaterial mat, double damagePct) {
        Color finalColor;
        Color specularColor = Color.LIGHTGRAY;

        if (Double.isNaN(damagePct) || damagePct < 20.0) {
            // < 20%: Bianco Sicuro
            finalColor = COL_SAFE;
        } else if (damagePct < 50.0) {
            // 20% - 50%: Bianco -> Arancione
            double t = (damagePct - 20.0) / 30.0; // range di 30 unità
            if (t < 0) t = 0; if (t > 1) t = 1;
            finalColor = COL_SAFE.interpolate(COL_WARN, t);
        } else {
            // 50% - 100%: Arancione -> Rosso Fuoco
            double t = (damagePct - 50.0) / 50.0; // range di 50 unità
            if (t < 0) t = 0; if (t > 1) t = 1;
            finalColor = COL_WARN.interpolate(COL_CRIT, t);

            // Effetto opaco se molto critico
            if (damagePct > 90.0) specularColor = Color.rgb(10, 10, 10);
        }

        mat.setDiffuseColor(finalColor);
        mat.setSpecularColor(specularColor);
    }

    private void loadModelAndAnalyze(String resourcePath, Group targetGroup) {
        SimpleObjParser parser = new SimpleObjParser(resourcePath);
        if (parser.meshes.isEmpty()) return;

        double midX = (parser.minX + parser.maxX) / 2.0;
        double midY = (parser.minY + parser.maxY) / 2.0;
        double midZ = (parser.minZ + parser.maxZ) / 2.0;
        targetGroup.getTransforms().add(new Translate(-midX, -midY, -midZ));

        double maxDim = Math.max(parser.maxX - parser.minX, Math.max(parser.maxY - parser.minY, parser.maxZ - parser.minZ));

        // *** DIMENSIONE CALIBRATA ***
        // Ridotto da 27 a 20 per evitare tagli
        double targetSize = 20.0;

        if (maxDim > 0) {
            double scale = targetSize / maxDim;
            targetGroup.getTransforms().add(new Scale(scale, scale, scale));
        }

        // Offset Y per centrare meglio le ruote in basso
        targetGroup.getTransforms().add(new Translate(0, 1.0, 0));

        // --- ANALISI GEOMETRICA AVANZATA ---
        double length = parser.maxZ - parser.minZ;
        double width  = parser.maxX - parser.minX;
        double height = parser.maxY - parser.minY; // Altezza totale

        // Soglie Front/Rear/Side
        double thresholdFront = length * 0.22;
        double thresholdRear  = -length * 0.22;
        double thresholdSide  = width * 0.18;

        // Soglia per identificare Tetto/Vetri (Top 25% dell'auto)
        // Nota: minY è la parte alta (negativa), maxY è il fondo.
        double topThreshold = parser.minY + (height * 0.25);

        for (String name : parser.meshes.keySet()) {
            MeshView mv = new MeshView(parser.meshes.get(name));
            mv.setCullFace(CullFace.NONE);

            Bounds b = mv.getBoundsInLocal();
            double cx = (b.getMinX() + b.getMaxX()) / 2.0;
            double cy = (b.getMinY() + b.getMaxY()) / 2.0; // Altezza
            double cz = (b.getMinZ() + b.getMaxZ()) / 2.0;

            CarZone zone = CarZone.CENTER;
            PhongMaterial mat = new PhongMaterial();

            if (name.contains("Circle") || name.toLowerCase().contains("wheel")) {
                zone = CarZone.WHEEL;
                mat.setDiffuseColor(COL_WHEEL);
                mat.setSpecularColor(Color.DARKGRAY);
            } else {
                // Logica prioritaria per altezza (Tetto/Vetri)
                // Se il pezzo è molto in alto (cy < topThreshold)
                if (cy < topThreshold) {
                    // Se è molto centrale in Z -> Tetto
                    if (Math.abs(cz) < length * 0.15) {
                        zone = CarZone.ROOF;
                        mat.setDiffuseColor(COL_ROOF);
                        mat.setSpecularColor(Color.WHITE);
                    } else {
                        // Se è alto ma un po' avanti/dietro -> Probabile vetro
                        zone = CarZone.GLASS;
                        mat.setDiffuseColor(COL_GLASS);
                        mat.setSpecularColor(Color.CYAN);
                    }
                } else {
                    // Logica Standard Carrozzeria
                    if (cz > thresholdFront) zone = CarZone.FRONT;
                    else if (cz < thresholdRear) zone = CarZone.REAR;

                    if (zone == CarZone.CENTER) {
                        if (cx > thresholdSide) zone = CarZone.RIGHT;
                        else if (cx < -thresholdSide) zone = CarZone.LEFT;
                    }

                    mat.setDiffuseColor(COL_SAFE);
                    mat.setSpecularColor(Color.LIGHTGRAY);
                }
            }

            partZoneMap.put(name, zone);
            materialsMap.put(name, mat);
            mv.setMaterial(mat);
            targetGroup.getChildren().add(mv);
        }
    }
}