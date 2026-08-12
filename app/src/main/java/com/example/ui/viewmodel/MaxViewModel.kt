package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiBrain
import com.example.data.api.diagnostics.GeminiDiagnosticResult
import com.example.data.db.*
import com.example.data.model.*
import com.example.system.InstalledAppInfo
import com.example.system.SystemControlManager
import com.example.system.SystemTelemetry
import com.example.voice.MaxVoiceEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MaxViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MaxDatabase.getInstance(application)
    private val dao = db.maxDao()
    private val brain = GeminiBrain(application)
    val systemManager = SystemControlManager(application)

    val voiceEngine = MaxVoiceEngine(application) {
        // Called when voice utterance completes
        _maxState.value = MaxState.IDLE
    }

    // UI States
    private val _maxState = MutableStateFlow(MaxState.IDLE)
    val maxState: StateFlow<MaxState> = _maxState

    private val _lastSpeechText = MutableStateFlow("Systems online, Sir. MAX is ready for deployment.")
    val lastSpeechText: StateFlow<String> = _lastSpeechText

    private val _userInputQuery = MutableStateFlow("")
    val userInputQuery: StateFlow<String> = _userInputQuery

    private val _systemTelemetry = MutableStateFlow(systemManager.getTelemetry())
    val systemTelemetry: StateFlow<SystemTelemetry> = _systemTelemetry

    val commandLogs: StateFlow<List<CommandLogEntity>> = dao.getAllCommandLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notesList: StateFlow<List<NoteEntity>> = dao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val autoReplyList: StateFlow<List<AutoReplyEntity>> = dao.getAllAutoReplies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _conversationMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(sender = "MAX", text = "Systems online, Boss. MAX Core is ready for your deployment.")
        )
    )
    val conversationMessages: StateFlow<List<ChatMessage>> = _conversationMessages

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps

    private val _geminiDiagnosticResult = MutableStateFlow<GeminiDiagnosticResult?>(null)
    val geminiDiagnosticResult: StateFlow<GeminiDiagnosticResult?> = _geminiDiagnosticResult

    private var telemetryJob: Job? = null

    init {
        // Greet user on launch
        viewModelScope.launch {
            _lastSpeechText.value = "Systems online, Boss. MAX is ready for deployment."
            voiceEngine.speak("Systems online, Boss. MAX is ready for deployment.")
            loadInstalledApps()
            populateSampleDataIfNeeded()
        }


        // Periodically refresh system telemetry ticks
        telemetryJob = viewModelScope.launch {
            while (true) {
                _systemTelemetry.value = systemManager.getTelemetry()
                delay(2000)
            }
        }

        // Observe voice recognizer text
        viewModelScope.launch {
            voiceEngine.speechRecognizedText.collect { text ->
                if (text.isNotBlank()) {
                    val lower = text.lowercase().trim()
                    if (lower == "max" || lower == "hey max" || lower == "hey max!" || lower == "max!") {
                        val wakeAck = "Yes Boss? Boliyen, main sun raha hoon!"
                        _lastSpeechText.value = wakeAck
                        voiceEngine.speak(wakeAck)
                    } else if (lower.startsWith("max ") || lower.startsWith("hey max ")) {
                        val cleanQuery = text.replace(Regex("(?i)^(hey max|max)\\s*"), "").trim()
                        if (cleanQuery.isNotEmpty()) {
                            _userInputQuery.value = cleanQuery
                            executePrompt(cleanQuery)
                        } else {
                            val wakeAck = "Yes Boss? Boliyen, main sun raha hoon!"
                            _lastSpeechText.value = wakeAck
                            voiceEngine.speak(wakeAck)
                        }
                    } else {
                        _userInputQuery.value = text
                        executePrompt(text)
                    }
                }
            }
        }

        // Synchronize listening state
        viewModelScope.launch {
            voiceEngine.isListening.collect { listening ->
                if (listening) {
                    _maxState.value = MaxState.LISTENING
                } else if (_maxState.value == MaxState.LISTENING) {
                    _maxState.value = MaxState.IDLE
                }
            }
        }

        // Synchronize speaking state
        viewModelScope.launch {
            voiceEngine.isSpeaking.collect { speaking ->
                if (speaking) {
                    _maxState.value = MaxState.SPEAKING
                } else if (_maxState.value == MaxState.SPEAKING) {
                    _maxState.value = MaxState.IDLE
                }
            }
        }
    }

    fun getApiKey(): String {
        return brain.getActiveKey()
    }

    private fun loadInstalledApps() {


        viewModelScope.launch {
            _installedApps.value = systemManager.getInstalledApps()
        }
    }

    private suspend fun populateSampleDataIfNeeded() {
        // Pre-populate sample notes & auto-replies if database is fresh
        dao.insertNote(
            NoteEntity(
                title = "Arc Reactor Core Specs",
                content = "Vibranium containment matrix operating at 3.5 gigawatts. Thermal dissipation stabilized.",
                fileType = "DOCX",
                folder = "Stark Tech"
            )
        )
        dao.insertNote(
            NoteEntity(
                title = "Meeting Summary - Pepper Potts",
                content = "Quarterly budget allocated for autonomous flight routines. Next review scheduled for Friday.",
                fileType = "SUMMARY",
                folder = "Communications"
            )
        )

        dao.insertAutoReply(
            AutoReplyEntity(
                sender = "Pepper Potts",
                platform = "WHATSAPP",
                incomingMessage = "Max, are Tony's suit diagnostics complete for tonight?",
                summary = "Query regarding suit diagnostic status.",
                generatedReply = "Systems online, Pepper. Suit diagnostics are 100% complete and verified.",
                status = "SENT"
            )
        )
        dao.insertAutoReply(
            AutoReplyEntity(
                sender = "Happy Hogan",
                platform = "EMAIL",
                incomingMessage = "Can you send me the security log for Sector 4?",
                summary = "Security log request for Sector 4.",
                generatedReply = "Sector 4 security logs compiled and attached. All perimeters secure.",
                status = "DRAFTED"
            )
        )
    }

    fun onQueryChanged(newText: String) {
        _userInputQuery.value = newText
    }

    fun stopAllAudioAndListening() {
        voiceEngine.stopSpeaking()
        voiceEngine.stopListening()
        _maxState.value = MaxState.IDLE
    }

    fun runGeminiDiagnosticCheck(apiKey: String = com.example.BuildConfig.GEMINI_API_KEY) {
        _maxState.value = MaxState.PROCESSING
        _conversationMessages.value = _conversationMessages.value + ChatMessage(
            sender = "USER",
            text = "Run Gemini API Connectivity Diagnostic Ping"
        )
        viewModelScope.launch {
            val result = brain.runGeminiDiagnostic(apiKey)
            _geminiDiagnosticResult.value = result
            _maxState.value = MaxState.IDLE

            val speech = if (result.isSuccess) {
                "Gemini API Ping Succeeded! HTTP 200 OK. Latency: ${result.latencyMs}ms on model ${result.modelTested}."
            } else {
                "Gemini API Diagnostic Failed. Code: ${result.statusCode ?: "None"} (${result.statusCategory}). Error: ${result.errorMessage ?: "Unknown error"}"
            }

            _lastSpeechText.value = speech
            _conversationMessages.value = _conversationMessages.value + ChatMessage(
                sender = "MAX",
                text = speech
            )

            dao.insertCommandLog(
                CommandLogEntity(
                    prompt = "Gemini Diagnostic Ping",
                    response = speech,
                    actionType = "SYSTEM_DIAGNOSTIC",
                    status = if (result.isSuccess) "PASSED_200" else "FAILED_${result.statusCode ?: 0}"
                )
            )

            voiceEngine.speak(speech)
        }
    }

    fun testWakeWord() {
        stopAllAudioAndListening()
        val wakeAck = "Yes Boss? Boliyen, main sun raha hoon!"
        _lastSpeechText.value = wakeAck
        _maxState.value = MaxState.LISTENING
        voiceEngine.speak(wakeAck)
        viewModelScope.launch {
            delay(1500)
            voiceEngine.startListening()
        }
    }

    fun toggleVoiceListening() {
        if (voiceEngine.isListening.value || voiceEngine.isSpeaking.value) {
            stopAllAudioAndListening()
        } else {
            voiceEngine.startListening()
        }
    }

    fun executePrompt(userPrompt: String) {
        if (userPrompt.isBlank()) return
        _userInputQuery.value = ""
        _maxState.value = MaxState.PROCESSING

        // Add user prompt to chat history
        val userMsg = ChatMessage(sender = "USER", text = userPrompt)
        _conversationMessages.value = _conversationMessages.value + userMsg

        viewModelScope.launch {
            // Brain logic evaluation
            val parsedAction = brain.processUserPrompt(userPrompt)
            _maxState.value = MaxState.EXECUTING

            var systemExecutionStatus = "Executed"

            // Perform phone & system actions
            when (parsedAction.actionType) {
                ActionType.OPEN_APP -> {
                    val statusMsg = systemManager.openAppByName(parsedAction.target)
                    systemExecutionStatus = statusMsg
                }
                ActionType.TOGGLE_SETTINGS -> {
                    val statusMsg = systemManager.toggleSystemSetting(parsedAction.target)
                    systemExecutionStatus = statusMsg
                }
                ActionType.SEND_WHATSAPP -> {
                    systemManager.sendWhatsAppMessage(parsedAction.target, parsedAction.details.ifEmpty { userPrompt })
                    systemExecutionStatus = "WhatsApp Dispatched"
                }
                ActionType.DRAFT_EMAIL -> {
                    systemManager.draftEmail(parsedAction.target, parsedAction.details.ifEmpty { userPrompt })
                    systemExecutionStatus = "Email Client Opened"
                }
                ActionType.MAKE_CALL -> {
                    systemManager.makeCall(parsedAction.target)
                    systemExecutionStatus = "Call Link Placed"
                }
                ActionType.CREATE_FILE -> {
                    val fileName = if (parsedAction.target.isNotBlank()) parsedAction.target else "Max_Document_${System.currentTimeMillis() % 1000}.txt"
                    val content = if (parsedAction.details.isNotBlank()) parsedAction.details else userPrompt
                    val fileStatus = systemManager.createFileInStorage(fileName, content)

                    dao.insertNote(
                        NoteEntity(
                            title = fileName,
                            content = content,
                            fileType = "TXT",
                            folder = "System Files"
                        )
                    )
                    systemExecutionStatus = fileStatus
                }
                ActionType.WEB_SEARCH -> {
                    systemExecutionStatus = "Live Search Analyzed"
                }
                ActionType.SYSTEM_DIAGNOSTIC -> {
                    val result = brain.runGeminiDiagnostic()
                    _geminiDiagnosticResult.value = result
                    systemExecutionStatus = if (result.isSuccess) "Diagnostic HTTP 200 OK (${result.latencyMs}ms)" else "Diagnostic Failed (${result.statusCategory})"
                }
                ActionType.GENERAL_TALK -> {
                    systemExecutionStatus = "Processed"
                }
            }

            val finalSpeech = parsedAction.speechResponse
            _lastSpeechText.value = finalSpeech

            // Add MAX response to chat history
            val maxMsg = ChatMessage(sender = "MAX", text = finalSpeech)
            _conversationMessages.value = _conversationMessages.value + maxMsg

            // Log command
            dao.insertCommandLog(
                CommandLogEntity(
                    prompt = userPrompt,
                    response = finalSpeech,
                    actionType = parsedAction.actionType.name,
                    status = systemExecutionStatus
                )
            )

            // Speak response via Voice Engine
            voiceEngine.speak(finalSpeech)
        }
    }


    fun createNote(title: String, content: String, fileType: String, folder: String) {
        viewModelScope.launch {
            val noteId = dao.insertNote(
                NoteEntity(
                    title = title,
                    content = content,
                    fileType = fileType,
                    folder = folder
                )
            )
            systemManager.createFileInStorage("$title.$fileType", content)
            val msg = "Note '$title' created successfully in $folder, Sir."
            _lastSpeechText.value = msg
            voiceEngine.speak(msg)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            dao.deleteNoteById(id)
        }
    }

    fun createAutoReply(sender: String, platform: String, message: String, response: String) {
        viewModelScope.launch {
            dao.insertAutoReply(
                AutoReplyEntity(
                    sender = sender,
                    platform = platform,
                    incomingMessage = message,
                    summary = "Autonomous reply for $sender",
                    generatedReply = response,
                    status = "DRAFTED"
                )
            )
            val msg = "Autonomous reply rule configured for $sender on $platform, Sir."
            _lastSpeechText.value = msg
            voiceEngine.speak(msg)
        }
    }

    fun dispatchAutoReply(id: Long, sender: String, platform: String, reply: String) {
        viewModelScope.launch {
            dao.updateReplyStatus(id, "SENT")
            if (platform == "WHATSAPP") {
                systemManager.sendWhatsAppMessage(sender, reply)
            } else {
                systemManager.draftEmail(sender, reply)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearCommandLogs()
        }
    }

    fun getApiKeySlot(slotNumber: Int): String {
        val prefs = getApplication<Application>().getSharedPreferences("max_jarvis_prefs", android.content.Context.MODE_PRIVATE)
        return prefs.getString("api_key_slot_$slotNumber", "") ?: ""
    }

    fun saveApiKeySlot(slotNumber: Int, key: String) {
        val prefs = getApplication<Application>().getSharedPreferences("max_jarvis_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("api_key_slot_$slotNumber", key.trim()).apply()
        val msg = "API Key Slot $slotNumber updated, Boss!"
        _lastSpeechText.value = msg
        voiceEngine.speak(msg)
    }

    fun toggleBackgroundWakeService(context: android.content.Context, enable: Boolean) {
        val intent = android.content.Intent(context, com.example.system.MaxWakeService::class.java)
        try {
            if (enable) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                val msg = "Background Wake Listening ON! Say 'Max' anytime, Boss!"
                _lastSpeechText.value = msg
                voiceEngine.speak(msg)
            } else {
                context.stopService(intent)
                val msg = "Background Wake Listening OFF, Boss."
                _lastSpeechText.value = msg
                voiceEngine.speak(msg)
            }
        } catch (e: Exception) {
            val msg = "Could not start background service. Grant microphone permissions, Boss."
            _lastSpeechText.value = msg
            voiceEngine.speak(msg)
        }
    }

    override fun onCleared() {
        super.onCleared()
        telemetryJob?.cancel()
        voiceEngine.release()
    }
}
