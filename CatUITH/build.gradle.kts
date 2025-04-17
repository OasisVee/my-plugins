version = "1.2.2"
description = "Upload images to catbox.moe directly from Discord. (bigger files can take upwards of minutes depending on your internet speed)"
aliucord.author("scruz", 794527403580981248L)
aliucord.changelog.set(
    """
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