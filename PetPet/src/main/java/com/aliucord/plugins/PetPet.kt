package com.aliucord.plugins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import com.bumptech.glide.Glide
import com.aliucord.utils.GifEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import android.graphics.Matrix

@AliucordPlugin
class PetPet : Plugin() {
    companion object {
        private const val FRAMES = 10
        private const val DEFAULT_DELAY = 20
        private const val DEFAULT_RESOLUTION = 128
        private const val GITHUB_REPO_URL = "https://raw.githubusercontent.com/VenPlugs/petpet/main/frames/"
    }
    
    private val frameCache = mutableListOf<Bitmap>()
    private val executor = Executors.newFixedThreadPool(3)
    
    override fun start(context: Context) {
        preloadFrames(context)
        
        val arguments = listOf(
            createCommandOption(
                ApplicationCommandType.USER,
                "user",
                "User whose avatar to use as image",
                null,
                false,
                false
            ),
            createCommandOption(
                ApplicationCommandType.INTEGER,
                "delay",
                "The delay between each frame. Defaults to 20.",
                null,
                false,
                false
            ),
            createCommandOption(
                ApplicationCommandType.INTEGER,
                "resolution",
                "Resolution for the gif. Defaults to 128.",
                null,
                false,
                false
            ),
            createCommandOption(
                ApplicationCommandType.BOOLEAN,
                "no-server-pfp",
                "Use the normal avatar instead of the server specific one",
                null,
                false,
                false
            )
        )
        
        commands.registerCommand(
            "petpet",
            "Create a petpet gif from a user's avatar",
            arguments
        ) { ctx: CommandContext ->
            try {
                val user = ctx.getUser("user")
                if (user == null) {
                    return@registerCommand CommandResult("You need to specify a user to pet!")
                }
                
                val delay = ctx.getIntOrDefault("delay", DEFAULT_DELAY)
                val resolution = ctx.getIntOrDefault("resolution", DEFAULT_RESOLUTION)
                val noServerPfp = ctx.getBoolOrDefault("no-server-pfp", false)
                
                val serverId = if (noServerPfp) null else ctx.currentChannel.guildId
                val avatarUrl = IconUtils.getForUser(user.id, serverId.toString()).replace("?size=128", "?size=2048")
                
                val gifFile = createPetpetGif(context, avatarUrl, delay, resolution)
                
                ctx.addAttachment(Uri.fromFile(gifFile).toString(), "petpet.gif")
                return@registerCommand CommandResult("")
            } catch (e: Throwable) {
                Main.logger.error("Failed to create petpet", e)
                return@registerCommand CommandResult("Failed to create petpet: ${e.message}")
            }
        }
    }
    
    private fun preloadFrames(context: Context) {
        try {
            frameCache.clear()
            
            val latch = CountDownLatch(FRAMES)
            
            for (i in 0 until FRAMES) {
                executor.submit {
                    try {
                        val frameUrl = "$GITHUB_REPO_URL/pet$i.gif"
                        val request = ImageRequest.Builder(context)
                            .data(frameUrl)
                            .build()
                        
                        val drawable = runBlocking {
                            Coil.imageLoader(context).execute(request).drawable
                        }
                        
                        if (drawable != null) {
                            val bitmap = drawableToBitmap(drawable)
                            synchronized(frameCache) {
                                frameCache.add(i, bitmap)
                            }
                        }
                    } catch (e: Exception) {
                        Main.logger.error("Failed to load frame $i", e)
                    } finally {
                        latch.countDown()
                    }
                }
            }
            
            latch.await()
            
            if (frameCache.size != FRAMES) {
                Main.logger.error("Failed to load all frames: ${frameCache.size}/$FRAMES loaded")
            }
        } catch (e: Exception) {
            Main.logger.error("Failed to preload frames", e)
        }
    }
    
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: DEFAULT_RESOLUTION
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: DEFAULT_RESOLUTION
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        
        return bitmap
    }
    
    private fun createPetpetGif(context: Context, avatarUrl: String, delay: Int, resolution: Int): File {
        val avatarBitmap = runBlocking {
            val request = ImageRequest.Builder(context)
                .data(avatarUrl)
                .build()
            
            val drawable = Coil.imageLoader(context).execute(request).drawable
            drawableToBitmap(drawable!!)
        }
        
        val resizedAvatar = Bitmap.createScaledBitmap(avatarBitmap, resolution, resolution, true)
        
        val outputFile = File.createTempFile("petpet", ".gif", context.cacheDir)
        outputFile.deleteOnExit()
        
        val gifEncoder = GifEncoder().apply {
            start(FileOutputStream(outputFile))
            setRepeat(0)
            setDelay(delay)
            setQuality(10)
        }
        
        val frameBitmap = Bitmap.createBitmap(resolution, resolution, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frameBitmap)
        
        for (i in 0 until FRAMES) {
            canvas.drawColor(android.graphics.Color.TRANSPARENT)
            
            val j = if (i < FRAMES / 2) i else FRAMES - i
            val width = (0.8 + j * 0.02).toFloat() * resolution
            val height = (0.8 - j * 0.05).toFloat() * resolution
            val offsetX = (1 - width / resolution) * 0.5 + 0.1
            val offsetY = 1 - height / resolution - 0.08
            
            val matrix = Matrix()
            matrix.postScale(width / resolution, height / resolution)
            matrix.postTranslate(offsetX * resolution, offsetY * resolution)
            
            canvas.drawBitmap(resizedAvatar, matrix, null)
            
            val frame = if (i < frameCache.size) frameCache[i] else frameCache[0]
            val resizedFrame = Bitmap.createScaledBitmap(frame, resolution, resolution, true)
            canvas.drawBitmap(resizedFrame, 0f, 0f, null)
            
            gifEncoder.addFrame(frameBitmap)
        }
        
        gifEncoder.finish()
        
        return outputFile
    }
    
    private fun CommandContext.getIntOrDefault(key: String, default: Int): Int {
        return try {
            getInt(key) ?: default
        } catch (e: Exception) {
            default
        }
    }
    
    private fun CommandContext.getBoolOrDefault(key: String, default: Boolean): Boolean {
        return try {
            getBoolean(key) ?: default
        } catch (e: Exception) {
            default
        }
    }
    
    override fun stop(context: Context) {
        commands.unregisterAll()
        executor.shutdown()
        frameCache.clear()
    }
}
