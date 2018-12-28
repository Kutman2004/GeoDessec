package nil.com.geodessec;

import android.Manifest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements MediaPlayer.OnCompletionListener, SeekBar.OnSeekBarChangeListener {

    private MediaRecorder mRecorder;
    public static final String TAG = "MainActivity";
    private ImageView recordButton;
    private TextView textView;
    private TextView currentDurationLabel;
    private TextView totalDurationLabel;
    PermissionListener permissionlistener;
    private static String mFileName = null;
    private StorageReference mStorageReference;
    private StorageReference filepathReference;
    private MediaPlayer m;
    private SeekBar seekProgressBar;
    private Handler mHandler = new Handler();
    private Utilities utils;
    private VisualizerView visualizerView;
    private Visualizer visualizer;
    private int length;
    private boolean isPaused;
    private String audioDownloadFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permissionlistener = new PermissionListener() {
            @Override
            public void onPermissionGranted() {
                Toast.makeText(MainActivity.this, "Permission Granted", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPermissionDenied(List<String> deniedPermissions) {
                Log.d(TAG, deniedPermissions.toString());
                Toast.makeText(MainActivity.this, "Permission Denied", Toast.LENGTH_SHORT).show();

            }
        };
        //start your camera
        TedPermission.with(MainActivity.this)
                .setPermissionListener(permissionlistener)
                .setDeniedMessage("If you reject permission,you can not use this service\n\nPlease turn on permissions at [Settings] > [Permission]")
                .setPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .check();
        setContentView(R.layout.activity_main);

        utils = new Utilities();

        mRecorder = new MediaRecorder();
        recordButton = findViewById(R.id.imageView);
        textView = findViewById(R.id.textView);
        seekProgressBar = findViewById(R.id.seekBar);
        currentDurationLabel = findViewById(R.id.textView2);
        totalDurationLabel = findViewById(R.id.textView3);
        visualizerView = findViewById(R.id.visualizerview);
        m = new MediaPlayer();
        seekProgressBar.setOnSeekBarChangeListener(this); // Important
        m.setOnCompletionListener(this);
        audioDownloadFilePath = "";

        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/new_audio.mp3";

        mStorageReference = FirebaseStorage.getInstance().getReference();

        recordButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    startRecording();
                    textView.setText(R.string.init_text);

                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    textView.setText(R.string.stopped_text);
                    stopRecording();
                    Toast.makeText(MainActivity.this, "Uploading...Please wait", Toast.LENGTH_LONG).show();

                }
                return true;
            }
        });
    }

    public void startRecording() {

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        mRecorder.setOutputFile(mFileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mRecorder.prepare();
            mRecorder.start();
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed");
        } catch (IllegalStateException e) {
            Log.e(TAG, "start failed");
        }


    }

    public void stopRecording() {

        if (mRecorder != null) {

            try {
                mRecorder.stop();
            } catch (RuntimeException e) {
                mRecorder.release();
            }

        }
        if (mRecorder != null) {
            mRecorder.release();
        }
        mRecorder = null;
        uploadAudio();
    }

    private void uploadAudio() {

        filepathReference = mStorageReference.child("audio").child("new_audio.mp3");
        Uri uri = Uri.fromFile(new File(mFileName));

        filepathReference.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {

            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Toast.makeText(MainActivity.this, "uploaded", Toast.LENGTH_SHORT).show();
                Log.d(TAG,"Upload Successful");
                textView.setText(R.string.upload_finish);



            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "failed to upload", Toast.LENGTH_SHORT).show();
                Log.d(TAG,"Upload Failure");
            }
        });
    }

    public void playAudio(View view) {

        m = new MediaPlayer();

        Log.d(TAG,mStorageReference.child("audio").child("new_audio.mp3").getDownloadUrl().toString());
        mStorageReference.child("audio").child("new_audio.mp3").getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                audioDownloadFilePath = uri.toString();
            }
        });

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setupVisualizerFxAndUI();
        // Make sure the visualizer is enabled only when you actually want to
        // receive data, and
        // when it makes sense to receive data.
        visualizer.setEnabled(true);
        // When the stream ends, we don't need to collect any more data. We
        // don't do this ins
        // setupVisualizerFxAndUI because we likely want to have more,
        // non-Visualizer related code
        // in this callback.
        m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mediaPlayer) {
                visualizer.setEnabled(true);


            }
        });


        m.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            m.setDataSource(audioDownloadFilePath);
            m.prepare();
            m.start();

            if (isPaused) {
                m.seekTo(length);
                isPaused = false;

            }
            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    Toast.makeText(MainActivity.this, "finished", Toast.LENGTH_SHORT).show();
                }
            });

            seekProgressBar.setProgress(0);
            seekProgressBar.setMax(100);

            // Updating progress bar
            updateProgressBar();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private void setupVisualizerFxAndUI() {
        visualizer = new Visualizer(m.getAudioSessionId());
        visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
        visualizer.setDataCaptureListener(
                new Visualizer.OnDataCaptureListener() {
                    public void onWaveFormDataCapture(Visualizer visualizer,
                                                      byte[] bytes, int samplingRate) {
                        visualizerView.updateVisualizer(bytes);
                    }

                    public void onFftDataCapture(Visualizer visualizer,
                                                 byte[] bytes, int samplingRate) {
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, false);
    }


    private void updateProgressBar() {

        mHandler.postDelayed(mUpdateTimeTask, 100);


    }

    private Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            long totalDuration = m.getDuration();
            long currentDuration = m.getCurrentPosition();

            // Displaying Total Duration time
            totalDurationLabel.setText("" + utils.milliSecondsToTimer(totalDuration));
            // Displaying time completed playing
            currentDurationLabel.setText("" + utils.milliSecondsToTimer(currentDuration));

            // Updating progress bar
            int progress = (utils.getProgressPercentage(currentDuration, totalDuration));
            //Log.d("Progress", ""+progress);
            seekProgressBar.setProgress(progress);

            // Running the thread after 100 milliseconds
            mHandler.postDelayed(this, 100);
        }
    };

    public void pauseAudio(View view) {
        if (m != null && m.isPlaying()) {
            m.pause();
            length = m.getCurrentPosition();
            isPaused = true;
        }


    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        m.reset();
        try {
            m.setDataSource(audioDownloadFilePath);
            m.prepare();
            m.start();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mHandler.removeCallbacks(mUpdateTimeTask);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mHandler.removeCallbacks(mUpdateTimeTask);
        int totalDuration = m.getDuration();
        int currentPosition = utils.progressToTimer(seekBar.getProgress(), totalDuration);

        // forward or backward to certain seconds
        m.seekTo(currentPosition);

        // update timer progress again
        updateProgressBar();
    }


}
