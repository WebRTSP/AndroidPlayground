package webrtsp.playground

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

class MainActivity : AppCompatActivity() {
    private lateinit var surfaceViewRenderer: SurfaceViewRenderer

    private val model: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.surfaceViewRenderer)

        surfaceViewRenderer.apply {
            init(model.eglBase.eglBaseContext, object: RendererCommon.RendererEvents {
                override fun onFirstFrameRendered() {
                }
                override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
                }
            })
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        }
    }

    override fun onStart() {
        super.onStart()

        model.attachSurfaceViewRenderer(surfaceViewRenderer)
        model.tryConnect()
    }
    override fun onStop() {
        model.detachSurfaceViewRenderer(surfaceViewRenderer)

        super.onStop()
    }
}
