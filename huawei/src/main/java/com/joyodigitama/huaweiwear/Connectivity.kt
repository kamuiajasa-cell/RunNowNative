package com.joyodigitama.huaweiwear

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Whether the phone has a network that reaches the internet right now. */
internal fun isOnline(context: Context): Boolean = try {
  val manager = context.getSystemService(ConnectivityManager::class.java)
  val network = manager?.activeNetwork
  if (manager == null) true
  else if (network == null) false
  else manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
} catch (e: SecurityException) {
  // Without the permission there is no way to tell; the request itself will fail and say so.
  true
}
