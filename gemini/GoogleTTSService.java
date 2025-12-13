package org.simulator.gemini;

import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Versione STABILE e FLUIDA.
 * - Fix Crash NPE su Mute (Race Condition risolta).
 * - Regex ottimizzata: meno pause su virgole/punti e virgola.
 * - Velocità aumentata a 1.20x.
 */
public class GoogleTTSService {

    private static boolean isMuted = false;
    private static MediaPlayer currentPlayer;

    private static final BlockingQueue<String> playbackQueue = new LinkedList<>();
    private static final Map<String, String> audioCache = new ConcurrentHashMap<>();

    private static boolean isPlaying = false;

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String TTS_URL = "https://translate.google.com/translate_tts?ie=UTF-8&tl=it&client=tw-ob&q=";

    public static void setMuted(boolean muted) {
        isMuted = muted;
        if (muted) stopSpeaking();
    }

    public static boolean isMuted() { return isMuted; }

    // --- FIX CRASH: Gestione sicura del Thread JavaFX ---
    public static void stopSpeaking() {
        playbackQueue.clear();

        // Catturiamo il riferimento PRIMA che diventi null
        MediaPlayer playerToStop = currentPlayer;
        currentPlayer = null;
        isPlaying = false;

        if (playerToStop != null) {
            Platform.runLater(() -> {
                try {
                    playerToStop.stop();
                    playerToStop.dispose();
                } catch (Exception ignored) {
                    // Ignora errori se il player è già distrutto
                }
            });
        }
    }

    public static void speak(String text) {
        if (isMuted || text == null || text.isEmpty()) return;

        String cleanText = cleanMarkdown(text);

        // --- FIX PAUSE ---
        // Taglia solo su . ! ? seguito da spazio. Ignora ; : , per fare frasi più lunghe e fluide.
        String[] sentences = cleanText.split("(?<=[.!?])\\s+");

        new Thread(() -> downloadAndQueue(sentences)).start();
    }

    private static void downloadAndQueue(String[] sentences) {
        for (String sentence : sentences) {
            if (sentence.trim().length() < 2) continue;

            try {
                String cacheKey = computeHash(sentence);
                String fileUri;

                if (audioCache.containsKey(cacheKey) && new File(URI.create(audioCache.get(cacheKey))).exists()) {
                    fileUri = audioCache.get(cacheKey);
                } else {
                    fileUri = downloadAudio(sentence);
                    if (fileUri != null) {
                        audioCache.put(cacheKey, fileUri);
                    }
                }

                if (fileUri != null) {
                    playbackQueue.put(fileUri);
                    Platform.runLater(GoogleTTSService::processQueue);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static String downloadAudio(String text) throws Exception {
        // Taglia testi troppo lunghi per evitare errore 404 da Google
        if (text.length() > 200) text = text.substring(0, 199);

        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
        URI uri = URI.create(TTS_URL + encoded);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/91.0.4472.124")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200 && response.body().length > 0) {
            Path tempFile = Files.createTempFile("tts_", ".mp3");
            Files.write(tempFile, response.body(), StandardOpenOption.WRITE);
            tempFile.toFile().deleteOnExit();
            return tempFile.toUri().toString();
        }
        return null;
    }

    private static void processQueue() {
        if (isMuted || isPlaying || playbackQueue.isEmpty()) return;

        try {
            String nextFileUri = playbackQueue.poll();
            if (nextFileUri == null) return;

            isPlaying = true;

            // Assicuriamoci di pulire il vecchio player in modo sicuro
            if (currentPlayer != null) {
                MediaPlayer old = currentPlayer;
                currentPlayer = null;
                old.dispose();
            }

            Media sound = new Media(nextFileUri);
            currentPlayer = new MediaPlayer(sound);

            // --- FIX FLUIDITÀ ---
            // 1.20x riduce i tempi morti tra le parole e rende la frase più compatta
            currentPlayer.setRate(1.20);

            currentPlayer.setOnEndOfMedia(() -> {
                isPlaying = false;
                // Dispose immediato per liberare risorse
                MediaPlayer completed = currentPlayer;
                if (completed != null) completed.dispose();

                processQueue(); // Chiama subito la prossima
            });

            currentPlayer.setOnError(() -> {
                isPlaying = false;
                processQueue();
            });

            currentPlayer.play();

        } catch (Exception e) {
            isPlaying = false;
            e.printStackTrace();
            processQueue();
        }
    }

    private static String cleanMarkdown(String input) {
        return input.replaceAll("[\\*#_`]", "")
                .replaceAll("\\[.*?\\]", "")
                .replace("  ", " ")
                .trim();
    }

    private static String computeHash(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) hexString.append(String.format("%02x", b));
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    private static class LinkedList<T> extends LinkedBlockingQueue<T> {}
}