package com.serinova.plugins

import android.view.View
import android.text.Editable
import android.widget.TextView
import android.annotation.SuppressLint
import android.text.util.Linkify

import com.aliucord.Utils
import com.aliucord.views.Button
import com.aliucord.views.TextInput
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils
import com.aliucord.views.Divider

import com.lytefast.flexinput.R
import com.discord.utilities.color.ColorCompat
import com.discord.utilities.view.text.TextWatcher
import com.discord.views.CheckedSetting

class PixeldrainSettings(private val settings: SettingsAPI) : SettingsPage() {

    @SuppressLint("SetTextI18n")
    override fun onViewBound(view: View?) {
        super.onViewBound(view)
        setActionBarTitle("PixelUITH")
        val ctx = requireContext()
        val p = DimenUtils.defaultPadding

        val errorField = TextView(ctx).apply {
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.colorError))
            setPadding(p, p, p, p)
        }

        // HEADER
        TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Header).apply {
            text = "Pixeldrain API Settings"
            addView(this)
        }

        // TEXT
        TextView(ctx).apply {
            text = "Enter your Pixeldrain API key. Get one from: https://pixeldrain.com/user/api_keys"
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.colorOnPrimary))
            setPadding(p, p, p, p)
            addView(this)
        }

        // API KEY INPUT BOX
        val apiKeyInput = TextInput(ctx, "API Key")
        apiKeyInput.apply {
            val currentKey = settings.getString("pixeldrainApiKey", "")
            editText.setText(if (currentKey.isNotEmpty()) "${currentKey.take(8)}..." else "")
            editText.addTextChangedListener(object : TextWatcher() {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {}
            })
        }

        // SAVE BUTTON
        val saveButton = Button(ctx).apply {
            text = "Save API Key"
            setOnClickListener {
                val inputText = apiKeyInput.editText.text.toString()
                if (inputText.isNotBlank() && !inputText.endsWith("...")) {
                    settings.setString("pixeldrainApiKey", inputText)
                    Utils.showToast("API Key Saved!")
                    apiKeyInput.editText.setText("${inputText.take(8)}...")
                } else if (inputText.isBlank()) {
                    errorField.text = "API Key cannot be empty"
                } else {
                    Utils.showToast("API Key already saved!")
                }
            }
        }

        // CLEAR BUTTON
        val clearButton = Button(ctx).apply {
            text = "Clear API Key"
            setOnClickListener {
                settings.setString("pixeldrainApiKey", "")
                apiKeyInput.editText.setText("")
                Utils.showToast("API Key Cleared!")
            }
        }

        // DIV
        val divider = Divider(ctx).apply { setPadding(p, p, p, p) }

        // ADVANCED SETTINGS HEADER
        val advHeader = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Header).apply { 
            text = "Upload Settings" 
        }

        // CHECKED SETTINGS
        val uploadAllAttachments = Utils.createCheckedSetting(
            ctx, CheckedSetting.ViewType.CHECK,
            "Upload all attachment types", 
            "Try to upload all attachment types instead of just images and videos.\n(Warning: May fail for some file types)"
        ).apply {
            isChecked = settings.getBool("uploadAllAttachments", false)
            setOnCheckedListener {
                settings.setBool("uploadAllAttachments", it)
            }
        }
        
        val switchOffPlugin = Utils.createCheckedSetting(
            ctx, CheckedSetting.ViewType.CHECK,
            "Disable PixelUITH", 
            "Disable this plugin to send attachments normally.\nSlash command available: \"/puith disable\""
        ).apply {
            isChecked = settings.getBool("pluginOff", false)
            setOnCheckedListener {
                settings.setBool("pluginOff", it)
            }
        }

        // 2nd DIV
        val secondDivider = Divider(ctx).apply { setPadding(p, p, p, p) }

        // INFO HEADER
        val infoHeader = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Header).apply { 
            text = "Information" 
        }

        // HELP/INFO
        val helpInfo = TextView(ctx).apply {
            linksClickable = true
            text = """How to use:
1. Get an API key from Pixeldrain: https://pixeldrain.com/user/api_keys
2. Enter your API key above and click "Save API Key"
3. Send messages with attachments - they'll be automatically uploaded to Pixeldrain!

Features:
• Supports images, videos, and other file types
• Files are uploaded to your Pixeldrain account
• Original attachments are replaced with Pixeldrain links
• Use /puith commands to manage settings

Links:
- Support Server: https://discord.gg/tdjBfsvhHT
- Pixeldrain: https://pixeldrain.com/
- Get API Key: https://pixeldrain.com/user/api_keys"""
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.colorOnPrimary))
            setPadding(p, p, p, p)
        }
        Linkify.addLinks(helpInfo, Linkify.WEB_URLS)

        addView(apiKeyInput)
        addView(saveButton)
        addView(clearButton)
        addView(errorField)

        addView(divider)

        addView(advHeader)
        addView(uploadAllAttachments)
        addView(switchOffPlugin)

        addView(secondDivider)
        addView(infoHeader)
        addView(helpInfo)
    }
}