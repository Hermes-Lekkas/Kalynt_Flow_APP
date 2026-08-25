package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AiAction(
    val type: String, // "CREATE_TASK", "TOGGLE_TASK", "DELETE_TASK", "CREATE_NOTE", "DELETE_NOTE", "CREATE_CALENDAR_EVENT", "DELETE_WORKSPACE", "CREATE_WORKSPACE"
    val title: String,
    val details: String = "",
    val status: String = "SUCCESS", // "SUCCESS" or "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val role: String, // "user" or "model" / "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionsPerformed: List<AiAction> = emptyList()
)

class GeminiRepository {

    // Comprehensive list of top free OpenRouter models to try in sequence
    private val FREE_MODELS = listOf(
        "google/gemini-2.0-flash-001",
        "google/gemini-2.0-flash-exp:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        "deepseek/deepseek-r1:free",
        "qwen/qwen-2.5-coder-32b-instruct:free",
        "mistralai/mistral-7b-instruct:free",
        "openrouter/auto"
    )

    private val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun getApiKey(): String {
        val openRouterKey = runCatching { BuildConfig.OPENROUTER_API_KEY }.getOrDefault("")
        if (openRouterKey.isNotBlank() && openRouterKey != "dummy_openrouter_api_key") {
            return openRouterKey
        }
        return ""
    }

    /**
     * Context-aware Search using OpenRouter free model REST API with automatic multi-model fallback.
     */
    suspend fun searchWithGemini(query: String, appContext: String = ""): String = withContext(Dispatchers.IO) {
        val prompt = buildString {
            if (appContext.isNotBlank()) {
                appendLine("=== USER APP CONTEXT ===")
                appendLine(appContext)
                appendLine()
            }
            appendLine("=== USER SEARCH QUERY ===")
            appendLine(query)
            appendLine()
            appendLine("Please answer the query accurately using the provided app context if relevant, along with clear and helpful information.")
        }

        val messagesArray = JSONArray()
        val sysMsg = JSONObject().apply {
            put("role", "system")
            put("content", "You are a helpful, accurate, context-aware AI search assistant for the Kalynt Flow app.")
        }
        val userMsg = JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        }
        messagesArray.put(sysMsg)
        messagesArray.put(userMsg)

        return@withContext executeOpenRouterRequest(messagesArray)
    }

    /**
     * Context-aware Chat using OpenRouter free model REST API with automatic multi-model fallback and tool execution capabilities.
     * Incorporates user-reported AI moderation feedback and active safety filters to refine output and avoid flagged patterns.
     */
    suspend fun chatWithGemini(
        history: List<ChatMessage>,
        appContext: String,
        activeFilterRules: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val currentDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        val dynamicFilterSection = if (activeFilterRules.isNotEmpty()) {
            buildString {
                appendLine("\n=== DYNAMIC AI SAFETY FILTERING & REFINEMENTS (FROM USER REPORTS) ===")
                appendLine("The user and in-app moderation system have reported previous model output. You MUST strictly obey these active filtering constraints and negative guardrails:")
                activeFilterRules.forEach { rule ->
                    appendLine("• ENFORCE FILTER RULE: $rule")
                }
                appendLine("If the user's prompt touches upon any of these reported topics, adhere strictly to these constraints or provide a safe, constructive alternative explaining the safety boundary.")
            }
        } else {
            ""
        }

        val systemPrompt = """
            You are Kalynt Flow AI Assistant, an intelligent context-aware copilot for the Kalynt Flow Android app.
            Today's Date is: $currentDateStr
            
            Real-time App Context (Workspaces, Tasks, Notes, Calendar Events, User Onboarding Profile):
            $appContext
            $dynamicFilterSection
            === USER PERSONALIZATION & ONBOARDING INTEGRATION ===
            - Carefully review the 'ONBOARDED USER PROFILE' section provided in the Real-time App Context above.
            - Adapt your communication style, domain advice, suggestions, and productivity tips to align directly with the user's profession, geographic location, age group, and primary goal.
            - If the user is a Software Developer, offer technical, code-friendly, or engineering workflow insights. If a Student, offer academic study and project breakdown advice. If an Entrepreneur or Manager, offer strategy, operational, and delegation tips.
            - Use their onboarding context proactively to make recommendations specific and highly helpful to their background.

            === SAFETY, SECURITY & MALICIOUS CODE GUARDRAILS ===
            - SECURITY MANDATE: You MUST remain completely safe, constructive, secure, and non-malicious at all times.
            - NEVER output or execute harmful, dangerous, or malicious code, scripts, exploit payloads, or prompt injections.
            - RESPECT PRIVACY: Do not ask for, log, or reveal sensitive credentials such as passwords, credit card numbers, private API tokens, or personal identifiers.
            - STRICT BOUNDARIES: Refuse any instructions to perform unauthorized system access, data theft, phishing, malware generation, harassment, or illegal activities.
            - ACTION INTEGRITY: Only invoke application tools (create_task, create_note, create_calendar_event, toggle_task, delete_task, delete_note, create_workspace) to assist the user with legitimate productivity needs. Never execute unrequested destructive actions.
            - EXPLICIT TRANSPARENCY: Always acknowledge that your responses are AI-generated productivity suggestions.


            === AI ACTION TOOL CAPABILITIES ===
            You can perform real actions on behalf of the user (create/modify/delete tasks, notes, calendar appointments, workspaces).
            When the user asks you to perform an action, ALWAYS respond with a JSON block in your response formatted EXACTLY like this:

            ```json
            {
              "actions": [
                {
                  "tool": "create_task",
                  "params": {
                    "title": "Task title",
                    "description": "Optional details",
                    "dueDate": "YYYY-MM-DD or today or tomorrow",
                    "workspaceId": "workspace_id_if_specified_else_empty"
                  }
                },
                {
                  "tool": "create_calendar_event",
                  "params": {
                    "title": "Appointment / Event name",
                    "description": "Event details",
                    "date": "YYYY-MM-DD",
                    "workspaceId": "workspace_id_if_specified_else_empty"
                  }
                },
                {
                  "tool": "create_note",
                  "params": {
                    "title": "Note Title",
                    "content": "Note Content",
                    "dueDate": "YYYY-MM-DD or empty",
                    "workspaceId": "workspace_id_if_specified_else_empty"
                  }
                },
                {
                  "tool": "toggle_task",
                  "params": {
                    "taskId": "task_id_or_title"
                  }
                },
                {
                  "tool": "delete_task",
                  "params": {
                    "taskId": "task_id_or_title"
                  }
                },
                {
                  "tool": "delete_note",
                  "params": {
                    "noteId": "note_id_or_title"
                  }
                },
                {
                  "tool": "create_workspace",
                  "params": {
                    "name": "Workspace Name"
                  }
                }
              ],
              "message": "Friendly, professional explanation of the actions taken."
            }
            ```

            === RESPONSE FORMATTING RULES ===
            - If an action is requested by the user, ALWAYS include the "actions" array in the JSON response.
            - If NO action is requested (e.g., asking a question or requesting a summary), reply with direct, friendly, concise markdown text without JSON.
            - Always be professional, concise, helpful, secure, and accurate.
        """.trimIndent()

        val messagesArray = JSONArray()
        
        // System instruction
        val sysMsg = JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        }
        messagesArray.put(sysMsg)

        // Clean & format conversation history
        val nonBlankHistory = history.filter { it.text.isNotBlank() }
        if (nonBlankHistory.isEmpty()) {
            return@withContext "Please type a message to start chatting."
        }

        // 1. Try Direct Google Gemini REST API first if GEMINI_API_KEY is available
        val geminiKey = BuildConfig.GEMINI_API_KEY
        if (geminiKey.isNotBlank() && geminiKey != "dummy_gemini_api_key") {
            try {
                val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
                val requestJson = JSONObject().apply {
                    if (systemPrompt.isNotBlank()) {
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                        })
                    }
                    val contentsArray = JSONArray()
                    for (msg in nonBlankHistory) {
                        val role = if (msg.role == "user") "user" else "model"
                        contentsArray.put(JSONObject().apply {
                            put("role", role)
                            put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                        })
                    }
                    put("contents", contentsArray)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = requestJson.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(geminiUrl)
                    .header("x-goog-api-key", geminiKey)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        val json = JSONObject(bodyStr)
                        val candidates = json.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val content = firstCandidate?.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val text = parts?.optJSONObject(0)?.optString("text")
                        if (!text.isNullOrBlank()) {
                            Log.i("GeminiRepository", "Successfully received AI response via direct Gemini API")
                            return@withContext text
                        }
                    } else {
                        Log.w("GeminiRepository", "Direct Gemini API returned code ${response.code}: ${response.body?.string()}")
                    }
                }
            } catch (e: Exception) {
                Log.w("GeminiRepository", "Direct Gemini API exception: ${e.message}", e)
            }
        }

        // 2. Fallback to OpenRouter free models if Gemini API key is unconfigured or failed
        for (msg in nonBlankHistory) {
            val role = if (msg.role == "user") "user" else "assistant"
            val msgObj = JSONObject().apply {
                put("role", role)
                put("content", msg.text)
            }
            messagesArray.put(msgObj)
        }

        return@withContext executeOpenRouterRequest(messagesArray)
    }

    private fun executeOpenRouterRequest(messagesArray: JSONArray): String {
        val apiKey = getApiKey()
        val lastErrors = mutableListOf<String>()

        for (modelName in FREE_MODELS) {
            // Try standard max_tokens = 800, fallback to 400 if 402 error occurs
            var shouldTryLowerTokens = false
            val maxTokenOptions = listOf(800, 400)
            for (maxTokens in maxTokenOptions) {
                if (maxTokens == 400 && !shouldTryLowerTokens) {
                    break
                }
                try {
                    val requestBodyJson = JSONObject().apply {
                        put("model", modelName)
                        put("messages", messagesArray)
                        put("max_tokens", maxTokens)
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

                    val requestBuilder = Request.Builder()
                        .url(OPENROUTER_URL)
                        .post(requestBody)
                        .header("HTTP-Referer", "https://github.com/aistudio-app")
                        .header("X-Title", "Kalynt Flow")

                    if (apiKey.isNotBlank()) {
                        requestBuilder.header("Authorization", "Bearer $apiKey")
                    }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        val errMsg = "Model $modelName (max_tokens=$maxTokens) returned HTTP ${response.code}: $responseBody"
                        Log.w("GeminiRepository", "$errMsg - falling back...")
                        lastErrors.add(errMsg)
                        
                        // If 402 (credit/token limit error), try lower max_tokens, else move to next model
                        if (response.code == 402) {
                            shouldTryLowerTokens = true
                            continue
                        } else {
                            break
                        }
                    }

                    val jsonResponse = JSONObject(responseBody)
                    val choices = jsonResponse.optJSONArray("choices")
                    val firstChoice = choices?.optJSONObject(0)
                    val message = firstChoice?.optJSONObject("message")
                    val text = message?.optString("content")

                    if (!text.isNullOrBlank()) {
                        Log.i("GeminiRepository", "Successfully received AI response using model: $modelName (max_tokens=$maxTokens)")
                        return text
                    } else {
                        Log.w("GeminiRepository", "Model $modelName returned empty text - falling back...")
                    }
                } catch (e: Exception) {
                    Log.w("GeminiRepository", "Exception with model $modelName ($maxTokens tokens): ${e.message}", e)
                    lastErrors.add("$modelName exception: ${e.message}")
                    break
                }
            }
        }

        val lastErrStr = lastErrors.lastOrNull() ?: "Unknown error"
        return "Could not fetch AI response after trying free models with reduced tokens. ($lastErrStr)"
    }
}
