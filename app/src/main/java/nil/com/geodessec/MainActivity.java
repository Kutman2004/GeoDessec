package nil.com.geodessec;

import android.Manifest;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
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

import com.emrekose.recordbutton.RecordButton;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;
import com.ontbee.legacyforks.cn.pedant.SweetAlert.SweetAlertDialog;

import org.florescu.android.rangeseekbar.RangeSeekBar;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements MediaPlayer.OnCompletionListener, SeekBar.OnSeekBarChangeListener {

    public static final String TAG = "MainActivity";
    private static String mFileName = null;
    PermissionListener permissionlistener;
    Runnable r;
    private MediaRecorder mRecorder;
    private RecordButton recordButton;
    private TextView textView;
    private TextView currentDurationLabel;
    private TextView totalDurationLabel;
    private StorageReference mStorageReference;
    private StorageReference filepathReference;
    private MediaPlayer m;
    private RangeSeekBar rangeSeekBar;
    private Handler mHandler = new Handler();
    private Utilities utils;
    private int length, stopPosition;
    private ImageView playButton;
    private boolean isPaused;
    private String audioDownloadFilePath;
    private Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            long currentDuration = m.getCurrentPosition();

            // Displaying Total Duration time
            // Displaying time completed playing
            totalDurationLabel.setText("" + getTime((Integer) rangeSeekBar.getSelectedMaxValue()));
            currentDurationLabel.setText("" + utils.milliSecondsToTimer(currentDuration));

            // Running the thread after 100 milliseconds
            mHandler.postDelayed(this, 100);
        }
    };

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
        new SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                .setTitleText("Get Started")
                .setContentText("Now Hold the mic and speak")
                .show();

        mRecorder = new MediaRecorder();
        recordButton = findViewById(R.id.recordBtn);
        textView = findViewById(R.id.textView);
        rangeSeekBar = findViewById(R.id.rangeSeekBar);
        currentDurationLabel = findViewById(R.id.textView2);
        totalDurationLabel = findViewById(R.id.textView3);
        playButton = findViewById(R.id.button);
        playButton.setImageResource(R.drawable.play);
        m = new MediaPlayer();
        m.setOnCompletionListener(this);
        audioDownloadFilePath = "";
        rangeSeekBar.setRangeValues(0, m.getDuration());
        rangeSeekBar.setNotifyWhileDragging(true);
        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/new_audio.mp3";

        Log.d(TAG, FirebaseStorage.getInstance().getReference() + "");
        mStorageReference = FirebaseStorage.getInstance().getReference();

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//
//                m.setAudioStreamType(AudioManager.STREAM_MUSIC);
                if (isPaused) {
                    m.seekTo(length);
                    m.start();
                    isPaused = false;
                }
                try {
                    m.setDataSource(audioDownloadFilePath);
                    m.prepare();
                    m.start();
                    updateProgressBar();
                    final int duration = m.getDuration() / 1000;
                    currentDurationLabel.setText("00:00");
                    totalDurationLabel.setText(getTime(duration));
                    final File localFile = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS), "New_Audio22.mp3");
                    try {
                        localFile.createNewFile();
                        mStorageReference.getFile(localFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                                Toast.makeText(MainActivity.this, "got file", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    rangeSeekBar.setRangeValues(0, duration);
                    rangeSeekBar.setSelectedMinValue(0);
                    rangeSeekBar.setSelectedMaxValue(duration);
                    rangeSeekBar.setEnabled(true);
                    rangeSeekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener<Integer>() {
                        @Override
                        public void onRangeSeekBarValuesChanged(RangeSeekBar bar, Integer minValue, Integer maxValue) {

                            m.seekTo(minValue * 1000);
                            currentDurationLabel.setText(getTime((Integer) bar.getSelectedMinValue()));
                            totalDurationLabel.setText(getTime((Integer) bar.getSelectedMaxValue()));
                        }
                    });


                } catch (IOException e) {
                    e.printStackTrace();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                final Handler handler = new Handler();
                handler.postDelayed(r = new Runnable() {
                    @Override
                    public void run() {

                        if (m.getCurrentPosition() >= (Integer) rangeSeekBar.getSelectedMaxValue() * 1000) {
                            m.seekTo((Integer) rangeSeekBar.getSelectedMinValue() * 1000);
                        }
                        handler.postDelayed(r, 100);
                    }
                }, 100);


            }
        });

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
        new SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                .setTitleText("Upload Successful")
                .setContentText("Now click the play button!")
                .show();
        filepathReference.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {

            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                Toast.makeText(MainActivity.this, "uploaded", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Upload Successful");

                textView.setText(R.string.upload_finish);
                mStorageReference.child("audio").child("new_audio.mp3").getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        audioDownloadFilePath = uri.toString();
                        //setVolumeControlStream(AudioManager.STREAM_MUSIC);
                    }
                });

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "failed to upload", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Upload Failure");
            }
        });
    }

    private String getTime(int seconds) {
        int hr = seconds / 3600;
        int rem = seconds % 3600;
        int mn = rem / 60;
        int sec = rem % 60;
        return String.format("%02d", hr) + ":" + String.format("%02d", mn) + ":" + String.format("%02d", sec);
    }

    private void updateProgressBar() {

        mHandler.postDelayed(mUpdateTimeTask, 200);
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


    public void pauseAudio(View view) {
        if (m != null && m.isPlaying()) {
            m.pause();
            length = m.getCurrentPosition();
            isPaused = true;
        }


    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (m.isPlaying() && m != null)
            m.stop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPosition = m.getCurrentPosition(); //stopPosition is an int
        m.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        m.seekTo(stopPosition);
        m.start();
    }
}
