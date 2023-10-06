package de.microsensys.sample_rest;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    @SuppressWarnings("FieldCanBeLocal")
    private final String destinationService = "http://localhost:19813";
    @SuppressWarnings("FieldCanBeLocal")
    private final String ApiKey = "hL4bA4nB4yI0vI0fC8fH7eT6";

    //Buttons to send the commands
    Button button_clearText;
    RadioGroup radioGroupPortType;
    RadioButton radioUsb;
    RadioButton radioBT;
    RadioButton radioBle;
    RadioGroup radioGroupProt;
    RadioButton radioHF;
    RadioButton radioUHF;
    EditText editText_ReaderName;
    Button button_connect;
    Button button_disconnect;
    Button button_readReaderID;
    Button button_identify;
    Button button_readBytes;
    Button button_writeBytes;
    EditText editText_pageNum;

    //Text box where the info will be shown
    EditText editText_Results;

    //private CheckConnectingReader mCheckThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Load Android-Views from xml and set "OnClickListener"
        button_clearText = findViewById(R.id.button_cleartext);
        button_clearText.setOnClickListener(arg0 -> clearText());
        button_connect = findViewById(R.id.button_connect);
        button_connect.setOnClickListener(v -> connect());
        button_disconnect = findViewById(R.id.button_disconnect);
        button_disconnect.setOnClickListener(v -> disconnect());
        button_disconnect.setEnabled(false);
        button_readReaderID = findViewById(R.id.button_readerID);
        button_readReaderID.setOnClickListener(v -> readReaderID());
        button_identify = findViewById(R.id.button_identify);
        button_identify.setOnClickListener(v -> identify());
        button_readBytes = findViewById(R.id.button_readbytes);
        button_readBytes.setOnClickListener(v -> readBytes());
        button_writeBytes = findViewById(R.id.button_writebytes);
        button_writeBytes.setOnClickListener(v -> writeBytes());
        editText_pageNum = findViewById(R.id.editText_pageNum);

        editText_ReaderName = findViewById(R.id.editText_readerName);

        radioGroupPortType = findViewById(R.id.radiogroupPortType);
        radioUsb = findViewById(R.id.radio_Usb);
        radioBT = findViewById(R.id.radio_BtClassic);
        radioBle = findViewById(R.id.radio_Ble);
        radioBT.setChecked(true);
        radioGroupProt = findViewById(R.id.radiogroupProtType);
        radioHF = findViewById(R.id.radio_HF);
        radioUHF = findViewById(R.id.radio_UHF);
        radioUHF.setChecked(true);

        editText_Results = findViewById(R.id.editText_Results);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        try {
            editText_Results.setText(performRestRequest("GET", "/api/iidservice/Version"));
            //Update UI depending on the connection status
            setUiDisabled(performRestRequest("GET", "/api/iidservice/doc/IsConnected").compareToIgnoreCase("true") == 0);
        } catch (Exception ex) {
            ex.printStackTrace();
            editText_Results.append("Exception: " + ex);
        }
    }

    protected void clearText() {
        editText_Results.setText("");
    }
    void setEnabledRadioButtons(RadioGroup _rg, boolean _enabled){
        for(int i=0; i<_rg.getChildCount(); i++){
            _rg.getChildAt(i).setEnabled(_enabled);
        }
    }
    private void setUiDisabled(boolean _isReaderConnected){
        button_disconnect.setEnabled(_isReaderConnected);
        button_connect.setEnabled(!_isReaderConnected);
        setEnabledRadioButtons(radioGroupPortType, !_isReaderConnected);
        setEnabledRadioButtons(radioGroupProt, !_isReaderConnected);
        editText_ReaderName.setEnabled(!_isReaderConnected);
    }

    private String performRestRequest(String _requestType, String _subPath) throws ExecutionException, InterruptedException {
        return RequestRunner.executeRequest(new RequestRunner(ApiKey, _requestType, destinationService + _subPath)).get();
    }

    //Function to connect to the reader
    protected void connect() {
        editText_Results.setText("");//Text box clear.

        //Check if the reader was already connected
        try {
            if (performRestRequest("GET", "/api/iidservice/doc/IsConnected").compareToIgnoreCase("true") == 0){
                //Already connected --> call "disconnect" first
                editText_Results.append("Disconnect first.\n");
                return;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            editText_Results.append("Exception: " + ex);
        }

        //Initialize object
        int portType = 4;
        if (radioBT.isChecked())
            portType = 2;
        if (radioBle.isChecked()) {
            portType = 10; //TODO value!?
        }
        int interfType = 1356;
        if (radioUHF.isChecked()) {
            interfType = 868;
        }

        try {
            //Once the instance is configured, call "initialize" to connect / open the communication port
            performRestRequest(
                    "POST",
                    "/api/iidservice/doc/interface/CurrentSettings?&PortName=" +
                            editText_ReaderName.getText().toString() +
                            "&PortType=" + portType +
                            "&InterfaceType=" + interfType
            );
        } catch (Exception ex){
            ex.printStackTrace();
            editText_Results.append("Exception: " + ex);
        }

        //Enable and disable UI controls
        setUiDisabled(true);
        editText_Results.append("Connecting...");

        try {
            //Once the instance is configured, call "initialize" to connect / open the communication port
            editText_Results.append(performRestRequest("POST", "/api/iidservice/doc/Initialize"));

            //TODO check IsConnected!?! In thread or here!?
            //startCheckConnectingThread();
        } catch (Exception ex){
            ex.printStackTrace();
            editText_Results.append("Exception: " + ex);
        }
    }

    protected void disconnect() {
        //Hide the Keyboard
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(editText_Results.getWindowToken(), 0);
        editText_Results.append("\n\n/----------/\n");

        editText_Results.append("Disconnecting. \n");
        try {
            editText_Results.append(performRestRequest("POST", "/api/iidservice/doc/Terminate"));
        } catch (Exception ex){
            ex.printStackTrace();
            editText_Results.append("Exception: " + ex);
            return;
        }

        //Enable and disable UI controls
        setUiDisabled(false);
    }

    protected void readReaderID() {
        //Hide the Keyboard
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(editText_Results.getWindowToken(), 0);
        editText_Results.append("\n\n/----------/\n");

        //Check the instance is initialized
        try {
            if (performRestRequest("GET", "/api/iidservice/doc/IsConnected").compareToIgnoreCase("true") == 0){
                //Reader connected
                editText_Results.append("readerID \n");

                //Reader the Reader ID
                String result = performRestRequest("GET", "/api/iidservice/doc/ReaderInfo");
                editText_Results.append(result);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            editText_Results.append("Exception: " + ex);
        }
    }

    protected void identify() {
        //Hide the Keyboard
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(editText_Results.getWindowToken(), 0);
        editText_Results.append("\n\n/----------/\n");

        //Check the instance is initialized
        try {
            if (performRestRequest("GET", "/api/iidservice/doc/IsConnected").compareToIgnoreCase("true") == 0){
                //Reader connected
                editText_Results.append("identify \n");

                //Scan for a transponder identifier
                String result = performRestRequest("GET", "/api/iidservice/doc/Identify");
                editText_Results.append(result);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            editText_Results.append("Exception: " + ex);
        }
    }

    protected void readBytes() {
        //Hide the Keyboard
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(editText_Results.getWindowToken(), 0);
        editText_Results.append("\n\n/----------/\n");

        //Check the instance is initialized
        try {
            if (performRestRequest("GET", "/api/iidservice/doc/IsConnected").compareToIgnoreCase("true") == 0){
                //Reader connected
                editText_Results.append("readBytes \n");

                //Scan for a transponder identifier
                String resultIdentify = performRestRequest("GET", "/api/iidservice/doc/Identify");
                String tagIDhex;
                JSONObject objResult = new JSONObject(resultIdentify);
                if (objResult.getInt("interfaceType") == 1356){
                    JSONObject scanResultObj = objResult.getJSONObject("scanResult");
                    tagIDhex = scanResultObj.getString("TagID");
                } else{
                    //TODO test
                    JSONArray scanResultList = objResult.getJSONArray("scanResult");
                    tagIDhex = scanResultList.getJSONObject(0).getString("TagID");
                }
                tagIDhex = tagIDhex.replace(" ","");

                editText_Results.append("UID found... (Hexadecimal):\n  ");
                editText_Results.append(tagIDhex);
                editText_Results.append("\n");

                String result = performRestRequest("GET", "/api/iidservice/doc/ReadBytes?&TagID="+tagIDhex+"&PageNum=3&FromByte=0&LengthBytes=16");
                editText_Results.append(result);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            editText_Results.append("Exception: " + ex);
        }
    }

    protected void writeBytes() {
//Hide the Keyboard
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(editText_Results.getWindowToken(), 0);
        editText_Results.append("\n\n/----------/\n");

        //Check the instance is initialized
        try {
            if (performRestRequest("GET", "/api/iidservice/doc/IsConnected").compareToIgnoreCase("true") == 0){
                //Reader connected
                editText_Results.append("readBytes \n");

                //Scan for a transponder identifier
                String resultIdentify = performRestRequest("GET", "/api/iidservice/doc/Identify");
                String tagIDhex;
                JSONObject objResult = new JSONObject(resultIdentify);
                if (objResult.getInt("interfaceType") == 1356){
                    JSONObject scanResultObj = objResult.getJSONObject("scanResult");
                    tagIDhex = scanResultObj.getString("TagID");
                } else{
                    //TODO test
                    JSONArray scanResultList = objResult.getJSONArray("scanResult");
                    tagIDhex = scanResultList.getJSONObject(0).getString("TagID");
                }
                tagIDhex = tagIDhex.replace(" ","");

                editText_Results.append("UID found... (Hexadecimal):\n  ");
                editText_Results.append(tagIDhex);
                editText_Results.append("\n");

                String result = performRestRequest("POST", "/api/iidservice/doc/WriteBytes?&TagID="+tagIDhex+"&PageNum=3&FromByte=0&Data=31323334");
                editText_Results.append(result);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            editText_Results.append("Exception: " + ex);
        }
    }
}