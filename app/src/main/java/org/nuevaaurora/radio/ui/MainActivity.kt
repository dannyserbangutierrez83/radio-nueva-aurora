package org.nuevaaurora.radio.ui

import android.content.ComponentName
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.nuevaaurora.radio.R
import org.nuevaaurora.radio.service.RadioService

class MainActivity : AppCompatActivity() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController?
        get() = if (::controllerFuture.isInitialized && controllerFuture.isDone) controllerFuture.get() else null

    private lateinit var btnPlayStop: Button
    private lateinit var tvStatus: TextView

    private val streamUrl = "https://radio.nueva-aurora.org/main.mp3"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnPlayStop = findViewById(R.id.btnPlayStop)
        tvStatus = findViewById(R.id.tvStatus)

        btnPlayStop.setOnClickListener { togglePlayback() }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, RadioService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ onControllerReady() }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        MediaController.releaseFuture(controllerFuture)
        super.onStop()
    }

    private fun onControllerReady() {
        val ctrl = controller ?: return
        ctrl.addListener(playerListener)
        updateUI(ctrl)
    }

    private fun togglePlayback() {
        val ctrl = controller ?: return

        if (!isNetworkAvailable()) {
            showStatus("Sin conexión, intenta de nuevo")
            return
        }

        if (ctrl.isPlaying) {
            ctrl.stop()
        } else {
            ctrl.setMediaItem(MediaItem.fromUri(streamUrl))
            ctrl.prepare()
            ctrl.play()
        }
    }

    private fun updateUI(ctrl: Player) {
        when {
            ctrl.isPlaying -> {
                btnPlayStop.text = "Stop"
                showStatus("En vivo")
            }
            ctrl.playbackState == Player.STATE_BUFFERING -> {
                btnPlayStop.text = "Stop"
                showStatus("Cargando...")
            }
            else -> {
                btnPlayStop.text = "Play"
                showStatus("")
            }
        }
    }

    private fun showStatus(msg: String) {
        tvStatus.text = msg
        tvStatus.visibility = if (msg.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            controller?.let { updateUI(it) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            controller?.let { updateUI(it) }
        }

        override fun onPlayerError(error: PlaybackException) {
            btnPlayStop.text = "Play"
            showStatus("Radio no disponible en este momento")
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
