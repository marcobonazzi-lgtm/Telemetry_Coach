package org.simulator.gemini;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class GeminiService {

    // ⚠️ LA TUA CHIAVE API
    private static final String API_KEY = "KEY";

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + API_KEY;

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public static String chat(String userMessage, String technicalContext) {
        if (API_KEY == null || API_KEY.length() < 30 || API_KEY.contains("KEY")) {
            return "ERRORE: Chiave API non configurata.";
        }

        try {
            String systemPrompt = "Sei un Coach di Guida Virtuale per sim-racing (Assetto Corsa), esperto ma dal tono amichevole, discorsivo e incoraggiante. " +
                    "Immagina di parlare al pilota nei box: il tuo obiettivo è fargli capire dove migliorare spiegando i concetti a parole, non solo snocciolando numeri.\n\n" +

                    "LINEE GUIDA COMPORTAMENTO:\n" +
                    "1. SE L'UTENTE SALUTA (es. 'Ciao'): Rispondi in modo naturale e simpatico (es. 'Ehilà! La macchina è calda, vuoi sapere come sei andato?'), senza forzare subito l'analisi tecnica.\n" +
                    "2. STILE DISCORSIVO: Quando analizzi i dati, EVITA il più possibile liste puntate schematiche o 'copia-incolla'. Integra i numeri nel discorso (es. 'Ho visto che le gomme sono un po' calde, siamo sui 98 gradi, prova a gestire meglio...').\n" +
                    "3. SIINTESI INTELLIGENTE: Usa i dati forniti sotto, ma concentrati solo su ciò che è rilevante per la domanda o per il problema principale del pilota.\n" +
                    "4. Rispondi sempre in italiano.\n\n" +

                    "DATI TELEMETRICI DELLA SESSIONE:\n" + technicalContext;
            String jsonBody = "{" +
                    "\"contents\": [{" +
                    "\"parts\": [{" +
                    "\"text\": \"" + escapeJson(systemPrompt + "\n\nUTENTE: " + userMessage) + "\"" +
                    "}]" +
                    "}]" +
                    "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractTextFromResponse(response.body());
            } else if (response.statusCode() == 429) {
                return "🚦 Troppe richieste! Il sistema è in pausa per 30 secondi. Riprova tra poco.";
            } else {
                return "Errore API (" + response.statusCode() + ")";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Errore di Connessione: " + e.getMessage();
        }
    }

    // --- CORREZIONE CRITICA PER LA "n" ---
    private static String extractTextFromResponse(String json) {
        try {
            int idx = json.indexOf("\"text\": \"");
            if (idx == -1) return "Nessuna risposta.";

            int start = idx + 9;
            StringBuilder result = new StringBuilder();
            boolean escape = false;

            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);

                if (escape) {
                    // QUI ERA L'ERRORE: Ora gestiamo correttamente i caratteri speciali
                    switch (c) {
                        case 'n': result.append('\n'); break; // Trasforma \n in A Capo reale
                        case 'r': break; // Ignora carriage return
                        case 't': result.append('\t'); break;
                        case '"': result.append('"'); break;
                        case '\\': result.append('\\'); break;
                        default: result.append(c);
                    }
                    escape = false;
                } else {
                    if (c == '\\') escape = true;
                    else if (c == '"') break; // Fine stringa JSON
                    else result.append(c);
                }
            }
            return result.toString().trim(); // Trim finale per pulire spazi extra
        } catch (Exception e) {
            return "Errore lettura risposta.";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}