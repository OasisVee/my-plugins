package com.serinova.plugins

import android.content.Context

import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.Logger
import com.aliucord.entities.Plugin
import com.aliucord.Constants.ALIUCORD_GUILD_ID
import com.aliucord.entities.MessageEmbedBuilder
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.Utils.createCommandChoice
import com.aliucord.api.CommandsAPI.CommandResult

import com.discord.api.commands.ApplicationCommandType
import com.discord.api.message.embed.MessageEmbed

class Result(val url: String)
class WaifuApiResponse(val url: String)

private fun buildEmbeds(urls: MutableList<String>, text: String? = null): List<MessageEmbed> {
    val embedList = mutableListOf<MessageEmbed>()
    for (url in urls) {
        val embed = MessageEmbedBuilder().setImage(url).setRandomColor()
        if (text != null) { embed.apply { embed.setFooter(text) } }
        embedList.add(embed.build())
    }
    return embedList
}

private fun makeReq(endpoint: String, category: String, count: Long): List<String> {
    val urls = mutableListOf<String>()
    for (i in 0 until count) {
        Thread.sleep(2_000)
        val result = Http.simpleJsonGet("https://api.waifu.pics/$endpoint/$category", WaifuApiResponse::class.java)
        urls.add(result.url)
    }
    return urls
}

@AliucordPlugin
class AnimeImageFetch : Plugin() {

    private val LOG = Logger("AnimeImageFetch")

    override fun start(ctx: Context) {
        // SFW categories
        val sfwChoices = listOf(
            createCommandChoice("Waifu [SFW]", "waifu"),
            createCommandChoice("Neko [SFW]", "neko"),
            createCommandChoice("Shinobu [SFW]", "shinobu"),
            createCommandChoice("Megumin [SFW]", "megumin"),
            createCommandChoice("Bully [SFW]", "bully"),
            createCommandChoice("Cuddle [SFW]", "cuddle"),
            createCommandChoice("Cry [SFW]", "cry"),
            createCommandChoice("Hug [SFW]", "hug"),
            createCommandChoice("Awoo [SFW]", "awoo"),
            createCommandChoice("Kiss [SFW]", "kiss"),
            createCommandChoice("Lick [SFW]", "lick"),
            createCommandChoice("Pat [SFW]", "pat"),
            createCommandChoice("Smug [SFW]", "smug"),
            createCommandChoice("Bonk [SFW]", "bonk"),
            createCommandChoice("Yeet [SFW]", "yeet"),
            createCommandChoice("Blush [SFW]", "blush"),
            createCommandChoice("Smile [SFW]", "smile"),
            createCommandChoice("Wave [SFW]", "wave"),
            createCommandChoice("Highfive [SFW]", "highfive"),
            createCommandChoice("Handhold [SFW]", "handhold"),
            createCommandChoice("Nom [SFW]", "nom"),
            createCommandChoice("Bite [SFW]", "bite"),
            createCommandChoice("Glomp [SFW]", "glomp"),
            createCommandChoice("Slap [SFW]", "slap"),
            createCommandChoice("Kill [SFW]", "kill"),
            createCommandChoice("Kick [SFW]", "kick"),
            createCommandChoice("Happy [SFW]", "happy"),
            createCommandChoice("Wink [SFW]", "wink"),
            createCommandChoice("Poke [SFW]", "poke"),
            createCommandChoice("Dance [SFW]", "dance"),
            createCommandChoice("Cringe [SFW]", "cringe")
        )
        
        // NSFW categories
        val nsfwChoices = listOf(
            createCommandChoice("Waifu [NSFW]", "nsfw_waifu"),
            createCommandChoice("Neko [NSFW]", "nsfw_neko"),
            createCommandChoice("Trap [NSFW]", "nsfw_trap"),
            createCommandChoice("Blowjob [NSFW]", "nsfw_blowjob")
        )
        
        // Combine all choices
        val allChoices = sfwChoices + nsfwChoices
        
        val limitChoices = listOf(
            createCommandChoice("2", "2"),
            createCommandChoice("5", "5"),
            createCommandChoice("8", "8"),
            createCommandChoice("10", "10")
        )

        val args = listOf(
            Utils.createCommandOption(
                ApplicationCommandType.STRING,
                "category",
                "Category of image/gif to get (SFW or NSFW marked)",
                required = true,
                choices = allChoices
            ),
            Utils.createCommandOption(
                ApplicationCommandType.BOOLEAN,
                "send",
                "Send to chat (WARNING: Use NSFW channel for NSFW content)"
            ),
            Utils.createCommandOption(
                ApplicationCommandType.STRING,
                "limit",
                "The limit of results to get. Default (1)",
                choices = limitChoices
            )
        )
        
        commands.registerCommand(
            "animefetch",
            "Get anime images/gifs from waifu.pics API (includes NSFW options)",
            args
        ) { ctx ->
            val chosenCategory = ctx.getRequiredString("category")
            val send = ctx.getBoolOrDefault("send", false)
            val limit = ctx.getLongOrDefault("limit", 1)
            
            // Determine if this is an NSFW request and extract the actual category
            val isNsfw = chosenCategory.startsWith("nsfw_")
            val endpoint = if (isNsfw) "nsfw" else "sfw"
            val category = if (isNsfw) chosenCategory.substring(5) else chosenCategory
            
            try {
                val urls = makeReq(endpoint, category, limit)
                
                if (ctx.currentChannel.guildId == ALIUCORD_GUILD_ID && send) {
                    val nsfw_warning = if (isNsfw) "NSFW content cannot be sent. " else ""
                    val embeds = buildEmbeds(urls as MutableList<String>, "${nsfw_warning}Won't send this in Aliucord server.")
                    return@registerCommand CommandResult(null, embeds, false, "AnimeImageFetch")
                } else {
                    if (send) {
                        val nsfw_warning = if (isNsfw) "**⚠️ NSFW CONTENT ⚠️**\n" else ""
                        CommandResult("${urls.joinToString("\n")}", null, true)
                    } else {
                        val embeds = buildEmbeds(urls as MutableList<String>, if (isNsfw) "⚠️ NSFW CONTENT" else null)
                        CommandResult(null, embeds, false, "AnimeImageFetch")
                    }
                }
            } catch (t: Throwable) {
                LOG.error(t)
                CommandResult("Oops, an error occurred. Check Debug Logs.",
                    null, false, "AnimeImageFetch")
            }
        }
    }

    override fun stop(ctx: Context) = commands.unregisterAll()
}
