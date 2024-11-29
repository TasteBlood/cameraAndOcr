package com.example.cameraandocr;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity{

    private ExecutorService cameraService;
    private PreviewView previewView;

    private AppCompatTextView tvName,tvSex,tvNation,tvBirth,tvAddress,tvId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED
        || checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA},100);
        }else{
            init();
        }
    }

    private void init(){
        setContentView(R.layout.activity_main);
        previewView = findViewById(R.id.preview);
        tvAddress = findViewById(R.id.tvAddress);
        tvBirth = findViewById(R.id.tvBirth);
        tvId = findViewById(R.id.tvId);
        tvNation = findViewById(R.id.tvNation);
        tvName = findViewById(R.id.tvName);
        tvSex = findViewById(R.id.tvSex);

        cameraService = Executors.newSingleThreadExecutor();
        try {
            startCamera();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startCamera() throws ExecutionException, InterruptedException {
        ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
        Preview preview = new Preview.Builder()
                .setTargetRotation(Surface.ROTATION_0)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280,769))
                .setTargetRotation(Surface.ROTATION_0)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();

        cameraProvider.bindToLifecycle(this,cameraSelector,preview,imageAnalysis);

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageAnalysis.setAnalyzer(new ScheduledThreadPoolExecutor(2), image -> {
            //在这里去识别
            TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            assert image.getImage()!=null;
            InputImage image2 = InputImage.fromMediaImage(image.getImage(),0);
            recognizer.process(image2)
                    .addOnSuccessListener(text->{
                        runOnUiThread(()->{
                            extractInfo(text.getText());
                        });
                    })
                    .addOnFailureListener(e->{
                        e.printStackTrace();
                    })
                    .addOnCompleteListener(task -> {
                        image.close();
                    });
        });

    }

    private void extractInfo(String text) {
        // 正则表达式，用于匹配每个字段，考虑可能的空格
        String namePattern = "姓名\\s*([\\u4e00-\\u9fa5]+)";   // 匹配姓名（中文），允许姓名后有空格
        String genderPattern = "性别\\s*([男女])";             // 匹配性别（男女），允许性别后有空格
        String ethnicityPattern = "民族\\s*([\\u4e00-\\u9fa5]+)";  // 匹配民族（中文），允许民族后有空格
        String birthPattern = "出生\\s*([0-9]{4} 年[0-9]+月[0-9]+日)"; // 匹配出生日期，允许出生后有空格
        String addressPattern = "住址\\s*([\\u4e00-\\u9fa5]+(省|市|县|区)[^公]+)"; // 匹配住址，允许住址后有空格
        String idPattern = "公民身份号码\\s*([0-9]{17}[xX0-9])";  // 匹配身份证号，允许身份证号前有空格

        // 使用正则表达式提取每一项
        String name = extractPattern(text, namePattern);
        String gender = extractPattern(text, genderPattern);
        String ethnicity = extractPattern(text, ethnicityPattern);
        String birthDate = extractPattern(text, birthPattern);
        String address = extractPattern(text, addressPattern);
        String idNumber = extractPattern(text, idPattern);
//        if(!TextUtils.isEmpty(name)&&!TextUtils.isEmpty(gender)&&!TextUtils.isEmpty(ethnicity)&&!TextUtils.isEmpty(birthDate)
//        &&!TextUtils.isEmpty(address) && !TextUtils.isEmpty(idNumber)){
//            //设置信息
//
//        }
        tvSex.setText(gender);
        tvName.setText(name);
        tvNation.setText(ethnicity);
        tvBirth.setText(birthDate);
        tvAddress.setText(address);
        tvId.setText(idNumber);
    }

    private static String extractPattern(String input, String pattern) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(input);
        if (m.find()) {
            return m.group(1);  // 返回第一个匹配组
        } else {
            return ""; // 未匹配到的返回"未找到"
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(cameraService!=null)
            cameraService.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==100){
            StringBuilder builder = new StringBuilder();
            if(Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[0]) && grantResults[0]==PackageManager.PERMISSION_DENIED){
                builder.append("请开启 文件读取");
            }
            if(Manifest.permission.CAMERA.equals(permissions[1]) && grantResults[1]==PackageManager.PERMISSION_DENIED){
                builder.append("、相机");
            }
            if(builder.length()<=0){
                //都授权了
                init();
            }else{
                Toast.makeText(this,builder.toString(),Toast.LENGTH_LONG).show();
            }
        }
    }
}
