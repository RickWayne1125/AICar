package org.blackwalnutlabs.angel.keeplane.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import android.widget.Toast
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class USBSerial(
    private val context: Context,
    private val serialListener: SerialInputOutputManager.Listener
) {

    private val TAG = "USBSerial"
    private var usbManager: UsbManager? = null
    private var serialPort: UsbSerialPort? = null
    private var drivers: List<UsbSerialDriver>? = null
    private var serialIoManager: SerialInputOutputManager? = null
    private var executorService: ExecutorService? = null

    fun initUsbSerial() { // 1.查找设备
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if ((drivers as MutableList<UsbSerialDriver>?)!!.size <= 0) {
            Toast.makeText(context, "无串口设备", Toast.LENGTH_SHORT).show()
            return
        } else {
            Toast.makeText(context, "找到 " + drivers!!.size + " 个串口设备", Toast.LENGTH_SHORT).show()
        }
        val device = drivers!![0].device
        if (usbManager!!.hasPermission(device)) {
            permissionAllow(device)
        } else {
            Log.e("TAG", "没有权限")
            val mUsbPermissionActionReceiver =
                UsbPermissionActionReceiver()
            val intent = Intent(ACTION_USB_PERMISSION)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
            val intentFilter = IntentFilter(ACTION_USB_PERMISSION)
            context.registerReceiver(mUsbPermissionActionReceiver, intentFilter)
            usbManager!!.requestPermission(device, pendingIntent)
        }
    }

    private fun permissionAllow(device: UsbDevice) {
        val result: MutableList<UsbSerialPort> =
            ArrayList()
        for (driver in drivers!!) {
            val ports = driver.ports
            result.addAll(ports)
        }
        val usbDeviceConnection = usbManager!!.openDevice(device)
        try {
            serialPort = result[0]
            serialPort!!.open(usbDeviceConnection)
            serialPort!!.setParameters(
                115200,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            serialIoManager = SerialInputOutputManager(serialPort, serialListener)
            executorService = Executors.newSingleThreadExecutor()
            executorService?.submit(serialIoManager)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private inner class UsbPermissionActionReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val usbDevice =
                        intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED,
                            false
                        )
                    ) { // user choose YES for your previously popup window asking for grant perssion for this usb device
                        usbDevice?.let { permissionAllow(it) }
                    } else { //user choose NO for your previously popup window asking for grant perssion for this usb device
                        Toast.makeText(
                            context,
                            "Permission denied for device$usbDevice",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    fun close() {
        if (serialPort != null) {
            serialPort!!.close()
        }
        if (serialIoManager != null) {
            serialIoManager!!.stop()
        }
        if (executorService != null) {
            executorService!!.shutdown()
        }
    }

    fun sendMsg(msg: String) {
        try {
            serialPort!!.write(msg.toByteArray(), 150)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    val isConnect: Boolean
        get() = serialPort != null

    companion object {
        private const val ACTION_USB_PERMISSION =
            "android.hardware.usb.action.USB_DEVICE_ATTACHED"
    }
}