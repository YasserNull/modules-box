import java.io.File
fun main() {
    val targetSize = 192
    val width = 500
    val height = 500
    val size = Math.min(width, height)
    val scale = if (size > targetSize) targetSize.toFloat() / size else 1f
    val scaledSize = (size * scale).toInt()
    
    val scaledWidth = (width * scale).toInt()
    val scaledHeight = (height * scale).toInt()
    
    println("scale=$scale, scaledSize=$scaledSize, scaledWidth=$scaledWidth, scaledHeight=$scaledHeight")
}
