package com.example.myapplication.model.opencv

import com.example.myapplication.setting.CarCommand
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.lang.Math.abs
import java.lang.Math.sqrt

class LaneDetector(
    tmpMap: Map<String?, Any?>,
    funMap: Map<String?, Any?>,
    othersMap: Map<String?, Any?>
){
    private val tmpMats: Array<Mat>?
    private val emptyMat: Mat?

    private val kernel: Mat?
    private val zeroMat: Mat?

    private val tmpMatOfPoints: Array<MatOfPoint>?
    private val emptyMatOfPoint: MatOfPoint?

    private val tmpMatOfPoint2fs: Array<MatOfPoint2f>?
    private val emptyMatOfPoint2f: MatOfPoint2f?

    init {
        tmpMats = tmpMap["Mat"] as Array<Mat>?
        emptyMat = funMap["EmptyMat"] as Mat?

        kernel = othersMap["kernel"] as Mat?

        zeroMat = funMap["ZeroMat"] as Mat?

        tmpMatOfPoints = tmpMap["MatOfPoint"] as Array<MatOfPoint>?
        emptyMatOfPoint = funMap["EmptyMatOfPoint"] as MatOfPoint?

        tmpMatOfPoint2fs = tmpMap["MatOfPoint2f"] as Array<MatOfPoint2f>?
        emptyMatOfPoint2f = funMap["EmptyMatOfPoint2f"] as MatOfPoint2f?
    }

    private var defaultRoiPoints: List<Point>? = null

    fun setDefaultRoiPoints(defaultRoiPoints: ArrayList<Point>?) {
        this.defaultRoiPoints = defaultRoiPoints
    }

    // 计算两点间距离
    private fun calDist(p1: Point?, p2: Point?): Double {
        return sqrt((p1!!.x - p2!!.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y))
    }

    private fun detectLine(
        src: Mat,
        // 传入hsv识别的高低阈值，用于识别各种颜色
        lowerThreshold: Scalar,
        upperThreshold: Scalar,
        roiPoints: List<Point>?,
        debugMat: Mat?
    ): DoubleArray {
        val result = doubleArrayOf(0.0, -999.0)

        // 因为rgb颜色空间中颜色作为rgb值时2不稳定，所以使用hsv颜色空间，便于颜色识别
        val hsvImg = tmpMats!![1]
        emptyMat!!.copyTo(hsvImg)

        Imgproc.cvtColor(src, hsvImg, Imgproc.COLOR_RGB2HSV)

        // 进行颜色识别
        Core.inRange(hsvImg, lowerThreshold, upperThreshold, hsvImg)

        // 降噪
        Imgproc.morphologyEx(hsvImg, hsvImg, Imgproc.MORPH_OPEN, kernel)

        // 只有在roi部分的图像，我们才进行对应操作（自转）
        val maskROI = tmpMats[2]
        zeroMat!!.copyTo(maskROI)

        // 使用
        var actRoiPoints = roiPoints
        if (actRoiPoints == null) {
            actRoiPoints = defaultRoiPoints
        }

        val pts: MutableList<MatOfPoint> = ArrayList()
        val roiMat = tmpMatOfPoints!![0]
        emptyMatOfPoint!!.copyTo(roiMat)
        roiMat.fromList(actRoiPoints)
        pts.add(roiMat)
        Core.fillPoly(maskROI, pts, Scalar(255.0))

        Core.bitwise_and(hsvImg, maskROI, hsvImg)

        // 中值滤波进行模糊
        Imgproc.medianBlur(hsvImg, hsvImg, 5)

        // 检测边缘
        Imgproc.Canny(hsvImg, hsvImg, 20.0, 60.0)

        // 计算轮廓
        val contours: List<MatOfPoint> = ArrayList()
        val useless = tmpMats[3]
        Imgproc.findContours(
            hsvImg,
            contours,
            useless,
            Imgproc.RETR_TREE,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        if (contours.isNotEmpty()) {
            var mID = 0
            var mA = Imgproc.contourArea(contours[0])
            for (i in 1 until contours.size) {
                val a = Imgproc.contourArea(contours[i])
                if (mA < a) {
                    mA = a
                    mID = i
                }
            }

            // 计算轮廓多边形的中心
            val moments = Imgproc.moments(contours[mID])
            val cx = (moments._m10 / moments._m00).toInt()
            val cy = (moments._m01 / moments._m00).toInt()

            val matOfPoint2f = tmpMatOfPoint2fs!![0]
            emptyMatOfPoint2f!!.copyTo(matOfPoint2f)
            matOfPoint2f.fromArray(*contours[mID].toArray())
            // 外接矩形
            val rotatedRect = Imgproc.minAreaRect(matOfPoint2f)
            val box = arrayOfNulls<Point>(4)
            rotatedRect.points(box)

            // 计算点在矩形中的位置，以及直线的斜率
            if (calDist(box[0], box[1]) > calDist(box[1], box[2]) &&
                box[1]!!.x - box[0]!!.x != 0.0
            ) {
                result[0] = (box[1]!!.y - box[0]!!.y) / (box[1]!!.x - box[0]!!.x)
            } else if (box[2]!!.x - box[1]!!.x != 0.0) {
                result[0] = (box[2]!!.y - box[1]!!.y) / (box[2]!!.x - box[1]!!.x)
            }

            // 计算直线截距
            result[1] = cy - result[0] * cx

            for (contour in contours) {
                contour.release()
            }
        }

        if (debugMat != null) {
            Core.bitwise_or(debugMat, hsvImg, debugMat)
        }

        return result
    }

    // 获得颜色识别的阈值
    fun laneDetection(
        src: Mat,
        roiPoints: List<Point>?,
        debugMat: Mat?
    ): DoubleArray {
        val lowerYellow = Scalar(11.0, 80.0, 80.0)
        val upperYellow = Scalar(34.0, 255.0, 255.0)
        val lowerWhite = Scalar(0.0, 0.0, (255 - 70).toDouble())
        val upperWhite = Scalar(255.0, 70.toDouble(), 255.0)

        val yellowWB =
            detectLine(src, lowerYellow, upperYellow, roiPoints, debugMat)
        val whiteWB =
            detectLine(src, lowerWhite, upperWhite, roiPoints, debugMat)
        return doubleArrayOf(yellowWB[0], yellowWB[1], whiteWB[0], whiteWB[1])
        return doubleArrayOf(yellowWB[0], yellowWB[1], whiteWB[0], whiteWB[1])

        // 车辆入库
        //val lowerBlue = Scalar()
    }

    private fun logicJudgement(
        aW: Double,
        aB: Double,
        bW: Double,
        bB: Double,
        reversal: Boolean
    ): String {
        var needQuickly = false
        if (aW != 0.0) {
            if (abs(aW) < 1.2) {
                needQuickly = true
            }
        }
        if (bW != 0.0) {
            if (abs(bW) < 1.2) {
                needQuickly = true
            }
        }
        if (aW != 0.0 && bW == 0.0) { // Turn right
            return if (needQuickly) {
                if (reversal) {
                    CarCommand.LEFTQUICKLY
                } else {
                    CarCommand.RIGHTQUICKLY
                }
            } else {
                if (reversal) {
                    CarCommand.LEFT
                } else {
                    CarCommand.RIGHT
                }
            }
        } else if (aW == 0.0 && bW != 0.0) { // Turn left
            return if (needQuickly) {
                if (reversal) {
                    CarCommand.RIGHTQUICKLY
                } else {
                    CarCommand.LEFTQUICKLY
                }
            } else {
                if (reversal) {
                    CarCommand.RIGHT
                } else {
                    CarCommand.LEFT
                }
            }
        }
        return CarCommand.STRAIGHT
    }

    fun detectLane(src: Mat, reversal: Boolean, debugMat: Mat?): String {
        val wbS = laneDetection(src, null, debugMat)
        return logicJudgement(wbS[0], wbS[1], wbS[2], wbS[3], reversal)
    }


}