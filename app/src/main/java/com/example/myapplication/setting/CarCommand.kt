package com.example.myapplication.setting

import org.json.JSONException
import org.json.JSONObject

object CarCommand {
    fun generateCommand(o: Int, v: Int, c: Int, d: Int, r: Int, a: Int): String {
        val jsonObject = JSONObject()
        try {
            jsonObject.put("o", o)
            jsonObject.put("v", v)
            jsonObject.put("c", c)
            jsonObject.put("d", d)
            jsonObject.put("r", r)
            jsonObject.put("a", a)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return jsonObject.toString()
    }
    @JvmField
    val LEFT = generateCommand(0, 15, 0, 0, 15000, 0)
    @JvmField
    val LEFTQUICKLY = generateCommand(0, 10, 0, 0, 7000, 0)
    @JvmField
    val LEFTSIGN = generateCommand(0,0,0,0,15000,0)
    @JvmField
    val RIGHT = generateCommand(0, 15, 0, 1, 15000, 0)
    @JvmField
    val RIGHTQUICKLY = generateCommand(0, 10, 0, 1, 7000, 0)
    @JvmField
    val RIGHTSIGN = generateCommand(0,0,0,1,8000,0)
    @JvmField
    val STRAIGHT = generateCommand(0, 21, 0, 0, 0, 0)
    @JvmField
    val STOP = generateCommand(0, 0, 0, 0, 0, 0)
    @JvmField
    val SLOWDOWN = generateCommand(0,5,0,0,0,0)
    @JvmField
    val UTURN = generateCommand(0,0,0,1,15000,180)
}