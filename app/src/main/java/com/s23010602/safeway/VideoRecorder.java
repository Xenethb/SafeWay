package com.s23010602.safeway;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoRecorder {

    private static final String TAG = "VideoRecorder";
    private Recording currentRecording;
    private ExecutorService cameraExecutor;
    private VideoCapture<Recorder> videoCapture;

    public void startRecording(Context context, PreviewView previewView, String outputPath, Callable<Void> onRecordingStarted) {
        // Only initialize executor if it doesn't exist to prevent leaks
        if (cameraExecutor == null) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                if (!(context instanceof LifecycleOwner)) {
                    Log.e(TAG, "Context must be a LifecycleOwner");
                    return;
                }

                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // PRODUCTION UPGRADE: Set quality to balance file size and clarity
                QualitySelector qualitySelector = QualitySelector.from(Quality.SD,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD));

                Recorder recorder = new Recorder.Builder()
                        .setExecutor(cameraExecutor)
                        .setQualitySelector(qualitySelector)
                        .build();

                videoCapture = VideoCapture.withOutput(recorder);

                cameraProvider.bindToLifecycle(
                        (LifecycleOwner) context,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        videoCapture
                );

                File file = new File(outputPath);
                FileOutputOptions outputOptions = new FileOutputOptions.Builder(file).build();

                // PRODUCTION UPGRADE: Explicitly enable audio for SOS evidence
                @SuppressLint("MissingPermission")
                PendingRecording pendingRecording = videoCapture.getOutput()
                        .prepareRecording(context, outputOptions)
                        .withAudioEnabled(); // Critical for safety apps

                currentRecording = pendingRecording.start(
                        ContextCompat.getMainExecutor(context),
                        videoRecordEvent -> {
                            if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                                try {
                                    onRecordingStarted.call();
                                } catch (Exception e) {
                                    Log.e(TAG, "Callback error", e);
                                }
                            } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                                VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) videoRecordEvent;
                                if (finalizeEvent.hasError()) {
                                    Log.e(TAG, "Video recording error: " + finalizeEvent.getError());
                                }
                            }
                        });

            } catch (Exception e) {
                Log.e(TAG, "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public void stopRecording() {
        if (currentRecording != null) {
            currentRecording.stop();
            currentRecording = null;
        }
        // We keep the executor alive until the object is destroyed,
        // but we ensure we don't leak it in startRecording.
    }
}