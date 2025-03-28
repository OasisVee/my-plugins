package com.serinova.plugins

import android.view.View
import android.text.Editable
import android.widget.TextView
import android.annotation.SuppressLint
import android.text.util.Linkify
import android.graphics.Typeface
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout

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

class PluginSettings(private val settings: SettingsAPI) : SettingsPage() {

    // Cat-UITH Color Scheme (Dark Background)
    private val colors = object {
        val base = 0xFF1A1A1A.toInt()        // Deep dark background, slightly warmer than pure black
        val text = 0xFFF0B3C9.toInt()        // Soft pink for main text (cat nose color)
        val subtext = 0xFF8A5B6E.toInt()     // Muted mauve for secondary text (cat fur tone)
        val lavender = 0xFFFFD1DC.toInt()    // Pastel pink for headers/highlights (soft cat-like color)
        val green = 0xFF8A5B6E.toInt()       // Muted mauve for save buttons
        val peach = 0xFFF0B3C9.toInt()       // Soft pink for links/warnings
        val red = 0xFFFF9CAF.toInt()         // Soft reddish pink for error/reset buttons
        val blue = 0xFF8A5B6E.toInt()        // Muted mauve for secondary buttons
        val surface0 = 0xFF2C2C2C.toInt()    // Slightly lighter dark background for surfaces
    }
    @SuppressLint("SetTextI18n")
    override fun onViewBound(view: View?) {
        super.onViewBound(view)
        setActionBarTitle("Cat-UITH")
        val ctx = requireContext()
        val p = DimenUtils.defaultPadding

        // Main layout with Catppuccin Mocha background
        view?.setBackgroundColor(colors.base)

        // Create a vertical layout for better organization
        val mainLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setPadding(p, p, p, p)
        }

        // Section Header
        fun createSectionHeader(text: String): TextView = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Header).apply {
            this.text = text
            setTextColor(colors.lavender)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, p * 2, 0, p)
        }

        // Styled Text
        fun createDescriptionText(text: String): TextView = TextView(ctx).apply {
            this.text = text
            setTextColor(colors.text)
            setPadding(p, p, p, p)
        }

        // Regex Settings Section
        mainLayout.addView(createSectionHeader("Regex Settings"))
        mainLayout.addView(createDescriptionText("Enter the regex pattern of the URL to receive"))

        // Regex Input
        val input = TextInput(ctx, "Regex").apply {
            editText.setText(settings.getString("regex", "https:\\/\\/files\\.catbox\\.moe\\/[\\w.-]*"))
            editText.setTextColor(colors.text)
            editText.setHintTextColor(colors.subtext)
        }
        mainLayout.addView(input)

        // Save Button
        val button = Button(ctx).apply {
            text = "Save"
            setTextColor(colors.base)
            setBackgroundColor(colors.green)
            setOnClickListener {
                settings.setString("regex", input.editText.text.toString().toRegex().toString())
                Utils.showToast("Saved")
            }
        }
        mainLayout.addView(button)

        // Divider
        mainLayout.addView(Divider(ctx).apply { 
            setBackgroundColor(colors.surface0)
            setPadding(p, p, p, p) 
        })

        // Catbox Settings Section
        mainLayout.addView(createSectionHeader("Catbox Settings"))
        mainLayout.addView(createDescriptionText("Enter your catbox.moe userhash (leave empty for anonymous uploads)"))

        // Userhash Input
        val userhashInput = TextInput(ctx, "Userhash").apply {
            editText.setText(settings.getString("catboxUserhash", ""))
            editText.setTextColor(colors.text)
            editText.setHintTextColor(colors.subtext)
        }
        mainLayout.addView(userhashInput)

        // Save Userhash Button
        val userhashButton = Button(ctx).apply {
            text = "Save"
            setTextColor(colors.base)
            setBackgroundColor(colors.blue)
            setOnClickListener {
                settings.setString("catboxUserhash", userhashInput.editText.text.toString())
                Utils.showToast("Saved")
            }
        }
        mainLayout.addView(userhashButton)

        // Divider
        mainLayout.addView(Divider(ctx).apply { 
            setBackgroundColor(colors.surface0)
            setPadding(p, p, p, p) 
        })

        // Advanced Settings Section
        mainLayout.addView(createSectionHeader("Advanced Settings"))

        // Checked Settings
        val uploadAllAttachments = Utils.createCheckedSetting(
            ctx, CheckedSetting.ViewType.CHECK,
            "Upload all attachment types", 
            "Try to upload all attachment types instead of just images.\n(Warning: Might error)"
        ).apply {
            isChecked = settings.getBool("uploadAllAttachments", false)
            setOnCheckedListener {
                settings.setBool("uploadAllAttachments", it)
            }
        }
        mainLayout.addView(uploadAllAttachments)

        val switchOffPlugin = Utils.createCheckedSetting(
            ctx, CheckedSetting.ViewType.CHECK,
            "Disable UITH", 
            "Disable this plugin to send attachments normally.\nSlash command available: \"/uith disable\""
        ).apply {
            isChecked = settings.getBool("pluginOff", false)
            setOnCheckedListener {
                settings.setBool("pluginOff", it)
            }
        }
        mainLayout.addView(switchOffPlugin)

        // Reset JSON Button
        val resetButton = Button(ctx).apply {
            text = "Reset JSON to Default"
            setTextColor(colors.base)
            setBackgroundColor(colors.red)
            setOnClickListener {
                settings.remove("jsonConfig")
                Utils.showToast("JSON configuration reset to default")
            }
        }
        mainLayout.addView(resetButton)

        // Divider
        mainLayout.addView(Divider(ctx).apply { 
            setBackgroundColor(colors.surface0)
            setPadding(p, p, p, p) 
        })

        // Links Section
        mainLayout.addView(createSectionHeader("Links"))

        // Help/Info
        val helpInfo = TextView(ctx).apply {
            linksClickable = true
            text = "- UITH README: https://git.io/JSyri"
            setTextColor(colors.peach)
        }
        Linkify.addLinks(helpInfo, Linkify.WEB_URLS)
        mainLayout.addView(helpInfo)

        // Add main layout to the view
        addView(mainLayout)
    }
}
