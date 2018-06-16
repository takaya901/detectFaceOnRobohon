package jp.co.sharp.sample.camera.customize;

/**
 * シナリオファイルで使用する定数の定義クラス.<br>
 * <p/>
 * <p>
 * controlタグのtargetにはPackage名を設定すること<br>
 * scene、memory_p(長期記憶の変数名)、resolve variable(アプリ変数解決の変数名)、accostのwordはPackage名を含むこと<br>
 * </p>
 */
public class ScenarioDefinitions {

    /**
     * sceneタグを指定する文字列
     */
    public static final String TAG_SCENE = "scene";
    /**
     * accostタグを指定する文字列
     */
    public static final String TAG_ACCOST = "accost";
    /**
     * target属性を指定する文字列
     */
    public static final String ATTR_TARGET = "target";
    /**
     * function属性を指定する文字列
     */
    public static final String ATTR_FUNCTION = "function";
    /**
     * Package名.
     */
    protected static final String PACKAGE = "jp.co.sharp.sample.camera";
    /**
     * シナリオ共通: controlタグで指定するターゲット名.
     */
    public static final String TARGET = PACKAGE;
    /**
     * scene名: アプリ共通シーン
     */
    public static final String SCENE_COMMON = PACKAGE + ".scene_common";
    /**
     * function：アプリ終了を通知する.
     */
    public static final String FUNC_END_APP = "end_app";
    /**
     * accost名：accostテスト発話実行.
     */
    public static final String ACC_ACCOST =  ScenarioDefinitions.PACKAGE + ".accost.t1";
    /**
     * accost名：アプリ終了発話実行.
     */
    public static final String ACC_END_APP = ScenarioDefinitions.PACKAGE + ".app_end.t2";
    /**
     * 音声UIコールバック用定義.
     */
    public static final String COMMAND_RESOLVE_VARIABLE = "onVoiceUIResolveVariable";
    /**
     * resolve variable：アプリで変数解決する値.
     */
    public static final String RESOLVE_RACERECOG_RESULT = ScenarioDefinitions.PACKAGE + ":face_recog_result";
    /**
     * static クラスとして使用する.
     */
    private ScenarioDefinitions() {
    }

}
