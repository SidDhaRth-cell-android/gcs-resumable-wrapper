    package com.flutteroid.gcsresummableuploadwrapper.internal.utils

    import android.content.BroadcastReceiver
    import android.content.Context
    import android.content.Intent
    import android.content.IntentFilter
    import android.net.*
    import android.os.Build
    import kotlin.also

    class NetworkHandler private constructor(private val context: Context) {

        interface NetworkListener {
            fun onAvailable()
            fun onLost()
        }

        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        private var listener: NetworkListener? = null

        @Volatile
        var isConnectedToInternet: Boolean = false
            private set

        fun setNetworkListener(listener: NetworkListener) {
            this.listener = listener
            register()
        }

        fun removeListeners(){
            this.listener = null
        }

        private fun register() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                connectivityManager.registerNetworkCallback(
                    request,
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            super.onAvailable(network)
                            isConnectedToInternet = true
                            listener?.onAvailable()
                        }

                        override fun onLost(network: Network) {
                            super.onLost(network)
                            isConnectedToInternet = false
                            listener?.onLost()
                        }
                    })
            } else {
                // Fallback for older versions
                val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
                context.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val activeNetwork = connectivityManager.activeNetworkInfo
                        if (activeNetwork != null && activeNetwork.isConnected) {
                            isConnectedToInternet = true
                            listener?.onAvailable()
                        } else {
                            isConnectedToInternet = false
                            listener?.onLost()
                        }
                    }
                }, filter)
            }
        }

        companion object {
            @Volatile
            private var INSTANCE: NetworkHandler? = null

            fun getInstance(context: Context): NetworkHandler {
                return INSTANCE ?: synchronized(this) {
                    INSTANCE ?: NetworkHandler(context.applicationContext).also { INSTANCE = it }
                }
            }
        }
    }