package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiBrain
import com.example.data.api.diagnostics.GeminiDiagnosticResult
import com.example.data.db.*
import com.example.data.model.*
import com.example.system.InstalledAppInfo
import com.example.system.MaxAccessibilityService
import com.example.system.SystemControlManager
import com.example.system.SystemTelemetry
import com.example.core.MaxContact
import com.example.core.WakePhrase
import com.example.tools.ConfirmationWords
import com.example.tools.ContactActions
import com.example.tools.ContactLookup
import com.example.tools.OfflineCommandRouter
import com.example.voice.MaxVoiceEngine
import com.example.voice.MicState
import com.example.voice.VoiceError
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
        // Called when a voice utterance completes. Only drop SPEAKING -> IDLE; never clobber a
        // LISTENING/PROCESSING state that something else has already moved on to.
        if (_maxState.value == MaxState.SPEAKING) _maxState.value = MaxState.IDLE
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

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled

    private val _isFallbackActive = MutableStateFlow(false)
    val isFallbackActive: StateFlow<Boolean> = _isFallbackActive

    private val _fallbackNotice = MutableStateFlow("")
    val fallbackNotice: StateFlow<String> = _fallbackNotice

    private var telemetryJob: Job? = null
    private var errorResetJob: Job? = null

    /** An action MAX has proposed and is waiting for a spoken/typed yes/no on. */
    private sealed class PendingAction {
        data class Call(val contact: MaxContact) : PendingAction()
        data class WhatsApp(val contact: MaxContact, val message: String) : PendingAction()
    }
    private var pending: PendingAction? = null
    private var pendingExpiresAt = 0L
    private var lastWakeAtMs = 0L

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

        // Mirror Live Mode's state (from the background MaxLiveService, which can keep running
        // whether or not this ViewModel is alive) into the same _maxState the UI already reads —
        // so the orb reflects Live Mode without the UI needing to know two state systems exist.
        viewModelScope.launch {
            com.example.core.MaxLiveStateBus.state.collect { liveState ->
                if (com.example.core.MaxLiveConfig.isLiveModeEnabled(getApplication())) {
                    _maxState.value = liveState
                }
            }
        }

        // Final transcripts ONLY. Partial results never reach here (see MaxVoiceEngine) — that was
        // the root cause of the mic closing ~1s after speech started.
        viewModelScope.launch {
            voiceEngine.finalResults.collect { text -> handleRecognizedSpeech(text) }
        }

        // Mirror the REAL microphone state into the UI state: LISTENING only while the
        // recognizer has actually reported the mic open.
        viewModelScope.launch {
            voiceEngine.micState.collect { mic -> applyMicState(mic) }
        }

        viewModelScope.launch {
            voiceEngine.errors.collect { error -> handleVoiceError(error) }
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

    fun checkAccessibilityStatus(context: Context) {
        val enabled = MaxAccessibilityService.isEnabled() || checkAccessibilitySystemSetting(context)
        _isAccessibilityEnabled.value = enabled
    }

    private fun checkAccessibilitySystemSetting(context: Context): Boolean {
        return try {
            val expectedService = "${context.packageName}/${MaxAccessibilityService::class.java.canonicalName}"
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains(expectedService, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    fun saveCustomKey(prefName: String, value: String) {
        val prefs = getApplication<Application>().getSharedPreferences("max_jarvis_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(prefName, value.trim()).apply()
        val msg = "Key saved to $prefName, Boss!"
        _lastSpeechText.value = msg
        voiceEngine.speak(msg)
    }

    fun getCustomKey(prefName: String): String {
        val prefs = getApplication<Application>().getSharedPreferences("max_jarvis_prefs", Context.MODE_PRIVATE)
        return prefs.getString(prefName, "") ?: ""
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

    fun runGeminiDiagnosticCheck(apiKey: String = "") {
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

    /**
     * A wake event ("Max" / "Hey Max") arrived. The wake service delivers it through BOTH a
     * broadcast and an activity intent, and used to fire it repeatedly, so it is de-duplicated
     * here: ignored while the mic is already in use or if one was handled a moment ago.
     */
    fun onWakeDetected(force: Boolean = false, greeting: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force) {
            if (now - lastWakeAtMs < WAKE_DEBOUNCE_MS) return
            if (voiceEngine.micState.value != MicState.OFF) return
            // A "wake" while MAX itself is talking is MAX hearing its own voice. Never let that
            // cut the sentence off (this was the self-trigger loop).
            if (voiceEngine.isSpeaking.value) return
        }
        lastWakeAtMs = now
        if (force) voiceEngine.stopSpeaking()
        if (greeting) {
            // Greeting rule: "Hello" / "Hello Max" -> reply starts with the Salam, then listen.
            _lastSpeechText.value = GREETING
            voiceEngine.speak(GREETING, thenListen = true)
        } else {
            // Short chime instead of a spoken "yes?" — about a second faster, and there is no
            // spoken word for the wake detector to mistake for a wake phrase.
            _lastSpeechText.value = WAKE_ACK
            playWakeChime()
            viewModelScope.launch {
                delay(CHIME_MS)
                voiceEngine.startListening()
            }
        }
    }

    private fun playWakeChime() {
        try {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 70)
            tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 110)
            viewModelScope.launch {
                delay(500)
                tone.release()
            }
        } catch (e: Exception) {
            // No chime is fine; listening still starts.
        }
    }

    /** Settings "test wake word" button. */
    fun testWakeWord() = onWakeDetected(force = true)

    fun toggleVoiceListening() {
        if (voiceEngine.micState.value != MicState.OFF || voiceEngine.isSpeaking.value) {
            stopAllAudioAndListening()
        } else {
            voiceEngine.startListening()
        }
    }

    private fun applyMicState(mic: MicState) {
        when (mic) {
            MicState.LISTENING -> _maxState.value = MaxState.LISTENING
            MicState.FINALIZING -> {
                if (_maxState.value == MaxState.LISTENING) _maxState.value = MaxState.PROCESSING
            }
            MicState.OFF -> {
                if (_maxState.value == MaxState.LISTENING) _maxState.value = MaxState.IDLE
            }
            MicState.STARTING -> {
                // Mic is not open yet — deliberately do NOT show LISTENING.
            }
        }
    }

    private fun handleRecognizedSpeech(text: String) {
        val heard = text.trim()
        if (heard.isEmpty()) return
        if (WakePhrase.isWakeOnly(heard)) {
            // Only "Max" / "Hello Max" was said — acknowledge and keep listening for the command.
            onWakeDetected(force = true, greeting = WakePhrase.startsWithHello(heard))
            return
        }
        executePrompt(WakePhrase.stripWake(heard), greeting = WakePhrase.startsWithHello(heard))
    }

    /** Shows, logs and speaks MAX's reply. [thenListen] reopens the mic afterwards (for questions). */
    private suspend fun respond(
        prompt: String,
        speech: String,
        actionType: String,
        status: String,
        greeting: Boolean = false,
        thenListen: Boolean = false
    ) {
        val text = withGreeting(speech, greeting)
        _lastSpeechText.value = text
        _conversationMessages.value = _conversationMessages.value + ChatMessage(sender = "MAX", text = text)
        dao.insertCommandLog(CommandLogEntity(prompt = prompt, response = text, actionType = actionType, status = status))
        voiceEngine.speak(text, thenListen = thenListen)
    }

    private suspend fun askConfirmation(
        prompt: String, question: String, action: PendingAction, actionType: String, greeting: Boolean
    ) {
        pending = action
        pendingExpiresAt = SystemClock.elapsedRealtime() + CONFIRM_WINDOW_MS
        respond(prompt, question, actionType, "AwaitingConfirmation", greeting, thenListen = true)
    }

    private suspend fun handleCall(prompt: String, name: String, greeting: Boolean) {
        if (name.isBlank()) {
            respond(prompt, "Kisko call karna hai? Naam ke saath bolo.", "MAKE_CALL", "NeedsInfo", greeting)
            return
        }
        val app = getApplication<android.app.Application>()
        when (val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { ContactActions.lookup(app, name) }) {
            is ContactLookup.NoAccess -> respond(prompt, found.spoken, "MAKE_CALL", "NoPermission", greeting)
            is ContactLookup.NotFound -> respond(prompt, found.spoken, "MAKE_CALL", "NotFound", greeting)
            is ContactLookup.Found ->
                if (found.confident) {
                    runPending(PendingAction.Call(found.contact), prompt, greeting)
                } else {
                    askConfirmation(
                        prompt, "Kya aap ${found.contact.name} ko call karna chahte hain? Haan ya nahi bolo.",
                        PendingAction.Call(found.contact), "MAKE_CALL", greeting
                    )
                }
        }
    }

    private suspend fun handleWhatsApp(prompt: String, name: String, message: String, greeting: Boolean) {
        if (name.isBlank() || message.isBlank()) {
            respond(
                prompt,
                "Kisko aur kya message bhejna hai? Aise bolo: Rahul ko WhatsApp karo ki main late hoon.",
                "SEND_WHATSAPP", "NeedsInfo", greeting
            )
            return
        }
        val app = getApplication<android.app.Application>()
        when (val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { ContactActions.lookup(app, name) }) {
            is ContactLookup.NoAccess -> respond(prompt, found.spoken, "SEND_WHATSAPP", "NoPermission", greeting)
            is ContactLookup.NotFound -> respond(prompt, found.spoken, "SEND_WHATSAPP", "NotFound", greeting)
            // A message can't be un-sent and speech recognition can mishear, so ALWAYS read it back first.
            is ContactLookup.Found -> askConfirmation(
                prompt, "${found.contact.name} ko WhatsApp par bhejun: $message. Haan ya nahi?",
                PendingAction.WhatsApp(found.contact, message), "SEND_WHATSAPP", greeting
            )
        }
    }

    private suspend fun runPending(action: PendingAction, prompt: String, greeting: Boolean) {
        _maxState.value = MaxState.EXECUTING
        val app = getApplication<android.app.Application>()
        val (type, result) = when (action) {
            is PendingAction.Call ->
                "MAKE_CALL" to kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    ContactActions.placeCall(app, action.contact)
                }
            is PendingAction.WhatsApp ->
                "SEND_WHATSAPP" to kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    ContactActions.sendWhatsApp(app, action.contact, action.message)
                }
        }
        respond(prompt, result.spoken, type, if (result.success) "Executed" else "Failed", greeting)
    }

    private fun withGreeting(body: String, greeting: Boolean): String =
        if (greeting && !body.startsWith("Assalam", ignoreCase = true)) "$GREETING. $body" else body

    private fun handleVoiceError(error: VoiceError) {
        _lastSpeechText.value = error.message
        errorResetJob?.cancel()
        if (error.hard) {
            _maxState.value = MaxState.ERROR
            errorResetJob = viewModelScope.launch {
                delay(3000)
                if (_maxState.value == MaxState.ERROR) _maxState.value = MaxState.IDLE
            }
        } else if (_maxState.value == MaxState.LISTENING || _maxState.value == MaxState.PROCESSING) {
            _maxState.value = MaxState.IDLE
        }
    }

    fun executePrompt(userPrompt: String, greeting: Boolean = WakePhrase.startsWithHello(userPrompt)) {
        if (userPrompt.isBlank()) return
        _userInputQuery.value = ""
        _maxState.value = MaxState.PROCESSING

        // Add user prompt to chat history
        val userMsg = ChatMessage(sender = "USER", text = userPrompt)
        _conversationMessages.value = _conversationMessages.value + userMsg

        viewModelScope.launch {
            // 1) Is this the answer to a question MAX just asked ("Rahul ko call karun?")?
            val waiting = pending
            if (waiting != null) {
                pending = null
                if (SystemClock.elapsedRealtime() <= pendingExpiresAt) {
                    when (ConfirmationWords.classify(userPrompt)) {
                        ConfirmationWords.Answer.YES -> { runPending(waiting, userPrompt, greeting); return@launch }
                        ConfirmationWords.Answer.NO -> {
                            respond(userPrompt, "Theek hai, cancel kar diya.", "CONFIRMATION", "Cancelled", greeting)
                            return@launch
                        }
                        ConfirmationWords.Answer.OTHER -> Unit // new command; the old question is dropped
                    }
                }
            }

            // 2) Calls and WhatsApp messages: parsed on the phone (fast), then confirmed / executed.
            val callName = OfflineCommandRouter.parseCallTarget(userPrompt)
            if (callName != null) { handleCall(userPrompt, callName, greeting); return@launch }
            val wa = OfflineCommandRouter.parseWhatsApp(userPrompt)
            if (wa != null) { handleWhatsApp(userPrompt, wa.first, wa.second, greeting); return@launch }

            // Simple commands (open an app, time, date) run fully OFFLINE and instantly — no
            // internet, no AI call. Anything else goes to the AI as before.
            val local = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.example.tools.OfflineCommandRouter.handle(getApplication<android.app.Application>(), userPrompt)
            }
            if (local != null) {
                val speech = withGreeting(local.spoken, greeting)
                _lastSpeechText.value = speech
                _conversationMessages.value = _conversationMessages.value + ChatMessage(sender = "MAX", text = speech)
                dao.insertCommandLog(
                    CommandLogEntity(
                        prompt = userPrompt,
                        response = speech,
                        actionType = "OFFLINE_COMMAND",
                        status = if (local.success) "Executed" else "Failed"
                    )
                )
                voiceEngine.speak(speech)
                return@launch
            }

            // Brain logic evaluation
            val parsedAction = brain.processUserPrompt(userPrompt)
            _isFallbackActive.value = parsedAction.isFallback
            if (parsedAction.isFallback) {
                _fallbackNotice.value = "⚠️ Local Fallback Mode: No active Gemini/OpenAI/Claude API Key found. Add key in Settings for live AI answers."
            } else {
                _fallbackNotice.value = ""
            }
            _maxState.value = MaxState.EXECUTING

            var systemExecutionStatus = "Executed"
            // Overrides parsedAction.speechResponse when real fetched data (weather, search
            // results) needs to actually be spoken — the AI/local-fallback speechResponse is
            // just a generic placeholder like "Checking weather for you, Boss." until this
            // fills in the real answer below.
            var spokenOverride: String? = null

            // Perform phone & system actions
            when (parsedAction.actionType) {
                ActionType.OPEN_APP -> {
                    val statusMsg = systemManager.openAppByName(parsedAction.target)
                    systemExecutionStatus = statusMsg
                    // Speak what REALLY happened (found / not found), not the AI's guess.
                    spokenOverride = statusMsg
                }
                ActionType.TOGGLE_SETTINGS -> {
                    val statusMsg = systemManager.toggleSystemSetting(parsedAction.target)
                    systemExecutionStatus = statusMsg
                }
                ActionType.SEND_WHATSAPP -> {
                    handleWhatsApp(userPrompt, parsedAction.target, parsedAction.details.trim(), greeting)
                    return@launch
                }
                ActionType.DRAFT_EMAIL -> {
                    systemManager.draftEmail(parsedAction.target, parsedAction.details.ifEmpty { userPrompt })
                    systemExecutionStatus = "Email Client Opened"
                }
                ActionType.MAKE_CALL -> {
                    handleCall(userPrompt, parsedAction.target, greeting)
                    return@launch
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
                    val query = parsedAction.target.ifBlank { userPrompt }
                    val searchApiKey = getCustomKey("google_search_api_key")
                    val searchEngineId = getCustomKey("google_search_engine_id")

                    if (searchApiKey.isNotBlank() && searchEngineId.isNotBlank()) {
                        // Real results, fetched back into the conversation — not just "opened a browser".
                        val results = com.example.core.SearchTask.search(query, searchApiKey, searchEngineId)
                        systemExecutionStatus = if (results.isNotEmpty()) {
                            val summary = results.take(3).joinToString(" | ") { "${it.title}: ${it.snippet}" }
                            spokenOverride = "Boss, top result: ${results.first().title}. ${results.first().snippet}"
                            "Found ${results.size} results: $summary"
                        } else {
                            spokenOverride = "I searched for '$query' but couldn't find anything useful, Boss."
                            "Search API configured but returned no results for '$query'"
                        }
                    } else {
                        // No search key configured — actually open a browser search (previously
                        // this branch did nothing at all and just claimed "Live Search Analyzed").
                        val opened = systemManager.openWebSearch(query)
                        systemExecutionStatus = if (opened) "Browser search opened for '$query'" else "Couldn't open browser search"
                        spokenOverride = if (opened) "Opened a browser search for '$query', Boss." else "I couldn't open a browser to search, Boss."
                    }
                }
                ActionType.GET_WEATHER -> {
                    val city = parsedAction.target.ifBlank { null }
                    if (city == null) {
                        systemExecutionStatus = "No city specified"
                        spokenOverride = "Which city's weather do you want, Boss?"
                    } else {
                        val weather = com.example.core.WeatherTask.getWeather(city)
                        if (weather != null) {
                            val desc = com.example.core.WeatherTask.describeCode(weather.weatherCode)
                            systemExecutionStatus = "${weather.resolvedLocationName}: ${weather.temperatureCelsius}°C, $desc"
                            spokenOverride = "It's ${weather.temperatureCelsius.toInt()} degrees and $desc in ${weather.resolvedLocationName}, Boss."
                        } else {
                            systemExecutionStatus = "Couldn't find weather for '$city'"
                            spokenOverride = "I couldn't find weather for '$city', Boss."
                        }
                    }
                }
                ActionType.YOUTUBE_SEARCH -> {
                    val query = parsedAction.target.ifBlank { "trending" }
                    val opened = systemManager.openYouTubeSearch(query)
                    systemExecutionStatus = if (opened) "YouTube opened for '$query'" else "Couldn't open YouTube"
                    spokenOverride = if (opened) "Opened YouTube for '$query', Boss." else "I couldn't open YouTube, Boss."
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

            val finalSpeech = withGreeting(spokenOverride ?: parsedAction.speechResponse, greeting)
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

    fun toggleLiveMode(context: android.content.Context, enable: Boolean) {
        com.example.core.MaxLiveConfig.setLiveModeEnabled(context, enable)
        val intent = android.content.Intent(context, com.example.system.MaxLiveService::class.java)
        try {
            if (enable) {
                // Live Mode and the classic wake service both listen for "MAX" in the background
                // — running both at once would fight over the mic, so the classic one stops here.
                context.stopService(android.content.Intent(context, com.example.system.MaxWakeService::class.java))
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                val msg = "Live Mode ON, Boss — real-time voice, say 'MAX' any time."
                _lastSpeechText.value = msg
            } else {
                context.stopService(intent)
                val msg = "Live Mode OFF, Boss."
                _lastSpeechText.value = msg
                voiceEngine.speak(msg)
            }
        } catch (e: Exception) {
            val msg = "Couldn't start Live Mode — check microphone permission and your Gemini API key, Boss."
            _lastSpeechText.value = msg
            voiceEngine.speak(msg)
        }
    }

    override fun onCleared() {
        super.onCleared()
        telemetryJob?.cancel()
        voiceEngine.release()
    }

    private companion object {
        const val WAKE_ACK = "Ji Boss, boliye?"
        const val GREETING = "Assalam Walekum"
        const val CHIME_MS = 180L
        const val CONFIRM_WINDOW_MS = 45_000L
        const val WAKE_DEBOUNCE_MS = 4000L
    }
}
