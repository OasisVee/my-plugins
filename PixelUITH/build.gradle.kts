version = "1.0.0"
description = "Upload files to Pixeldrain within Discord."
aliucord.author("scruz", 794527403580981248L)
aliucord.changelog.set(
        """
        1.0.0
        * Complete rewrite for Pixeldrain API integration
        * Added secure API key management
        * Improved file type support (images, videos, documents)
        * Better error handling and user feedback
        * Simplified settings interface
        * Updated slash commands (/puith apikey, /puith current, /puith disable)
        """.trimIndent()
)

aliucord.excludeFromUpdaterJson.set(false)
