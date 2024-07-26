import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cool.wuhao.keytouch.R

class BluetoothService : Service() {
    private lateinit var hidDevice: BluetoothHidDevice

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        bluetoothAdapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                // 处理服务断开
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val deviceAddress = it.getStringExtra("DEVICE_ADDRESS") ?: return START_NOT_STICKY
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress)

            if (!checkBluetoothPermissions()) {
                stopSelf()
                return START_NOT_STICKY
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
            }
            when (it.action) {
                "SEND_KEY_EVENT" -> {
                    val keyboardReport = it.getByteArrayExtra("KEY_EVENT") ?: return START_NOT_STICKY
                    hidDevice.sendReport(device, 0, keyboardReport)
                }
                "SEND_TOUCH_EVENT" -> {
                    val touchReport = it.getByteArrayExtra("TOUCH_EVENT") ?: return START_NOT_STICKY
                    hidDevice.sendReport(device, 1, touchReport)
                }
                else -> {
                    // Handle other actions if needed
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun checkBluetoothPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Bluetooth Service"
            val descriptionText = "Channel for Bluetooth service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("BLUETOOTH_SERVICE_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "BLUETOOTH_SERVICE_CHANNEL")
            .setContentTitle("Bluetooth Service")
            .setContentText("Running...")
            .setSmallIcon(R.drawable.ic_launcher_background) // Replace with your icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}