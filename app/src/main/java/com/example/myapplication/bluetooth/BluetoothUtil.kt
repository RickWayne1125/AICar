import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Message
import android.util.Log
import android.widget.Toast
import org.blackwalnutlabs.angels.sendclient.bluetooth.BluetoothState
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.*

@SuppressLint("NewApi")
class BluetoothUtil(private val mContext: Context) {
    private val mBluetoothStateListener: BluetoothStateListener? = null
    private var mDataReceivedListener: OnDataReceivedListener? = null
    private var mBluetoothConnectionListener: BluetoothConnectionListener? = null
    private val mAutoConnectionListener: AutoConnectionListener? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    // Member object for the chat services
    private var mChatService: BluetoothService? = null
    // Name and Address of the connected device
    private var mDeviceName: String? = null
    private var mDeviceAddress: String? = null
    private var isAutoConnecting = false
    private var isAutoConnectionEnabled = false
    private var isConnected = false
    private var isConnecting = false
    private var isServiceRunning = false
    private var keyword = ""
    private var isAndroid = BluetoothState.DEVICE_ANDROID
    private var bcl: BluetoothConnectionListener? = null
    private var c = 0

    interface BluetoothStateListener {
        fun onServiceStateChanged(state: Int)
    }

    interface OnDataReceivedListener {
        fun onDataReceived(data: ByteArray?, message: String?)
    }

    interface BluetoothConnectionListener {
        fun onDeviceConnected(name: String?, address: String?)
        fun onDeviceDisconnected()
        fun onDeviceConnectionFailed()
    }

    interface AutoConnectionListener {
        fun onAutoConnectionStarted()
        fun onNewConnection(name: String?, address: String?)
    }

    val isBluetoothEnabled: Boolean
        get() = mBluetoothAdapter!!.isEnabled

    val isServiceAvailable: Boolean
        get() = mChatService != null

    fun setupService() {
        mChatService = BluetoothService(mContext, mHandler)
    }

    val serviceState: Int
        get() = if (mChatService != null) mChatService!!.state else -1

    fun startService(isAndroid: Boolean) {
        if (mChatService != null) {
            if (mChatService!!.state == BluetoothState.STATE_NONE) {
                isServiceRunning = true
                mChatService!!.start(isAndroid)
                this@BluetoothUtil.isAndroid = isAndroid
            }
        }
    }

    fun stopService() {
        if (mChatService != null) {
            isServiceRunning = false
            mChatService!!.stop()
        }
        Handler().postDelayed({
            if (mChatService != null) {
                isServiceRunning = false
                mChatService!!.stop()
            }
        }, 500)
    }

    @SuppressLint("HandlerLeak")
    private val mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                BluetoothState.MESSAGE_WRITE -> {
                }
                BluetoothState.MESSAGE_READ -> {
                    var str: String? = null
                    val arg1 = msg.arg1
                    if (arg1 == 0) {
                        str = "text"
                    } else if (arg1 == 1) {
                        str = "photo"
                    } else if (arg1 == 2) {
                        str = "video"
                    }
                    val readBuf = msg.obj as ByteArray
                    //                String readMessage = new String(readBuf);
                    if (readBuf != null && readBuf.size > 0) {
                        if (mDataReceivedListener != null) mDataReceivedListener!!.onDataReceived(readBuf, str)
                    }
                }
                BluetoothState.MESSAGE_DEVICE_NAME -> {
                    mDeviceName = msg.data.getString(BluetoothState.DEVICE_NAME)
                    mDeviceAddress = msg.data.getString(BluetoothState.DEVICE_ADDRESS)
                    if (mBluetoothConnectionListener != null) mBluetoothConnectionListener!!.onDeviceConnected(mDeviceName, mDeviceAddress)
                    isConnected = true
                }
                BluetoothState.MESSAGE_TOAST -> Toast.makeText(mContext, msg.data.getString(BluetoothState.TOAST)
                        , Toast.LENGTH_SHORT).show()
                BluetoothState.MESSAGE_STATE_CHANGE -> {
                    mBluetoothStateListener?.onServiceStateChanged(msg.arg1)
                    if (isConnected && msg.arg1 != BluetoothState.STATE_CONNECTED) {
                        if (mBluetoothConnectionListener != null) mBluetoothConnectionListener!!.onDeviceDisconnected()
                        if (isAutoConnectionEnabled) {
                            isAutoConnectionEnabled = false
                            autoConnect(keyword)
                        }
                        isConnected = false
                        mDeviceName = null
                        mDeviceAddress = null
                    }
                    if (!isConnecting && msg.arg1 == BluetoothState.STATE_CONNECTING) {
                        isConnecting = true
                    } else if (isConnecting) {
                        if (msg.arg1 != BluetoothState.STATE_CONNECTED) {
                            if (mBluetoothConnectionListener != null) mBluetoothConnectionListener!!.onDeviceConnectionFailed()
                        }
                        isConnecting = false
                    }
                }
            }
        }
    }

    fun connect(data: Intent): BluetoothDevice {
        val address = data.extras!!.getString(BluetoothState.EXTRA_DEVICE_ADDRESS)
        val device = mBluetoothAdapter!!.getRemoteDevice(address)
        mChatService!!.connect(device)
        return device
    }

    fun connect(address: String?) {
        val device = mBluetoothAdapter!!.getRemoteDevice(address)
        mChatService!!.connect(device)
    }

    fun setOnDataReceivedListener(listener: OnDataReceivedListener?) {
        mDataReceivedListener = listener
    }

    fun setBluetoothConnectionListener(listener: BluetoothConnectionListener?) {
        mBluetoothConnectionListener = listener
    }

    //添加头发送数据
    fun send(data: ByteArray, str: String) {
        val length = data.size
        var length_b: ByteArray? = null
        try {
            length_b = intToByteArray(length)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (length_b == null) return
        val headerInfo = ByteArray(headInfoLength)
        for (i in 0 until headInfoLength - 8) {
            headerInfo[i] = i.toByte()
        }
        for (i in 0..3) {
            headerInfo[6 + i] = length_b[i]
        }
        if (str == "text") {
            for (i in 0..3) {
                headerInfo[10 + i] = 0.toByte()
            }
        } else if (str == "photo") {
            for (i in 0..3) {
                headerInfo[10 + i] = 1.toByte()
            }
        } else if (str == "video") {
            for (i in 0..3) {
                headerInfo[10 + i] = 2.toByte()
            }
        }
        val sendMsg = ByteArray(length + headInfoLength)
        for (i in sendMsg.indices) {
            if (i < headInfoLength) {
                sendMsg[i] = headerInfo[i]
            } else {
                sendMsg[i] = data[i - headInfoLength]
            }
        }
        mChatService!!.write(sendMsg)
    }

    val pairedDeviceName: Array<String?>
        get() {
            var c = 0
            val devices = mBluetoothAdapter!!.bondedDevices
            val name_list = arrayOfNulls<String>(devices.size)
            for (device in devices) {
                name_list[c] = device.name
                c++
            }
            return name_list
        }

    val pairedDeviceAddress: Array<String?>
        get() {
            var c = 0
            val devices = mBluetoothAdapter!!.bondedDevices
            val address_list = arrayOfNulls<String>(devices.size)
            for (device in devices) {
                address_list[c] = device.address
                c++
            }
            return address_list
        }

    fun autoConnect(keywordName: String) {
        if (!isAutoConnectionEnabled) {
            keyword = keywordName
            isAutoConnectionEnabled = true
            isAutoConnecting = true
            mAutoConnectionListener?.onAutoConnectionStarted()
            val arr_filter_address = ArrayList<String?>()
            val arr_filter_name = ArrayList<String?>()
            val arr_name = pairedDeviceName
            val arr_address = pairedDeviceAddress
            for (i in arr_name.indices) {
                if (arr_name[i]!!.contains(keywordName)) {
                    arr_filter_address.add(arr_address[i])
                    arr_filter_name.add(arr_name[i])
                }
            }
            bcl = object : BluetoothConnectionListener {
                override fun onDeviceConnected(name: String?, address: String?) {
                    bcl = null
                    isAutoConnecting = false
                }

                override fun onDeviceDisconnected() {}
                override fun onDeviceConnectionFailed() {
                    Log.e("CHeck", "Failed")
                    if (isServiceRunning) {
                        if (isAutoConnectionEnabled) {
                            c++
                            if (c >= arr_filter_address.size) c = 0
                            connect(arr_filter_address[c])
                            Log.e("CHeck", "Connect")
                            mAutoConnectionListener?.onNewConnection(arr_filter_name[c]
                                    , arr_filter_address[c])
                        } else {
                            bcl = null
                            isAutoConnecting = false
                        }
                    }
                }
            }
            setBluetoothConnectionListener(bcl)
            c = 0
            mAutoConnectionListener?.onNewConnection(arr_name[c], arr_address[c])
            if (arr_filter_address.size > 0) connect(arr_filter_address[c]) else Toast.makeText(mContext, "Device name mismatch", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val headInfoLength = 14
        @Throws(Exception::class)
        fun intToByteArray(i: Int): ByteArray {
            val buf = ByteArrayOutputStream()
            val dos = DataOutputStream(buf)
            dos.writeInt(i)
            val b = buf.toByteArray()
            dos.close()
            buf.close()
            return b
        }
    }

    init {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    }
}