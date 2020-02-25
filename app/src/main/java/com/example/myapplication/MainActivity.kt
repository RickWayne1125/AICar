package com.example.myapplication

import BluetoothUtil
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.SurfaceView
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.myapplication.model.opencv.LaneDetector
import com.example.myapplication.model.tensorflow.TrafficSignDetector
import com.example.myapplication.setting.CarCommand
import com.example.myapplication.setting.ImageSetting
import com.example.myapplication.setting.ImageSetting.MAXHEIGHT
import com.example.myapplication.setting.ImageSetting.MAXWIDTH
import com.example.myapplication.util.PermissionUtils
import com.example.myapplication.util.PermissionUtils.requestMultiPermissions
import com.hoho.android.usbserial.util.SerialInputOutputManager
import org.blackwalnutlabs.angel.keeplane.utils.USBSerial
import org.blackwalnutlabs.angels.sendclient.bluetooth.BluetoothState
import org.json.JSONException
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.util.*

class MainActivity : Activity(), CameraBridgeViewBase.CvCameraViewListener2 {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setScreen()
        setContentView(R.layout.activity_main)
        mBt = BluetoothUtil(this)
        initBlue()
    }

    private fun setScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        requestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    /**
     * Permission
     */

    private fun requestPermission() {
        requestMultiPermissions(this, mPermissionGrant)
    }

    private val mPermissionGrant: PermissionUtils.PermissionGrant = object : PermissionUtils.PermissionGrant {
        override fun onPermissionGranted(requestCode: Int) {
            Toast.makeText(
                this@MainActivity,
                "Result Permission Grant CODE_MULTI_PERMISSION",
                Toast.LENGTH_SHORT
            ).show()

            initCamera()
        }
    }

    private var openCvCameraView: JavaCameraView? = null

    // 时间记录器
    private var calendar:Calendar = Calendar.getInstance()
    private var now:Long  = 0
    private var previous:Long = 0

    private fun initCamera() {
        openCvCameraView = findViewById(R.id.HelloOpenCvView)
        openCvCameraView?.visibility = SurfaceView.VISIBLE
        openCvCameraView?.setCvCameraViewListener(this)
        openCvCameraView?.setMaxFrameSize(ImageSetting.MAXWIDTH, ImageSetting.MAXHEIGHT)
        openCvCameraView?.enableFpsMeter()
        openCvCameraView?.enableView()
    }

    override fun onPause() {
        super.onPause()
        openCvCameraView?.disableView()
    }

    override fun onResume() {
        super.onResume()

        // 这部分是蓝牙监听程序 ↓
        if (!mBt!!.isBluetoothEnabled) { //打开蓝牙
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, BluetoothState.REQUEST_ENABLE_BT)
        } else {
            if (!mBt!!.isServiceAvailable) { //开启监听
                mBt!!.setupService()
                mBt!!.startService(BluetoothState.DEVICE_ANDROID)
            }
        }
        // 这部分是蓝牙监听程序 ↑

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADMIN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission()
        } else {
            initCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        trafficSignDetector?.close()

        openCvCameraView?.disableView()
    }

    companion object {
        private val TAG = PermissionUtils::class.java.simpleName

        /**
         * OpenCV
         */
        init {
            if (!OpenCVLoader.initDebug()) {
                Log.d(TAG, "OpenCV not loaded")
            } else {
                Log.d(TAG, "OpenCV loaded")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        PermissionUtils.requestPermissionsResult(
            this,
            requestCode,
            permissions,
            grantResults,
            mPermissionGrant
        )
        initCamera()
    }


    override fun onCameraViewStopped() {}

    private var willSendMat: Mat? = null

    private lateinit var tmpMats: Array<Mat?>
    private var emptyMat: Mat? = null
    private var zeroMat: Mat? = null

    private var kernel: Mat? = null

    private lateinit var tmpMatOfPoints: Array<MatOfPoint?>
    private var emptyMatOfPoint: MatOfPoint? = null

    private lateinit var tmpMatOfPoint2fs: Array<MatOfPoint2f?>
    private var emptyMatOfPoint2f: MatOfPoint2f? = null

    override fun onCameraViewStarted(width: Int, height: Int) {
        var len = 2
        tmpMats = arrayOfNulls(len)
        for (i in 0 until len) {
            tmpMats[i] = Mat()
        }

        emptyMat = Mat()

        len = 4
        tmpMats = arrayOfNulls(len)
        for (i in 0 until len) {
            tmpMats[i] = Mat()
        }

        emptyMat = Mat()
        zeroMat = Mat(Size(MAXWIDTH.toDouble(), MAXHEIGHT.toDouble()), CvType.CV_8U, Scalar(0.0))

        kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))

        len = 1
        tmpMatOfPoints = arrayOfNulls(len)
        for (i in 0 until len) {
            tmpMatOfPoints[i] = MatOfPoint()
        }

        emptyMatOfPoint = MatOfPoint()



        len = 1
        tmpMatOfPoint2fs = arrayOfNulls(len)
        for (i in 0 until len) {
            tmpMatOfPoint2fs[i] = MatOfPoint2f()
        }

        emptyMatOfPoint2f = MatOfPoint2f()

        initTFLiteModel()

        initOpenCVModel()

        initSerial()

//        initVideoSend()
//
//        willSendMat = Mat()
    }

    private var reversal:Boolean = false

    private var command = CarCommand.STOP

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
//        val rgbImg = inputFrame.rgba()
//
//        Imgproc.cvtColor(rgbImg, rgbImg, Imgproc.COLOR_RGBA2RGB)
//
//        Imgproc.cvtColor(rgbImg, rgbImg, Imgproc.COLOR_RGBA2RGB)
//
//        rgbImg.copyTo(willSendMat)
//
//        return rgbImg
        val rgbaImg = inputFrame.rgba()

        val rgbImg = rgbaImg
        Imgproc.cvtColor(rgbaImg, rgbImg, Imgproc.COLOR_RGBA2RGB)

        val debugMat = tmpMats[0]
        zeroMat?.copyTo(debugMat)

        now = Date().time

        // 进行路线识别
        if(now-previous > 1000) {
            val wbS = laneDetector!!.laneDetection(rgbImg, null, debugMat)

//        // 调试用，后续开发请将其删除 ↓
//        val yy0 = 0.0
//        val yx0 = (yy0 - wbS[1]) / wbS[0]
//        val yy1 = MAXHEIGHT * 1.0
//        val yx1 = (yy1 - wbS[1]) / wbS[0]
//        Core.line(rgbImg, Point(yx0, yy0), Point(yx1, yy1), Scalar(255.0, 0.0, 0.0), 3)
//
//        val wy0 = 0.0
//        val wx0 = (wy0 - wbS[3]) / wbS[2]
//        val wy1 = MAXHEIGHT * 1.0
//        val wx1 = (wy1 - wbS[3]) / wbS[2]
//        Core.line(rgbImg, Point(wx0, wy0), Point(wx1, wy1), Scalar(0.0, 255.0, 0.0), 3)
//        // 调试用，后续开发请将其删除 ↑


            command = laneDetector?.detectLane(rgbImg, reversal, debugMat)!!
        }
        Log.e(TAG,now.toString()+" "+previous.toString())

        // 进行路标推理
        val jsonArray = trafficSignDetector!!.detectImage(rgbImg)

        // 绘制出识别区域的矩形
        for (i in 0 until jsonArray!!.length()) {
            try {
                val jsonObject = jsonArray.getJSONObject(i)
                val point1 = Point(
                    jsonObject.getInt("xmin").toDouble(),
                    jsonObject.getInt("ymin").toDouble()
                )

                val point2 = Point(
                    jsonObject.getInt("xmax").toDouble(),
                    jsonObject.getInt("ymax").toDouble()
                )

                Core.rectangle(rgbImg, point1, point2, Scalar(255.0), 3)

                Log.e(TAG, jsonObject.getString("label"))

                when(jsonObject.getString("label")) {
                    "BanRight" -> {
                        if(command.equals(CarCommand.RIGHT)){
                            command = CarCommand.STRAIGHT
                            previous=Date().time
                        }
                    }
                    "turnRight" -> {
                        command = CarCommand.RIGHTSIGN
                        previous=Date().time
                    }
                    "Crosswalk" -> {
                        if(command[1].toInt()>5) {
                            command = CarCommand.SLOWDOWN
                            previous=Date().time
                        }
                    }
                    "goStraightOrTurnLeft" -> {
                        if((!command.equals(CarCommand.STRAIGHT))&&(!command.equals(CarCommand.LEFT))){
                            command = CarCommand.STRAIGHT
                            previous=Date().time
                        }
                    }
                    "Uturn" -> {
                        command = CarCommand.UTURN
                        previous=Date().time
                    }
                    "BanTurnAround" -> {
                        if(command.equals(CarCommand.UTURN)){
                            command = CarCommand.STRAIGHT
                            previous=Date().time
                        }
                    }
                    "goStraight" -> {
                        command = CarCommand.STRAIGHT
                        previous=Date().time

                    }
                    "Parking" -> {
                        command = CarCommand.STOP
                        previous=Date().time
                    }
                    "noParkingCar" -> {
                        if(command.equals(CarCommand.STOP)){
                            command = CarCommand.STRAIGHT
                            previous=Date().time
                        }
                    }
                    "BanStraight"-> {
                        if(command.equals(CarCommand.STRAIGHT)){
                            command = CarCommand.STOP
                            previous=Date().time
                        }
                    }
                    "speed80" -> {
                        command = CarCommand.UTURN
                        previous=Date().time
                    }
                    "10T" -> {
                        command = CarCommand.LEFTSIGN
                        previous = Date().time
                        reversal = true
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }



        Log.e(TAG, command)

        return rgbImg!!
    }


    /**
     * BlueTooth
     */
    private var mBt: BluetoothUtil? = null
    private var isBluetoothConnnect = false

    private fun initBlue() {
        /**
         * reveice data
         */
        mBt!!.setOnDataReceivedListener(object : BluetoothUtil.OnDataReceivedListener {
            override fun onDataReceived(data: ByteArray?, message: String?) {}
        })
        mBt!!.setBluetoothConnectionListener(object : BluetoothUtil.BluetoothConnectionListener {
            override fun onDeviceConnected(name: String?, address: String?) {
                isBluetoothConnnect = true
                Toast.makeText(applicationContext, "连接到 $name\n$address", Toast.LENGTH_SHORT).show()
            }

            override fun onDeviceDisconnected() {
                isBluetoothConnnect = false
                //断开蓝牙连接
                Toast.makeText(applicationContext, "蓝牙断开", Toast.LENGTH_SHORT).show()
            }

            override fun onDeviceConnectionFailed() {
                Toast.makeText(applicationContext, "无法连接", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BluetoothState.REQUEST_CONNECT_DEVICE) {
            if (resultCode == RESULT_OK) mBt!!.connect(data)
        } else if (requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                mBt!!.setupService()
                mBt!!.startService(BluetoothState.DEVICE_ANDROID)
            } else {
                finish()
            }
        }
    }

    /**
     * Video send
     */
    private var sendCommandTimer: Timer? = null

    private val controlHandler: Handler = object : Handler() {
//        override fun handleMessage(msg: Message) {
//            if (msg.what == 0) {
//                val resizedMat = Mat()
//                Imgproc.resize(willSendMat, resizedMat, Size(320.0, 240.0))
//                val bmp = Bitmap.createBitmap(resizedMat.width(), resizedMat.height(), Bitmap.Config.ARGB_8888)
//                Utils.matToBitmap(resizedMat, bmp)
//                val stream = ByteArrayOutputStream()
//                bmp.compress(Bitmap.CompressFormat.JPEG, 50, stream)
//                val imageBytes: ByteArray = stream.toByteArray()
//                mBt?.send(imageBytes, "video")
//            }
//            super.handleMessage(msg)
//        }
        override fun handleMessage(msg: Message) {
            if (msg.what == 0) {
                if (usbSerial!!.isConnect) {
                    usbSerial?.sendMsg(command)
                }
            }
            super.handleMessage(msg)
        }
    }

    private fun initVideoSend() {
        val sendCommandTask: TimerTask = object : TimerTask() {
            override fun run() {
                controlHandler.sendEmptyMessage(0)
            }
        }
        sendCommandTimer = Timer()
        sendCommandTimer?.schedule(sendCommandTask, 500, 500)
    }


    private var laneDetector: LaneDetector? = null

    private var roiPoints: Array<Point?> = arrayOfNulls(2)
    private var defaultRoiPoints = ArrayList<Point>()

    private fun initOpenCVModel() {
        val tmpMap: MutableMap<String?, Any?> =
            HashMap()
        tmpMap["Mat"] = tmpMats
        tmpMap["MatOfPoint"] = tmpMatOfPoints
        tmpMap["MatOfPoint2f"] = tmpMatOfPoint2fs


        val funMap: MutableMap<String?, Any?> =
            HashMap()
        funMap["EmptyMat"] = emptyMat
        funMap["ZeroMat"] = zeroMat
        funMap["EmptyMatOfPoint"] = emptyMatOfPoint
        funMap["EmptyMatOfPoint2f"] = emptyMatOfPoint2f

        val othersMap: MutableMap<String?, Any?> =
            HashMap()
        othersMap["kernel"] = kernel

        laneDetector = LaneDetector(tmpMap, funMap, othersMap)

        //Log.d("mes", tmpMap["MatOfPoint2f"].toString())

        roiPoints[0] = Point(MAXWIDTH/2.0-MAXWIDTH*1/8,MAXHEIGHT/2.0-MAXHEIGHT/7)
        roiPoints[1] = Point(MAXWIDTH/2.0-MAXWIDTH*3/8,MAXHEIGHT*1.0)
        defaultRoiPoints.add(roiPoints[0]!!)
        defaultRoiPoints.add(roiPoints[1]!!)
        defaultRoiPoints.add(Point(MAXWIDTH - roiPoints[1]!!.x, roiPoints[1]!!.y))
        defaultRoiPoints.add(Point(MAXWIDTH - roiPoints[0]!!.x, roiPoints[0]!!.y))
        laneDetector?.setDefaultRoiPoints(defaultRoiPoints)

    }

    private var trafficSignDetector: TrafficSignDetector? = null

    private fun initTFLiteModel() {
        val tmpMap: MutableMap<String?, Any?> =
            HashMap()
        tmpMap["MatOfPoint2f"] = tmpMatOfPoint2fs
        tmpMap["Mat"] = tmpMats


        val funMap: MutableMap<String?, Any?> =
            HashMap()
        funMap["EmptyMatOfPoint2f"] = emptyMatOfPoint2f
        funMap["EmptyMat"] = emptyMat

        val othersMap: MutableMap<String?, Any?> =
            HashMap()
        othersMap["activity"] = this

        trafficSignDetector = TrafficSignDetector(tmpMap, funMap, othersMap)

        Log.d("mes0", tmpMap["MatOfPoint2f"].toString())
    }

    // 串口发送指令
    private var usbSerial: USBSerial? = null

    private val serialListener: SerialInputOutputManager.Listener =
        object : SerialInputOutputManager.Listener {
            override fun onRunError(e: Exception) {
                Log.d(TAG, "Runner stopped.")
            }

            override fun onNewData(data: ByteArray) {
                val msg = String(data)
                Log.e(TAG, "这是我从串口接收的信息：$msg")
            }
        }
    private fun initSerial() {
        usbSerial = USBSerial(this, serialListener)
        usbSerial?.initUsbSerial()

        // 定时器发送指令
        sendCommandTimer = Timer()
        val sendCommandTask: TimerTask = object : TimerTask() {
            override fun run() {
                controlHandler.sendEmptyMessage(0)
            }
        }
        sendCommandTimer?.schedule(sendCommandTask, 150, 150)
    }




}
