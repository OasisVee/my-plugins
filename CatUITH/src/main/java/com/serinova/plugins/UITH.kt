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
import com.discord.api.premium.PremiumTier
import com.discord.models.user.User
import com.discord.stores.StoreStream
import com.discord.utilities.premium.PremiumUtils
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
    
    // Constants for Discord file size limits
    private val DEFAULT_MAX_FILE_SIZE_MB = 25
    private val NITRO_CLASSIC_MAX_FILE_SIZE_MB = 50
    private val NITRO_MAX_FILE_SIZE_MB = 500
    
    // --- UPDATED getFileSizeMB function (reads stream) ---
    private fun getFileSizeMB(context: Context, uri: Uri): Double {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                var size: Long = 0
                val buffer = ByteArray(1024) // Read in 1KB chunks
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    size += bytesRead
                }
                return size / (1024.0 * 1024.0)
            }
        } catch (e: Exception) {
            LOG.error("Failed to get file size for Uri: $uri", e)
        } finally {
            try {
                inputStream?.close()
            } catch (e: IOException) {
                LOG.error("Failed to close input stream", e)
            }
        }
        return 0.0 // Return 0.0 if size calculation fails
    }
    // --- END UPDATED getFileSizeMB function ---
    
    private fun extractFilename(url: String): String {
        return url.substringAfterLast("/", "")
    }
    
    // Get user's max file size based on Nitro status
    private fun getUserMaxFileSizeMB(): Int {
        val currentUser = StoreStream.getUsers().me
        if (currentUser == null) {
            return DEFAULT_MAX_FILE_SIZE_MB
        }
        
        return when (currentUser.getPremiumTier()) {
            PremiumTier.TIER_1 -> NITRO_CLASSIC_MAX_FILE_SIZE_MB // Nitro Classic
            PremiumTier.TIER_2 -> NITRO_MAX_FILE_SIZE_MB // Nitro
            else -> DEFAULT_MAX_FILE_SIZE_MB // No Nitro
        }
    }
    
    // Check if file should be uploaded to Catbox based on size and user settings
    private fun shouldUploadToCatbox(fileSizeMB: Double): Boolean {
        // Always upload in album mode
        if (albumMode) return true
        
        // If large files only setting is disabled, always upload to Catbox
        if (!settings.getBool("uploadLargeFilesOnly", false)) return true
        
        // Check if file size exceeds Discord's limit based on user's Nitro status
        val userMaxFileSizeMB = getUserMaxFileSizeMB()
        return fileSizeMB > userMaxFileSizeMB
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
                val settingsUploadLargeFilesOnly = settings.getBool("uploadLargeFilesOnly", false)
                val settingsPluginOff = settings.getBool("pluginOff", false)
                val settingsTimeout = settings.getString("timeout", "200")
                val userMaxFileSize = getUserMaxFileSizeMB()
                val sb = StringBuilder()
                sb.append("json config:```\n$configData\n```\n\n")
                sb.append("regex:```\n$configRegex\n```\n\n")
                sb.append("catbox userhash: `$userhashDisplay`\n")
                sb.append("uploadAllAttachments: `$settingsUploadAllAttachments`\n")
                sb.append("uploadLargeFilesOnly: `$settingsUploadLargeFilesOnly`\n")
                sb.append("pluginOff: `$settingsPluginOff`\n")
                sb.append("timeout: `$settingsTimeout` seconds\n")
                sb.append("Your max Discord upload limit: `$userMaxFileSize MB`")
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
                val albumDesc = it.getSubCommandArgs("album")?.get("description")?.toString() 
                    ?: settings.getString("defaultAlbumDesc", "Created with CatUITH")
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
            
            // Early return conditions
            if (settings.getBool("pluginOff", false)) { return@before }
            if (attachments.isEmpty()) { return@before }
            
            val sxcuConfig = settings.getString("jsonConfig", 
                GsonUtils.toJson(DEFAULT_CATBOX_CONFIG)
            )
            val configData = GsonUtils.fromJson(sxcuConfig, Config::class.java)
            val catboxUserhash = settings.getString("catboxUserhash", "")
            val timeoutSeconds = settings.getString("timeout", "200").toLong()
            val timeoutMillis = timeoutSeconds * 1000
            val uploadLargeFilesOnly = settings.getBool("uploadLargeFilesOnly", false)
            val userMaxFileSize = getUserMaxFileSizeMB()
            
            // For tracking files to upload to catbox and files to keep for Discord
            val filesToUploadToCatbox = mutableListOf<Attachment<*>>()
            val filesToKeepForDiscord = mutableListOf<Attachment<*>>()
            var largestFileSizeMB = 0.0 // Keep track of the largest file size overall
            
            // Analyze all attachments to decide which ones need to be uploaded to Catbox
            for (attachment in attachments) {
                try {
                    // Using the updated getFileSizeMB that reads the stream reliably
                    val fileSizeMB = getFileSizeMB(context, attachment.uri)
                    
                    if (fileSizeMB > largestFileSizeMB) {
                        largestFileSizeMB = fileSizeMB
                    }
                    
                    // Check MIME type restrictions unless uploadAllAttachments is true
                    val mimeType = context.getContentResolver().getType(attachment.uri)
                    val mime = if (mimeType != null) {
                        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    } else {
                        attachment.uri.toString().substringAfterLast('.', "")
                    }
                    
                    val isAllowedMimeType = mime in arrayOf("png", "jpg", "jpeg", "webp", "gif", "mp4") || 
                                           settings.getBool("uploadAllAttachments", false)
                    
                    // Decide if this file should be handled by the plugin (uploaded to Catbox)
                    if (isAllowedMimeType && (shouldUploadToCatbox(fileSizeMB) || albumMode)) {
                        filesToUploadToCatbox.add(attachment)
                    } else {
                        filesToKeepForDiscord.add(attachment) // Leave for Discord
                    }
                } catch (e: Exception) {
                    LOG.error("Failed to process attachment ${attachment.uri} for size/type check: ${e.message}", e)
                    // If processing an attachment fails, leave it for Discord as a fallback
                    filesToKeepForDiscord.add(attachment) 
                    continue // Move to the next attachment
                }
            }
            
            // If no files are marked for Catbox upload, let Discord handle the ones in filesToKeepForDiscord
            if (filesToUploadToCatbox.isEmpty()) {
                if (uploadLargeFilesOnly && !albumMode) {
                    Utils.showToast("All eligible files within Discord upload limits, uploading directly to Discord", false)
                } else if (!albumMode) {
                    // If uploadLargeFilesOnly is false, files were intended for Catbox but none were eligible/processed
                     Utils.showToast("No eligible files for Catbox upload. Sending via Discord.", false)
                }
                 // Ensure only files intended for Discord are passed
                it.args[3] = filesToKeepForDiscord
                
                // If filesToKeepForDiscord is also empty, cancel the message send entirely
                if (filesToKeepForDiscord.isEmpty()) {
                     content.set("")
                     it.result = null // Cancel message send if no content or attachments remain
                }
                return@before // Exit the patcher hook early
            }
            
            // Show appropriate toast while Catbox upload is happening
            if (albumMode) {
                Utils.showToast("Uploading files to catbox.moe for album...", false)
            } else {
                 val totalCatboxSizeMB = filesToUploadToCatbox.sumOf { getFileSizeMB(context, it.uri) }
                 Utils.showToast("Uploading ${filesToUploadToCatbox.size} file(s) (${String.format("%.1f", totalCatboxSizeMB)} MB total) to catbox.moe. Please wait...", false)
            }
            
            val uploadedUrls = mutableListOf<String>()
    
            // --- Perform Catbox Uploads for files in filesToUploadToCatbox ---
            for (attachment in filesToUploadToCatbox) {
                 // Re-get mime type here in case it was missed earlier or for clarity
                val mimeType = context.getContentResolver().getType(attachment.uri)
                val mime = if (mimeType != null) {
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                } else {
                    attachment.uri.toString().substringAfterLast('.', "")
                }

                try {
                    // Create a temporary file from the attachment URI to upload
                    val tempFile = File.createTempFile("uith_", ".${mime ?: "tmp"}")
                    tempFile.deleteOnExit() // Ensure temp file is deleted later

                    context.contentResolver.openInputStream(attachment.uri)?.use { inputStream ->
                         FileOutputStream(tempFile).use { output ->
                             inputStream.copyTo(output) // Copy stream to temp file
                         } // inputstream and outputstream are closed automatically by use{}

                         // Perform the upload using the temp file
                         val json = if (catboxUserhash.isNullOrEmpty() || configData.Name != "catbox.moe") {
                             newUpload(tempFile, configData, LOG, timeout = timeoutMillis)
                         } else {
                             newUpload(tempFile, configData, LOG, catboxUserhash, timeout = timeoutMillis)
                         }

                         // Parse the response to find the uploaded URL
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
                         } else {
                             LOG.debug("Catbox upload failed or URL not found for ${attachment.uri}. Response: $json")
                             Utils.showToast("UITH: Failed to upload a file to Catbox", true)
                             // If upload failed, this file's URL won't be added, effectively dropping it
                         }
                    } ?: run {
                         LOG.debug("Failed to open input stream for attachment ${attachment.uri} during upload prep.")
                         Utils.showToast("UITH: Failed to prepare a file for upload", true)
                    }

                    try {
                        tempFile.delete() // Clean up the temporary file
                    } catch (e: Exception) {
                        LOG.debug("Failed to delete temp file: ${e.message}")
                    }
                } catch (ex: Throwable) {
                    LOG.error("Catbox upload process threw an exception for ${attachment.uri}", ex)
                    Utils.showToast("UITH: Failed to process a file for upload", true)
                    // If an exception occurs during prep/upload, this file's URL won't be added
                }
            }
            // --- End Catbox Uploads ---


            // --- Logic based on successful Catbox uploads (mimics original blocking) ---
            if (uploadedUrls.isNotEmpty()) {
                if (albumMode) {
                    // In album mode, we don't send any message, just confirm files added to album list
                    content.set("") // Clear message content
                    it.result = null // Cancel the Discord message send entirely
                    it.args[3] = emptyList<Attachment<*>>() // Ensure no attachments go to Discord
        
                    Utils.showToast("${uploadedUrls.size} file(s) uploaded and ready for album **$pendingAlbumTitle**. Use /cuith finishalb when done to create the album.", false)
                    // Explicitly return to block Discord's default send
                    return@before 
                } else {
                    // Append uploaded URLs to message content
                    content.set("$plainText\n${uploadedUrls.joinToString("\n")}")
                    Utils.showToast("Catbox upload completed successfully!", false)
        
                    // --- BLOCK DISCORD ATTACHMENT HANDLING (Original Method) ---
                    LOG.debug("Attempting to block Discord attachment handling...") // <-- ADDED LOG
                    // Modify the arguments passed to Discord's sendMessage
                    it.args[2] = content // Pass the modified content
                    it.args[3] = emptyList<Attachment<*>>() // Pass an EMPTY list of attachments
                    // By passing an empty list, we prevent Discord from trying to upload anything itself.
                    
                    LOG.debug("it.args[3] set to empty list. Returning before.") // <-- ADDED LOG
                    // Explicitly return to block Discord's default send process for attachments
                    return@before 
                    // --- END BLOCK ---
                }
            } else {
                 // If filesToUploadToCatbox wasn't empty, but *none* of them successfully uploaded
                 LOG.debug("Attempted Catbox uploads for ${filesToUploadToCatbox.size} files, but no URLs were successfully retrieved. Leaving attachments for Discord.")
                 Utils.showToast("UITH: Failed to upload files to Catbox. Sending attachments via Discord.", false)
                 
                 // In this case, we leave the original list of attachments (minus any successfully uploaded)
                 // for Discord to handle as a fallback.
                 it.args[3] = filesToKeepForDiscord 
                 // No explicit return here, so Discord's default sendMessage runs with filesToKeepForDiscord
            }
        }
    }

    override fun stop(ctx: Context) {
        patcher.unpatchAll()
        commands.unregisterAll()
    }
}
