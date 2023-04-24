package com.cocopie.mobile.xgen.example

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.SparseArray
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileNotFoundException

class MainActivity : AppCompatActivity() {

    private lateinit var imageView1: ImageView
    private lateinit var textView1: TextView
    private lateinit var textView2: TextView
    private lateinit var loopCountEdit: EditText

    private val labelsMap = SparseArray<String>()
    private var labelCount = 0
    private var imageWidth = 0
    private var imageHeight = 0
    private var imageChannel = 0
    private lateinit var modelMean: FloatArray
    private lateinit var modelStd: FloatArray
    private var sEngine: Long = -1

    private val loaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                SUCCESS -> {
                    Log.i("MainActivity", "OpenCV loaded successfully")
                }
                else -> super.onManagerConnected(status)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        this.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(R.layout.activity_main)
        initData()
        textView1 = findViewById(R.id.text1)
        textView2 = findViewById(R.id.text2)
        imageView1 = findViewById(R.id.image1)
        textView2.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_PICK_IMAGE)
        }
        loopCountEdit = findViewById(R.id.loop_count)
        ActivityCompat.requestPermissions(this@MainActivity, mPermissionList, REQUEST_STORAGE_PERMISSION)

        initModel()
    }

    private fun initData() {
        labelCount = 1000
        imageWidth = 224
        imageHeight = 224
        imageChannel = 3
        modelMean = floatArrayOf(0.485f, 0.456f, 0.406f)
        modelStd = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private fun initModel() {
        object : Thread() {
            override fun run() {
                val mobilev2 = "mobilev2_half_opt_manual"
                val model = mobilev2
                val labelsFile = File(cacheDir, "imagenet_labels_1000.json")
                val pbFile = File(cacheDir, "${model}.pb")
                val dataFile = File(cacheDir, "${model}.data")
                CoCoPIEUtils.copyAssetsFile(this@MainActivity, labelsFile.absolutePath, "imagenet_labels_1000.json")
                CoCoPIEUtils.copyAssetsFile(this@MainActivity, pbFile.absolutePath, "${model}.pb")
                CoCoPIEUtils.copyAssetsFile(this@MainActivity, dataFile.absolutePath, "${model}.data")
                sEngine = CoCoPIEJNIExporter.CreateOpt(pbFile.absolutePath, dataFile.absolutePath)

                val labelsJson = labelsFile.readText()
                val labelsObject = JSONObject(labelsJson)
                val labelsData = labelsObject.optJSONArray("data")
                if (labelsData != null) {
                    for (i in 0 until labelsData.length()) {
                        val labelObject = labelsData.optJSONObject(i)
                        if (labelObject != null) {
                            labelsMap.put(labelObject.getInt("Class ID"), labelObject.getString("Class Name"))
                        }
                    }
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            Log.d("MainActivity", "Internal OpenCV library not found. Using OpenCV Manager for initialization")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, loaderCallback)
        } else {
            Log.d("MainActivity", "OpenCV library found inside package. Using it!")
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    private fun PreProcess(bitmap: Bitmap) {
        val intValues = IntArray(imageHeight * imageWidth)
        val floatValues = FloatArray(imageHeight * imageWidth * imageChannel)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var idx = 0
        for (value in intValues) {
            floatValues[idx++] = ((value shr 16 and 0xFF) / 255f - modelMean[0]) / modelStd[0]
            floatValues[idx++] = ((value shr 8 and 0xFF) / 255f - modelMean[1]) / modelStd[1]
            floatValues[idx++] = ((value and 0xFF) / 255f - modelMean[2]) / modelStd[2]
        }
        val maxLoopCount = try {
            loopCountEdit.text.toString().toInt()
        } catch (e: NumberFormatException) {
            1
        }
        for (i in 0 until maxLoopCount) {
            if (sEngine != -1L) {
                CoCoPIEUtils.RunModel(sEngine, floatValues)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun PostProcess(event: WDSROutputEvent) {
        val accuracy = event.data.pixels
        var maxAccu = Float.NEGATIVE_INFINITY
        var index = -1
        for (i in 0 until labelCount) {
            if (accuracy[i] > maxAccu) {
                index = i
                maxAccu = accuracy[i]
            }
        }
        if (index >= 0) {
            textView1.text = "Classification: $index ${labelsMap[index]} (${event.data.costTime} ms)"
        } else {
            textView1.text = "Detect error"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            val writeExternalStorage = grantResults[0] == PackageManager.PERMISSION_GRANTED
            val readExternalStorage = grantResults[1] == PackageManager.PERMISSION_GRANTED
            if (!writeExternalStorage || !readExternalStorage) {
                Toast.makeText(this, "No permission", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_PICK_IMAGE) {
                if (data != null) {
                    try {
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(data.data!!))

                        // Imgproc.resize is better than Bitmap.createScaledBitmap
                        val src = Mat()
                        Utils.bitmapToMat(bitmap, src)
                        val scaled = Mat()
                        Imgproc.resize(src, scaled, Size(imageWidth.toDouble(), imageHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC)
                        val scaledBitmap = Bitmap.createBitmap(scaled.width(), scaled.height(), Bitmap.Config.ARGB_8888)
                        Utils.matToBitmap(scaled, scaledBitmap)
//                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, imageWidth, imageHeight, true)

                        val handlerThread = HandlerThread("PreProcess")
                        handlerThread.start()
                        val handler = Handler(handlerThread.looper)
                        handler.post { PreProcess(scaledBitmap) }
                        imageView1.setImageBitmap(bitmap)
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                        Toast.makeText(this, "Error image. please select another image", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Error image. please select another image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val REQUEST_PICK_IMAGE = 11101
        private const val REQUEST_STORAGE_PERMISSION = 100
        private val mPermissionList = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
}