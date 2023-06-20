package com.example.gesturetest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TestView()
        }
    }
}

@Composable
private fun TestView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var isFix1Applied by rememberSaveable { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Text("Fix #1")
            Switch(
                checked = isFix1Applied,
                onCheckedChange = { isFix1Applied = it },
            )
        }
        var isFix2Applied by rememberSaveable { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Text("Fix #2")
            Switch(
                checked = isFix2Applied,
                onCheckedChange = { isFix2Applied = it },
            )
        }
        var isSystem by rememberSaveable(isFix1Applied) { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("System detection")
            Switch(
                checked = isSystem,
                onCheckedChange = { isSystem = it },
            )
        }

        var scale by rememberSaveable(isSystem) { mutableStateOf(1f) }

        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier
                .zIndex(-1f)
                .weight(1f)
                .fillMaxWidth()
                .scale(scale)
                .pointerInput(isSystem, isFix1Applied) {
                    if (!isSystem) {
                        lumaDetectTransformGestures(
                            applyIgnoreFirstZoomFix = isFix2Applied,
                            onGesture = { isMultitouch, _, zoom ->
                                if (isFix1Applied && !isMultitouch) {
                                    return@lumaDetectTransformGestures
                                }
                                scale *= zoom
                            },
                            onGestureEnd = {

                            },
                        )
                    } else {
                        detectTransformGestures { _, _, zoom, _ ->
                            scale *= zoom
                        }
                    }
                }
        )
    }

}

suspend fun PointerInputScope.lumaDetectTransformGestures(
    applyIgnoreFirstZoomFix: Boolean,
    onGesture: (isMultitouch: Boolean, pan: Offset, zoom: Float) -> Unit,
    onGestureEnd: (panVelocity: Offset) -> Unit,
) {
    val panVelocityTracker = VelocityTracker()
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        var zoomsToIgnore = if (applyIgnoreFirstZoomFix) 2 else 0
        panVelocityTracker.resetTracking()
        do {
            val event = awaitPointerEvent()

            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                val uptimeMillis = event.changes.first().uptimeMillis
                panVelocityTracker.addPosition(uptimeMillis, panChange)

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    if (
                        zoomChange != 1f ||
                        panChange != Offset.Zero
                    ) {
                        if (zoomChange == 1f || zoomsToIgnore == 0) {
                            onGesture(event.changes.count() > 1, panChange, zoomChange)
                        } else {
                            zoomsToIgnore -= 1
                        }
                    }
                    event.changes.fastForEach {
                        if (it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            }
            val isAnyPressed = event.changes.fastAny { it.pressed }
            if (!canceled && !isAnyPressed) {
                onGestureEnd(
                    panVelocityTracker.calculateVelocity().run { Offset(x, y) },
                )
            }
        } while (!canceled && isAnyPressed)
    }
}
