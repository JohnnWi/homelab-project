package com.homelab.app.util

import android.content.Context
import com.homelab.app.R
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorHandler {
    fun getMessage(context: Context, error: Throwable?): String {
        return when (error) {
            is ConnectException, is UnknownHostException -> context.getString(R.string.error_server_unreachable) // We'll need to add string resources
            is SocketTimeoutException -> context.getString(R.string.error_timeout)
            is IOException -> context.getString(R.string.error_network)
            is HttpException -> {
                when (error.code()) {
                    401 -> context.getString(R.string.error_invalid_credentials) // E.g., Pihole auth
                    403 -> context.getString(R.string.error_forbidden)
                    404 -> context.getString(R.string.error_not_found)
                    in 500..599 -> context.getString(R.string.error_server)
                    else -> context.getString(R.string.error_unknown_status, error.code())
                }
            }
            else -> error?.localizedMessage ?: context.getString(R.string.error_unknown)
        }
    }
}
