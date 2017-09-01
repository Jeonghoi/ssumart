package com.jeonghoi.ssumart;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.jeonghoi.ssumart.Body.BodyMeasure;
import com.jeonghoi.ssumart.Commute.CommuteSummary;
import com.jeonghoi.ssumart.DataUpdater.UpdateListener;
import com.jeonghoi.ssumart.Weather.WeatherData;
import com.naver.speech.clientapi.SpeechRecognitionResult;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

/**
 * The main {@link Activity} class and entry point into the UI.
 */
public class HomeActivity extends Activity {

    /**
     * The IDs of {@link TextView TextViews} in {@link R.layout#activity_home} which contain the news
     * headlines.
     */
    private static final int[] NEWS_VIEW_IDS = new int[]{
            R.id.news_1,
            R.id.news_2,
            R.id.news_3,
            R.id.news_4,
    };

    /**
     * The listener used to populate the UI with weather data.
     */
    private final UpdateListener<WeatherData> weatherUpdateListener =
            new UpdateListener<WeatherData>() {
                @Override
                public void onUpdate(WeatherData data) {
                    if (data != null) {

                        // Populate the current temperature rounded to a whole number.
                        String temperature = String.format(Locale.US, "%d°",
                                Math.round(getLocalizedTemperature(data.currentTemperature)));
                        temperatureView.setText(temperature);

                        // Populate the 24-hour forecast summary, but strip any period at the end.
                        String summary = util.stripPeriod(data.daySummary);
                        weatherSummaryView.setText(summary);

                        // Populate the precipitation probability as a percentage rounded to a whole number.
                        String precipitation =
                                String.format(Locale.US, "%d%%", Math.round(100 * data.dayPrecipitationProbability));
                        precipitationView.setText(precipitation);

                        // Populate the icon for the current weather.
                        iconView.setImageResource(data.currentIcon);

                        // Show all the views.
                        temperatureView.setVisibility(View.VISIBLE);
                        weatherSummaryView.setVisibility(View.VISIBLE);
                        precipitationView.setVisibility(View.VISIBLE);
                        iconView.setVisibility(View.VISIBLE);
                    } else {

                        // Hide everything if there is no data.
                        temperatureView.setVisibility(View.GONE);
                        weatherSummaryView.setVisibility(View.GONE);
                        precipitationView.setVisibility(View.GONE);
                        iconView.setVisibility(View.GONE);
                    }
                }
            };

    /**
     * The listener used to populate the UI with news headlines.
     */
    private final UpdateListener<List<String>> newsUpdateListener =
            new UpdateListener<List<String>>() {
                @Override
                public void onUpdate(List<String> headlines) {

                    // Populate the views with as many headlines as we have and hide the others.
                    for (int i = 0; i < NEWS_VIEW_IDS.length; i++) {
                        if ((headlines != null) && (i < headlines.size())) {
                            newsViews[i].setText(headlines.get(i));
                            newsViews[i].setVisibility(View.VISIBLE);
                        } else {
                            newsViews[i].setVisibility(View.GONE);
                        }
                    }
                }
            };

    /**
     * The listener used to populate the UI with body measurements.
     */
    private final UpdateListener<BodyMeasure[]> bodyUpdateListener =
            new UpdateListener<BodyMeasure[]>() {
                @Override
                public void onUpdate(BodyMeasure[] bodyMeasures) {
                    if (bodyMeasures != null) {
                        bodyView.setBodyMeasures(bodyMeasures);
                        bodyView.setVisibility(View.VISIBLE);
                    } else {
                        bodyView.setVisibility(View.GONE);
                    }
                }
            };

    /**
     * The listener used to populate the UI with the commute summary.
     */
    private final UpdateListener<CommuteSummary> commuteUpdateListener =
            new UpdateListener<CommuteSummary>() {
                @Override
                public void onUpdate(CommuteSummary summary) {
                    if (summary != null) {
                        commuteTextView.setText(summary.text);
                        commuteTextView.setVisibility(View.VISIBLE);
                        travelModeView.setImageDrawable(summary.travelModeIcon);
                        travelModeView.setVisibility(View.VISIBLE);
                        if (summary.trafficTrendIcon != null) {
                            trafficTrendView.setImageDrawable(summary.trafficTrendIcon);
                            trafficTrendView.setVisibility(View.VISIBLE);
                        } else {
                            trafficTrendView.setVisibility(View.GONE);
                        }
                    } else {
                        commuteTextView.setVisibility(View.GONE);
                        travelModeView.setVisibility(View.GONE);
                        trafficTrendView.setVisibility(View.GONE);
                    }
                }
            };

    private TextView temperatureView;
    private TextView weatherSummaryView;
    private TextView precipitationView;
    private ImageView iconView;
    private TextView[] newsViews = new TextView[NEWS_VIEW_IDS.length];
    private BodyView bodyView;
    private TextView commuteTextView;
    private ImageView travelModeView;
    private ImageView trafficTrendView;

    private Weather weather;
    private News news;
    private Body body;
    private Commute commute;
    private Util util;

    private String wake_word = "바보야";



    //--------------------//

    private static final String TAG = HomeActivity.class.getSimpleName();
    private static final String CLIENT_ID = "VV0WpX9CMx_yMW_la4A5";
    // 1. "내 애플리케이션"에서 Client ID를 확인해서 이곳에 적어주세요.
    // 2. build.gradle (Module:app)에서 패키지명을 실제 개발자센터 애플리케이션 설정의 '안드로이드 앱 패키지 이름'으로 바꿔 주세요

    private RecognitionHandler handler;
    private NaverRecognizer naverRecognizer;

    private TextView txtResult;
    private Button btnStart;
    private String mResult;

    private AudioWriterPCM writer;


    private NaverTTSTask mNaverTTSTask;

    String[] mTextString;

    //-------------------//

    // Handle speech recognition Messages.
    private void handleMessage(Message msg) {
        switch (msg.what) {
            case R.id.clientReady:
                // Now an user can speak.
                txtResult.setText("Connected");
                writer = new AudioWriterPCM(
                        Environment.getExternalStorageDirectory().getAbsolutePath() + "/NaverSpeechTest");
                writer.open("Test");
                break;

            case R.id.audioRecording:
                writer.write((short[]) msg.obj);
                break;

            case R.id.partialResult:
                // Extract obj property typed with String.
                mResult = (String) (msg.obj);
                txtResult.setText(mResult);
/*
          if(mResult.matches(".*"+wake_word+".*")) {

              String mText;
              if (mResult.length() > 0) { //한글자 이상 1
                  mText = "안녕하세요";
                  mTextString = new String[]{mText};

                  //AsyncTask 실행
                  mNaverTTSTask = new NaverTTSTask();
                  mNaverTTSTask.execute(mTextString);
              } else {
                  Toast.makeText(HomeActivity.this, "말을 하세요", Toast.LENGTH_SHORT).show();
                  return;
              }

              mResult = "";
              txtResult.setText("Connecting...");
              btnStart.setText(R.string.str_stop);
              naverRecognizer.recognize();
          }

*/
                break;

            case R.id.finalResult:
                // Extract obj property typed with String array.
                // The first element is recognition result for speech.
                SpeechRecognitionResult speechRecognitionResult = (SpeechRecognitionResult) msg.obj;
                List<String> results = speechRecognitionResult.getResults();
                StringBuilder strBuf = new StringBuilder();
                for(String result : results) {
                    strBuf.append(result);
                    strBuf.append("\n");
                }
                mResult = strBuf.toString();
                txtResult.setText(mResult);

        /*//////////////////////

        //사용자가 입력한 텍스트를 이 배열변수에 담는다.
          //if(mResult_2.matches(".*"+wake_word+".*")) {

              String mText;
              if (mResult_2.length() > 0) { //한글자 이상 1
                  //mText = "안녕하세요";
                  mTextString = new String[]{mResult_2};

                  //AsyncTask 실행
                  mNaverTTSTask = new NaverTTSTask();
                  mNaverTTSTask.execute(mTextString);
              } else {
                  Toast.makeText(HomeActivity.this, "안녕이라고 말 하세요", Toast.LENGTH_SHORT).show();
                  return;
              }
         // }
        //////////////////*/


                break;

            case R.id.recognitionError:
                if (writer != null) {
                    writer.close();
                }

                mResult = "Error code : " + msg.obj.toString();
                txtResult.setText(mResult);
                btnStart.setText(R.string.str_start);
                btnStart.setEnabled(true);
                break;

            case R.id.clientInactive:
                if (writer != null) {
                    writer.close();
                }

                int idx = mResult.indexOf("\n");
                String mResult_2 = mResult.substring(0, idx);

                //사용자가 입력한 텍스트를 이 배열변수에 담는다.
                if(mResult_2.matches(".*"+wake_word+".*")) {


                    String mText;
                    if (mResult_2.length() > 0) { //한글자 이상 1
                        mText = "안녕하세요";
                        mTextString = new String[]{mText};

                        //AsyncTask 실행
                        mNaverTTSTask = new NaverTTSTask();
                        mNaverTTSTask.execute(mTextString);
                    } else {
                        Toast.makeText(HomeActivity.this, "안녕이라고 말 하세요", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }else {
                    mResult = "";
                    txtResult.setText("Connecting...");
                    btnStart.setText(R.string.str_stop);
                    naverRecognizer.recognize();
                }

                btnStart.setText(R.string.str_start);
                btnStart.setEnabled(true);
                break;
        }
    }
    //------------------//

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        //-------//

        txtResult = (TextView) findViewById(R.id.txt_result);
        btnStart = (Button) findViewById(R.id.btn_start);

        handler = new RecognitionHandler(this);
        naverRecognizer = new NaverRecognizer(this, handler, CLIENT_ID);


        btnStart.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if(!naverRecognizer.getSpeechRecognizer().isRunning()) {
                    // Start button is pushed when SpeechRecognizer's state is inactive.
                    // Run SpeechRecongizer by calling recognize().
                    mResult = "";
                    txtResult.setText("Connecting...");
                    btnStart.setText(R.string.str_stop);
                    naverRecognizer.recognize();
                } else {
                    Log.d(TAG, "stop and wait Final Result");
                    btnStart.setEnabled(false);

                    naverRecognizer.getSpeechRecognizer().stop();
                }
            }
        });

        //-------//


        temperatureView = (TextView) findViewById(R.id.temperature);
        weatherSummaryView = (TextView) findViewById(R.id.weather_summary);
        precipitationView = (TextView) findViewById(R.id.precipitation);
        iconView = (ImageView) findViewById(R.id.icon);
        for (int i = 0; i < NEWS_VIEW_IDS.length; i++) {
            newsViews[i] = (TextView) findViewById(NEWS_VIEW_IDS[i]);
        }
        bodyView = (BodyView) findViewById(R.id.body);
        commuteTextView = (TextView) findViewById(R.id.commuteText);
        travelModeView = (ImageView) findViewById(R.id.travelMode);
        trafficTrendView = (ImageView) findViewById(R.id.trafficTrend);

        weather = new Weather(this, weatherUpdateListener);
        news = new News(newsUpdateListener);
        body = new Body(this, bodyUpdateListener);
        commute = new Commute(this, commuteUpdateListener);
        util = new Util(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        weather.start();
        news.start();
        body.start();
        commute.start();


        mResult = "";
        txtResult.setText("Connecting...");
        btnStart.setText(R.string.str_stop);
        naverRecognizer.recognize();


        ///////
        // NOTE : initialize() must be called on start time.
        naverRecognizer.getSpeechRecognizer().initialize();
        ////////
    }

    @Override
    protected void onStop() {
        weather.stop();
        news.stop();
        body.stop();
        commute.stop();
        super.onStop();

        ////////////
        // NOTE : release() must be called on stop time.
        naverRecognizer.getSpeechRecognizer().release();
        ///
    }

    @Override
    protected void onResume() {
        super.onResume();
        util.hideNavigationBar(temperatureView);


    }

    //////////////
    // Declare handler for handling SpeechRecognizer thread's Messages.
    static class RecognitionHandler extends Handler {
        private final WeakReference<HomeActivity> mActivity;

        RecognitionHandler(HomeActivity activity) {
            mActivity = new WeakReference<HomeActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            HomeActivity activity = mActivity.get();
            if (activity != null) {
                activity.handleMessage(msg);
            }
        }
    }
    //////////////


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return util.onKeyUp(keyCode, event);
    }

    /**
     * Converts a temperature in degrees Fahrenheit to degrees Celsius, depending on the
     * {@link Locale}.
     */
    private double getLocalizedTemperature(double temperatureFahrenheit) {
        // First approximation: Fahrenheit for US and Celsius anywhere else.
        return Locale.US.equals(Locale.getDefault()) ?
                temperatureFahrenheit : (temperatureFahrenheit - 32.0) / 1.8;
    }

    private class NaverTTSTask extends AsyncTask<String[], Void, String>{

        @Override
        protected String doInBackground(String[]... strings) {
            //여기서 서버에 요청
            //APIExamTTS.main(mTextString);

            String clientId = "VV0WpX9CMx_yMW_la4A5";//애플리케이션 클라이언트 아이디값";
            String clientSecret = "jjbuTvcuo6";//애플리케이션 클라이언트 시크릿값";
            try {
                String text = URLEncoder.encode(mTextString[0], "UTF-8");
                String apiURL = "https://openapi.naver.com/v1/voice/tts.bin";
                URL url = new URL(apiURL);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("X-Naver-Client-Id", clientId);
                con.setRequestProperty("X-Naver-Client-Secret", clientSecret);
                // post request
                String postParams = "speaker=mijin&speed=3&text=" + text;
                con.setDoOutput(true);
                con.setDoInput(true);
                DataOutputStream wr = new DataOutputStream(con.getOutputStream());///여기서 에러 난다?
                Log.d(TAG, String.valueOf(wr));
                wr.writeBytes(postParams);
                wr.flush();
                wr.close();
                int responseCode = con.getResponseCode();
                BufferedReader br;
                if(responseCode==200) { // 정상 호출
                    InputStream is = con.getInputStream();
                    int read = 0;
                    byte[] bytes = new byte[1024];
                    //폴더를 만들어 줘야 겠다. 없으면 새로 생성하도록 해야 한다. 일단 Naver폴더에 저장하도록 하자.
                    File dir = new File(Environment.getExternalStorageDirectory()+"/", "Naver");
                    if(!dir.exists()){
                        dir.mkdirs();
                    }
                    // 랜덤한 이름으로 mp3 파일 생성
                    //String tempname = Long.valueOf(new Date().getTime()).toString();
                    String tempname = "naverttstemp"; //하나의 파일명으로 덮어쓰기 하자.
                    File f = new File(Environment.getExternalStorageDirectory() + File.separator + "Naver/" + tempname + ".mp3");
                    f.createNewFile();
                    OutputStream outputStream = new FileOutputStream(f);
                    while ((read =is.read(bytes)) != -1) {
                        outputStream.write(bytes, 0, read);
                    }
                    is.close();

                    //여기서 바로 재생하도록 하자. mp3파일 재생 어떻게 하지? 구글링!
                    String Path_to_file = Environment.getExternalStorageDirectory()+ File.separator+"Naver/"+tempname+".mp3";
                    MediaPlayer audioPlay = new MediaPlayer();
                    audioPlay.setDataSource(Path_to_file);
                    audioPlay.prepare();//이걸 해줘야 하는군. 없으면 에러난다.
                    audioPlay.start();
                    //재생하고 나서 파일을 지워줘야 하나? 이거참 고민이네... if문으로 분기 시켜야 하나?
                    //아니면 유니크한 파일로 만들지 말고 하나의 파일명으로 저장하게 할수도 있을듯...

                    //재생완료 확인~~
                    audioPlay.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        public void onCompletion(MediaPlayer mp) {
                            Log.i(TAG, "The Playing music is Completed.");


                            mResult = "";
                            txtResult.setText("Connecting...");
                            btnStart.setText(R.string.str_stop);
                            naverRecognizer.recognize();

                        }
                    });

                } else {  // 에러 발생
                    br = new BufferedReader(new InputStreamReader(con.getErrorStream()));
                    String inputLine;
                    StringBuffer response = new StringBuffer();
                    while ((inputLine = br.readLine()) != null) {
                        response.append(inputLine);
                    }
                    br.close();
                }
            } catch (Exception e) {
                System.out.println(e);
            }



            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            //방금 받은 파일명의 mp3가 있으면 플레이 시키자. 맞나 여기서 하는거?
            //아닌가 파일을 만들고 바로 실행되게 해야 하나? AsyncTask 백그라운드 작업중에...?

        }
    }

}
