package com.tencent.yolov8ncnn;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST_CODE = 0;
    private static final int OPEN_GALLERY_REQUEST_CODE = 1;

    private String[] class_name = new String[]{
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
            "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
            "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
            "hair drier", "toothbrush"
    };
    private Button btn_select_image;
    private Button btn_recognize;
    private TextView tv_result;
    private YoloAPI yoloAPI;
    private Bitmap bitmap;
    private ImageView imageView;

    @SuppressLint("MissingInflatedId")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        yoloAPI = new YoloAPI();
        /*gpu选择1,cpu选择0*/
        boolean init = yoloAPI.Init(getAssets(), 1, 1);
        if (init) Toast.makeText(this, "模型加载成功", Toast.LENGTH_SHORT).show();
        else Toast.makeText(this, "模型加载失败", Toast.LENGTH_SHORT).show();

        btn_select_image = findViewById(R.id.btn_select_image);
        btn_recognize = findViewById(R.id.btn_recognize);
        tv_result = findViewById(R.id.tv_result);
        imageView = findViewById(R.id.imageView);

        btn_select_image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applyPermission();
            }
        });

        btn_recognize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (init && bitmap != null) {
                    tv_result.setText("结果:\n");

                    // 记录预测开始时间
                    long startTime = System.currentTimeMillis();

                    YoloAPI.Obj[] detect = yoloAPI.Detect(bitmap, true);

                    // 记录预测结束时间
                    long endTime = System.currentTimeMillis();
                    StringBuilder res = new StringBuilder();
                    res.append(String.format("预测时间: %d 毫秒\n", (endTime - startTime)));

                    if (detect != null && detect.length != 0) {
                        for (YoloAPI.Obj obj : detect) {
                            res.append(String.format("类别: %s, 概率: %.2f%%, 坐标: (%.2f, %.2f, %.2f, %.2f)\n",
                                    class_name[obj.label],
                                    obj.prob * 100,
                                    obj.x, obj.y, obj.w, obj.h));
                        }

                        tv_result.setText(res.toString());

                        // 绘制目标框
                        Bitmap resultBitmap = drawBoundingBoxes(bitmap, detect);

                        // 更新 ImageView 显示结果图像
                        imageView.setImageBitmap(resultBitmap);
                    } else {
                        tv_result.setText(tv_result.getText().toString() + "无结果\n");
                    }
                } else if (bitmap == null) {
                    Toast.makeText(getApplicationContext(), "请选择图片", Toast.LENGTH_SHORT).show();
                } else if (!init) {
                    Toast.makeText(getApplicationContext(), "模型加载失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * 绘制检测到的目标框
     */
    private Bitmap drawBoundingBoxes(Bitmap bitmap, YoloAPI.Obj[] detect) {
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setColor(Color.RED);
        paint.setAntiAlias(true);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.YELLOW);
        textPaint.setTextSize(40);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setAntiAlias(true);

        for (YoloAPI.Obj obj : detect) {
            float left = obj.x;
            float top = obj.y;
            float right = obj.x + obj.w;
            float bottom = obj.y + obj.h;

            // 绘制矩形框
            canvas.drawRect(left, top, right, bottom, paint);

            // 绘制标签文字
            String label = String.format("%s %.2f%%", class_name[obj.label], obj.prob * 100);
            canvas.drawText(label, left, top - 10, textPaint);
        }

        return mutableBitmap;
    }

    /**
     * 申请动态权限
     */
    private void applyPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            openGallery();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        } else {
            openGallery();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, "你拒绝使用存储权限！", Toast.LENGTH_SHORT).show();
                Log.d("HL", "你拒绝使用存储权限！");
            }
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, null);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, OPEN_GALLERY_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_GALLERY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(data.getData());
                    Bitmap bitmap2 = BitmapFactory.decodeStream(inputStream);
                    imageView.setImageBitmap(bitmap2);
                    bitmap = bitmap2;
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
