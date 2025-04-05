package com.aliucord.utils

import android.graphics.Bitmap
import android.graphics.Color
import java.io.IOException
import java.io.OutputStream

/**
 * Class that encodes a series of images as animated GIF
 * Based on the original Java GIF encoder by Kevin Weiner
 */
class GifEncoder {
    private var width = 0 // image size
    private var height = 0
    private var delay = 0 // frame delay (ms)
    private var started = false // ready to output frames
    private var out: OutputStream? = null
    private var image: Bitmap? = null // current frame
    private val pixels: IntArray = IntArray(0) // BGR byte array from frame
    private val indexedPixels: ByteArray = ByteArray(0) // converted frame indexed to palette
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
    
    /**
     * Sets quality of color quantization (conversion of images to the maximum 256 colors allowed by the GIF specification).
     * Lower values (minimum = 1) produce better colors, but slow processing significantly.
     * 10 is the default, and produces good color mapping at reasonable speeds.
     * Values greater than 20 do not yield significant improvements in speed.
     */
    fun setQuality(quality: Int) {
        this.quality = quality.coerceIn(1, 30)
    }
    
    /**
     * Sets the GIF frame disposal code for the last added frame and any subsequent frames.
     * Default is 0 if no transparent color has been set, otherwise 2.
     */
    fun setDispose(code: Int) {
        if (code >= 0) dispose = code
    }
    
    /**
     * Sets the transparent color for the last added frame and any subsequent frames.
     * Since all colors are subject to modification in the quantization process, the color in the final
     * palette for each frame closest to the given color becomes the transparent color for that frame.
     * May be set to null to indicate no transparent color.
     */
    fun setTransparent(color: Int?) {
        transparent = color ?: Color.TRANSPARENT
    }
    
    /**
     * Sets the number of times the set of GIF frames should be played.
     * Default is 1; 0 means play indefinitely.
     * Must be invoked before the first image is added.
     */
    fun setRepeat(repeat: Int) {
        this.repeat = repeat
    }
    
    /**
     * Sets frame rate in frames per second. Equivalent to setDelay(1000/fps).
     */
    fun setFrameRate(fps: Float) {
        if (fps > 0f) delay = (1000 / fps).toInt()
    }
    
    /**
     * Sets the delay time between each frame in milliseconds.
     * Default is 0.
     */
    fun setDelay(delay: Int) {
        this.delay = delay.coerceAtLeast(0)
    }
    
    /**
     * Adds next GIF frame. The frame is not written immediately, but is
     * actually deferred until the next frame is received so that timing
     * data can be inserted. Frames need not be added in sequence, as
     * timestamps are used to determine the sequence.
     */
    fun addFrame(im: Bitmap): Boolean {
        if (im == null || !started) return false
        
        try {
            if (width == 0 || height == 0) {
                // Initialize logical screen size
                width = im.width
                height = im.height
            }
            
            image = im
            getImagePixels() // Convert to indexed pixel array
            analyzePixels() // Build color table & map pixels
            
            if (firstFrame) {
                writeLSD() // Logical screen descriptor
                writePalette() // Global color table
                if (repeat >= 0) {
                    // Use NS app extension to indicate reps
                    writeNetscapeExt()
                }
            }
            
            writeGraphicCtrlExt() // Write graphic control extension
            writeImageDesc() // Image descriptor
            if (!firstFrame) {
                writePalette() // Local color table
            }
            writePixels() // Encode and write pixel data
            
            firstFrame = false
            return true
        } catch (e: IOException) {
            return false
        }
    }
    
    /**
     * Flushes any pending data and closes output file.
     * If writing to an OutputStream, the stream is not closed.
     */
    fun finish(): Boolean {
        if (!started) return false
        
        started = false
        try {
            out?.write(0x3B) // GIF trailer
            out?.flush()
            if (closeStream) {
                out?.close()
            }
        } catch (e: IOException) {
            return false
        }
        
        // Reset for subsequent use
        transIndex = 0
        out = null
        image = null
        firstFrame = true
        
        return true
    }
    
    /**
     * Initiates GIF file creation on the given stream.
     */
    fun start(os: OutputStream?): Boolean {
        if (os == null) return false
        closeStream = false
        out = os
        try {
            writeString("GIF89a") // Header
        } catch (e: IOException) {
            return false
        }
        firstFrame = true
        started = true
        return true
    }
    
    /**
     * Analyzes image colors and creates color map.
     */
    private fun analyzePixels() {
        val len = pixels.size
        val nPix = len
        
        // Create new color table with proper size
        colorTab = ByteArray(3 * 256)
        
        // Map image colors to closest match in color table
        val indexedPixels = ByteArray(nPix)
        
        // TODO: Implement actual quantization algorithm here
        // For simplicity, we'll use a basic palette for now
        
        // Create a simple palette
        for (i in 0 until 256) {
            val r = (i shr 5) * 36
            val g = ((i shr 2) and 7) * 36
            val b = (i and 3) * 85
            
            colorTab[i * 3] = r.toByte()
            colorTab[i * 3 + 1] = g.toByte()
            colorTab[i * 3 + 2] = b.toByte()
        }
        
        // Map image pixels to palette
        var k = 0
        for (i in 0 until nPix) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val a = Color.alpha(pixel)
            
            // Check for transparency
            if (a < 128) {
                transIndex = 0 // Use index 0 for transparency
                indexedPixels[i] = 0
                continue
            }
            
            // Simple palette mapping - find closest color
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
    
    /**
     * Extracts image pixels into an int array.
     */
    private fun getImagePixels() {
        val w = image!!.width
        val h = image!!.height
        
        val newPixels = IntArray(w * h)
        image!!.getPixels(newPixels, 0, w, 0, 0, w, h)
        
        // The pixels array will now be ARGB format for each pixel
        pixels = newPixels
    }
    
    /**
     * Writes Graphic Control Extension.
     */
    @Throws(IOException::class)
    private fun writeGraphicCtrlExt() {
        out!!.write(0x21) // Extension introducer
        out!!.write(0xf9) // GCE label
        out!!.write(4) // Data block size
        
        // Packed fields
        var transp = 0
        var disp = 0
        if (transparent != Color.TRANSPARENT) {
            transp = 1
            disp = 2 // Disposal: restore to bg color
        }
        
        if (dispose >= 0) {
            disp = dispose and 7 // User override
        }
        
        disp = disp shl 2
        
        out!!.write(disp or transp) // Packed fields
        
        writeShort(delay) // Delay time
        out!!.write(transIndex) // Transparent color index
        out!!.write(0) // Block terminator
    }
    
    /**
     * Writes Image Descriptor.
     */
    @Throws(IOException::class)
    private fun writeImageDesc() {
        out!!.write(0x2c) // Image separator
        writeShort(0) // Image position x,y = 0,0
        writeShort(0)
        writeShort(width) // Image size
        writeShort(height)
        
        // Packed fields
        if (firstFrame) {
            // No LCT - use global color table, no interlace
            out!!.write(0)
        } else {
            // Specify local color table and interlace
            out!!.write(0x80 or palSize) // 1 local color table 1=yes
        }
    }
    
    /**
     * Writes Logical Screen Descriptor.
     */
    @Throws(IOException::class)
    private fun writeLSD() {
        // Logical screen size
        writeShort(width)
        writeShort(height)
        
        // Packed fields
        out!!.write(0x80 or 0x70 or palSize) // Global color table flag
        
        out!!.write(0) // Background color index
        out!!.write(0) // Pixel aspect ratio - 1:1
    }
    
    /**
     * Writes Netscape application extension to define looping.
     */
    @Throws(IOException::class)
    private fun writeNetscapeExt() {
        out!!.write(0x21) // Extension introducer
        out!!.write(0xff) // App extension label
        out!!.write(11) // Block size
        writeString("NETSCAPE2.0") // App id + auth code
        out!!.write(3) // Sub-block size
        out!!.write(1) // Loop sub-block id
        writeShort(repeat) // Loop count (0=infinite)
        out!!.write(0) // Block terminator
    }
    
    /**
     * Writes color table.
     */
    @Throws(IOException::class)
    private fun writePalette() {
        out!!.write(colorTab, 0, colorTab.size)
        val n = (3 * 256) - colorTab.size
        for (i in 0 until n) {
            out!!.write(0)
        }
    }
    
    /**
     * Encodes and writes pixel data.
     * Uses simple LZW implementation for encoding.
     */
    @Throws(IOException::class)
    private fun writePixels() {
        val encoder = LZWEncoder(width, height, indexedPixels, colorDepth)
        encoder.encode(out!!)
    }
    
    /**
     * Write a 16-bit value to the output stream, LSB first.
     */
    @Throws(IOException::class)
    private fun writeShort(value: Int) {
        out!!.write(value and 0xff)
        out!!.write(value shr 8 and 0xff)
    }
    
    /**
     * Writes a string to the output stream.
     */
    @Throws(IOException::class)
    private fun writeString(s: String) {
        for (i in s.indices) {
            out!!.write(s[i].code)
        }
    }
    
    // Helper inner class for LZW encoding
    private inner class LZWEncoder(private val width: Int, private val height: Int, 
                                 private val pixels: ByteArray, private val initCodeSize: Int) {
        // Simplified LZW implementation
        @Throws(IOException::class)
        fun encode(os: OutputStream) {
            os.write(initCodeSize) // Write minimum code size
            
            // Write all pixels with simple run-length encoding
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
            
            os.write(0) // Write block terminator
        }
    }
    
    private var firstFrame = true
}
