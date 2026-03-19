package com.homelab.app.util

import android.content.Context
import com.homelab.app.R
import com.homelab.app.data.remote.HtmlResponseException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorHandler {
    fun getMessage(context: Context, error: Throwable?): String {
        Logger.e("ErrorHandler", "Handling error", error)
        return when (error) {
            is HtmlResponseException -> context.getString(R.string.error_html_response)
            is ConnectException, is UnknownHostException -> context.getString(R.string.error_server_unreachable) // We'll need to add string resources
            is SocketTimeoutException -> context.getString(R.string.error_timeout)
            is SerializationException -> context.getString(R.string.error_parsing)
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
            is IllegalStateException -> {
                if (error.message == "Healthchecks authentication failed") {
                    context.getString(R.string.error_invalid_credentials)
                } else {
                    error.localizedMessage ?: context.getString(R.string.error_unknown)
                }
            }
            else -> error?.localizedMessage ?: context.getString(R.string.error_unknown)
        }
    }
}
