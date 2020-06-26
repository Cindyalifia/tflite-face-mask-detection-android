package com.example.aps1

import android.content.ContentUris
import android.content.Intent
import android.graphics.*
import android.graphics.Paint.Align
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import com.esafirm.imagepicker.features.ImagePicker
import com.esafirm.imagepicker.model.Image
import com.example.facemaskdetection.Box
import com.example.facemaskdetection.OverlayView
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.face.FaceDetector
import kotlinx.android.synthetic.main.activity_main.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.lang.Float.min
import kotlin.math.ceil


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraView.setLifecycleOwner(this)

        // Create a FaceDetector
        val faceDetector = FaceDetector.Builder(this).setTrackingEnabled(true)
            .build()
        if (!faceDetector.isOperational) {
            AlertDialog.Builder(this)
                .setMessage("Could not set up the face detector!")
                .show()
        }

        cameraView.addFrameProcessor{ frame ->
            val matrix = Matrix()
            matrix.setRotate(frame.rotationToUser.toFloat())

            if (frame.dataClass === ByteArray::class.java){
                val out = ByteArrayOutputStream()
                val yuvImage = YuvImage(
                    frame.getData(),
                    ImageFormat.NV21,
                    frame.size.width,
                    frame.size.height,
                    null
                )
                yuvImage.compressToJpeg(
                    Rect(0, 0, frame.size.width, frame.size.height), 100, out
                )
                val imageBytes = out.toByteArray()
                var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                bitmap = Bitmap.createScaledBitmap(bitmap, overlayView.width, overlayView.height, true)

                overlayView.boundingBox = processBitmap(bitmap, faceDetector)
                overlayView.invalidate()
            } else {
                Toast.makeText(this, "Camera Data not Supported", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processBitmap(bitmap: Bitmap, faceDetector: FaceDetector): MutableList<Box>{
        val boundingBoxList = mutableListOf<Box>()

        // Detect the faces
        val frame = Frame.Builder().setBitmap(bitmap).build()
        val faces = faceDetector.detect(frame)

        // Mark out the identified face
        for (i in 0 until faces.size()) {
            val thisFace = faces.valueAt(i)
            val left = thisFace.position.x
            val top = thisFace.position.y
            val right = left + thisFace.width
            val bottom = top + thisFace.height
            val bitmapCropped = Bitmap.createBitmap(bitmap,
                left.toInt(),
                top.toInt(),
                if (right.toInt() > bitmap.width) {
                    bitmap.width - left.toInt()
                } else {
                    thisFace.width.toInt()
                },
                if (bottom.toInt() > bitmap.height) {
                    bitmap.height - top.toInt()
                } else {
                    thisFace.height.toInt()
                })
            val label = predict(bitmapCropped)
            var predictionn = ""
            val with = label["WithMask"]?: 0F
            val without = label["WithoutMask"]?: 0F

            if (with > without){
                predictionn = "With Mask : " + String.format("%.1f", with*100) + "%"
            } else {
                predictionn = "Without Mask : " + String.format("%.1f", without*100) + "%"
            }
            boundingBoxList.add(Box(RectF(left, top, right, bottom), predictionn, with>without))
        }
        return boundingBoxList
    }

    private fun predict(input: Bitmap): MutableMap<String, Float> {
        // load model
        val modelFile = FileUtil.loadMappedFile(this, "model.tflite")
        val model = Interpreter(modelFile, Interpreter.Options()) 
        val labels = FileUtil.loadLabels(this, "labels.txt")

        // data type
        val imageDataType = model.getInputTensor(0).dataType() 
        val inputShape = model.getInputTensor(0).shape() 

        val outputDataType = model.getOutputTensor(0).dataType() 
        val outputShape = model.getOutputTensor(0).shape() 

        var inputImageBuffer = TensorImage(imageDataType)
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, outputDataType) 

        // preprocess
        val cropSize = kotlin.math.min(input.width, input.height)
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeWithCropOrPadOp(cropSize, cropSize)) 
            .add(ResizeOp(inputShape[1], inputShape[2], ResizeOp.ResizeMethod.NEAREST_NEIGHBOR)) 
            .add(NormalizeOp(127.5f, 127.5f)) 
            .build()

        // load image
        inputImageBuffer.load(input) 
        inputImageBuffer = imageProcessor.process(inputImageBuffer) 

        // run model
        model.run(inputImageBuffer.buffer, outputBuffer.buffer.rewind())

        // get output
        val labelOutput = TensorLabel(labels, outputBuffer) 

        val label = labelOutput.mapWithFloatValue
        return label
    }

}
