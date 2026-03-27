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

    // INCOLLA QUI LA TUA CHIAVE API A PAGAMENTO (quella col limite di 2 euro)
    private static final String API_KEY = "".trim();

    // Usiamo il 2.5 Flash: economico, veloce e intelligente
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    // --- GESTIONE LICENZA HARD-BLOCK ---
    // Di default l'app parte bloccata. Solo il tasto Premium può sbloccarla.
    private static boolean isPremium = false;

    public static class ChatMessage {
        String role;
        String text;

        public ChatMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    private static final List<ChatMessage> history = new ArrayList<>();
    private static final int MAX_HISTORY = 6;

    public static void clearHistory() {
        history.clear();
    }

    public static void unlockPremium() {
        isPremium = true;
    }

    public static boolean isPremium() {
        return isPremium;
    }

    public static String chat(String userMessage, String technicalContext) {
        // BLOCCO TOTALE SE NON È PREMIUM (Costringe all'acquisto)
        if (!isPremium) {
            return "🔒 L'Ingegnere di Pista AI è una funzionalità PRO! Clicca sul pulsante giallo 'Sblocca Premium' per attivare l'analisi istantanea del tuo stile di guida e dei setup.";
        }

        if (API_KEY == null || API_KEY.length() < 30 || API_KEY.contains("INCOLLA_QUI")) {
            return "ERRORE: Chiave API non configurata.";
        }

        try {
            history.add(new ChatMessage("user", userMessage));

            // NUOVO PROMPT: Discorsivo, amichevole ma super compatto (max 3-4 frasi)
            String systemPrompt = "Sei un Coach di Guida Virtuale per sim-racing (Assetto Corsa), esperto ma dal tono amichevole, discorsivo e incoraggiante. " +
                    "Immagina di parlare al pilota nei box: il tuo obiettivo è fargli capire dove migliorare spiegando i concetti a parole.\n\n" +
                    "LINEE GUIDA COMPORTAMENTO:\n" +
                    "1. STILE DISCORSIVO: EVITA assolutamente le liste puntate schematiche o i comandi stile robot. Parla in modo naturale.\n" +
                    "2. BREVITÀ (FONDAMENTALE): Devi essere molto conciso. Usa MASSIMO 3 o 4 frasi per rispondere. Vai dritto al consiglio principale senza dilungarti.\n" +
                    "3. NUMERI: Integra i dati nel discorso (es. 'Ho visto che le gomme anteriori sono sui 98 gradi, un po' caldine, prova a...').\n" +
                    "4. Rispondi sempre in italiano.\n\n" +
                    "DATI TELEMETRICI DELLA SESSIONE:\n" + (technicalContext != null ? technicalContext : "");

            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{");

            jsonBuilder.append("\"system_instruction\": {");
            jsonBuilder.append("\"parts\": [{ \"text\": \"").append(escapeJson(systemPrompt)).append("\" }]");
            jsonBuilder.append("},");

            jsonBuilder.append("\"contents\": [");

            int startIdx = Math.max(0, history.size() - MAX_HISTORY);
            for (int i = startIdx; i < history.size(); i++) {
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
                history.add(new ChatMessage("model", responseText));
                return responseText;
            } else if (response.statusCode() == 429) {
                history.remove(history.size() - 1);
                return "🚦 Troppe richieste! Riprova tra poco.";
            } else {
                history.remove(history.size() - 1);
                return "Errore API (" + response.statusCode() + ")";
            }

        } catch (Exception e) {
            e.printStackTrace();
            if (!history.isEmpty() && history.get(history.size() - 1).role.equals("user")) {
                history.remove(history.size() - 1);
            }
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
