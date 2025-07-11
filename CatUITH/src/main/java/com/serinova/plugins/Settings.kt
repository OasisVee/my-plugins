package com.serinova.plugins

import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.widget.TextView
import android.annotation.SuppressLint
import android.text.util.Linkify
import android.graphics.Typeface
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ArrayAdapter
import android.widget.Spinner

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
    val base = 0xFF1A1A1A.toInt() // Deep dark background, slightly warmer than pure black
    val text = 0xFFF0B3C9.toInt() // Soft pink for main text (cat nose color)
    val subtext = 0xFF8A5B6E.toInt() // Muted mauve for secondary text (cat fur tone)
    val lavender = 0xFFFFD1DC.toInt() // Pastel pink for headers/highlights (soft cat-like color)
    val green = 0xFF8A5B6E.toInt() // Muted mauve for save buttons
    val peach = 0xFFF0B3C9.toInt() // Soft pink for links/warnings
    val red = 0xFFFF9CAF.toInt() // Soft reddish pink for error/reset buttons
    val blue = 0xFF8A5B6E.toInt() // Muted mauve for secondary buttons
    val surface0 = 0xFF2C2C2C.toInt() // Slightly lighter dark background for surfaces
  }
  @SuppressLint("SetTextI18n")
  override fun onViewBound(view: View?) {
    super.onViewBound(view)
    setActionBarTitle("CatUITH")
    val ctx = requireContext()
    val p = DimenUtils.defaultPadding
    view?.setBackgroundColor(colors.base)
    val mainLayout = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
      setPadding(p, p, p, p)
    }
    fun createSectionHeader(text: String): TextView = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Header).apply {
      this.text = text
      setTextColor(colors.lavender)
      typeface = Typeface.DEFAULT_BOLD
      setPadding(0, p * 2, 0, p)
    }
    fun createDescriptionText(text: String): TextView = TextView(ctx).apply {
      this.text = text
      setTextColor(colors.text)
      setPadding(p, p, p, p)
    }
    mainLayout.addView(createSectionHeader("Upload Service"))
    mainLayout.addView(createDescriptionText("Choose between Catbox (permanent) or Litterbox (temporary) uploads"))
    val serviceSelection = Utils.createCheckedSetting(
      ctx, CheckedSetting.ViewType.CHECK,
      "Use Litterbox (temporary files)",
      "Upload to Litterbox instead of Catbox. Files will be automatically deleted after the specified time."
    ).apply {
      isChecked = settings.getBool("useLitterbox", false)
      setOnCheckedListener {
        settings.setBool("useLitterbox", it)
      }
    }
    mainLayout.addView(serviceSelection)
    mainLayout.addView(createDescriptionText("Litterbox file retention time (only applies when using Litterbox)"))

    val timeOptions = arrayOf("1h", "12h", "24h", "72h")
    val timeLabels = arrayOf("1 Hour", "12 Hours", "24 Hours", "72 Hours")

    val timeSpinner = Spinner(ctx).apply {
      val customAdapter = object : ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, timeLabels) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
          val view = super.getView(position, convertView, parent) as TextView
          view.setTextColor(colors.text)
          view.setBackgroundColor(colors.surface0)
          return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
          val view = super.getDropDownView(position, convertView, parent) as TextView
          view.setTextColor(colors.text)
          view.setBackgroundColor(colors.surface0)
          return view
        }
      }

      customAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
      adapter = customAdapter

      val currentTime = settings.getString("litterboxTime", "12h")
      val currentIndex = timeOptions.indexOf(currentTime)
      if (currentIndex >= 0) setSelection(currentIndex)
    }
    mainLayout.addView(timeSpinner)
    val serviceButton = Button(ctx).apply {
      text = "Save Service Settings"
      setTextColor(colors.base)
      setBackgroundColor(colors.green)
      setOnClickListener {
        val selectedTime = timeOptions[timeSpinner.selectedItemPosition]
        settings.setString("litterboxTime", selectedTime)
        Utils.showToast("Service settings saved")
      }
    }
    mainLayout.addView(serviceButton)
    mainLayout.addView(Divider(ctx).apply {
      setBackgroundColor(colors.surface0)
      setPadding(p, p, p, p)
    })
    mainLayout.addView(createSectionHeader("Catbox Settings"))
    mainLayout.addView(createDescriptionText("Enter your catbox.moe userhash (leave empty for anonymous uploads)"))
    val userhashInput = TextInput(ctx, "Userhash").apply {
      editText.setText(settings.getString("catboxUserhash", ""))
      editText.setTextColor(colors.text)
      editText.setHintTextColor(colors.subtext)
    }
    mainLayout.addView(userhashInput)
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
    mainLayout.addView(createDescriptionText("Your userhash is required for creating editable albums. Without it, albums will be anonymous and cannot be modified later.\n\nNote: Album features are only available with Catbox, not Litterbox."))
    mainLayout.addView(Divider(ctx).apply {
      setBackgroundColor(colors.surface0)
      setPadding(p, p, p, p)
    })
    mainLayout.addView(createSectionHeader("Upload History Settings"))
    mainLayout.addView(createDescriptionText("Configure how many uploaded files to remember"))
    val maxHistoryInput = TextInput(ctx, "Max History Size").apply {
      editText.setText(settings.getInt("maxHistorySize", 50).toString())
      editText.setTextColor(colors.text)
      editText.setHintTextColor(colors.subtext)
    }
    mainLayout.addView(maxHistoryInput)
    val maxHistoryButton = Button(ctx).apply {
      text = "Save"
      setTextColor(colors.base)
      setBackgroundColor(colors.blue)
      setOnClickListener {
        try {
          val maxSize = maxHistoryInput.editText.text.toString().toInt()
          if (maxSize < 0) {
            Utils.showToast("History size cannot be negative")
          } else {
            settings.setInt("maxHistorySize", maxSize)
            Utils.showToast("Saved max history size")
          }
        } catch (e: NumberFormatException) {
          Utils.showToast("Please enter a valid number")
        }
      }
    }
    mainLayout.addView(maxHistoryButton)
    mainLayout.addView(createDescriptionText("Set to 0 to disable upload history. Use /cuith history to view recent uploads."))
    val clearHistoryButton = Button(ctx).apply {
      text = "Clear Upload History"
      setTextColor(colors.base)
      setBackgroundColor(colors.red)
      setOnClickListener {
        settings.setString("uploadHistory", "[]")
        Utils.showToast("Upload history cleared")
      }
    }
    mainLayout.addView(clearHistoryButton)
    mainLayout.addView(Divider(ctx).apply {
      setBackgroundColor(colors.surface0)
      setPadding(p, p, p, p)
    })
    mainLayout.addView(createSectionHeader("Advanced Settings"))
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
    mainLayout.addView(createSectionHeader("Timeout Settings"))
    mainLayout.addView(createDescriptionText("Enter the timeout duration in seconds"))

    val timeoutInput = TextInput(ctx, "Timeout").apply {
      editText.setText(settings.getString("timeout", "200"))
      editText.setTextColor(colors.text)
      editText.setHintTextColor(colors.subtext)
    }
    mainLayout.addView(timeoutInput)
    val timeoutButton = Button(ctx).apply {
      text = "Save"
      setTextColor(colors.base)
      setBackgroundColor(colors.blue)
      setOnClickListener {
        settings.setString("timeout", timeoutInput.editText.text.toString())
        Utils.showToast("Saved")
      }
    }
    mainLayout.addView(timeoutButton)
    mainLayout.addView(createSectionHeader("Album Settings"))
    mainLayout.addView(createDescriptionText("Configure album creation defaults (Catbox only)"))
    val albumDescInput = TextInput(ctx, "Default Album Description").apply {
      editText.setText(settings.getString("defaultAlbumDesc", "Created with CatUITH"))
      editText.setTextColor(colors.text)
      editText.setHintTextColor(colors.subtext)
    }
    mainLayout.addView(albumDescInput)
    val albumDescButton = Button(ctx).apply {
      text = "Save"
      setTextColor(colors.base)
      setBackgroundColor(colors.blue)
      setOnClickListener {
        settings.setString("defaultAlbumDesc", albumDescInput.editText.text.toString())
        Utils.showToast("Saved default album description")
      }
    }
    mainLayout.addView(albumDescButton)
    val autoFinishAlbum = Utils.createCheckedSetting(
      ctx, CheckedSetting.ViewType.CHECK,
      "Auto-finish albums",
      "Automatically create album after files are uploaded (instead of waiting for /finishalb command)"
    ).apply {
      isChecked = settings.getBool("autoFinishAlbum", false)
      setOnCheckedListener {
        settings.setBool("autoFinishAlbum", it)
      }
    }
    mainLayout.addView(autoFinishAlbum)
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
    mainLayout.addView(Divider(ctx).apply {
      setBackgroundColor(colors.surface0)
      setPadding(p, p, p, p)
    })
    mainLayout.addView(createSectionHeader("Service Information"))

    val serviceInfoText = TextView(ctx).apply {
      text = "• Catbox: Permanent file hosting with optional user accounts\n" +
      "• Litterbox: Temporary file hosting (files auto-delete)\n" +
      "• Album features only work with Catbox\n" +
      "• Litterbox is anonymous only (no user accounts)"
      setTextColor(colors.text)
      setPadding(p, p, p, p)
    }
    mainLayout.addView(serviceInfoText)
    mainLayout.addView(createSectionHeader("Album Feature Help"))

    val albumHelpText = TextView(ctx).apply {
      text = "Album Commands (Catbox only):\n" +
      "• /cuith album <title> [description] - Start creating an album\n" +
      "• /cuith finishalb - Complete album creation and get link\n" +
      "• /cuith cancelalb - Cancel album creation\n\n" +
      "Note: A catbox.moe userhash is required for editable albums.\n" +
      "Anonymous albums cannot be modified after creation."
      setTextColor(colors.text)
      setPadding(p, p, p, p)
    }
    mainLayout.addView(albumHelpText)
    mainLayout.addView(createSectionHeader("Upload History Help"))

    val historyHelpText = TextView(ctx).apply {
      text = "Upload History Commands:\n" +
      "• /cuith history - View recent upload history\n" +
      "• /cuith clearhistory - Clear upload history\n\n" +
      "Upload history tracks your recent file uploads and allows you\n" +
      "to easily re-share them without re-uploading."
      setTextColor(colors.text)
      setPadding(p, p, p, p)
    }
    mainLayout.addView(historyHelpText)
    mainLayout.addView(createSectionHeader("Links"))
    val helpInfo = TextView(ctx).apply {
      linksClickable = true
      text = "- UITH README: https://git.io/JSyri\n" +
      "- Catbox.moe: https://catbox.moe\n" +
      "- Litterbox.catbox.moe: https://litterbox.catbox.moe"
      setTextColor(colors.peach)
    }
    Linkify.addLinks(helpInfo, Linkify.WEB_URLS)
    mainLayout.addView(helpInfo)
    addView(mainLayout)
  }
}