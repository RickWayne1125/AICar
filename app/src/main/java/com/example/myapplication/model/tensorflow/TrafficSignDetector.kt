package com.example.myapplication.model.tensorflow

import android.app.Activity
import android.graphics.Bitmap
import com.example.myapplication.setting.ImageSetting
import com.example.myapplication.setting.TensorFlowSetting
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TrafficSignDetector(
    tmpMap: Map<String?, Any?>,
    funMap: Map<String?, Any?>,
    othersMap: Map<String?, Any?>
) {
    private var tfliteModel: MappedByteBuffer? = null
    protected var tflite: Interpreter? = null

    var labelList: MutableList<String?>? = null

    private val intValues =
        IntArray(TensorFlowSetting.DIM_IMG_SIZE_X * TensorFlowSetting.DIM_IMG_SIZE_Y)
    protected var imgData: ByteBuffer? = null

    private val tmpMats: Array<Mat>?
    private val emptyMat: Mat?

    // 识别出的对象坐标数组、标签数组、识别概率数组、对象数量数组
    private lateinit var boxArray: Array<Array<FloatArray>>
    private lateinit var labelArray: Array<FloatArray>
    private lateinit var scoreArray: Array<FloatArray>
    private lateinit var numArray: FloatArray

    init {
        tmpMats = tmpMap["Mat"] as Array<Mat>?
        emptyMat = funMap["EmptyMat"] as Mat?

        try {
            val activity = (othersMap["activity"] as Activity?)!!
            tfliteModel = loadModelFile(activity)
            labelList = loadLabelList(activity)

            val tfliteOptions =
                Interpreter.Options()
            tfliteOptions.setUseNNAPI(true)
            tfliteOptions.setNumThreads(2)

            tflite = Interpreter(tfliteModel!!, tfliteOptions)

            imgData = ByteBuffer.allocateDirect(
                1 * TensorFlowSetting.DIM_BATCH_SIZE * TensorFlowSetting.DIM_IMG_SIZE_X * TensorFlowSetting.DIM_IMG_SIZE_Y * TensorFlowSetting.DIM_PIXEL_SIZE
            )
            imgData!!.order(ByteOrder.nativeOrder())

            boxArray = Array(1) { Array(10) { FloatArray(4) } }
            labelArray = Array(1) { FloatArray(10) }
            scoreArray = Array(1) { FloatArray(10) }
            numArray = FloatArray(1)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // 读取标签
    @Throws(IOException::class)
    private fun loadLabelList(activity: Activity?): MutableList<String?> {
        val labels: MutableList<String?> =
            ArrayList()
        val reader = BufferedReader(
            InputStreamReader(
                activity!!.assets.open(TensorFlowSetting.LABEL_PATH)
            )
        )
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            labels.add(line)
        }
        reader.close()

        return labels
    }

    // 读取模型
    @Throws(IOException::class)
    private fun loadModelFile(activity: Activity?): MappedByteBuffer {
        val fileDescriptor =
            activity!!.assets.openFd(TensorFlowSetting.MODELFILE)
        val inputStream =
            FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
    }

    // android的bitmap类型的图像转换为tf的bytebuffer类型的图像
    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        if (imgData == null) {
            return
        }

        imgData!!.rewind()

        bitmap.getPixels(
            intValues,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        )

        imgData!!.rewind()

        var pixel = 0
        for (i in 0 until TensorFlowSetting.DIM_IMG_SIZE_X) {
            for (j in 0 until TensorFlowSetting.DIM_IMG_SIZE_Y) {
                val v = intValues[pixel++]
                imgData!!.put((v shr 16 and 0xFF).toByte())
                imgData!!.put((v shr 8 and 0xFF).toByte())
                imgData!!.put((v and 0xFF).toByte())
            }
        }
    }

    // 模型推理
    fun detectImage(src: Mat): JSONArray? {
        val processedMat = tmpMats!![1]
        emptyMat!!.copyTo(processedMat)

        Imgproc.resize(src, processedMat, Size(300.0, 300.0))

        // opencv获取的mat类型图像转换为android的bitmap
        if (tflite != null) {
            val bmp = Bitmap.createBitmap(
                processedMat.width(), processedMat.height(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(processedMat, bmp)
            // bitmap类型图像转换为bytebuffer类型
            convertBitmapToByteBuffer(bmp)

            // 设置模型传入的参数以及保存识别结果的位置
            val inputs = arrayOf<Any?>(imgData)
            val outputs: MutableMap<Int?, Any?> =
                HashMap<Int?, Any?>()
            outputs[0] = boxArray
            outputs[1] = labelArray
            outputs[2] = scoreArray
            outputs[3] = numArray

            // 进行推理
            tflite!!.runForMultipleInputsOutputs(inputs, outputs)

            // 识别结果保存为json
            val jsonArray = JSONArray()
            for (i in 0 until numArray[0].toInt()) {
                if (scoreArray[0][i] > 0.7) {
                    val jsonObject = JSONObject()
                    val ymin = (boxArray[0][i][0] * ImageSetting.MAXHEIGHT).toInt()
                    val xmin = (boxArray[0][i][1] * ImageSetting.MAXWIDTH).toInt()
                    val ymax = (boxArray[0][i][2] * ImageSetting.MAXHEIGHT).toInt()
                    val xmax = (boxArray[0][i][3] * ImageSetting.MAXWIDTH).toInt()
                    try {
                        jsonObject.put("ymin", ymin)
                        jsonObject.put("xmin", xmin)
                        jsonObject.put("ymax", ymax)
                        jsonObject.put("xmax", xmax)
                        jsonObject.put("score", scoreArray[0][i])
                        jsonObject.put("label", labelList!![labelArray[0][i].toInt()])
                        jsonArray.put(jsonObject)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            }
            return jsonArray
        }
        return null
    }

    // 资源释放
    fun close() {
        if (tflite != null) {
            tflite!!.close()
            tflite = null
        }
        tfliteModel = null
    }
}