version = "1.3.0"
description = "Upload images to catbox.moe directly from Discord. (bigger files can take upwards of minutes depending on your internet speed)"
aliucord.author("scruz", 794527403580981248L)
aliucord.changelog.set(
    """
    # 1.3.0
    * added upload history, use /cuith history to see your history from this update, files uploaded before this update won't be seen since it stores it locally
    * added max history amount in settings and also added /cuith clearhistory and a clear history button in settings

    # 1.2.3
    * added finishalb to cuith as a subcommand
    [BUGS]
    * doesnt clear the message content when in album mode (even though it realistically should)

    # 1.2.0/1.2.2
    * added album functionality
    * added commands "/cuith album", "/cuith cancelalb", "/finishalb" (to be changed)
    * fixed the breaking of changelog (stupid whitespaces) [1.2.2]

    # 1.1.2/3
    * increased time wait to 3.3 minutes (200k milliseconds)
    * added a toast before upload stating it might take a while (toast changes depending on the size)
    * added a setting to change the default timeout

    # 1.1.1
    * made catbox json default
    * added a userhash option in settings for catbox

    # 1.1.0
    * made catbox the default regex
    * added gif and mp4 as part of default

    # 1.0.0
    * Initial release of catbox.moe version
    * Simplified plugin that uploads directly to catbox.moe
    * No sxcu configuration needed
    """.trimIndent()
)
