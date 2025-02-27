package com.ncautomation.messages.messaging

import android.content.Context
import android.telephony.SmsMessage
import android.widget.Toast.LENGTH_LONG
import com.klinker.android.send_message.Settings
import com.ncautomation.commons.extensions.showErrorToast
import com.ncautomation.commons.extensions.toast
import com.ncautomation.messages.R
import com.ncautomation.messages.extensions.config
import com.ncautomation.messages.extensions.messagingUtils
import com.ncautomation.messages.helpers.SEND_TYPE_DEFAULT
import com.ncautomation.messages.helpers.SEND_TYPE_MMS
import com.ncautomation.messages.helpers.SEND_TYPE_SMS
import com.ncautomation.messages.messaging.SmsException.Companion.EMPTY_DESTINATION_ADDRESS
import com.ncautomation.messages.messaging.SmsException.Companion.ERROR_PERSISTING_MESSAGE
import com.ncautomation.messages.messaging.SmsException.Companion.ERROR_SENDING_MESSAGE
import com.ncautomation.messages.models.Attachment
import kotlinx.coroutines.selects.select

@Deprecated("TODO: Move/rewrite messaging config code into the app.")
fun Context.getSendMessageSettings(): Settings {
    val settings = Settings()
    settings.useSystemSending = true
    settings.deliveryReports = config.enableDeliveryReports
    settings.sendLongAsMms = config.sendLongMessageMMS
    settings.sendLongAsMmsAfter = 1
    settings.group = config.sendGroupMessageMMS
    if (config.useSignature) settings.signature = config.messageSignature
    return settings
}

fun Context.isLongMmsMessage(text: String, settings: Settings = getSendMessageSettings()): Boolean {
    val data = SmsMessage.calculateLength(text, false)
    val numPages = data.first()
    return numPages > settings.sendLongAsMmsAfter && settings.sendLongAsMms
}

/** Sends the message using the in-app SmsManager API wrappers if it's an SMS or using android-smsmms for MMS. */
fun Context.sendMessageCompat(text: String, addresses: List<String>, subId: Int?, attachments: List<Attachment>, messageId: Long? = null, subject: String? = null, groupSendType: Int? = SEND_TYPE_DEFAULT) {
    val settings = getSendMessageSettings()

    if (subId != null) {
        settings.subscriptionId = subId
    }
    var textWithSignature = text
    if (!settings.signature.isNullOrEmpty()) textWithSignature = text + "\n" + settings.signature

    val messagingUtils = messagingUtils
    var groupIsMms = when {
        groupSendType == SEND_TYPE_DEFAULT -> settings.group
        else -> groupSendType == SEND_TYPE_MMS
    }
    settings.group = groupIsMms
    val isMms = attachments.isNotEmpty() || isLongMmsMessage(textWithSignature, settings) || addresses.size > 1 && groupIsMms
    //we are going to send all messages with an email destination as mms.
    val isEmailDest = addresses.any { it.contains("@") }
    if (isMms || isEmailDest) {
        // we send all MMS attachments separately to reduces the chances of hitting provider MMS limit.
        if (attachments.isNotEmpty()) {
            val lastIndex = attachments.lastIndex
            if (attachments.size > 1) {
                for (i in 0 until lastIndex) {
                    val attachment = attachments[i]
                    messagingUtils.sendMmsMessage("", addresses, attachment, settings, messageId)
                }
            }

            val lastAttachment = attachments[lastIndex]
            messagingUtils.sendMmsMessage(textWithSignature, addresses, lastAttachment, settings, messageId, subject)
        } else {
            messagingUtils.sendMmsMessage(textWithSignature, addresses, null, settings, messageId, subject)
        }
    } else {
        try {

            messagingUtils.sendSmsMessage(textWithSignature, addresses.toSet(), settings.subscriptionId, settings.deliveryReports, messageId)
        } catch (e: SmsException) {
            when (e.errorCode) {
                EMPTY_DESTINATION_ADDRESS -> toast(id = R.string.empty_destination_address, length = LENGTH_LONG)
                ERROR_PERSISTING_MESSAGE -> toast(id = R.string.unable_to_save_message, length = LENGTH_LONG)
                ERROR_SENDING_MESSAGE -> toast(
                    msg = getString(R.string.unknown_error_occurred_sending_message, e.errorCode),
                    length = LENGTH_LONG
                )
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

/**
 * Check if a given "address" is a short code.
 * There's not much info available on these special numbers, even the wikipedia page (https://en.wikipedia.org/wiki/Short_code)
 * contains outdated information regarding max number of digits. The exact parameters for short codes can vary by country and by carrier.
 *
 * This function simply returns true if the [address] contains at least one letter.
 */
fun isShortCodeWithLetters(address: String): Boolean {
    return address.any { it.isLetter() }
}
