package com.example.core

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real-time Gemini Live session — continuous voice conversation over one WebSocket, natural
 * Gemini TTS voice, tool calls arriving mid-conversation instead of MAX's old one-shot
 * "send prompt, parse one action, done" REST flow. Ported from the Iris project's audited
 * implementation, with the tool surface mapped to MAX's existing [SystemControlManager]
 * capabilities so Live Mode and the classic REST mode share the same underlying actions.
 *
 * Protocol notes (mediaChunks deprecated → audio/video fields, v1beta endpoint, interrupted
 * handling) all carried over from Iris's verified migration — see [MaxLiveConfig].
 */
class MaxLiveClient(
    private val wsUrl: String,
    private val systemInstructionText: String,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onToolCall: (name: String, id: String?, args: JSONObject) -> Unit,
    private val onSessionEnded: () -> Unit,
    private val onInterrupted: () -> Unit = {}
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isSessionReady = false

    fun connect() {
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendSetupMessage(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "MAX Live socket failure", t)
                isSessionReady = false
                onSessionEnded()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isSessionReady = false
                onSessionEnded()
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", MaxLiveConfig.LIVE_MODEL)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Puck"))
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemInstructionText)))
                })
                put("tools", JSONArray().put(JSONObject().apply {
                    put("functionDeclarations", buildToolDeclarations())
                }))
            })
        }
        ws.send(setup.toString())
    }

    /** Tool surface mirrors MAX's existing [SystemControlManager]/ActionType capabilities —
     * execution happens in MaxLiveService, dispatching into the SAME controller methods the
     * classic REST flow uses, so both modes stay behaviorally consistent. */
    private fun buildToolDeclarations(): JSONArray {
        fun tool(name: String, description: String, params: JSONObject) = JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", params)
        }
        fun stringParams(vararg names: Pair<String, String>) = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                names.forEach { (n, desc) -> put(n, JSONObject().apply { put("type", "STRING"); put("description", desc) }) }
            })
            put("required", JSONArray(names.map { it.first }))
        }

        return JSONArray().apply {
            put(tool("open_app", "Open an app on the phone by name.", stringParams("app_name" to "Name of the app, as heard")))
            put(tool("toggle_setting", "Toggle a device setting (wifi, bluetooth, silent mode, or open general settings).", stringParams("setting" to "Setting name, as heard")))
            put(tool(
                "send_whatsapp_message",
                "Send a WhatsApp message to a contact by name — fuzzy-matched against the phone's contacts.",
                stringParams("contact_name" to "Name of the contact, as heard", "message" to "Message text to send")
            ))
            put(tool("make_call", "Call a contact by name — fuzzy-matched against the phone's contacts.", stringParams("contact_name" to "Name of the contact, as heard")))
            put(tool(
                "draft_email",
                "Open the email app with a draft pre-filled.",
                stringParams("recipient" to "Recipient email or name", "body" to "Email content")
            ))
            put(tool(
                "get_weather",
                "Get current weather for a city. Free, no special setup — always attempt this.",
                stringParams("city" to "City name, as heard")
            ))
            put(tool("youtube_search", "Search YouTube for a video.", stringParams("query" to "Search query")))
            put(tool("web_search", "Search the web for a query.", stringParams("query" to "Search query")))
            put(tool(
                "take_photo_and_describe",
                "Take a photo with the camera to see and describe what's in front of the user.",
                JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject())
                }
            ))
            put(tool(
                "start_screen_share",
                "Start watching the user's screen (periodic screenshots) to help with what's " +
                "currently displayed. Requires a one-time system permission dialog.",
                JSONObject().apply { put("type", "OBJECT"); put("properties", JSONObject()) }
            ))
            put(tool(
                "stop_screen_share",
                "Stop watching the user's screen.",
                JSONObject().apply { put("type", "OBJECT"); put("properties", JSONObject()) }
            ))
            put(tool(
                "end_conversation",
                "Call this once the user has said goodbye or the conversation has clearly wrapped up — ends Live Mode and returns to background wake-word listening.",
                JSONObject().apply { put("type", "OBJECT"); put("properties", JSONObject()) }
            ))
        }
    }

    fun sendAudioChunk(pcm16: ByteArray) {
        if (!isSessionReady) return
        val message = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("audio", JSONObject().apply {
                    put("mimeType", "audio/pcm;rate=${MaxLiveConfig.INPUT_SAMPLE_RATE}")
                    put("data", Base64.encodeToString(pcm16, Base64.NO_WRAP))
                })
            })
        }
        webSocket?.send(message.toString())
    }

    fun sendImageFrame(jpegBytes: ByteArray) {
        if (!isSessionReady) return
        val message = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("video", JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", Base64.encodeToString(jpegBytes, Base64.NO_WRAP))
                })
            })
        }
        webSocket?.send(message.toString())
    }

    fun sendToolResponse(toolName: String, id: String?, result: JSONObject) {
        val message = JSONObject().apply {
            put("toolResponse", JSONObject().apply {
                put("functionResponses", JSONArray().put(JSONObject().apply {
                    put("name", toolName)
                    if (id != null) put("id", id)
                    put("response", result)
                }))
            })
        }
        webSocket?.send(message.toString())
    }

    private fun handleServerMessage(text: String) {
        val json = try { JSONObject(text) } catch (e: Exception) {
            Log.w(TAG, "Non-JSON server message, ignoring")
            return
        }

        when {
            json.has("setupComplete") -> {
                isSessionReady = true
            }
            json.has("toolCall") -> {
                val calls = json.getJSONObject("toolCall").optJSONArray("functionCalls")
                calls?.let {
                    for (i in 0 until it.length()) {
                        val call = it.getJSONObject(i)
                        val id = if (call.has("id")) call.getString("id") else null
                        onToolCall(call.getString("name"), id, call.optJSONObject("args") ?: JSONObject())
                    }
                }
            }
            json.has("serverContent") -> {
                val content = json.getJSONObject("serverContent")
                val turnComplete = content.optBoolean("turnComplete", false)
                val interrupted = content.optBoolean("interrupted", false)

                if (interrupted) onInterrupted()

                content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        part.optJSONObject("inlineData")?.let { inline ->
                            onAudioReceived(Base64.decode(inline.getString("data"), Base64.NO_WRAP))
                        }
                    }
                }
            }
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Session ended")
        webSocket = null
        isSessionReady = false
    }

    companion object {
        private const val TAG = "MaxLiveClient"
    }
}
