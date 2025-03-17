/*
 * Wolf's Aliucord Plugins
 * Copyright (C) 2021 Wolfkid200444
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.aliucord.plugins

import android.content.Context
import android.net.Uri
import com.aliucord.Http
import com.aliucord.Main
import com.aliucord.Utils.createCommandOption
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI.CommandResult
import com.aliucord.entities.CommandContext
import com.aliucord.entities.Plugin
import com.discord.api.commands.ApplicationCommandType
import com.discord.utilities.icon.IconUtils
import java.io.File
import java.io.FileOutputStream

@AliucordPlugin
class PetPet : Plugin() {
    override fun start(context: Context) {
        val arguments = listOf(
            createCommandOption(
                ApplicationCommandType.USER,
                "name",
                "The user to pet",
                null,
                true,
                false
            )
        )
        commands.registerCommand(
            "petpet",
            "pet someone",
            arguments
        ) { ctx: CommandContext ->
            val user = ctx.getRequiredUser("name")
            val avatar = IconUtils.getForUser(user)
            var file: File? = null
            try {
                file = imageToDataUri(avatar, context)
                if (file != null) {
                    ctx.addAttachment(Uri.fromFile(file).toString(), "petpet.gif")
                    return@registerCommand CommandResult("")
                } else {
                    return@registerCommand CommandResult("Failed to generate petpet image")
                }
            } catch (e: Throwable) {
                Main.logger.error(e)
                return@registerCommand CommandResult("Error: ${e.message ?: "Unknown error occurred"}")
            }
        }
    }

    @Throws(Throwable::class)
    private fun imageToDataUri(avatar: String, mContext: Context): File? {
        try {
            val res = Http.Request(url + avatar.replace("webp", "png"))
                .connect()
                .execute()
            
            if (!res.ok()) {
                Main.logger.error("API request failed with status code: ${res.statusCode}")
                return null
            }
            
            val f = File.createTempFile("temp", ".gif", mContext.cacheDir)
            FileOutputStream(f).use { fos -> res.pipe(fos) }
            f.deleteOnExit()
            return f
        } catch (e: Http.HttpException) {
            Main.logger.error("HTTP Exception: ${e.message}")
            throw e
        } catch (e: Exception) {
            Main.logger.error("Exception while fetching image: ${e.message}")
            return null
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
    }

    companion object {
        // The original API is down, you might want to use an alternative service
        private const val url = "https://api.obamabot.me/v1/image/petpet?avatar="
        
        // Fallback URL if you want to try an alternative service
        // private const val url = "https://some-alternative-petpet-api.com/generate?avatar="
    }
}
