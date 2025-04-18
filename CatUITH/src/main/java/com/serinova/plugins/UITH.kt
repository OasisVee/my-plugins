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
private fun createAlbum(title: String, desc: String, files: List<String>, userhash: String?, log: Logger, timeout: Long = 200_000): String {
    val lock = Object()
    val result = StringBuilder()

    synchronized(lock) {
        Utils.threadPool.execute {
            try {
                val params = mutableMapOf<String, Any>()
                val resp = Http.Request("https://catbox.moe/user/api.php", "POST")
                params["reqtype"] = "createalbum"
                params["title"] = title
                params["desc"] = desc
                params["files"] = files.joinToString(" ")
                if (!userhash.isNullOrEmpty()) {
                    params["userhash"] = userhash
                }
                
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
private fun addToAlbum(short: String, files: List<String>, userhash: String?, log: Logger, timeout: Long = 200_000): String {
    val lock = Object()
    val result = StringBuilder()

    synchronized(lock) {
        Utils.threadPool.execute {
            try {
                val params = mutableMapOf<String, Any>()
                val resp = Http.Request("https://catbox.moe/user/api.php", "POST")
                params["reqtype"] = "addtoalbum"
                params["short"] = short
                params["files"] = files.joinToString(" ")
                if (!userhash.isNullOrEmpty()) {
                    params["userhash"] = userhash
                } else {
                    log.debug("Warning: Attempting to modify an album without a userhash")
                }
                
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
    
    log.debug("API RESPONSE (Add to album):\n${result.toString()}")
    return result.toString()
}

@AliucordPlugin
class CatUITH : Plugin() {

    init {
        settingsTab = SettingsTab(PluginSettings::class.java).withArgs(settings)
    }

    private val LOG = Logger("CatUITH")
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
    private var albumMode = false
    private var pendingAlbumTitle = ""
    private var pendingAlbumDesc = ""
    private var pendingAlbumShort = ""
    private var collectedFiles = mutableListOf<String>()
    private val textContentField = MessageContent::class.java.getDeclaredField("textContent").apply { isAccessible = true }
    private fun MessageContent.set(text: String) = textContentField.set(this, text)
    private var re = try {
        settings.getString("regex", "https:\\/\\/files\\.catbox\\.moe\\/[\\w.-]*").toRegex().toString()
    } catch (e: Throwable) {
        LOG.error(e)
    }
    private val pattern = Pattern.compile(re.toString())
    private val albumPattern = Pattern.compile("https:\\/\\/catbox\\.moe\\/c\\/[\\w]{6}")
    private fun getFileSizeMB(inputStream: InputStream?): Double {
        return try {
            if (inputStream != null) {
                val bytes = inputStream.available()
                bytes / (1024.0 * 1024.0)
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
    private fun extractFilename(url: String): String {
        return url.substringAfterLast("/", "")
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
            ),
            Utils.createCommandOption(
                ApplicationCommandType.SUBCOMMAND,
                "album",
                "Create a catbox album",
                subCommandOptions = listOf(
                    Utils.createCommandOption(
                        ApplicationCommandType.STRING,
                        "title",
                        "Album title",
                        required = true
                    ),
                    Utils.createCommandOption(
                        ApplicationCommandType.STRING,
                        "description",
                        "Album description",
                        required = false
                    )
                )
            ),
            Utils.createCommandOption(
                ApplicationCommandType.SUBCOMMAND,
                "cancelalb",
                "Cancel album creation mode"
            ),
            Utils.createCommandOption(
                ApplicationCommandType.SUBCOMMAND,
                "finishalb",
                "Finish album creation and get link"
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

            if (it.containsArg("album")) {
                val catboxUserhash = settings.getString("catboxUserhash", "")
                if (catboxUserhash.isNullOrEmpty()) {
                    return@registerCommand CommandResult(
                        "⚠️ Warning: No userhash set. Creating anonymous album. You won't be able to edit it later.\n" +
                        "Set your userhash in settings if you want to manage this album in the future.",
                        null,
                        false
                    )
                }
                val albumTitle = it.getSubCommandArgs("album")?.get("title").toString()
                val albumDesc = it.getSubCommandArgs("album")?.get("description")?.toString() ?: ""
                albumMode = true
                pendingAlbumTitle = albumTitle
                pendingAlbumDesc = albumDesc
                collectedFiles.clear()
                
                return@registerCommand CommandResult(
                    "✅ Album Mode activated!\n\n" +
                    "Title: **$albumTitle**\n" +
                    "Description: ${if (albumDesc.isEmpty()) "*None*" else albumDesc}\n\n" +
                    "Now send your files to add them to the album. " +
                    "When you're done, type `/cuith finishalb` to get the album link.\n\n" +
                    "(To cancel album creation, use `/cuith cancelalb`)",
                    null,
                    false
                )
            }

            if (it.containsArg("cancelalb")) {
                if (!albumMode) {
                    return@registerCommand CommandResult("❌ No active album creation to cancel.", null, false)
                }
                albumMode = false
                pendingAlbumTitle = ""
                pendingAlbumDesc = ""
                pendingAlbumShort = ""
                collectedFiles.clear()
                
                return@registerCommand CommandResult("✅ Album creation cancelled.", null, false)
            }

            if (it.containsArg("finishalb")) {
                if (!albumMode || collectedFiles.isEmpty()) {
                    return@registerCommand CommandResult(
                        "❌ No active album with files to finalize. Start album creation with `/cuith album`.", 
                        null, 
                        false
                    )
                }
                val catboxUserhash = settings.getString("catboxUserhash", "")
                val timeoutSeconds = settings.getString("timeout", "200").toLong()
                val timeoutMillis = timeoutSeconds * 1000
                
                Utils.showToast("Creating album... Please wait", false)
                val response = createAlbum(
                    pendingAlbumTitle,
                    pendingAlbumDesc,
                    collectedFiles,
                    catboxUserhash,
                    LOG,
                    timeoutMillis
                )
                val albumMatcher = albumPattern.matcher(response)
                val result = if (albumMatcher.find()) {
                    val albumUrl = albumMatcher.group()
                    pendingAlbumShort = albumUrl.substringAfterLast("/", "")
                    
                    "✅ Album created successfully!\n\n" +
                    "Title: **${pendingAlbumTitle}**\n" +
                    "Files: ${collectedFiles.size}\n" +
                    "Album URL: $albumUrl"
                } else {
                    "❌ Failed to create album. Server response: $response"
                }
                albumMode = false
                pendingAlbumTitle = ""
                pendingAlbumDesc = ""
                collectedFiles.clear()
                
                return@registerCommand CommandResult(result, null, false)
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
            val sxcuConfig = settings.getString("jsonConfig", 
                GsonUtils.toJson(DEFAULT_CATBOX_CONFIG)
            )
            val configData = GsonUtils.fromJson(sxcuConfig, Config::class.java)
            val catboxUserhash = settings.getString("catboxUserhash", "")
            val timeoutSeconds = settings.getString("timeout", "200").toLong()
            val timeoutMillis = timeoutSeconds * 1000
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
            if (albumMode) {
                Utils.showToast("Uploading files to catbox.moe for album...", false)
            } else if (largestFileSizeMB > 25) {
                Utils.showToast("Uploading large file (${String.format("%.1f", largestFileSizeMB)} MB) to catbox.moe. This may take several minutes, please wait...", false)
            } else if (largestFileSizeMB > 10) {
                Utils.showToast("Uploading file (${String.format("%.1f", largestFileSizeMB)} MB) to catbox.moe. This might take a while, please wait...", false)
            } else {
                Utils.showToast("Uploading to catbox.moe...", false)
            }
            val uploadedUrls = mutableListOf<String>()
    
            for (attachment in attachments) {
                val mimeType = context.getContentResolver().getType(attachment.uri)
                val mime = if (mimeType != null) {
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                } else {
                    attachment.uri.toString().substringAfterLast('.', "")
                }
                if (mime !in arrayOf("png", "jpg", "jpeg", "webp", "gif", "mp4")) {
                    if (!settings.getBool("uploadAllAttachments", false)) {
                        continue
                    }
                }

                try {
                    val tempFile = File.createTempFile("uith_", ".${mime ?: "tmp"}")
                    tempFile.deleteOnExit()
                    val inputStream = context.contentResolver.openInputStream(attachment.uri)
                    if (inputStream != null) {
                        FileOutputStream(tempFile).use { output ->
                            inputStream.copyTo(output)
                            inputStream.close()
                        }
                        val json = if (catboxUserhash.isNullOrEmpty() || configData.Name != "catbox.moe") {
                            newUpload(tempFile, configData, LOG, timeout = timeoutMillis)
                        } else {
                            newUpload(tempFile, configData, LOG, catboxUserhash, timeout = timeoutMillis)
                        }
                        val matcher = pattern.matcher(json)
                        if (matcher.find()) {
                            val fileUrl = matcher.group()
                            uploadedUrls.add(fileUrl)
                            if (albumMode) {
                                val filename = extractFilename(fileUrl)
                                if (filename.isNotEmpty()) {
                                    collectedFiles.add(filename)
                                }
                            }
                        }
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
                if (albumMode) {
                    content.set("")
                    it.result = null
                    it.args[3] = emptyList<Attachment<*>>()
        
                    Utils.showToast("${uploadedUrls.size} file(s) uploaded and ready for album **$pendingAlbumTitle**. Use /cuith finishalb when done to create the album.", false)
                    return@before
                } else {
                    content.set("$plainText\n${uploadedUrls.joinToString("\n")}")
                    Utils.showToast("Upload completed successfully!", false)
        
                    it.args[2] = content
                    it.args[3] = emptyList<Attachment<*>>()
                }
            }
            return@before
        }
    }

    override fun stop(ctx: Context) {
        patcher.unpatchAll()
        commands.unregisterAll()
    }
}