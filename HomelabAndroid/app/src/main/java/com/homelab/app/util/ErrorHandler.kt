package com.homelab.app.util

import android.content.Context
import com.homelab.app.R
import com.homelab.app.data.remote.HtmlResponseException
import com.homelab.app.data.repository.PatchmonApiException
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
                    400 -> context.getString(R.string.error_bad_request)
                    401 -> context.getString(R.string.error_invalid_credentials) // E.g., Pihole auth
                    403 -> context.getString(R.string.error_forbidden)
                    404 -> context.getString(R.string.error_not_found)
                    429 -> context.getString(R.string.error_rate_limited)
                    in 500..599 -> context.getString(R.string.error_server)
                    else -> context.getString(R.string.error_unknown_status, error.code())
                }
            }
            is PatchmonApiException -> {
                when (error.kind) {
                    PatchmonApiException.Kind.BAD_REQUEST -> context.getString(R.string.patchmon_error_bad_request)
                    PatchmonApiException.Kind.INVALID_HOST_ID -> context.getString(R.string.patchmon_error_invalid_host_id)
                    PatchmonApiException.Kind.DELETE_CONSTRAINT -> context.getString(R.string.patchmon_error_delete_constraint)
                    PatchmonApiException.Kind.INVALID_CREDENTIALS -> context.getString(R.string.patchmon_error_invalid_credentials)
                    PatchmonApiException.Kind.IP_NOT_ALLOWED -> context.getString(R.string.patchmon_error_ip_not_allowed)
                    PatchmonApiException.Kind.ACCESS_DENIED -> context.getString(R.string.patchmon_error_access_denied)
                    PatchmonApiException.Kind.FORBIDDEN -> context.getString(R.string.patchmon_error_forbidden)
                    PatchmonApiException.Kind.HOST_NOT_FOUND -> context.getString(R.string.patchmon_error_host_not_found)
                    PatchmonApiException.Kind.NOT_FOUND -> context.getString(R.string.patchmon_error_not_found)
                    PatchmonApiException.Kind.RATE_LIMITED -> context.getString(R.string.patchmon_error_rate_limited)
                    PatchmonApiException.Kind.SERVER_ERROR -> context.getString(R.string.patchmon_error_server)
                    PatchmonApiException.Kind.CONNECTION_ERROR -> context.getString(R.string.patchmon_error_connection)
                }
            }
            is IllegalStateException -> {
                val msg = error.message.orEmpty()
                when {
                    msg == "Healthchecks authentication failed" ||
                    msg == "Linux Update authentication failed" ||
                    msg == "Pangolin authentication failed" ||
                    msg.startsWith("401:") || msg.startsWith("403:") ->
                        context.getString(R.string.error_invalid_credentials)
                    else -> error.localizedMessage ?: context.getString(R.string.error_unknown)
                }
            }
            else -> error?.localizedMessage ?: context.getString(R.string.error_unknown)
        }
    }
}
