package jp.co.sharp.sample.camera;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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


/**
 * カメラAPIを利用したサンプルを実装したActivity.
 */
public class MainActivity extends Activity implements MainActivityVoiceUIListener.MainActivityScenarioCallback {
    public static final String TAG = MainActivity.class.getSimpleName();

    /**
     * 顔認識結果通知Action定義.
     * */
    public static final String ACTION_RESULT_FACE_DETECTION = "jp.co.sharp.sample.camera.action.RESULT_FACE_DETECTION";
    /**
     * 写真/動画撮影結果通知Action定義.
     * */
    public static final String ACTION_RESULT_TAKE_PICTURE = "jp.co.sharp.sample.camera.action.RESULT_TAKE_PICTURE";
    /**
     * 動画撮影結果通知Action定義.
     * */
    public static final String ACTION_RESULT_REC_MOVIE = "jp.co.sharp.sample.camera.action.RESULT_REC_MOVIE";
    /**
     * 音声UI制御.
     */
    private VoiceUIManager mVoiceUIManager = null;
    /**
     * 音声UIイベントリスナー.
     */
    private MainActivityVoiceUIListener mMainActivityVoiceUIListener = null;
    /**
     * 音声UIの再起動イベント検知.
     */
    private VoiceUIStartReceiver mVoiceUIStartReceiver = null;
    /**
     * ホームボタンイベント検知.
     */
    private HomeEventReceiver mHomeEventReceiver;
    /**
     * カメラ結果取得用.
     */
    private CameraResultReceiver mCameraResultReceiver;
    /**
     * 顔認識結果発話用文字列.
     */
    private String mSpeachText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.activity_main);

        if(!OpenCVLoader.initDebug()){
            Log.i("OpenCV", "Failed");
        }else{
            Log.i("OpenCV", "successfully built !");
        }

        // 顔認識 首振無ボタン
        Button faceRecogButton = (Button)findViewById(R.id.face_recog_button);
        faceRecogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBroadcast(getIntentForFaceDetection("FALSE"));
            }
        });

        // 顔認識 首振有ボタン
        Button faceRecogMoveButton = (Button)findViewById(R.id.face_recog_move_button);
        faceRecogMoveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBroadcast(getIntentForFaceDetection("TRUE"));
            }
        });

        // 写真撮影 顔認識有ボタン
        Button cameraFaceButton = (Button)findViewById(R.id.shoot_camera_face_button);
        cameraFaceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBroadcast(getIntentForPhoto(true));
            }
        });

        // 写真撮影 顔認識無ボタン
        Button cameraButton = (Button)findViewById(R.id.shoot_camera_button);
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBroadcast(getIntentForPhoto(false));
            }
        });

        // 動画撮影 時間指定無ボタン
        Button recordMovieButton = (Button)findViewById(R.id.record_movie_button);
        recordMovieButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBroadcast(getIntentForVideo(0));
            }
        });

        // 動画撮影 10秒間ボタン
        Button recordMovie10secButton = (Button)findViewById(R.id.record_movie_10sec_button);
        recordMovie10secButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBroadcast(getIntentForVideo(10));
            }
        });


        //ホームボタンの検知登録.
        mHomeEventReceiver = new HomeEventReceiver();
        IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeEventReceiver, filterHome);

        //カメラ連携起動結果取得用レシーバー登録.
        mCameraResultReceiver = new CameraResultReceiver();
        IntentFilter filterCamera = new IntentFilter(ACTION_RESULT_TAKE_PICTURE);
        filterCamera.addAction(ACTION_RESULT_REC_MOVIE);
        filterCamera.addAction(ACTION_RESULT_FACE_DETECTION);
        registerReceiver(mCameraResultReceiver, filterCamera);

        //VoiceUI再起動の検知登録.
        mVoiceUIStartReceiver = new VoiceUIStartReceiver();
        IntentFilter filter = new IntentFilter(VoiceUIManager.ACTION_VOICEUI_SERVICE_STARTED);
        registerReceiver(mVoiceUIStartReceiver, filter);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume()");

        //VoiceUIManagerのインスタンス取得.
        if (mVoiceUIManager == null) {
            mVoiceUIManager = VoiceUIManager.getService(getApplicationContext());
        }
        //MainActivityVoiceUIListener生成.
        if (mMainActivityVoiceUIListener == null) {
            mMainActivityVoiceUIListener = new MainActivityVoiceUIListener(this);
        }
        //VoiceUIListenerの登録.
        VoiceUIManagerUtil.registerVoiceUIListener(mVoiceUIManager, mMainActivityVoiceUIListener);

        //Scene有効化.
        VoiceUIManagerUtil.enableScene(mVoiceUIManager, ScenarioDefinitions.SCENE_COMMON);

        //顔認識の結果があれば発話を実行する
        if (mVoiceUIManager != null && !mSpeachText.equals("")) {
            VoiceUIVariableUtil.VoiceUIVariableListHelper helper =
                new VoiceUIVariableUtil.VoiceUIVariableListHelper().addAccost(ScenarioDefinitions.ACC_ACCOST);
            VoiceUIManagerUtil.updateAppInfo(mVoiceUIManager, helper.getVariableList(), true);
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");

        //バックに回ったら発話を中止する.
        VoiceUIManagerUtil.stopSpeech();

        //VoiceUIListenerの解除.
        VoiceUIManagerUtil.unregisterVoiceUIListener(mVoiceUIManager, mMainActivityVoiceUIListener);

        //Scene無効化.
        VoiceUIManagerUtil.disableScene(mVoiceUIManager, ScenarioDefinitions.SCENE_COMMON);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");

        //ホームボタンの検知破棄.
        this.unregisterReceiver(mHomeEventReceiver);

        //カメラ連携起動結果取得用レシーバー破棄.
        this.unregisterReceiver(mCameraResultReceiver);

        //VoiceUI再起動の検知破棄.
        this.unregisterReceiver(mVoiceUIStartReceiver);

        //インスタンスのごみ掃除.
        mVoiceUIManager = null;
        mMainActivityVoiceUIListener = null;
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
                    String key = variable.getName();
                    Log.d(TAG, "onVoiceUIResolveVariable: " + key + ":" + variable.getStringValue());
                    if (ScenarioDefinitions.RESOLVE_RACERECOG_RESULT.equals(key)) {
                        variable.setStringValue(mSpeachText);
                    }
                    //発話後はリセットする
                    mSpeachText = "";
                }
                break;
            default:
                break;
        }
    }

    /**
     * ホームボタンの押下イベントを受け取るためのBroadcastレシーバークラス.<br>
     * <p/>
     * アプリは必ずホームボタンで終了する.
     */
    private class HomeEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "Receive Home button pressed");
            // ホームボタン押下でアプリ終了する.
            finish();
        }
    }

    /**
     * カメラ連携の結果を受け取るためのBroadcastレシーバー クラス.<br>
     * <p/>
     * それぞれの結果毎に処理を行う.
     */
    private class CameraResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "CameraResultReceiver#onReceive() : " + action);
            switch(action){
                case ACTION_RESULT_FACE_DETECTION:
                    int result = intent.getIntExtra(FaceDetectionUtil.EXTRA_RESULT_CODE, FaceDetectionUtil.RESULT_CANCELED);
                    if(result == FaceDetectionUtil.RESULT_OK){
                        HashMap<String,String> hashMapFace =
                            (HashMap<String,String>)intent.getSerializableExtra(FaceDetectionUtil.EXTRA_MAP_FACE_DETECTION);

                        //電話帳よりContact IDを参照する
                        AddressBookManager addressMng = AddressBookManager.getService(getApplicationContext());
                        List<String> nameList = new ArrayList<String>();

                        for (String key : hashMapFace.keySet()) {
                            int contactId = Integer.valueOf(hashMapFace.get(key));
                            Log.d(TAG, "contact id = " + contactId);
                            if(contactId == AddressBookCommonUtils.CONTACT_ID_OWNER){
                                //オーナーを検出した場合
                                nameList.add(getOwnerName(addressMng));
                            }else if(contactId == -1){
                                //電話帳登録されていない人を検出した場合
                                //登録されているにもかかわらず、カウントしてしまうケースがあるので、あまり参考にしない方がよい。
                            }else {
                                //電話帳登録されている人を検出した場合
                                nameList.add(getNameByContactId(addressMng, contactId));
                            }
                        }
                        //ペット検出結果
                        int pet = intent.getIntExtra(FaceDetectionUtil.EXTRA_PET_DETECTION, -1);
                        if(pet == FaceDetectionUtil.DOG_DETECTION){
                            nameList.add("犬");
                        }else if(pet == FaceDetectionUtil.CAT_DETECTION){
                            nameList.add("猫");
                        }else if(pet == FaceDetectionUtil.BOTH_DETECTION){
                            nameList.add("犬と猫");
                        }

                        //発話用のテキスト作成
                        if(nameList.isEmpty()){
                            mSpeachText = "誰も居ないなあ";
                        }else {
                            for (String name : nameList) {
                                mSpeachText += name;
                                mSpeachText += "、";
                            }
                            mSpeachText += "を見つけたよ。";
                        }
                    }
                    break;
                case ACTION_RESULT_TAKE_PICTURE:
                    result = intent.getIntExtra(ShootMediaUtil.EXTRA_RESULT_CODE, ShootMediaUtil.RESULT_CANCELED);
                    if(result == ShootMediaUtil.RESULT_OK) {
                        //撮影したファイルのパスを取得する
                        String path = intent.getStringExtra(ShootMediaUtil.EXTRA_PHOTO_TAKEN_PATH);
                        TextView textView = (TextView)findViewById(R.id.text_file_path);
                        textView.setText(path);

                        Bitmap bm = BitmapFactory.decodeFile(path);
                        Mat mat = new Mat();
                        Utils.bitmapToMat(bm, mat);
                        Mat detected = mat.clone();
                        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);
                        CascadeClassifier detector = new CascadeClassifier(
                                "/storage/emulated/0/DCIM/100SHARP/haarcascade_frontalface_default.xml");
                        if (detector.empty()) {
                            Log.i("OpenCV", "cascade not found");
                        }
                        try {
                            MatOfRect faces = new MatOfRect();
                            Size minFaceSize = new Size(mat.width(), mat.height());
                            detector.detectMultiScale(mat, faces, 1.1, 2, 2, new Size(),
                                    new Size());
                            Rect[] facesArray = faces.toArray();
                            for (int i = 0; i < facesArray.length; i++) {
                                Imgproc.rectangle(detected, facesArray[i].tl(), facesArray[i].br(), new Scalar(0, 0, 255), 3);
                            }
                            Imgcodecs.imwrite(path,detected);			// 画像データをJPG形式で保存

                        }
                        catch (Exception ex){
                            Log.i("OpenCV", ex.getMessage());
                        }
                    }
                    break;
                case ACTION_RESULT_REC_MOVIE:
                    result = intent.getIntExtra(ShootMediaUtil.EXTRA_RESULT_CODE, ShootMediaUtil.RESULT_CANCELED);
                    if(result == ShootMediaUtil.RESULT_OK) {
                        //撮影したファイルのパスを取得する
                        String path = intent.getStringExtra(ShootMediaUtil.EXTRA_VIDEO_TAKEN_PATH);
                        TextView textView = (TextView)findViewById(R.id.text_file_path);
                        textView.setText(path);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 顔検出実行用インテント取得関数
     * @param swing String型でTRUE or FALSE
     * @return 顔検出実行用intent
     */
    private Intent getIntentForFaceDetection(String swing) {
        Intent intent = new Intent(FaceDetectionUtil.ACTION_FACE_DETECTION_MODE);
        intent.setPackage(FaceDetectionUtil.PACKAGE);
        intent.putExtra(FaceDetectionUtil.EXTRA_REPLYTO_ACTION, ACTION_RESULT_FACE_DETECTION);
        intent.putExtra(FaceDetectionUtil.EXTRA_REPLYTO_PKG, getPackageName());
        intent.putExtra(FaceDetectionUtil.EXTRA_FACE_DETECTION_LENGTH, FaceDetectionUtil.EXTRA_FACE_DETECTION_LENGTH_NORMAL);
        intent.putExtra(FaceDetectionUtil.EXTRA_MOVE_HEAD, swing);
        return intent;
    }

    /**
     * 写真撮影実行用インテント取得関数
     * @param facedetect boolean型
     * @return 写真撮影実行用intent
     */
    private Intent getIntentForPhoto(boolean facedetect) {
        Intent intent = new Intent(ShootMediaUtil.ACTION_SHOOT_IMAGE);
        intent.setPackage(ShootMediaUtil.PACKAGE);
        intent.putExtra(ShootMediaUtil.EXTRA_FACE_DETECTION, facedetect);
        intent.putExtra(ShootMediaUtil.EXTRA_REPLYTO_ACTION, ACTION_RESULT_TAKE_PICTURE);
        intent.putExtra(ShootMediaUtil.EXTRA_REPLYTO_PKG, getPackageName());
        return intent;
    }

    /**
     * 動画撮影実行用インテント取得関数
     * @param time int型
     * @return 動画撮影実行用intent
     */
    private Intent getIntentForVideo(int time) {
        Intent intent = new Intent(ShootMediaUtil.ACTION_SHOOT_MOVIE);
        intent.setPackage(ShootMediaUtil.PACKAGE);
        intent.putExtra(ShootMediaUtil.EXTRA_MOVIE_LENGTH, time);
        intent.putExtra(ShootMediaUtil.EXTRA_REPLYTO_ACTION, ACTION_RESULT_REC_MOVIE);
        intent.putExtra(ShootMediaUtil.EXTRA_REPLYTO_PKG, getPackageName());
        return intent;
    }

    /**
     * オーナー名取得関数
     * @param addressMng AddressBookManager
     * @return オーナーの呼び方
     */
    private String getOwnerName(AddressBookManager addressMng) {
        String ret = "";
        try {
            OwnerProfileData ownerdata = addressMng.getOwnerProfileData();
            String nickName = ownerdata.getNickname();
            String firstName = ownerdata.getFirstname();
            String lastName = ownerdata.getLastname();

            //オーナーの呼び方の優先順位「ニックネーム」→「名(ファーストネーム)＋さん」→「姓(ラストネーム)＋さん」
            if(nickName != null && !("".equals(nickName)) ) {
                ret = nickName;
            }else if(firstName != null && !("".equals(firstName)) ) {
                firstName = firstName + "さん";
                ret = firstName;
            }else if(lastName != null && !("".equals(lastName)) ) {
                lastName = lastName + "さん";
                ret = lastName;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException occur : " + e.getMessage());
        }
        return ret;
    }

    /**
     * 電話帳登録されている人の名前取得関数
     * @param addressMng AddressBookManager
     * @param id Contact ID
     * @return 友達の呼び方
     */
    private String getNameByContactId(AddressBookManager addressMng, int id) {
        String ret = "";
        try {
            AddressBookData address = addressMng.getAddressBookData(id);
            String nickName = address.getNickname();
            String firstName = address.getFirstname();
            String lastName = address.getLastname();
            //オーナー以外の呼び方の優先順位「ニックネーム」→「姓(ラストネーム)＋さん」→「名(ファーストネーム)＋さん」
            if (nickName != null && !("".equals(nickName))) {
                ret = nickName;
            } else if (lastName != null && !("".equals(lastName))) {
                ret = lastName + "さん";
            } else if (firstName != null && !("".equals(firstName))) {
                ret = firstName + "さん";
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException occur : " + e.getMessage());
        }
        return ret;
    }

    /**
     * 音声UI再起動イベントを受け取るためのBroadcastレシーバークラス.<br>
     * <p/>
     * 稀に音声UIのServiceが再起動することがあり、その場合アプリはVoiceUIの再取得とListenerの再登録をする.
     */
    private class VoiceUIStartReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (VoiceUIManager.ACTION_VOICEUI_SERVICE_STARTED.equals(action)) {
                Log.d(TAG, "VoiceUIStartReceiver#onReceive():VOICEUI_SERVICE_STARTED");
                //VoiceUIManagerのインスタンス取得.
                mVoiceUIManager = VoiceUIManager.getService(getApplicationContext());
                if (mMainActivityVoiceUIListener == null) {
                    mMainActivityVoiceUIListener = new MainActivityVoiceUIListener(getApplicationContext());
                }
                //VoiceUIListenerの登録.
                VoiceUIManagerUtil.registerVoiceUIListener(mVoiceUIManager, mMainActivityVoiceUIListener);
            }
        }
    }
}
