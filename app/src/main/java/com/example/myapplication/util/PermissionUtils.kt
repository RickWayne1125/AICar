package com.example.myapplication.util

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.myapplication.R

object PermissionUtils {
    private val TAG = PermissionUtils::class.java.simpleName
    private const val CODE_MULTI_PERMISSION = 100
    private const val PERMISSION_CAMERA = Manifest.permission.CAMERA
    private const val PERMISSION_BLUETOOTH =
        Manifest.permission.BLUETOOTH
    private const val PERMISSION_BLUETOOTH_ADMIN =
        Manifest.permission.BLUETOOTH_ADMIN
    private val requestPermissions = arrayOf(
        PERMISSION_CAMERA,
        PERMISSION_BLUETOOTH,
        PERMISSION_BLUETOOTH_ADMIN
    )

    private fun requestMultiResult(
        activity: Activity?,
        permissions: Array<String>,
        grantResults: IntArray,
        permissionGrant: PermissionGrant
    ) {
        if (activity == null) {
            return
        }
        //TODO
        Log.d(
            TAG,
            "onRequestPermissionsResult permissions length:" + permissions.size
        )
        val notGranted = ArrayList<String>()
        for (i in permissions.indices) {
            Log.d(
                TAG,
                "permissions: [i]:" + i + ", permissions[i]" + permissions[i] + ",grantResults[i]:" + grantResults[i]
            )
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                notGranted.add(permissions[i])
            }
        }
        if (notGranted.size == 0) {
            Toast.makeText(activity, "all permission success$notGranted", Toast.LENGTH_SHORT)
                .show()
            permissionGrant.onPermissionGranted(CODE_MULTI_PERMISSION)
        } else {
            openSettingActivity(activity, "those permission need granted!")
        }
    }

    /**
     * 一次申请多个权限
     */
    @JvmStatic
    fun requestMultiPermissions(activity: Activity, grant: PermissionGrant) {
        val permissionsList: List<String>? =
            getNoGrantedPermission(activity, false)
        val shouldRationalePermissionsList: List<String>? =
            getNoGrantedPermission(activity, true)
        //TODO checkSelfPermission
        if (permissionsList == null || shouldRationalePermissionsList == null) {
            return
        }
        Log.d(
            TAG,
            "requestMultiPermissions permissionsList:" + permissionsList.size + ",shouldRationalePermissionsList:" + shouldRationalePermissionsList.size
        )
        when {
            permissionsList.isNotEmpty() -> {
                ActivityCompat.requestPermissions(
                    activity, permissionsList.toTypedArray(),
                    CODE_MULTI_PERMISSION
                )
                Log.d(TAG, "showMessageOKCancel requestPermissions")
            }
            shouldRationalePermissionsList.isNotEmpty() -> {
                showMessageOKCancel(activity, "should open those permission",
                    DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
                        ActivityCompat.requestPermissions(
                            activity, shouldRationalePermissionsList.toTypedArray(),
                            CODE_MULTI_PERMISSION
                        )
                        Log.d(
                            TAG,
                            "showMessageOKCancel requestPermissions"
                        )
                    }
                )
            }
            else -> {
                grant.onPermissionGranted(CODE_MULTI_PERMISSION)
            }
        }
    }

    private fun showMessageOKCancel(
        context: Activity,
        message: String,
        okListener: DialogInterface.OnClickListener
    ) {
        AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton("OK", okListener)
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    @JvmStatic
    fun requestPermissionsResult(
        activity: Activity?, requestCode: Int, permissions: Array<String>,
        grantResults: IntArray, permissionGrant: PermissionGrant
    ) {
        if (activity == null) {
            return
        }
        Log.d(
            TAG,
            "requestPermissionsResult requestCode:$requestCode"
        )
        if (requestCode == CODE_MULTI_PERMISSION) {
            requestMultiResult(activity, permissions, grantResults, permissionGrant)
            return
        }
        if (requestCode < 0 || requestCode >= requestPermissions.size) {
            Log.w(
                TAG,
                "requestPermissionsResult illegal requestCode:$requestCode"
            )
            Toast.makeText(activity, "illegal requestCode:$requestCode", Toast.LENGTH_SHORT)
                .show()
            return
        }
        Log.i(
            TAG,
            "onRequestPermissionsResult requestCode:$requestCode,permissions:" + permissions.contentToString()
                    + ",grantResults:" + grantResults.contentToString() + ",length:" + grantResults.size
        )
        if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "onRequestPermissionsResult PERMISSION_GRANTED")
            //TODO success, do something, can use callback
            permissionGrant.onPermissionGranted(requestCode)
        } else { //TODO hint user this permission function
            Log.i(
                TAG,
                "onRequestPermissionsResult PERMISSION NOT GRANTED"
            )
            //TODO
            val permissionsHint =
                activity.resources.getStringArray(R.array.permissions)
            openSettingActivity(
                activity,
                "Result" + permissionsHint[requestCode]
            )
        }
    }

    private fun openSettingActivity(activity: Activity, message: String) {
        showMessageOKCancel(
            activity,
            message,
            DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                Log.d(
                    TAG,
                    "getPackageName(): " + activity.packageName
                )
                val uri =
                    Uri.fromParts("package", activity.packageName, null)
                intent.data = uri
                activity.startActivity(intent)
            }
        )
    }

    private fun getNoGrantedPermission(
        activity: Activity,
        isShouldRationale: Boolean
    ): ArrayList<String>? {
        val permissions = ArrayList<String>()
        for (requestPermission in requestPermissions) { //TODO checkSelfPermission
            var checkSelfPermission: Int
            checkSelfPermission = try {
                ActivityCompat.checkSelfPermission(activity, requestPermission)
            } catch (e: RuntimeException) {
                Toast.makeText(activity, "please open those permission", Toast.LENGTH_SHORT)
                    .show()
                Log.e(TAG, "RuntimeException:" + e.message)
                return null
            }
            if (checkSelfPermission != PackageManager.PERMISSION_GRANTED) {
                Log.i(
                    TAG,
                    "getNoGrantedPermission ActivityCompat.checkSelfPermission != PackageManager.PERMISSION_GRANTED:$requestPermission"
                )
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        requestPermission
                    )
                ) {
                    Log.d(
                        TAG,
                        "shouldShowRequestPermissionRationale if"
                    )
                    if (isShouldRationale) {
                        permissions.add(requestPermission)
                    }
                } else {
                    if (!isShouldRationale) {
                        permissions.add(requestPermission)
                    }
                    Log.d(
                        TAG,
                        "shouldShowRequestPermissionRationale else"
                    )
                }
            }
        }
        return permissions
    }

    interface PermissionGrant {
        fun onPermissionGranted(requestCode: Int)
    }
}