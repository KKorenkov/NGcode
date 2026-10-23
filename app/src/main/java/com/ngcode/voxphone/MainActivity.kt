package com.ngcode.voxphone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.voximplant.android.sdk.core.Node

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var loginPanel: View
    private lateinit var user: EditText
    private lateinit var password: EditText
    private lateinit var node: Spinner
    private lateinit var dialPanel: View
    private lateinit var number: EditText
    private lateinit var callBtn: Button
    private lateinit var callPanel: View
    private lateinit var peer: TextView
    private lateinit var callState: TextView
    private lateinit var answerBtn: Button
    private lateinit var hangupBtn: Button
    private lateinit var controls: View
    private lateinit var muteBtn: Button
    private lateinit var speakerBtn: Button
    private lateinit var dtmf: EditText

    private val handler = Handler(Looper.getMainLooper())
    private val renderListener = { render() }
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000)
        }
    }

    /** Action waiting for the microphone permission. */
    private var pendingAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarInsets(requireViewById(R.id.root))

        status = requireViewById(R.id.status)
        loginPanel = requireViewById(R.id.loginPanel)
        user = requireViewById(R.id.user)
        password = requireViewById(R.id.password)
        node = requireViewById(R.id.node)
        dialPanel = requireViewById(R.id.dialPanel)
        number = requireViewById(R.id.number)
        callBtn = requireViewById(R.id.callBtn)
        callPanel = requireViewById(R.id.callPanel)
        peer = requireViewById(R.id.peer)
        callState = requireViewById(R.id.callState)
        answerBtn = requireViewById(R.id.answerBtn)
        hangupBtn = requireViewById(R.id.hangupBtn)
        controls = requireViewById(R.id.controls)
        muteBtn = requireViewById(R.id.muteBtn)
        speakerBtn = requireViewById(R.id.speakerBtn)
        dtmf = requireViewById(R.id.dtmf)

        val nodes = Node.entries
        node.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nodes.map { it.name })
        node.setSelection(nodes.indexOfFirst { it.name == Prefs.lastNode(this) }.coerceAtLeast(0))
        user.setText(Prefs.lastUser(this))

        requireViewById<Button>(R.id.loginBtn).setOnClickListener {
            val u = user.text.toString().trim()
            val p = password.text.toString()
            if (u.isEmpty() || p.isEmpty()) {
                toast("Введите логин и пароль")
                return@setOnClickListener
            }
            Phone.login(u, p, nodes[node.selectedItemPosition])
            password.text.clear()
        }
        requireViewById<Button>(R.id.logoutBtn).setOnClickListener { Phone.logout() }
        callBtn.setOnClickListener {
            val n = number.text.toString().filter { it.isDigit() || it == '+' }
            if (n.isEmpty()) {
                toast("Введите номер")
                return@setOnClickListener
            }
            withMicrophone { Phone.dial(n)?.let(::toast) }
        }
        answerBtn.setOnClickListener { withMicrophone { Phone.answer() } }
        hangupBtn.setOnClickListener { Phone.hangup() }
        muteBtn.setOnClickListener { Phone.toggleMute() }
        speakerBtn.setOnClickListener { Phone.toggleSpeaker() }
        requireViewById<Button>(R.id.dtmfBtn).setOnClickListener {
            Phone.sendDtmf(dtmf.text.toString())
            dtmf.text.clear()
        }

        requestMissing(basePermissions())
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) {
            intent.action = null
            withMicrophone { Phone.answer() }
        }
    }

    override fun onStart() {
        super.onStart()
        Phone.addListener(renderListener)
        handler.post(ticker)
    }

    override fun onStop() {
        Phone.removeListener(renderListener)
        handler.removeCallbacks(ticker)
        super.onStop()
    }

    private fun render() {
        val call = Phone.call
        // Show the screen above the lock screen only while there is a call.
        setShowWhenLocked(call != null)
        setTurnScreenOn(call != null)

        status.text = Phone.info.ifEmpty {
            when (Phone.state) {
                Phone.State.Online -> "В сети"
                Phone.State.Connecting -> "Подключение…"
                Phone.State.Offline -> if (Phone.signedIn) "Нет соединения" else "Войдите в аккаунт Voximplant"
            }
        }

        loginPanel.visibility = if (!Phone.signedIn && call == null) View.VISIBLE else View.GONE
        dialPanel.visibility = if (Phone.signedIn && call == null) View.VISIBLE else View.GONE
        callBtn.isEnabled = Phone.state == Phone.State.Online
        callPanel.visibility = if (call != null) View.VISIBLE else View.GONE
        if (call == null) return

        peer.text = Phone.peerName(call)
        val seconds = call.duration / 1000
        callState.text = if (Phone.connected) {
            "${Phone.callStatus} %d:%02d".format(seconds / 60, seconds % 60)
        } else {
            Phone.callStatus
        }
        answerBtn.visibility = if (Phone.ringing) View.VISIBLE else View.GONE
        hangupBtn.text = if (Phone.ringing) "Отклонить" else "Завершить"
        controls.visibility = if (Phone.ringing) View.GONE else View.VISIBLE
        muteBtn.text = if (call.isMuted) "Включить микрофон" else "Выключить микрофон"
        speakerBtn.text = if (Phone.isSpeaker) "Динамик: вкл" else "Динамик: выкл"
    }

    // ---------- Permissions ----------

    private fun basePermissions(): List<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
    }

    private fun requestMissing(permissions: List<String>) {
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
    }

    private fun withMicrophone(action: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingAction = action
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val action = pendingAction ?: return
        pendingAction = null
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            toast("Без доступа к микрофону звонки невозможны")
        }
    }

    // ---------- Helpers ----------

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    /** Since targetSdk 35 the app draws edge-to-edge, so keep content clear of the system bars. */
    private fun applySystemBarInsets(root: View) {
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
                )
            }
            insets
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.ngcode.voxphone.ANSWER"
        private const val REQUEST_PERMISSIONS = 1
    }
}
