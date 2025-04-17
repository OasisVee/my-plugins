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

// Function to create an album
private fun createAlbum(title: String, desc: String, files: List<String>, userhash: String?, log: Logger, timeout: Long = 200_000): String {
    val lock = Object()
    val result = StringBuilder()

    synchronized(lock) {
        Utils.threadPool.execute {
            try {
                val params = mutableMapOf<String, Any>()
                val resp = Http.Request("https://catbox.moe/user/api.php", "POST")
                
                // Set required parameters
                params["reqtype"] = "createalbum"
                params["title"] = title
                params["desc"] = desc
                params["files"] = files.joinToString(" ")
                
                // Add userhash if provided
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

// Function to add files to an existing album
private fun addToAlbum(short: String, files: List<String>, userhash: String?, log: Logger, timeout: Long = 200_000): String {
    val lock = Object()
    val result = StringBuilder()

    synchronized(lock) {
        Utils.threadPool.execute {
            try {
                val params = mutableMapOf<String, Any>()
                val resp = Http.Request("https://catbox.moe/user/api.php", "POST")
                
                // Set required parameters
                params["reqtype"] = "addtoalbum"
                params["short"] = short
                params["files"] = files.joinToString(" ")
                
                // Add userhash - required for album editing
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

    // Album Mode State
    private var albumMode = false
    private var pendingAlbumTitle = ""
    private var pendingAlbumDesc = ""
    private var pendingAlbumShort = ""
    private var collectedFiles = mutableListOf<String>()

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

    // Regex for album URL
    private val albumPattern = Pattern.compile("https:\\/\\/catbox\\.moe\\/c\\/[\\w]{6}")

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

    // Extract filename from URL
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
                // Get the userhash for album creation
                val catboxUserhash = settings.getString("catboxUserhash", "")
                
                // Check if userhash is set for non-anonymous albums
                if (catboxUserhash.isNullOrEmpty()) {
                    return@registerCommand CommandResult(
                        "⚠️ Warning: No userhash set. Creating anonymous album. You won't be able to edit it later.\n" +
                        "Set your userhash in settings if you want to manage this album in the future.",
                        null,
                        false
                    )
                }
                
                // Get album details from command
                val albumTitle = it.getSubCommandArgs("album")?.get("title").toString()
                val albumDesc = it.getSubCommandArgs("album")?.get("description")?.toString() ?: ""
                
                // Set album mode
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
                
                // Reset album mode
                albumMode = false
                pendingAlbumTitle = ""
                pendingAlbumDesc = ""
                pendingAlbumShort = ""
                collectedFiles.clear()
                
                return@registerCommand CommandResult("✅ Album creation cancelled.", null, false)
            }

            CommandResult("", null, false)
        }

        // Register the finishalb command separately as it needs special handling
        commands.registerCommand(
            "finishalb",
            "Finish album creation and get link",
            emptyList()
        ) {
            if (!albumMode || collectedFiles.isEmpty()) {
                return@registerCommand CommandResult(
                    "❌ No active album with files to finalize. Start album creation with `/cuith album`.", 
                    null, 
                    false
                )
            }
            
            // Get the userhash for album creation
            val catboxUserhash = settings.getString("catboxUserhash", "")
            val timeoutSeconds = settings.getString("timeout", "200").toLong()
            val timeoutMillis = timeoutSeconds * 1000
            
            Utils.showToast("Creating album... Please wait", false)
            
            // Create the album with collected files
            val response = createAlbum(
                pendingAlbumTitle,
                pendingAlbumDesc,
                collectedFiles,
                catboxUserhash,
                LOG,
                timeoutMillis
            )
            
            // Try to extract album URL
            val albumMatcher = albumPattern.matcher(response)
            val result = if (albumMatcher.find()) {
                val albumUrl = albumMatcher.group()
                
                // Extract album short code for potential future use
                pendingAlbumShort = albumUrl.substringAfterLast("/", "")
                
                "✅ Album created successfully!\n\n" +
                "Title: **${pendingAlbumTitle}**\n" +
                "Files: ${collectedFiles.size}\n" +
                "Album URL: $albumUrl"
            } else {
                "❌ Failed to create album. Server response: $response"
            }
            
            // Reset album mode
            albumMode = false
            pendingAlbumTitle = ""
            pendingAlbumDesc = ""
            collectedFiles.clear()
            
            return@registerCommand CommandResult(result, null, false)
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
            
            // Show toast based on file size and mode
            if (albumMode) {
                Utils.showToast("Uploading files to catbox.moe for album...", false)
            } else if (largestFileSizeMB > 25) {
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
                            val fileUrl = matcher.group()
                            uploadedUrls.add(fileUrl)
                            
                            // If in album mode, add the filename to collected files
                            if (albumMode) {
                                val filename = extractFilename(fileUrl)
                                if (filename.isNotEmpty()) {
                                    collectedFiles.add(filename)
                                }
                            }
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
                // If in album mode, show only toast notification and cancel message send
                if (albumMode) {
                    Utils.showToast("${uploadedUrls.size} file(s) uploaded and ready for album **$pendingAlbumTitle**. Use /finishalb when done to create the album.", false)
                    // Cancel the message send operation entirely to prevent message from being sent to channel
                    it.result = null
                } else {
                    // Normal mode - Join all URLs with newlines and add to the message
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