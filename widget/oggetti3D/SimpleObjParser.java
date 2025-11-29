package org.simulator.widget.oggetti3D;

import javafx.scene.shape.TriangleMesh;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleObjParser {
    public Map<String, TriangleMesh> meshes = new HashMap<>();
    public float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
    public float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
    public float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

    public SimpleObjParser(String resourcePath) {
        try {
            InputStream is = getClass().getResourceAsStream(resourcePath);
            if (is == null) {
                System.err.println("SimpleObjParser: File non trovato -> " + resourcePath);
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            List<float[]> globalVertices = new ArrayList<>();
            Map<String, List<Integer>> facesByGroup = new HashMap<>();
            String currentGroup = "DefaultBody";

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                try {
                    if (line.startsWith("v ")) {
                        String[] parts = line.split("\\s+");
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float z = Float.parseFloat(parts[3]);
                        globalVertices.add(new float[]{x, y, z});

                        if (x < minX) minX = x; if (x > maxX) maxX = x;
                        if (y < minY) minY = y; if (y > maxY) maxY = y;
                        if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;

                    } else if (line.startsWith("g ") || line.startsWith("o ")) {
                        String groupName = line.substring(2).trim();
                        if (!groupName.isEmpty()) currentGroup = groupName;

                    } else if (line.startsWith("f ")) {
                        facesByGroup.putIfAbsent(currentGroup, new ArrayList<>());
                        String[] parts = line.split("\\s+");

                        List<Integer> faceVerts = new ArrayList<>();
                        for (int i = 1; i < parts.length; i++) {
                            String part = parts[i];
                            int slashIdx = part.indexOf('/');
                            String vIdxStr = (slashIdx > 0) ? part.substring(0, slashIdx) : part;

                            int rawIdx = Integer.parseInt(vIdxStr);
                            int absIdx;

                            if (rawIdx < 0) {
                                // Indice relativo: -1 è l'ultimo aggiunto
                                absIdx = globalVertices.size() + rawIdx;
                            } else {
                                // Indice assoluto: 1-based -> 0-based
                                absIdx = rawIdx - 1;
                            }

                            faceVerts.add(absIdx);
                        }

                        // Triangolazione
                        for (int i = 0; i < faceVerts.size() - 2; i++) {
                            facesByGroup.get(currentGroup).add(faceVerts.get(0));
                            facesByGroup.get(currentGroup).add(faceVerts.get(i + 1));
                            facesByGroup.get(currentGroup).add(faceVerts.get(i + 2));
                        }
                    }
                } catch (Exception e) {
                    // Ignora errori di parsing su singole righe
                }
            }
            reader.close();

            // Costruzione Mesh (come prima)
            for (Map.Entry<String, List<Integer>> entry : facesByGroup.entrySet()) {
                String groupName = entry.getKey();
                List<Integer> globalFaceIndices = entry.getValue();
                if (globalFaceIndices.isEmpty()) continue;

                TriangleMesh mesh = new TriangleMesh();
                List<Float> localPoints = new ArrayList<>();
                Map<Integer, Integer> globalToLocalMap = new HashMap<>();
                int[] newFaces = new int[globalFaceIndices.size() * 2];

                int faceCursor = 0;
                for (Integer globalIdx : globalFaceIndices) {
                    // Controllo di sicurezza extra
                    if (globalIdx < 0 || globalIdx >= globalVertices.size()) continue;

                    int localIdx;
                    if (globalToLocalMap.containsKey(globalIdx)) {
                        localIdx = globalToLocalMap.get(globalIdx);
                    } else {
                        float[] v = globalVertices.get(globalIdx);
                        localPoints.add(v[0]);
                        localPoints.add(v[1]);
                        localPoints.add(v[2]);
                        localIdx = (localPoints.size() / 3) - 1;
                        globalToLocalMap.put(globalIdx, localIdx);
                    }
                    newFaces[faceCursor++] = localIdx;
                    newFaces[faceCursor++] = 0;
                }

                float[] floatArray = new float[localPoints.size()];
                for(int i=0; i<localPoints.size(); i++) floatArray[i] = localPoints.get(i);

                mesh.getPoints().addAll(floatArray);
                mesh.getTexCoords().addAll(0, 0);
                mesh.getFaces().addAll(newFaces);

                meshes.put(groupName, mesh);
            }

            globalVertices.clear();
            facesByGroup.clear();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
