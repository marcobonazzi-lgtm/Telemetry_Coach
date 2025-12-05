package org.simulator.gemini;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class GeminiService {

    // ⚠️ LA TUA CHIAVE API
    private static final String API_KEY = "";

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + API_KEY;

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    // --- MEMORIA DELLA CHAT ---
    public static class ChatMessage {
        String role;
        String text;

        public ChatMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    // Lista statica per mantenere la memoria tra le chiamate
    private static final List<ChatMessage> history = new ArrayList<>();

    // DA CHIAMARE SOLO QUANDO SI CARICA UN NUOVO FILE CSV
    public static void clearHistory() {
        history.clear();
    }

    public static String chat(String userMessage, String technicalContext) {
        if (API_KEY == null || API_KEY.length() < 30 || API_KEY.contains("INCOLLA_QUI")) {
            return "ERRORE: Chiave API non configurata.";
        }

        try {
            // 1. Aggiungi messaggio utente alla storia
            history.add(new ChatMessage("user", userMessage));

            String systemPrompt = "Sei un Coach di Guida Virtuale per sim-racing (Assetto Corsa), esperto ma dal tono amichevole, discorsivo e incoraggiante. " +
                    "Immagina di parlare al pilota nei box: il tuo obiettivo è fargli capire dove migliorare spiegando i concetti a parole, non solo snocciolando numeri.\n\n" +

                    "LINEE GUIDA COMPORTAMENTO:\n" +
                    "1. SE L'UTENTE SALUTA (es. 'Ciao'): Rispondi in modo naturale e simpatico, senza forzare subito l'analisi tecnica.\n" +
                    "2. STILE DISCORSIVO: Quando analizzi i dati, EVITA il più possibile liste puntate schematiche o 'copia-incolla'. Integra i numeri nel discorso (es. 'Ho visto che le gomme sono un po' calde, siamo sui 98 gradi, prova a gestire meglio...').\n" +
                    "3. SIINTESI INTELLIGENTE: Usa i dati forniti sotto, ma concentrati solo su ciò che è rilevante per la domanda o per il problema principale del pilota.\n" +
                    "4. Rispondi sempre in italiano.\n\n" +

                    "DATI TELEMETRICI DELLA SESSIONE:\n" + technicalContext;
            // 3. Costruzione JSON con Storia completa
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{");

            // System Instruction
            jsonBuilder.append("\"system_instruction\": {");
            jsonBuilder.append("\"parts\": [{ \"text\": \"").append(escapeJson(systemPrompt)).append("\" }]");
            jsonBuilder.append("},");

            // Contents (Storia)
            jsonBuilder.append("\"contents\": [");
            for (int i = 0; i < history.size(); i++) {
                ChatMessage msg = history.get(i);
                jsonBuilder.append("{");
                jsonBuilder.append("\"role\": \"").append(msg.role).append("\",");
                jsonBuilder.append("\"parts\": [{ \"text\": \"").append(escapeJson(msg.text)).append("\" }]");
                jsonBuilder.append("}");
                if (i < history.size() - 1) jsonBuilder.append(",");
            }
            jsonBuilder.append("]");
            jsonBuilder.append("}");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBuilder.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseText = extractTextFromResponse(response.body());
                // 4. Aggiungi risposta AI alla storia
                history.add(new ChatMessage("model", responseText));
                return responseText;
            } else if (response.statusCode() == 429) {
                return "🚦 Troppe richieste! Riprova tra 30 secondi.";
            } else {
                return "Errore API (" + response.statusCode() + ")";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Errore di Connessione: " + e.getMessage();
        }
    }

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
                    switch (c) {
                        case 'n': result.append('\n'); break;
                        case 'r': break;
                        case 't': result.append('\t'); break;
                        case '"': result.append('"'); break;
                        case '\\': result.append('\\'); break;
                        default: result.append(c);
                    }
                    escape = false;
                } else {
                    if (c == '\\') escape = true;
                    else if (c == '"') break;
                    else result.append(c);
                }
            }
            return result.toString().trim();
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
