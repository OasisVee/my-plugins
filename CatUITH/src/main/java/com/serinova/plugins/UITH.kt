package com.serinova.plugins

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap

import com.aliucord.Http
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.entities.Plugin
import com.aliucord.annotations.AliucordPlugin

import com.aliucord.patcher.before
import com.aliucord.utils.GsonUtils
import com.aliucord.api.CommandsAPI.CommandResult
import com.discord.api.commands.ApplicationCommandType
import com.discord.widgets.chat.MessageContent
import com.discord.widgets.chat.MessageManager
import com.discord.widgets.chat.input.ChatInputViewModel
import com.lytefast.flexinput.model.Attachment

import com.google.gson.JsonSyntaxException
import org.json.JSONException
import org.json.JSONObject

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.IndexOutOfBoundsException
import java.util.regex.Pattern

private fun newUpload(file: File, data: Config, log: Logger, userhash: String? = null, timeout: Long = 200_000): String {
    val lock = Object()
    val result = StringBuilder()

    synchronized(lock) {
        Utils.threadPool.execute {
            try {
                val params = mutableMapOf<String, Any>()
                val resp = Http.Request("${data.RequestURL}", "POST")

                if (data.Headers != null) {
                    for ((k, v) in data.Headers!!.entries) {
                        resp.setHeader(k, v)
                    }
                }

                if (data.Arguments != null) {
                    for ((k, v) in data.Arguments!!.entries) {
                        params[k] = v
                    }
                }
                
                // Add userhash if provided and the host is catbox
                if (!userhash.isNullOrEmpty() && data.Name == "catbox.moe") {
                    params["userhash"] = userhash
                }
                
                params["${data.FileFormName}"] = file
                result.append(resp.executeWithMultipartForm(params).text())
            } catch (ex: Throwable) {
                if (ex is IOException) {
                    log.debug("${ex.message} | ${ex.cause} | $ex | ${ex.printStackTrace()}")
                }
                log.error(ex)
            } finally {
                synchronized(lock) {
                    lock.notifyAll()
                }
            }
        }
        lock.wait(timeout)
    }
    try {
        log.debug("JSON FORMATTED:\n${JSONObject(result.toString()).toString(4)}")
        log.debug("API RAW RESPONSE:\n${result.toString()}")
    } catch (e: JSONException) {
        log.debug("API RESPONSE:\n${result.toString()}")
    }
    return result.toString()
}

@AliucordPlugin
class CatUITH : Plugin() {

    init {
        settingsTab = SettingsTab(PluginSettings::class.java).withArgs(settings)
    }

    private val LOG = Logger("CatUITH")

    // Default Catbox Configuration
    private val DEFAULT_CATBOX_CONFIG = Config(
        Name = "catbox.moe",
        DestinationType = "ImageUploader",
        RequestURL = "https://catbox.moe/user/api.php",
        FileFormName = "fileToUpload",
        Headers = null,
        Arguments = mapOf("reqtype" to "fileupload"),
        ResponseType = null,
        URL = null
    )

    // source: https://github.com/TymanWasTaken/aliucord-plugins/blob/main/EncryptDMs/src/main/kotlin/tech/tyman/plugins/encryptdms/EncryptDMs.kt#L321-L326
    private val textContentField = MessageContent::class.java.getDeclaredField("textContent").apply { isAccessible = true }
    private fun MessageContent.set(text: String) = textContentField.set(this, text)

    // compile regex before uploading to speed up process
    private var re = try {
        settings.getString("regex", "https:\\/\\/files\\.catbox\\.moe\\/[\\w.-]*").toRegex().toString()
    } catch (e: Throwable) {
        LOG.error(e)
    }
    private val pattern = Pattern.compile(re.toString())

    // Get file size in MB
    private fun getFileSizeMB(inputStream: InputStream?): Double {
        return try {
            if (inputStream != null) {
                val bytes = inputStream.available()
                bytes / (1024.0 * 1024.0) // Convert bytes to MB
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    override fun start(ctx: Context) {
        val args = listOf(
            Utils.createCommandOption(
                ApplicationCommandType.SUBCOMMAND,
                "add",
                "Add json config",
                subCommandOptions = listOf(
                    Utils.createCommandOption(
                        ApplicationCommandType.STRING,
                        "json",
                        "Add json config (paste the contents)",
                        required = true
                    )
                )
            ),
            Utils.createCommandOption(
                ApplicationCommandType.SUBCOMMAND,
                "current",
                "View current UITH settings"
            ),
            Utils.createCommandOption(
                ApplicationCommandType.SUBCOMMAND,
                "disable",
                "Disable plugin",
                subCommandOptions = listOf(
                    Utils.createCommandOption(
                        ApplicationCommandType.BOOLEAN,
                        "disable",
                        required = true
                    )
                )
            )
        )

        commands.registerCommand("cuith", "(Catbox) Upload Image To Host", args) {
            if (it.containsArg("add")) {
                val config = try {
                    GsonUtils.fromJson(it.getSubCommandArgs("add")?.get("json").toString(), Config::class.java)
                } catch (ex: JsonSyntaxException) {
                    return@registerCommand CommandResult("Invalid json file data provided", null, false)
                }
                if (config?.RequestURL.isNullOrEmpty()) {
                    return@registerCommand CommandResult("\"RequestURL\" must not be empty!", null, false)
                }
                if (config?.FileFormName.isNullOrEmpty()) {
                    return@registerCommand CommandResult("\"FileFormName\" must not be empty!", null, false)
                }
                LOG.debug(config.toString())
                settings.setString("jsonConfig", it.getSubCommandArgs("add")?.get("json").toString())

                return@registerCommand CommandResult("Set data successfully", null, false)
            }

            if (it.containsArg("current")) {
                val configData = settings.getString("jsonConfig", 
                    GsonUtils.toJson(DEFAULT_CATBOX_CONFIG)
                )
                val configRegex = settings.getString("regex", null)
                val catboxUserhash = settings.getString("catboxUserhash", "")
                val userhashDisplay = if (catboxUserhash.isNullOrEmpty()) "Not set (anonymous uploads)" else "Set"
                val settingsUploadAllAttachments = settings.getBool("uploadAllAttachments", false)
                val settingsPluginOff = settings.getBool("pluginOff", false)
                val settingsTimeout = settings.getString("timeout", "200")
                val sb = StringBuilder()
                sb.append("json config:```\n$configData\n```\n\n")
                sb.append("regex:```\n$configRegex\n```\n\n")
                sb.append("catbox userhash: `$userhashDisplay`\n")
                sb.append("uploadAllAttachments: `$settingsUploadAllAttachments`\n")
                sb.append("pluginOff: `$settingsPluginOff`\n")
                sb.append("timeout: `$settingsTimeout` seconds")
                return@registerCommand CommandResult(sb.toString(), null, false)
            }

            if (it.containsArg("disable")) {
                val set = it.getSubCommandArgs("disable")?.get("disable").toString()
                if (set.lowercase() == "true") settings.setString("pluginOff", set)
                if (set.lowercase() == "false") settings.setString("pluginOff", set)
                return@registerCommand CommandResult(
                    "Plugin Disabled: ${settings.getString("pluginOff", false.toString())}",
                    null,
                    false
                )
            }

            CommandResult("", null, false)
        }

        patcher.before<ChatInputViewModel>(
            "sendMessage",
            Context::class.java,
            MessageManager::class.java,
            MessageContent::class.java,
            List::class.java,
            Boolean::class.javaPrimitiveType!!,
            Function1::class.java
        ) {
            val context = it.args[0] as Context
            val content = it.args[2] as MessageContent
            val plainText = content.textContent
            val attachments = (it.args[3] as List<Attachment<*>>).toMutableList()
    
            // Check if plugin is OFF
            if (settings.getBool("pluginOff", false)) { return@before }
    
            // Check if there are any attachments
            if (attachments.isEmpty()) { return@before }

            // Get config, use default Catbox config if not set
            val sxcuConfig = settings.getString("jsonConfig", 
                GsonUtils.toJson(DEFAULT_CATBOX_CONFIG)
            )
            val configData = GsonUtils.fromJson(sxcuConfig, Config::class.java)
            
            // Get catbox userhash if available
            val catboxUserhash = settings.getString("catboxUserhash", "")
            
            // Get timeout setting and convert to milliseconds
            val timeoutSeconds = settings.getString("timeout", "200").toLong()
            val timeoutMillis = timeoutSeconds * 1000
            
            // Check file sizes and display appropriate toast
            var largestFileSizeMB = 0.0
            for (attachment in attachments) {
                try {
                    val inputStream = context.contentResolver.openInputStream(attachment.uri)
                    val fileSizeMB = getFileSizeMB(inputStream)
                    inputStream?.close()
                    
                    if (fileSizeMB > largestFileSizeMB) {
                        largestFileSizeMB = fileSizeMB
                    }
                } catch (e: Exception) {
                    LOG.debug("Failed to get file size: ${e.message}")
                }
            }
            
            // Show toast based on file size
            if (largestFileSizeMB > 25) {
                Utils.showToast("Uploading large file (${String.format("%.1f", largestFileSizeMB)} MB) to catbox.moe. This may take several minutes, please wait...", false)
            } else if (largestFileSizeMB > 10) {
                Utils.showToast("Uploading file (${String.format("%.1f", largestFileSizeMB)} MB) to catbox.moe. This might take a while, please wait...", false)
            } else {
                Utils.showToast("Uploading to catbox.moe...", false)
            }
    
            // Process all attachments
            val uploadedUrls = mutableListOf<String>()
    
            for (attachment in attachments) {
                // Check file type for each attachment
                val mimeType = context.getContentResolver().getType(attachment.uri)
                val mime = if (mimeType != null) {
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                } else {
                    attachment.uri.toString().substringAfterLast('.', "")
                }
                
                // Check if we should process this file type
                if (mime !in arrayOf("png", "jpg", "jpeg", "webp", "gif", "mp4")) {
                    if (!settings.getBool("uploadAllAttachments", false)) {
                        continue
                    }
                }

                try {
                    // Create a temp file from the attachment URI
                    val tempFile = File.createTempFile("uith_", ".${mime ?: "tmp"}")
                    tempFile.deleteOnExit()
                    
                    // Copy the content from the URI to the temp file
                    val inputStream = context.contentResolver.openInputStream(attachment.uri)
                    if (inputStream != null) {
                        FileOutputStream(tempFile).use { output ->
                            inputStream.copyTo(output)
                            inputStream.close()
                        }
                        
                        // Now upload the temp file with userhash if available and applicable
                        val json = if (catboxUserhash.isNullOrEmpty() || configData.Name != "catbox.moe") {
                            newUpload(tempFile, configData, LOG, timeout = timeoutMillis)
                        } else {
                            newUpload(tempFile, configData, LOG, catboxUserhash, timeout = timeoutMillis)
                        }
                
                        // match URL from regex
                        val matcher = pattern.matcher(json)
                        if (matcher.find()) {
                            uploadedUrls.add(matcher.group())
                        }
                        
                        // Try to delete the temp file
                        try {
                            tempFile.delete()
                        } catch (e: Exception) {
                            LOG.debug("Failed to delete temp file: ${e.message}")
                        }
                    }
                } catch (ex: Throwable) {
                    LOG.error(ex)
                    Utils.showToast("UITH: Failed to upload one or more files", true)
                    continue
                }
            }

            if (uploadedUrls.isNotEmpty()) {
                // Join all URLs with newlines and add to the message
                content.set("$plainText\n${uploadedUrls.joinToString("\n")}")
                it.args[2] = content
                it.args[3] = emptyList<Attachment<*>>()
                
                // Show success toast after upload completes
                Utils.showToast("Upload completed successfully!", false)
            }
    
            return@before
        }
    }

    override fun stop(ctx: Context) {
        patcher.unpatchAll()
        commands.unregisterAll()
    }
}
