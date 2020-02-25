import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import org.blackwalnutlabs.angels.sendclient.bluetooth.BluetoothState
import java.io.*
import java.util.*

@SuppressLint("NewApi")
class BluetoothService(context: Context?, handler: Handler) {
    private val mAdapter: BluetoothAdapter
    private val mHandler: Handler
    private var mSecureAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null
    private var mState: Int
    private var isAndroid = BluetoothState.DEVICE_ANDROID

    @get:Synchronized
    @set:Synchronized
    var state: Int
        get() = mState
        private set(state) {
            Log.d(TAG, "setState() $mState -> $state")
            mState = state
            mHandler.obtainMessage(BluetoothState.MESSAGE_STATE_CHANGE, state, -1).sendToTarget()
        }

    //开启子线程
    @Synchronized
    fun start(isAndroid: Boolean) { // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        state = BluetoothState.STATE_LISTEN
        //开启子线程
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = AcceptThread(isAndroid)
            mSecureAcceptThread!!.start()
            this@BluetoothService.isAndroid = isAndroid
        }
    }

    @Synchronized
    fun connect(device: BluetoothDevice) { // Cancel any thread attempting to make a connection
        if (mState == BluetoothState.STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread!!.cancel()
                mConnectThread = null
            }
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(device)
        mConnectThread!!.start()
        state = BluetoothState.STATE_CONNECTING
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    @Synchronized
    fun connected(socket: BluetoothSocket?, device: BluetoothDevice, socketType: String?) {
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread!!.cancel()
            mSecureAcceptThread = null
        }
        mConnectedThread = ConnectedThread(socket, socketType)
        mConnectedThread!!.start()
        val msg = mHandler.obtainMessage(BluetoothState.MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        bundle.putString(BluetoothState.DEVICE_NAME, device.name)
        bundle.putString(BluetoothState.DEVICE_ADDRESS, device.address)
        msg.data = bundle
        mHandler.sendMessage(msg)
        state = BluetoothState.STATE_CONNECTED
    }

    @Synchronized
    fun stop() {
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread!!.cancel()
            mSecureAcceptThread!!.kill()
            mSecureAcceptThread = null
        }
        state = BluetoothState.STATE_NONE
    }

    fun write(out: ByteArray?) {
        var r: ConnectedThread?
        synchronized(this) {
            if (mState != BluetoothState.STATE_CONNECTED) return
            r = mConnectedThread
        }
        r!!.write(out)
    }

    private fun connectionFailed() {
        start(isAndroid)
    }

    private fun connectionLost() {
        start(isAndroid)
    }

    //监听蓝牙连接的线程
    private inner class AcceptThread(isAndroid: Boolean) : Thread() {
        private var mmServerSocket: BluetoothServerSocket?
        private val mSocketType: String? = null
        var isRunning = true
        override fun run() {
            name = "AcceptThread$mSocketType"
            var socket: BluetoothSocket? = null
            //死循环监听蓝牙连接状态，首次今进入一定满足条件，蓝牙连上后，循环停止
            while (mState != BluetoothState.STATE_CONNECTED && isRunning) {
                socket = try {
                    mmServerSocket!!.accept()
                } catch (e: IOException) {
                    break
                }
                if (socket != null) {
                    synchronized(this@BluetoothService) {
                        when (mState) {
                            BluetoothState.STATE_LISTEN, BluetoothState.STATE_CONNECTING -> connected(socket, socket.remoteDevice,
                                    mSocketType)
                            BluetoothState.STATE_NONE, BluetoothState.STATE_CONNECTED -> try {
                                socket.close()
                            } catch (e: IOException) {
                            }
                        }
                    }
                }
            }
        }

        fun cancel() {
            try {
                mmServerSocket!!.close()
                mmServerSocket = null
            } catch (e: IOException) {
            }
        }

        fun kill() {
            isRunning = false
        }

        init {
            var tmp: BluetoothServerSocket? = null
            try {
                tmp = if (isAndroid) //获取蓝牙socket
                    mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, UUID_ANDROID_DEVICE) else mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, UUID_OTHER_DEVICE)
            } catch (e: IOException) {
            }
            mmServerSocket = tmp
        }
    }

    private inner class ConnectThread(private val mmDevice: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket?
        private val mSocketType: String? = null
        override fun run() {
            mAdapter.cancelDiscovery()
            try {
                mmSocket!!.connect()
            } catch (e: IOException) {
                try {
                    mmSocket!!.close()
                } catch (e2: IOException) {
                }
                connectionFailed()
                return
            }
            synchronized(this@BluetoothService) { mConnectThread = null }
            connected(mmSocket, mmDevice, mSocketType)
        }

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
            }
        }

        init {
            var tmp: BluetoothSocket? = null
            try {
                tmp = if (isAndroid) mmDevice.createRfcommSocketToServiceRecord(UUID_ANDROID_DEVICE) else mmDevice.createRfcommSocketToServiceRecord(UUID_OTHER_DEVICE)
            } catch (e: IOException) {
            }
            mmSocket = tmp
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket?, socketType: String?) : Thread() {
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?
        override fun run() {
            var buffer: ByteArray
            val arr_byte = ArrayList<Int>()
            while (true) {
                try {
                    var valid = true
                    //判断前六位是不是12345
                    for (i in 0..5) {
                        val t = mmInStream!!.read()
                        if (t != i) {
                            valid = false
                            break
                        }
                    }
                    if (valid) {
                        val bufLength = ByteArray(4)
                        for (i in 0..3) {
                            bufLength[i] = mmInStream!!.read().toByte()
                        }
                        var textCount = 0
                        var photoCount = 0
                        var videoCount = 0
                        for (i in 0..3) {
                            val read = mmInStream!!.read()
                            if (read == 0) {
                                textCount++
                            } else if (read == 1) {
                                photoCount++
                            } else if (read == 2) {
                                videoCount++
                            }
                        }
                        val length = ByteArrayToInt(bufLength)
                        buffer = ByteArray(length)
                        for (i in 0 until length) {
                            buffer[i] = mmInStream!!.read().toByte()
                        }
                        val msg = Message.obtain()
                        msg.what = BluetoothState.MESSAGE_READ
                        msg.obj = buffer
                        if (textCount == 4) {
                            msg.arg1 = 0
                            mHandler.sendMessage(msg)
                        } else if (photoCount == 4) {
                            msg.arg1 = 1
                            mHandler.sendMessage(msg)
                        } else if (videoCount == 4) {
                            msg.arg1 = 2
                            mHandler.sendMessage(msg)
                        }
                    }
                } catch (e: IOException) {
                    connectionLost()
                    this@BluetoothService.start(isAndroid)
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun write(buffer: ByteArray?) {
            try { /*
                byte[] buffer2 = new byte[buffer.length + 2];
                for(int i = 0 ; i < buffer.length ; i++)
                    buffer2[i] = buffer[i];
                buffer2[buffer2.length - 2] = 0x0A;
                buffer2[buffer2.length - 1] = 0x0D;*/
                mmOutStream!!.write(buffer)
                // Share the sent message back to the UI Activity
//                mHandler.obtainMessage(BluetoothState.MESSAGE_WRITE
//                        , -1, -1, buffer).sendToTarget();
            } catch (e: IOException) {
            }
        }

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
            }
        }

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null
            try {
                tmpIn = mmSocket!!.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }
    }

    companion object {
        private const val TAG = "Bluetooth Service"
        private const val NAME_SECURE = "Bluetooth Secure"
        private val UUID_ANDROID_DEVICE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        private val UUID_OTHER_DEVICE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        @Throws(Exception::class)
        fun ByteArrayToInt(b: ByteArray?): Int {
            val buf = ByteArrayInputStream(b)
            val dis = DataInputStream(buf)
            return dis.readInt()
        }
    }

    init {
        mAdapter = BluetoothAdapter.getDefaultAdapter()
        mState = BluetoothState.STATE_NONE
        mHandler = handler
    }
}