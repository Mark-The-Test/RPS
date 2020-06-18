package com.stockman.mltest2;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.automl.FirebaseAutoMLLocalModel;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceAutoMLImageLabelerOptions;
import com.google.firebase.ml.vision.objects.FirebaseVisionObject;
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetector;
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions;
//import com.google.mlkit.common.model.LocalModel;
//import com.google.mlkit.vision.label.ImageLabel;
//import  com.google.mlkit.vision.label.ImageLabelerOptionsBase;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

//import com.google.mlkit.vision.common.InputImage;
//import com.google.mlkit.vision.label.ImageLabeler;
//import com.google.mlkit.vision.label.ImageLabeling;
//import com.google.mlkit.vision.label.automl.AutoMLImageLabelerLocalModel;
//import com.google.mlkit.vision.label.automl.AutoMLImageLabelerOptions;
//import com.google.mlkit.vision.label.ImageLabel;
//import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private TextToSpeech mTextToSpeech;
    private int REQUEST_CODE_PERMISSIONS = 101;
    private String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE"};
    TextureView mTextureView;
    TextView mButton;
    TextView mTextView, mMyMove, mTextView3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //need to caputre image
        mTextView = findViewById(R.id.textView2);
        mButton = findViewById(R.id.Ibutton);
        mTextureView = (TextureView) findViewById(R.id.emagemage);
        mMyMove = findViewById(R.id.my_move);
        mTextView3 = findViewById(R.id.textView3);

        if (allPermissionGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS);
        }

    }
    //camerax attempt
    private boolean allPermissionGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startCamera() {
        CameraX.unbindAll();
        Rational aspectRatio = new Rational(mTextureView.getWidth(), mTextureView.getHeight());
        Size screen = new Size(mTextureView.getWidth(), mTextureView.getHeight());
        PreviewConfig previewConfig = new PreviewConfig.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setTargetResolution(screen)
                .build();

        Preview preview = new Preview(previewConfig);
        Log.d("got this far", "startCamera: works");
        preview.setOnPreviewOutputUpdateListener(new Preview.OnPreviewOutputUpdateListener() {
            @Override
            public void onUpdated(Preview.PreviewOutput output) {
                ViewGroup parent = (ViewGroup) mTextureView.getParent();
                parent.removeView(mTextureView);
                parent.addView(mTextureView);
                mTextureView.setSurfaceTexture(output.getSurfaceTexture());
                updateTransform();

            }
        });
        mBackgroundThread = new HandlerThread("analise handler");
        startBackgroundThread();
        Log.d("got this far", "thread works");
        Analiser analiser = new Analiser();
       // Analyser analiser = new Analyser(); //downstream obj pipeline error
        ImageAnalysisConfig analysisConfig = new ImageAnalysisConfig.Builder()
                .setTargetResolution(screen)
                .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .setTargetRotation(Surface.ROTATION_0)
                .setImageQueueDepth(1)
                .setCallbackHandler(Handler.createAsync(mBackgroundThread.getLooper())) //looper !=null needs fixed

                .build();
        ImageAnalysis imageAnalysis = new ImageAnalysis(analysisConfig);
        imageAnalysis.setAnalyzer(analiser);
        Log.d("got this far", "analiser set");
        CameraX.bindToLifecycle(this, preview, imageAnalysis); //hand image anisiare
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void updateTransform() {

        Matrix mx = new Matrix();
        float h = mTextureView.getMeasuredHeight();
        float w = mTextureView.getMeasuredWidth();

        float cx = w / 2f;
        float cy = h / 2f;

        int rotationOgr;
        int rotation = (int) mTextureView.getRotation();

        switch (rotation) {
            case Surface.ROTATION_0:
                rotationOgr = 0;
                break;
            case Surface.ROTATION_90:
                rotationOgr = 90;
                break;
            case Surface.ROTATION_180:
                rotationOgr = 180;
                break;
            case Surface.ROTATION_270:
                rotationOgr = 270;
                break;
            default:
                return;
        }

        mx.postRotate((float) rotationOgr, cx, cy);
        mTextureView.setTransform(mx);

    }

    //obj labeler
    public class Analiser implements ImageAnalysis.Analyzer{
        private int degreesToFirebaseRotation(int degrees) {
            switch (degrees) {
                case 0:
                    return FirebaseVisionImageMetadata.ROTATION_0;
                case 90:
                    return FirebaseVisionImageMetadata.ROTATION_90;
                case 180:
                    return FirebaseVisionImageMetadata.ROTATION_180;
                case 270:
                    return FirebaseVisionImageMetadata.ROTATION_270;
                default:
                    throw new IllegalArgumentException(
                            "Rotation must be 0, 90, 180, or 270.");
            }
        }
        @Override
        public void analyze(ImageProxy imageProxy, int degrees){
            if (imageProxy == null || imageProxy.getImage() == null) {
                return;
            }
            Image mediaImage = imageProxy.getImage();
            int rotation = degreesToFirebaseRotation(degrees);
            FirebaseVisionImage image =
                    FirebaseVisionImage.fromMediaImage(mediaImage, rotation);
            // Pass image to an ML Kit Vision API
            // ...
            FirebaseAutoMLLocalModel mLocalModel = new FirebaseAutoMLLocalModel.Builder()
                    .setAssetFilePath("manifest.json") //try assets/manifest.json
                    .build();
            FirebaseVisionOnDeviceAutoMLImageLabelerOptions options =
                    new FirebaseVisionOnDeviceAutoMLImageLabelerOptions.Builder(mLocalModel)
                            .setConfidenceThreshold(0.65f) //setting confidence results in all paper, maybe due to loop set up?
                            .build();
            //
            FirebaseVisionImageLabeler labeler = null;
            try {
                labeler = FirebaseVision.getInstance()
                        .getOnDeviceAutoMLImageLabeler(options);
            } catch (FirebaseMLException e) {
                e.printStackTrace();
            }

            labeler.processImage(image)
                    .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
                        @Override
                        public void onSuccess(List<FirebaseVisionImageLabel> labels) {
                            for (FirebaseVisionImageLabel label: labels) {
                                String text = label.getText();
                                String entityId = label.getEntityId();
                                float confidence = label.getConfidence();
                                String print = text + " : "+ confidence;
                                Log.d("Aniliser works",print);
                                mTextView.setText(text);
                                int rps;
                                Random random = new Random();
                                rps = random.nextInt(2);
                                switch (rps) {
                                    case 0:
                                        mMyMove.setText("rock");
                                        break;
                                    case 1:
                                        mMyMove.setText("paper");
                                        break;
                                    case 2:
                                        mMyMove.setText("scissors");
                                        break;
                                }
                                String me, you;
                                me = (String) mMyMove.getText().toString();
                                you = (String) mTextView.getText().toString();
                                if (me.equals(you)) {
                                    //DRAW
                                    mTextView3.setText("DRAW");
                                    Log.d("if logic check", "comp : you " + me + ":" + you);
                                } else if ((you.equals("paper") && me.equals("rock")) ||
                                        (you.equals("rock") && me.equals("scissors")) ||
                                        (you.equals("scissors") && me.equals("paper"))) {
                                    //you win
                                    mTextView3.setText("YOU WIN");
                                    Log.d("if logic check", "comp : you " + me + ":" + you);
                                } else {
                                    //I win
                                    mTextView3.setText("I WIN");
                                    Log.d("if logic check", "comp : you " + me + ":" + you);
                                }
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            // Task failed with an exception
                            // ...
                        }
                    });


        }

    }
    //obj detection
    public class Analyser implements ImageAnalysis.Analyzer{
        private int degreesToFirebaseRotation(int degrees) {
            switch (degrees) {
                case 0:
                    return FirebaseVisionImageMetadata.ROTATION_0;
                case 90:
                    return FirebaseVisionImageMetadata.ROTATION_90;
                case 180:
                    return FirebaseVisionImageMetadata.ROTATION_180;
                case 270:
                    return FirebaseVisionImageMetadata.ROTATION_270;
                default:
                    throw new IllegalArgumentException(
                            "Rotation must be 0, 90, 180, or 270.");
            }
        }
        //object dect settings
        FirebaseVisionObjectDetectorOptions options =
                new FirebaseVisionObjectDetectorOptions.Builder()
                        .setDetectorMode(FirebaseVisionObjectDetectorOptions.STREAM_MODE)
                        .enableClassification()  // Optional
                        .build();
        //int dect using settings
        FirebaseVisionObjectDetector objectDetector =
                FirebaseVision.getInstance().getOnDeviceObjectDetector(options);
        @Override
        public void analyze(ImageProxy imageProxy, int degrees) {
            if (imageProxy == null || imageProxy.getImage() == null) {
                return;
            }
            Image mediaImage = imageProxy.getImage();
            int rotation = degreesToFirebaseRotation(degrees);
            FirebaseVisionImage image =
                    FirebaseVisionImage.fromMediaImage(mediaImage, rotation);
            objectDetector.processImage(image)
                    .addOnSuccessListener(
                            new OnSuccessListener<List<FirebaseVisionObject>>() {
                                @Override
                                public void onSuccess(List<FirebaseVisionObject> detectedObjects) {
                                    // Task completed successfully
                                    for (FirebaseVisionObject obj : detectedObjects) {
                                        Integer id = obj.getTrackingId();
                                        Rect bounds = obj.getBoundingBox();

                                        // If classification was enabled:
                                        int category = obj.getClassificationCategory();
                                        Float confidence = obj.getClassificationConfidence();
                                        Log.d("Ml", "onSuccess: "+category);
                                        //at this point pass to AutoML Model for costum id??
                                    }
                                }
                            })
                    .addOnFailureListener(
                            new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    // Task failed with an exception
                                    Log.d("Machine Learning", "onFailure: Ya Done Fucked Up");
                                }
                            });
        }
    }

}




