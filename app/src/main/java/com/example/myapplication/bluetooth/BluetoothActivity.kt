package org.blackwalnutlabs.angels.sendclient.bluetooth

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.Bundle
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R

class BluetoothActivity : AppCompatActivity() {
    private var mBtAdapter: BluetoothAdapter? = null
    private var mPairedDevicesArrayAdapter: ArrayAdapter<String>? = null
    private var pairedDevices: Set<BluetoothDevice>? = null
    private var scanButton: Button? = null
    private val mSp: SharedPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val listId = intent.getIntExtra("layout_list", R.layout.device_list)
        setContentView(listId)

        var strBluetoothDevices = intent.getStringExtra("bluetooth_devices")

        if (strBluetoothDevices == null) strBluetoothDevices = "Bluetooth Devices"

        title = strBluetoothDevices
        setResult(Activity.RESULT_CANCELED)
        scanButton = findViewById<View>(R.id.button_scan) as Button

        var strScanDevice = intent.getStringExtra("scan_for_devices")
        if (strScanDevice == null) strScanDevice = "SCAN FOR DEVICES"

        scanButton!!.text = "搜索"
        scanButton!!.setOnClickListener { doDiscovery() }

        val layout_text = intent.getIntExtra("layout_text", R.layout.device_name)
        mPairedDevicesArrayAdapter = ArrayAdapter(this, layout_text)

        val pairedListView = findViewById<View>(R.id.list_devices) as ListView

        pairedListView.adapter = mPairedDevicesArrayAdapter
        pairedListView.onItemClickListener = mDeviceClickListener

        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)

        this.registerReceiver(mReceiver, filter)
        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        this.registerReceiver(mReceiver, filter)

        mBtAdapter = BluetoothAdapter.getDefaultAdapter()
        pairedDevices = mBtAdapter?.bondedDevices
        if (pairedDevices!!.isNotEmpty()) {
            for (device in pairedDevices!!) {
                mPairedDevicesArrayAdapter!!.add(device.name + "\n" + device.address)
            }
        } else {
            val noDevices = "No devices found"
            mPairedDevicesArrayAdapter!!.add(noDevices)
        }
    }

    override fun onStart() {
        super.onStart()
    }

    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            //当发现蓝牙设备
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    var strNoFound = getIntent().getStringExtra("no_devices_found")
                    if (strNoFound == null) strNoFound = "No devices found"
                    if (mPairedDevicesArrayAdapter!!.getItem(0) == strNoFound) {
                        mPairedDevicesArrayAdapter!!.remove(strNoFound)
                    }
                    mPairedDevicesArrayAdapter!!.add(device.name + "\n" + device.address)
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                setProgressBarIndeterminateVisibility(false)
                var strSelectDevice = getIntent().getStringExtra("select_device")
                if (strSelectDevice == null) strSelectDevice = "Select a device to connect"
                title = strSelectDevice
            }
        }
    }

    //携带蓝牙地址返回主界面
    private val mDeviceClickListener = OnItemClickListener { av, v, arg2, arg3 ->
        if (mBtAdapter!!.isDiscovering) mBtAdapter!!.cancelDiscovery()
        var strNoFound = intent.getStringExtra("no_devices_found")
        if (strNoFound == null) strNoFound = "No devices found"
        if ((v as TextView).text.toString() != strNoFound) {
            val info = v.text.toString()
            val address = info.substring(info.length - 17)
            val intent = Intent()
            intent.putExtra(BluetoothState.EXTRA_DEVICE_ADDRESS, address)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    //扫描蓝牙设备
    private fun doDiscovery() {
        mPairedDevicesArrayAdapter!!.clear()
        if (pairedDevices!!.size > 0) {
            for (device in pairedDevices!!) {
                mPairedDevicesArrayAdapter!!.add(device.name + "\n" + device.address)
            }
        } else {
            var strNoFound = intent.getStringExtra("no_devices_found")
            if (strNoFound == null) strNoFound = "No devices found"
            mPairedDevicesArrayAdapter!!.add(strNoFound)
        }
        var strScanning = intent.getStringExtra("scanning")
        if (strScanning == null) strScanning = "Scanning for devices..."
        setProgressBarIndeterminateVisibility(true)
        title = strScanning
        if (mBtAdapter!!.isDiscovering) {
            mBtAdapter!!.cancelDiscovery()
        }
        mBtAdapter!!.startDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBtAdapter != null) {
            mBtAdapter!!.cancelDiscovery()
        }
        unregisterReceiver(mReceiver)
        finish()
    }
}