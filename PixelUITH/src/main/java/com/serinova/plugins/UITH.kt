package com.serinova.plugins

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import android.util.Base64

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
data class PixeldrainResponse(
    val success: Boolean?,
    val id: String?,
    val message: String?,
    val value: String?
)

private fun uploadToPixeldrain(file: File, apiKey: String, log: Logger): String? {
    val lock = Object()
    val result = StringBuilder()
    var fileId: String? = null

    synchronized(lock) {
        Utils.threadPool.execute {
            try {
                val credentials = ":$apiKey"
                val encodedCredentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                
                val params = mutableMapOf<String, Any>()
                val resp = Http.Request("https://pixeldrain.com/api/file", "POST")
                resp.setHeader("Authorization", "Basic $encodedCredentials")
                params["file"] = file
                params["name"] = file.name
                
                val response = resp.executeWithMultipartForm(params).text()
                result.append(response)
                try {
                    val pixeldrainResponse = GsonUtils.fromJson(response, PixeldrainResponse::class.java)
                    if (pixeldrainResponse?.success == true && pixeldrainResponse.id != null) {
                        fileId = pixeldrainResponse.id
                        log.debug("Upload successful, file ID: ${pixeldrainResponse.id}")
                    }
                    log.debug("Pixeldrain API Response: $response")
                } catch (e: JsonSyntaxException) {
                    log.error("Failed to parse Pixeldrain response: $response", e)
                }
                
            } catch (ex: Throwable) {
                if (ex is IOException) {
                    log.debug("IOException occurred: ${ex.message}")
                }
                log.error("Upload error", ex)
            } finally {
                synchronized(lock) {
                    lock.notifyAll()
                }
            }
        }
        lock.wait(15_000)
    }
    
    return fileId
}

@AliucordPlugin
class PixelUITH : Plugin() {

    init {
        settingsTab = SettingsTab(PixeldrainSettings::class.java).withArgs(settings)
    }

    private val LOG = Logger("PixelUITH")
    private val textContentField = MessageContent::class.java.getDeclaredField("textContent").apply { isAccessible = true }
    private fun MessageContent.set(text: String) = textContentField.set(this, text)

    override fun start(ctx: Context) {

        val args = listOf(
            Utils.createCommandOption(
                ApplicationCommandType.SUBCOMMAND, "apikey", "Set Pixeldrain API key",
                subCommandOptions = listOf(
                    Utils.createCommandOption(
                        ApplicationCommandType.STRING,
                        "key",
                        "Your Pixeldrain API key",
                        required = true
                    )
                )
            ),
            Utils.createCommandOption(
                ApplicationCommandType.SUBCOMMAND, "current", "View current PixelUITH settings"
            ),
            Utils.createCommandOption(
                ApplicationCommandType.SUBCOMMAND, "disable", "Disable plugin",
                subCommandOptions = listOf(
                    Utils.createCommandOption(
                        ApplicationCommandType.BOOLEAN,
                        "disable",
                        required = true
                    )
                )
            )
        )
        
        commands.registerCommand("puith", "Upload Image To Pixeldrain", args) {
            if (it.containsArg("apikey")) {
                val apiKey = it.getSubCommandArgs("apikey")?.get("key").toString()
                if (apiKey.isBlank() || apiKey == "null") {
                    return@registerCommand CommandResult("API key cannot be empty!", null, false)
                }
                
                settings.setString("pixeldrainApiKey", apiKey)
                return@registerCommand CommandResult("Pixeldrain API key set successfully!", null, false)
            }

            if (it.containsArg("current")) {
                val apiKey = settings.getString("pixeldrainApiKey", null)
                val settingsUploadAllAttachments = settings.getBool("uploadAllAttachments", false)
                val settingsPluginOff = settings.getBool("pluginOff", false)
                val sb = StringBuilder()
                sb.append("Pixeldrain API Key: `${if (apiKey != null) "Set (${apiKey.take(8)}...)" else "Not Set"}`\n")
                sb.append("uploadAllAttachments: `$settingsUploadAllAttachments`\n")
                sb.append("pluginOff: `$settingsPluginOff`")
                return@registerCommand CommandResult(sb.toString(), null, false)
            }

            if (it.containsArg("disable")) {
                val set = it.getSubCommandArgs("disable")?.get("disable").toString()
                if (set.lowercase() == "true") settings.setBool("pluginOff", true)
                if (set.lowercase() == "false") settings.setBool("pluginOff", false)
                return@registerCommand CommandResult(
                    "Plugin Disabled: ${settings.getBool("pluginOff", false)}", null, false
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
            if (settings.getBool("pluginOff", false)) { return@before }
            if (attachments.isEmpty()) { return@before }
            val apiKey = settings.getString("pixeldrainApiKey", null)
            if (apiKey == null) {
                LOG.debug("Pixeldrain API key not provided, skipping upload...")
                Utils.showToast("PixelUITH: Please set your Pixeldrain API key using /puith apikey", true)
                return@before
            }
            val uploadedUrls = mutableListOf<String>()
            Utils.showToast("PixelUITH: Uploading ${attachments.size} file(s) to Pixeldrain...", false)
            
            for (attachment in attachments) {
                val mimeType = context.getContentResolver().getType(attachment.uri)
                val mime = if (mimeType != null) {
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                } else {
                    attachment.uri.toString().substringAfterLast('.', "")
                }
                if (mime !in arrayOf("png", "jpg", "jpeg", "webp", "gif", "mp4", "webm")) {
                    if (!settings.getBool("uploadAllAttachments", false)) {
                        continue
                    }
                }
                
                try {
                    val fileName = attachment.displayName ?: "file.${mime ?: "tmp"}"
                    val tempFile = File.createTempFile("puith_", "_$fileName")
                    tempFile.deleteOnExit()
                    val inputStream = context.contentResolver.openInputStream(attachment.uri)
                    if (inputStream != null) {
                        FileOutputStream(tempFile).use { output ->
                            inputStream.copyTo(output)
                            inputStream.close()
                        }
                        val fileId = uploadToPixeldrain(tempFile, apiKey, LOG)
                        
                        if (fileId != null) {
                            uploadedUrls.add("https://pixeldrain.com/u/$fileId")
                        } else {
                            Utils.showToast("PixelUITH: Failed to upload ${fileName}", true)
                        }
                        try {
                            tempFile.delete()
                        } catch (e: Exception) {
                            LOG.debug("Failed to delete temp file: ${e.message}")
                        }
                    }
                } catch (ex: Throwable) {
                    LOG.error("Failed to upload file", ex)
                    Utils.showToast("PixelUITH: Failed to upload one or more files", true)
                    continue
                }
            }
            
            if (uploadedUrls.isNotEmpty()) {
                content.set("$plainText\n${uploadedUrls.joinToString("\n")}")
                it.args[2] = content
                it.args[3] = emptyList<Attachment<*>>()
                Utils.showToast("PixelUITH: Upload completed successfully!", false)
            } else {
                Utils.showToast("PixelUITH: No files were uploaded", true)
            }
            
            return@before
        }
    }

    override fun stop(ctx: Context) {
        patcher.unpatchAll()
        commands.unregisterAll()
    }
}