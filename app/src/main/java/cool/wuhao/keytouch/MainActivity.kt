import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var hidDevice: BluetoothHidDevice
    private var connectedDevice: BluetoothDevice? = null
    private val REQUEST_PERMISSIONS_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KeyTouchApp()
        }

        if (checkPermissions()) {
            startForegroundService(Intent(this, BluetoothService::class.java))
            initializeBluetooth()
        } else {
            requestPermissions()
        }
    }

    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        val serviceListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    registerApp(hidDevice)
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                // 处理服务断开
            }
        }

        bluetoothAdapter.getProfileProxy(this, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    private fun registerApp(hidDevice: BluetoothHidDevice) {
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "KeyTouch",
            "KeyTouch Keyboard and Touchpad",
            "Your Company",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            null
        )

        val callback = object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                // 处理应用程序状态变化
            }

            override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevice = device
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedDevice = null
                }
            }

            // 其他回调方法
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        hidDevice.registerApp(sdpSettings, null, null, Executors.newCachedThreadPool(), callback)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS_CODE)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun sendKeyEvent(key: Byte, isPressed: Boolean) {
        if (checkPermissions()) {
            connectedDevice?.let { device ->
                val keyboardReport = ByteArray(8)
                keyboardReport[0] = if (isPressed) 0x02 else 0x00
                keyboardReport[2] = key
                Intent(this, BluetoothService::class.java).apply {
                    action = "SEND_KEY_EVENT"
                    putExtra("KEY_EVENT", keyboardReport)
                    putExtra("DEVICE_ADDRESS", device.address)
                }.let { startService(it) }
            }
        }
    }

    fun sendTouchEvent(x: Int, y: Int) {
        if (checkPermissions()) {
            connectedDevice?.let { device ->
                val touchReport = byteArrayOf(0x02, 0x00, x.toByte(), y.toByte())
                Intent(this, BluetoothService::class.java).apply {
                    action = "SEND_TOUCH_EVENT"
                    putExtra("TOUCH_EVENT", touchReport)
                    putExtra("DEVICE_ADDRESS", device.address)
                }.let { startService(it) }
            }
        }
    }

    @Composable
    fun KeyTouchApp() {
        val context = LocalContext.current as MainActivity
        var key by remember { mutableStateOf("") }
        var x by remember { mutableStateOf(0) }
        var y by remember { mutableStateOf(0) }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            TextField(
                value = key,
                onValueChange = {
                    key = it
                    if (it.isNotEmpty()) {
                        context.sendKeyEvent(it[0].toByte(), true)
                        context.sendKeyEvent(it[0].toByte(), false)
                    }
                },
                label = { Text("Key Input") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color.Gray)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = { offset ->
                            val touchX = offset.x.toInt()
                            val touchY = offset.y.toInt()
                            context.sendTouchEvent(touchX, touchY)
                        })
                    }
            ) {
                Text(
                    text = "TouchPad",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}