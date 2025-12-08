package com.rokid.cxrmsamples.dataBeans

import android.annotation.SuppressLint
import android.os.Build
import com.rokid.cxrmsamples.R

object CONSTANT {
    const val BLUETOOTH_PERMISSION_REQUEST = 0x0010

    @SuppressLint("ObsoleteSdkInt")
    val BLUETOOTH_PERMISSIONS = mutableListOf(
        android.Manifest.permission.BLUETOOTH,
        android.Manifest.permission.BLUETOOTH_ADMIN,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(android.Manifest.permission.BLUETOOTH_CONNECT)
            add(android.Manifest.permission.BLUETOOTH_SCAN)
        }
    }.toTypedArray()

    const val SERVICE_UUID = "00009100-0000-1000-8000-00805f9b34fb"


    // Client Secret -- copy from https://ar.rokid.com/ -->Account Center-->Credential information
    const val CLIENT_SECRET = "ac4b4555-d295-11f0-961e-043f72fdb9c8"
    fun getSNResource() = R.raw.d1617e9173d04e7285b66bb8e6c6c569

    const val CUSTOM_CMD = "rk_custom_key"

}