package com.cocopie.mobile.xgen.example;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileNotFoundException;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_IMAGE = 11101;
    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final String[] mPermissionList = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE};

    private ImageView imageView1;
    private TextView textView1;
    private TextView textView2;
    private EditText loopCountEdit;

    private int labelCount;
    private int imageWidth;
    private int imageHeight;
    private int imageChannel;
    private float[] modelMean;
    private float[] modelStd;

    private long sEngine = -1;

    private void initData() {
        labelCount = 1000;
        imageWidth = 224;
        imageHeight = 224;
        imageChannel = 3;
        modelMean = new float[]{0.485f, 0.456f, 0.406f};
        modelStd = new float[]{0.229f, 0.224f, 0.225f};
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);

        initData();

        textView1 = findViewById(R.id.text1);
        textView2 = findViewById(R.id.text2);
        imageView1 = findViewById(R.id.image1);
        textView2.setOnClickListener(view -> {
            getImage();
        });
        loopCountEdit = findViewById(R.id.loop_count);
        ActivityCompat.requestPermissions(MainActivity.this, mPermissionList, REQUEST_STORAGE_PERMISSION);

        new Thread() {
            @Override
            public void run() {
                String pbPath = new File(getCacheDir(), "efficientnet_b0_ra_3dd342df.pb").getAbsolutePath();
                String dataPath = new File(getCacheDir(), "efficientnet_b0_ra_3dd342df.data").getAbsolutePath();
                CoCoPIEUtils.copyAssetsFile(MainActivity.this, pbPath, "efficientnet_b0_ra_3dd342df.pb");
                CoCoPIEUtils.copyAssetsFile(MainActivity.this, dataPath, "efficientnet_b0_ra_3dd342df.data");
                sEngine = CoCoPIEJNIExporter.Create(pbPath, dataPath);
            }
        }.start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    private void PreProcess(Bitmap bitmap) {
        int[] intValues = new int[imageHeight * imageWidth];
        float[] floatValues = new float[imageHeight * imageWidth * imageChannel];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int idx = 0;
        for (int value : intValues) {
            floatValues[idx++] = (((value >> 16) & 0xFF) / 255.f - modelMean[0]) / modelStd[0];
            floatValues[idx++] = (((value >> 8) & 0xFF) / 255.f - modelMean[1]) / modelStd[1];
            floatValues[idx++] = ((value & 0xFF) / 255.f - modelMean[2]) / modelStd[2];
        }

        int max_loop_count;
        try {
            max_loop_count = Integer.parseInt(loopCountEdit.getText().toString());
        } catch (NumberFormatException e) {
            max_loop_count = 1;
        }
        for (int i = 0; i < max_loop_count; i++) {
            if (sEngine != -1) {
                CoCoPIEUtils.RunModel(sEngine, floatValues);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void PostProcess(WDSROutputEvent event) {
        float[] accuracy = event.getData().pixels;
        float maxAccu = Float.NEGATIVE_INFINITY;
        int index = -1;
        for (int i = 0; i < labelCount; i++) {
            if (accuracy[i] > maxAccu) {
                index = i;
                maxAccu = accuracy[i];
            }
        }
        if (index >= 0) {
            textView1.setText("Classification: " + index + " (" + event.getData().costTime + " ms)");
        } else {
            textView1.setText("Detect error");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            boolean writeExternalStorage = grantResults[0] == PackageManager.PERMISSION_GRANTED;
            boolean readExternalStorage = grantResults[1] == PackageManager.PERMISSION_GRANTED;
            if (!writeExternalStorage || !readExternalStorage) {
                Toast.makeText(this, "No permission", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void getImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_PICK_IMAGE) {
                if (data != null) {
                    try {
                        Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(data.getData()));
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, imageWidth, imageHeight, true);
                        HandlerThread handlerThread = new HandlerThread("PreProcess");
                        handlerThread.start();
                        Handler handler = new Handler(handlerThread.getLooper());
                        handler.post(() -> PreProcess(scaledBitmap));
                        imageView1.setImageBitmap(bitmap);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error image. please select another image", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Error image. please select another image", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
