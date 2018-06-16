package jp.co.sharp.sample.camera;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jp.co.sharp.android.rb.addressbook.AddressBookCommonUtils;
import jp.co.sharp.android.rb.addressbook.AddressBookManager;
import jp.co.sharp.android.rb.addressbook.AddressBookVariable.AddressBookData;
import jp.co.sharp.android.rb.addressbook.AddressBookVariable.OwnerProfileData;
import jp.co.sharp.android.rb.camera.FaceDetectionUtil;
import jp.co.sharp.android.rb.camera.ShootMediaUtil;
import jp.co.sharp.android.voiceui.VoiceUIManager;
import jp.co.sharp.android.voiceui.VoiceUIVariable;
import jp.co.sharp.sample.camera.customize.ScenarioDefinitions;
import jp.co.sharp.sample.camera.util.VoiceUIManagerUtil;
import jp.co.sharp.sample.camera.util.VoiceUIVariableUtil;

import static android.hardware.Camera.getNumberOfCameras;
import static android.hardware.Camera.open;


/**
 * カメラAPIを利用したサンプルを実装したActivity.
 */
public class MainActivity extends Activity implements MainActivityVoiceUIListener.MainActivityScenarioCallback, CameraBridgeViewBase.CvCameraViewListener {
    public static final String TAG = MainActivity.class.getSimpleName();

    private CameraBridgeViewBase mOpenCvCameraView;
    private boolean              mIsJavaCamera = true;
    private MenuItem             mItemSwitchCamera = null;
    private CascadeClassifier detector;
    private static int noFaceFrameNum = 0;
    private Size minFaceSize;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
        if(!OpenCVLoader.initDebug()){
            Log.i("OpenCV", "Failed");
        }else{
            Log.i("OpenCV", "successfully built !");
        }
        detector = new CascadeClassifier("/storage/emulated/0/DCIM/100SHARP/haarcascade_frontalface_default.xml");
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.tutorial1_activity_java_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        minFaceSize = new Size(640/10, 480/10);
//        Log.i("OpenCV", String.valueOf(minFaceSize));

    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(Mat inputFrame) {
        Mat src = inputFrame;
        Mat detected = src.clone();
        try{
            Imgproc.cvtColor(src, src, Imgproc.COLOR_RGB2GRAY);
            MatOfRect faces = new MatOfRect();
            detector.detectMultiScale(src, faces, 1.1, 2, 2, minFaceSize, new Size());
            Rect[] facesArray = faces.toArray();
            if (faces.empty()){
                noFaceFrameNum++;
//                Log.i("OpenCV", String.valueOf(noFaceFrameNum));
            }
            else{
                noFaceFrameNum = 0;
            }
            for (int i = 0; i < facesArray.length; i++) {
                Imgproc.rectangle(detected, facesArray[i].tl(), facesArray[i].br(), new Scalar(0, 0, 255), 3);
            }
        }
        catch (Exception ex){
            Log.i("OpenCV", ex.getMessage());
        }

        if (noFaceFrameNum >= 60){
            Log.i("OpenCV", "おきて！");
            noFaceFrameNum = 0;
        }

        return detected;
    }

    /**
     * VoiceUIListenerクラスからのコールバックを実装する.
     */
    @Override
    public void onExecCommand(String command, List<VoiceUIVariable> variables) {
        Log.v(TAG, "onExecCommand() : " + command);
        switch (command) {
            case ScenarioDefinitions.FUNC_END_APP:
                finish();
                break;
            case ScenarioDefinitions.COMMAND_RESOLVE_VARIABLE:
                for (VoiceUIVariable variable : variables) {
//                    String key = variable.getName();
//                    Log.d(TAG, "onVoiceUIResolveVariable: " + key + ":" + variable.getStringValue());
//                    if (ScenarioDefinitions.RESOLVE_RACERECOG_RESULT.equals(key)) {
//                        variable.setStringValue(mSpeachText);
//                    }
//                    //発話後はリセットする
//                    mSpeachText = "";
                }
                break;
            default:
                break;
        }
    }
}
