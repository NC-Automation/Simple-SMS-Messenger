package com.ncautomation.messages.dialogs

import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.ncautomation.commons.extensions.*
import com.ncautomation.commons.helpers.ensureBackgroundThread
import com.ncautomation.messages.R
import com.ncautomation.messages.activities.SimpleActivity
import com.ncautomation.messages.databinding.DialogImportMessagesBinding
import com.ncautomation.messages.extensions.config
import com.ncautomation.messages.helpers.MessagesImporter
import com.ncautomation.messages.models.ImportResult

class ImportMessagesDialog(
    private val activity: SimpleActivity,
    private val uri: Uri,
) {

    private val config = activity.config
    private lateinit var importer: MessagesImporter
    private var binding: DialogImportMessagesBinding
    private lateinit var alert: AlertDialog
    public var isCanceled: Boolean = false

    init {
        var ignoreClicks = false
        var importComplete = false
        binding = DialogImportMessagesBinding.inflate(activity.layoutInflater).apply {
            importSmsCheckbox.isChecked = config.importSms
            importMmsCheckbox.isChecked = config.importMms
        }
        var dlg = this
        activity.getAlertDialogBuilder()
            .setPositiveButton(com.ncautomation.commons.R.string.ok, null)
            .setNegativeButton(com.ncautomation.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.import_messages) { alertDialog ->
                    importer = MessagesImporter(activity, dlg)
                    alert = alertDialog
                    alertDialog.setCanceledOnTouchOutside(false)
                    alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        isCanceled = true
                        alertDialog.dismiss()
                    }
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (importComplete){
                            alertDialog.dismiss()
                            return@setOnClickListener
                        }
                        if (ignoreClicks) {
                            return@setOnClickListener
                        }

                        if (!binding.importSmsCheckbox.isChecked && !binding.importMmsCheckbox.isChecked) {
                            activity.toast(R.string.no_option_selected)
                            return@setOnClickListener
                        }
                        ignoreClicks = true

                        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).beGone()
                        config.importSms = binding.importSmsCheckbox.isChecked
                        config.importMms = binding.importMmsCheckbox.isChecked
                        binding.importSmsCheckbox.beGone()
                        binding.importMmsCheckbox.beGone()
                        binding.importStatus1.text = "Reading messages from file."
                        binding.importStatus1.beVisible()
                        binding.importStatus1.setTextColor(activity.getProperTextColor())
                        binding.importStatus2.setTextColor(activity.getProperTextColor())
                        binding.importProgressbar.beVisible()

                        ensureBackgroundThread {
                            importer.importMessages(uri)
                            importComplete = true
                            activity.runOnUiThread {
                                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).beVisible()
                                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).beGone()
                                binding.importProgressbar.beGone()
                            }
                        }
                    }
                }
            }
    }

    fun setStatus(status1:String, status2: String = ""){
        activity.runOnUiThread {
            binding.importStatus1.beGoneIf(status1.isNullOrEmpty())
            binding.importStatus1.text = status1
            binding.importStatus2.beGoneIf(status2.isNullOrEmpty())
            binding.importStatus2.text = status2
        }
    }

    fun setProgress(progress:Int, max: Int) {
        activity.runOnUiThread {
            binding.importProgressbar.isIndeterminate = false
            binding.importProgressbar.progress = progress
            binding.importProgressbar.max = max
        }
    }

    private fun handleParseResult(result: ImportResult) {
        activity.toast(
            when (result) {
                ImportResult.IMPORT_OK -> com.ncautomation.commons.R.string.importing_successful
                ImportResult.IMPORT_PARTIAL -> com.ncautomation.commons.R.string.importing_some_entries_failed
                ImportResult.IMPORT_FAIL -> com.ncautomation.commons.R.string.importing_failed
                else -> com.ncautomation.commons.R.string.no_items_found
            }
        )
    }
}
