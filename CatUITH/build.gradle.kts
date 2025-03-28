version = "1.1.1"
description = "Upload images to catbox.moe directly from Discord."
aliucord.author("scruz", 794527403580981248L)
aliucord.changelog.set(
        """
        # 1.1.1
        * made catbox json default
        * added a userhash option in settings for catbox
        
        # 1.1.0
        *  made catbox the default regex
        *  added gif and mp4 as part of default
        
        # 1.0.0
        * Initial release of catbox.moe version
        * Simplified plugin that uploads directly to catbox.moe
        * No sxcu configuration needed
        """.trimIndent()
)

aliucord.excludeFromUpdaterJson.set(true)
