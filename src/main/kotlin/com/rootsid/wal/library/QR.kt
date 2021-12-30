package com.rootsid.wal.library

import boofcv.alg.fiducial.qrcode.QrCodeEncoder
import boofcv.alg.fiducial.qrcode.QrCodeGeneratorImage
import boofcv.factory.fiducial.FactoryFiducial
import boofcv.gui.image.ImagePanel
import boofcv.gui.image.ShowImages
import boofcv.io.image.UtilImageIO
import boofcv.io.webcamcapture.UtilWebcamCapture
import boofcv.kotlin.asBufferedImage
import boofcv.kotlin.asGrayU8
import boofcv.struct.image.GrayU8
import java.time.LocalDateTime

/**
 * Show QR image
 *
 * @param message Message to embed on the QR code
 */
fun showQRImage(message: String) {
    val qr = QrCodeEncoder().addAutomatic(message).fixate()
    val generator = QrCodeGeneratorImage(15).render(qr)
    ShowImages.showWindow(generator.gray.asBufferedImage(), "QR Code", true)
}

/**
 * Save QR image
 *
 * @param message Message to embed on the QR code
 * @param filename Output QR filename
 */
fun saveQRImage(message: String, filename: String) {
    val qr = QrCodeEncoder().addAutomatic(message).fixate()
    val generator = QrCodeGeneratorImage(15).render(qr)
    UtilImageIO.saveImage(generator.gray.asBufferedImage(), filename)
}

/**
 * Web cam QR scan
 *
 * @param seconds Amount of seconds to wait for a scan (timeout)
 * @return Message retrieved from the scaned QR image
 */
fun webCamQRScan(seconds: Long): String {
    // Open a webcam and create the detector
    val webcam = UtilWebcamCapture.openDefault(800, 600)
    val detector = FactoryFiducial.qrcode(null, GrayU8::class.java)
    // Create the panel used to display the image and
    val gui = ImagePanel()
    gui.preferredSize = webcam.viewSize
    val frame = ShowImages.showWindow(gui, "Gradient", true)
    val stopAt = LocalDateTime.now().plusSeconds(seconds)
    var message = ""
    var found = false

    while (!found) {
        if (LocalDateTime.now() > stopAt) {
            frame.isVisible = false
            frame.dispose()
            throw Exception("Scan QR timeout after $seconds seconds.")
        }
        // Load the image from the webcam
        val image = webcam.image ?: break
        // Convert to gray scale and detect QR codes inside
        detector.process(image.asGrayU8())
        for (qr in detector.detections) {
            message = qr.message
            found = true
        }
        gui.setImageRepaint(image)
    }
    frame.isVisible = false
    frame.dispose()
    return message
}

// TODO: Implement read qr image file
// fun readQRfile() {
//    // Opens a dialog and let's you select a directory
//    val directory = BoofSwingUtil.openFileChooser("QR Disk Scanning",BoofSwingUtil.FileTypes.DIRECTORIES) ?: return
//
//    // Create the scanner class
//    val detector = FactoryFiducial.qrcode(null,GrayU8::class.java)
//
//    // Walk through the path recursively, finding all image files, load them, scan for QR codes, add results to a map
//    val imageToMessages = mutableMapOf<String,List<String>>()
//    val elapsedTime = measureTimeMillis {
//        directory.walk().filter {UtilImageIO.isImage(it)}.forEach { f ->
//            val image = f.absoluteFile.loadImage(ImageType.SB_U8)
//            detector.process(image)
//            imageToMessages[f.absolutePath] = detector.detections.map { it.message }
//            println(f.name) // print so we can see something is happening
//        }
//    }
//
//    // Print a results summary
//    val totalMessages = imageToMessages.values.sumBy{it.size}
//    println("\nFound ${imageToMessages.size} images with $totalMessages messages averaging %.2f img/s".
//    format(imageToMessages.size/(elapsedTime*1e-3)))
// }
