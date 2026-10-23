package com.ngcode.voxphone

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.voximplant.android.sdk.calls.Call
import com.voximplant.android.sdk.calls.CallDisconnectReason
import com.voximplant.android.sdk.calls.CallException
import com.voximplant.android.sdk.calls.CallListener
import com.voximplant.android.sdk.calls.CallSettings
import com.voximplant.android.sdk.calls.IncomingCallListener
import com.voximplant.android.sdk.calls.RejectMode
import com.voximplant.android.sdk.calls.VICalls
import com.voximplant.android.sdk.core.AuthParams
import com.voximplant.android.sdk.core.Client
import com.voximplant.android.sdk.core.ClientSessionListener
import com.voximplant.android.sdk.core.ClientState
import com.voximplant.android.sdk.core.ConnectOptions
import com.voximplant.android.sdk.core.ConnectionCallback
import com.voximplant.android.sdk.core.ConnectionError
import com.voximplant.android.sdk.core.DisconnectReason
import com.voximplant.android.sdk.core.LoginCallback
import com.voximplant.android.sdk.core.LoginError
import com.voximplant.android.sdk.core.Node
import com.voximplant.android.sdk.core.audio.AudioDevice
import com.voximplant.android.sdk.core.audio.AudioDeviceListener
import com.voximplant.android.sdk.core.audio.AudioDeviceManager
import com.voximplant.android.sdk.core.audio.AudioDeviceType

/**
 * Single place that owns the Voximplant connection and the (only) current call.
 * All methods and SDK callbacks run on the main thread (see [VoxApp]).
 */
object Phone {
    enum class State { Offline, Connecting, Online }

    var state = State.Offline
        private set

    /** Last status or error message to show the user. */
    var info = ""
        private set

    /** True while the user wants to stay logged in; drives automatic reconnects. */
    var signedIn = false
        private set

    var call: Call? = null
        private set

    var callStatus = ""
        private set

    /** Incoming call that has not been answered yet. */
    var ringing = false
        private set

    /** The call is established and media is flowing. */
    var connected = false
        private set

    val isSpeaker: Boolean
        get() = AudioDeviceManager.selectedAudioDevice?.type == AudioDeviceType.Speaker

    private lateinit var app: Context
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private var retryDelayMs = RETRY_MIN_MS
    private var ringtone: Ringtone? = null
    private var ringback: ToneGenerator? = null

    fun init(context: Context) {
        app = context.applicationContext
        Client.setClientSessionListener(sessionListener)
        VICalls.setIncomingCallListener(incomingListener)
        AudioDeviceManager.addAudioDeviceListener(audioListener)

        if (Prefs.load(app) != null) {
            signedIn = true
            connectAndLogin()
        }
    }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun changed() = listeners.toList().forEach { it() }

    // ---------- Connection & login ----------

    fun login(user: String, password: String, node: Node) {
        Prefs.save(app, Credentials(user.trim(), password, node))
        signedIn = true
        retryDelayMs = RETRY_MIN_MS
        handler.removeCallbacks(reconnect)
        // Credentials may have changed: start from a clean connection.
        if (Client.clientState == ClientState.Disconnected) connectAndLogin() else Client.disconnect()
    }

    fun logout() {
        signedIn = false
        handler.removeCallbacks(reconnect)
        call?.let {
            it.hangup(null)
            end("")
        }
        Prefs.clearPassword(app)
        Client.disconnect()
        setState(State.Offline, "")
    }

    private val reconnect = Runnable { connectAndLogin() }

    private fun scheduleReconnect() {
        if (!signedIn) return
        handler.removeCallbacks(reconnect)
        handler.postDelayed(reconnect, retryDelayMs)
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(RETRY_MAX_MS)
    }

    private fun connectAndLogin() {
        val creds = Prefs.load(app) ?: return
        when (Client.clientState) {
            ClientState.Disconnected -> {
                setState(State.Connecting, "Подключение…")
                Client.connect(ConnectOptions(creds.node), object : ConnectionCallback {
                    override fun onSuccess() = doLogin(creds)
                    override fun onFailure(error: ConnectionError) {
                        setState(State.Offline, "Нет соединения ($error), повтор…")
                        scheduleReconnect()
                    }
                })
            }
            ClientState.Connected -> doLogin(creds)
            else -> Unit // already connecting, logging in or logged in
        }
    }

    private fun doLogin(creds: Credentials) {
        setState(State.Connecting, "Вход…")
        Client.login(creds.user, creds.password, object : LoginCallback {
            override fun onSuccess(displayName: String, authParams: AuthParams?) {
                retryDelayMs = RETRY_MIN_MS
                setState(State.Online, "В сети: $displayName")
            }

            override fun onFailure(loginError: LoginError) {
                when (loginError) {
                    LoginError.InvalidPassword, LoginError.InvalidUsername, LoginError.AccountFrozen -> {
                        // Retrying won't help: return to the login form.
                        signedIn = false
                        Prefs.clearPassword(app)
                        Client.disconnect()
                        setState(State.Offline, "Ошибка входа: $loginError")
                    }
                    else -> {
                        setState(State.Offline, "Ошибка входа: $loginError, повтор…")
                        scheduleReconnect()
                    }
                }
            }
        })
    }

    private val sessionListener = object : ClientSessionListener {
        override fun onConnectionClosed(reason: DisconnectReason) {
            // Keep an earlier error message (e.g. a failed login) if we are already offline.
            if (state != State.Offline) setState(State.Offline, "Соединение потеряно")
            if (signedIn) {
                // A user-initiated disconnect while signed in means "re-login with new credentials".
                if (reason == DisconnectReason.UserInitiated) connectAndLogin() else scheduleReconnect()
            }
        }

        override fun onReconnecting() = setState(State.Connecting, "Переподключение…")

        override fun onReconnected() = setState(State.Online, "В сети: ${Client.displayName ?: ""}")
    }

    private fun setState(s: State, message: String) {
        state = s
        info = message
        changed()
    }

    // ---------- Calls ----------

    /** Starts an outgoing call. Returns an error message or null on success. */
    fun dial(number: String): String? {
        if (state != State.Online) return "Нет подключения к Voximplant"
        if (call != null) return "Уже есть активный звонок"
        val c = VICalls.createCall(number, CallSettings()) ?: return "Не удалось создать звонок"
        attach(c)
        try {
            c.start()
        } catch (e: CallException) {
            detach()
            return "Ошибка звонка: ${e.error}"
        }
        callStatus = "Вызов…"
        CallService.start(app)
        changed()
        return null
    }

    fun answer() {
        val c = call ?: return
        if (!ringing) return
        ringing = false
        stopRinging()
        CallNotifications.cancelIncoming(app)
        try {
            c.answer(CallSettings())
            callStatus = "Соединение…"
            CallService.start(app)
        } catch (e: CallException) {
            callStatus = "Не удалось ответить: ${e.error}"
        }
        changed()
    }

    /** Rejects an incoming call or ends the current one. */
    fun hangup() {
        val c = call ?: return
        if (ringing) {
            try {
                c.reject(RejectMode.Decline, null)
            } catch (e: CallException) {
                end("Звонок завершён")
            }
        } else {
            c.hangup(null)
        }
    }

    fun toggleMute() {
        val c = call ?: return
        c.muteAudio(!c.isMuted)
        changed()
    }

    fun toggleSpeaker() {
        val type = if (isSpeaker) AudioDeviceType.Earpiece else AudioDeviceType.Speaker
        val device = AudioDeviceManager.getAudioDeviceOfType(type)
            ?: AudioDeviceManager.audioDevices.firstOrNull { it.type != AudioDeviceType.Speaker }
            ?: return
        AudioDeviceManager.selectAudioDevice(device)
    }

    fun sendDtmf(digits: String) {
        if (digits.isNotEmpty()) call?.sendDTMF(digits)
    }

    fun peerName(c: Call): String =
        c.remoteDisplayName?.takeIf { it.isNotBlank() }
            ?: c.remoteSipUri?.removePrefix("sip:")?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: c.number

    private val incomingListener = object : IncomingCallListener {
        override fun onIncomingCall(call: Call, hasIncomingVideo: Boolean, headers: Map<String, String>?) {
            if (this@Phone.call != null) {
                runCatching { call.reject(RejectMode.Busy, null) }
                return
            }
            attach(call)
            ringing = true
            callStatus = "Входящий звонок"
            startRinging()
            CallNotifications.showIncoming(app, peerName(call))
            changed()
        }
    }

    private val callListener = object : CallListener {
        override fun onStartRinging(call: Call, headers: Map<String, String>?) {
            callStatus = "Гудки…"
            startRingback()
            changed()
        }

        override fun onStopRinging(call: Call) = stopRingback()

        override fun onCallConnected(call: Call, withVideo: Boolean, headers: Map<String, String>?) {
            stopRingback()
            connected = true
            callStatus = "Разговор"
            changed()
        }

        override fun onCallDisconnected(
            call: Call,
            headers: Map<String, String>?,
            disconnectReason: CallDisconnectReason,
        ) {
            end(
                when (disconnectReason) {
                    CallDisconnectReason.AnsweredElsewhere -> "Отвечено на другом устройстве"
                    CallDisconnectReason.ConnectionLost -> "Звонок прерван: нет связи"
                    else -> "Звонок завершён"
                }
            )
        }

        override fun onCallFailed(call: Call, code: Int, description: String?, headers: Map<String, String>?) {
            end("Звонок не состоялся: $code ${description ?: ""}".trim())
        }

        override fun onCallReconnecting(call: Call) {
            callStatus = "Восстановление связи…"
            changed()
        }

        override fun onCallReconnected(call: Call) {
            callStatus = "Разговор"
            changed()
        }
    }

    private val audioListener = object : AudioDeviceListener {
        override fun onAudioDeviceChanged(audioDevice: AudioDevice) = changed()
        override fun onAudioDeviceListChanged(audioDevices: List<AudioDevice>) = changed()
    }

    private fun attach(c: Call) {
        call = c
        c.setCallListener(callListener)
    }

    private fun detach() {
        call?.setCallListener(null)
        call = null
        ringing = false
        connected = false
    }

    private fun end(message: String) {
        stopRinging()
        stopRingback()
        CallNotifications.cancelIncoming(app)
        CallService.stop(app)
        detach()
        callStatus = ""
        info = message
        changed()
        // Show the connection status again after a moment.
        handler.postDelayed({
            if (call == null && state == State.Online) setState(State.Online, "В сети: ${Client.displayName ?: ""}")
        }, 3000)
    }

    // ---------- Sounds ----------

    private fun startRinging() {
        stopRinging()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(app, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
            play()
        }
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
    }

    private fun startRingback() {
        if (ringback != null) return
        ringback = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80) }.getOrNull()
            ?.apply { startTone(ToneGenerator.TONE_SUP_RINGTONE) }
    }

    private fun stopRingback() {
        ringback?.release()
        ringback = null
    }

    private const val RETRY_MIN_MS = 3_000L
    private const val RETRY_MAX_MS = 60_000L
}
