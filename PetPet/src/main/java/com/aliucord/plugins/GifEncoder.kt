package com.aliucord.utils

import android.graphics.Bitmap
import android.graphics.Color
import java.io.IOException
import java.io.OutputStream

class GifEncoder {
    private var width = 0 // image size
    private var height = 0
    private var delay = 0 // frame delay (ms)
    private var started = false // ready to output frames
    private var out: OutputStream? = null
    private var image: Bitmap? = null // current frame
    private var pixels: IntArray = IntArray(0) // BGR byte array from frame
    private var indexedPixels: ByteArray = ByteArray(0) // converted frame indexed to palette
    private var colorDepth = 0 // number of bit planes
    private var colorTab: ByteArray = ByteArray(0) // RGB palette
    private var usedEntry = BooleanArray(256) // active palette entries
    private var palSize = 7 // color table size (bits-1)
    private var dispose = -1 // disposal code (-1 = use default)
    private var closeStream = false // close stream when finished
    private var transparent = Color.TRANSPARENT // transparent color if given
    private var transIndex = 0 // transparent index in color table
    private var repeat = -1 // loop count (0 = infinite)
    private var quality = 10 // quality level (1-30)
    
    fun setQuality(quality: Int) {
        this.quality = quality.coerceIn(1, 30)
    }
    
    fun setDispose(code: Int) {
        if (code >= 0) dispose = code
    }
    
    fun setTransparent(color: Int?) {
        transparent = color ?: Color.TRANSPARENT
    }
    
    fun setRepeat(repeat: Int) {
        this.repeat = repeat
    }
    
    fun setFrameRate(fps: Float) {
        if (fps > 0f) delay = (1000 / fps).toInt()
    }
    
    fun setDelay(delay: Int) {
        this.delay = delay.coerceAtLeast(0)
    }
    
    fun addFrame(im: Bitmap): Boolean {
        if (im == null || !started) return false
        
        try {
            if (width == 0 || height == 0) {
                width = im.width
                height = im.height
            }
            
            image = im
            getImagePixels()
            analyzePixels()
            
            if (firstFrame) {
                writeLSD()
                writePalette()
                if (repeat >= 0) {
                    writeNetscapeExt()
                }
            }
            
            writeGraphicCtrlExt()
            writeImageDesc()
            if (!firstFrame) {
                writePalette()
            }
            writePixels()
            
            firstFrame = false
            return true
        } catch (e: IOException) {
            return false
        }
    }
    
    fun finish(): Boolean {
        if (!started) return false
        
        started = false
        try {
            out?.write(0x3B)
            out?.flush()
            if (closeStream) {
                out?.close()
            }
        } catch (e: IOException) {
            return false
        }
        
        transIndex = 0
        out = null
        image = null
        firstFrame = true
        
        return true
    }
    
    fun start(os: OutputStream?): Boolean {
        if (os == null) return false
        closeStream = false
        out = os
        try {
            writeString("GIF89a")
        } catch (e: IOException) {
            return false
        }
        firstFrame = true
        started = true
        return true
    }
    
    private fun analyzePixels() {
        val len = pixels.size
        val nPix = len
        
        colorTab = ByteArray(3 * 256)
        
        val indexedPixels = ByteArray(nPix)
        
        for (i in 0 until 256) {
            val r = (i shr 5) * 36
            val g = ((i shr 2) and 7) * 36
            val b = (i and 3) * 85
            
            colorTab[i * 3] = r.toByte()
            colorTab[i * 3 + 1] = g.toByte()
            colorTab[i * 3 + 2] = b.toByte()
        }
        
        var k = 0
        for (i in 0 until nPix) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val a = Color.alpha(pixel)
            
            if (a < 128) {
                transIndex = 0
                indexedPixels[i] = 0
                continue
            }
            
            var minDist = Int.MAX_VALUE
            var index = 0
            
            for (j in 0 until 256) {
                val dr = r - (colorTab[j * 3].toInt() and 0xff)
                val dg = g - (colorTab[j * 3 + 1].toInt() and 0xff)
                val db = b - (colorTab[j * 3 + 2].toInt() and 0xff)
                
                val dist = dr * dr + dg * dg + db * db
                if (dist < minDist) {
                    minDist = dist
                    index = j
                }
            }
            
            indexedPixels[i] = index.toByte()
            usedEntry[index] = true
        }
        
        this.indexedPixels = indexedPixels
    }
    
    private fun getImagePixels() {
        val w = image!!.width
        val h = image!!.height
        
        val newPixels = IntArray(w * h)
        image!!.getPixels(newPixels, 0, w, 0, 0, w, h)
        
        pixels = newPixels
    }
    
    @Throws(IOException::class)
    private fun writeGraphicCtrlExt() {
        out!!.write(0x21)
        out!!.write(0xf9)
        out!!.write(4)
        
        var transp = 0
        var disp = 0
        if (transparent != Color.TRANSPARENT) {
            transp = 1
            disp = 2
        }
        
        if (dispose >= 0) {
            disp = dispose and 7
        }
        
        disp = disp shl 2
        
        out!!.write(disp or transp)
        
        writeShort(delay)
        out!!.write(transIndex)
        out!!.write(0)
    }
    
    @Throws(IOException::class)
    private fun writeImageDesc() {
        out!!.write(0x2c)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        
        if (firstFrame) {
            out!!.write(0)
        } else {
            out!!.write(0x80 or palSize)
        }
    }
    
    @Throws(IOException::class)
    private fun writeLSD() {
        writeShort(width)
        writeShort(height)
        
        out!!.write(0x80 or 0x70 or palSize)
        
        out!!.write(0)
        out!!.write(0)
    }
    
    @Throws(IOException::class)
    private fun writeNetscapeExt() {
        out!!.write(0x21)
        out!!.write(0xff)
        out!!.write(11)
        writeString("NETSCAPE2.0")
        out!!.write(3)
        out!!.write(1)
        writeShort(repeat)
        out!!.write(0)
    }
    
    @Throws(IOException::class)
    private fun writePalette() {
        out!!.write(colorTab, 0, colorTab.size)
        val n = (3 * 256) - colorTab.size
        for (i in 0 until n) {
            out!!.write(0)
        }
    }
    
    @Throws(IOException::class)
    private fun writePixels() {
        val encoder = LZWEncoder(width, height, indexedPixels, colorDepth)
        encoder.encode(out!!)
    }
    
    @Throws(IOException::class)
    private fun writeShort(value: Int) {
        out!!.write(value and 0xff)
        out!!.write(value shr 8 and 0xff)
    }
    
    @Throws(IOException::class)
    private fun writeString(s: String) {
        for (i in s.indices) {
            out!!.write(s[i].code)
        }
    }
    
    private inner class LZWEncoder(private val width: Int, private val height: Int, 
                                 private val pixels: ByteArray, private val initCodeSize: Int) {
        @Throws(IOException::class)
        fun encode(os: OutputStream) {
            os.write(initCodeSize)
            
            val maxLen = 255
            var pos = 0
            
            while (pos < pixels.size) {
                val len = (pixels.size - pos).coerceAtMost(maxLen)
                os.write(len)
                for (i in 0 until len) {
                    os.write(pixels[pos + i].toInt() and 0xFF)
                }
                pos += len
            }
            
            os.write(0)
        }
    }
    
    private var firstFrame = true
}
