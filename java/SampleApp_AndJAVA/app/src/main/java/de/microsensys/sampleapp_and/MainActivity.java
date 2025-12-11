package de.microsensys.sampleapp_and;

import android.content.Context;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import de.microsensys.exceptions.MssException;
import de.microsensys.exceptions.ReaderErrorException;
import de.microsensys.functions.RFIDFunctions;
import de.microsensys.utils.HelperFunctions;
import de.microsensys.utils.InterfaceTypeEnum;
import de.microsensys.utils.PermissionFunctions;
import de.microsensys.utils.PortTypeEnum;
import de.microsensys.utils.ProtocolTypeEnum;
import de.microsensys.utils.ReaderIDInfo;
import de.microsensys.utils.SystemMaskEnum;

public class MainActivity extends AppCompatActivity {

    //Buttons to send the commands
    Button button_clearText;
    RadioGroup radioGroupPortType;
    RadioButton radioUsb;
    RadioButton radioBT;
    RadioGroup radioGroupProt;
    RadioButton radioHF;
    RadioButton radioUHF;
    RadioButton radioLEGIC;
    EditText editText_ReaderName;
    Button button_connect;
    Button button_disconnect;
    Button button_readReaderID;
    Button button_identify;
    Button button_readBytes;
    Button button_writeBytes;
    CheckBox checkBox_legicFs;
    EditText editText_pageNum;

    //Text box where the info will be shown
    EditText editText_Results;

    //microsensys RFID package
    RFIDFunctions reader;

    private CheckConnectingReader mCheckThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

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
        checkBox_legicFs = findViewById(R.id.checkBox_legicFS);
        editText_pageNum = findViewById(R.id.editText_pageNum);

        editText_ReaderName = findViewById(R.id.editText_readerName);

        radioGroupPortType = findViewById(R.id.radiogroupPortType);
        radioUsb = findViewById(R.id.radio_Usb);
        radioBT = findViewById(R.id.radio_Bt);
        radioBT.setChecked(true);
        radioGroupProt = findViewById(R.id.radiogroupProtType);
        radioHF = findViewById(R.id.radio_HF);
        radioUHF = findViewById(R.id.radio_UHF);
        radioLEGIC = findViewById(R.id.radio_LEGIC);
        radioUHF.setChecked(true);

        editText_Results = findViewById(R.id.editText_Results);
    }

    @Override
    protected void onStop() {
        //For this sample, the reader must be not connected when stopped
        if (reader != null) {
            reader.terminate();
            reader = null;
        }

        super.onStop();
    }

    private void clearText() {
        editText_Results.setText("");
    }
    private void setEnabledRadioButtons(RadioGroup _rg, boolean _enabled){
        for(int i=0; i<_rg.getChildCount(); i++){
            _rg.getChildAt(i).setEnabled(_enabled);
        }
    }

    //Function to connect to the reader
    private void connect() {
        editText_Results.setText("");//Text box clear.

        //Check if the reader was already connected
        if (reader!=null) {
            if (reader.isConnected()) {
                //Already connected --> call "disconnect" first
                editText_Results.append("Disconnect first.\n");
                return;
            }
        }

        //Initialize object
        int portType = PortTypeEnum.USB;
        if (radioBT.isChecked())
            portType = PortTypeEnum.Bluetooth;

        //Check if there are permissions that need to be requested (USB permission is requested first when "initialize" is called)
        String[] neededPermissions = PermissionFunctions.getNeededPermissions(getApplicationContext(), portType);
        if (neededPermissions.length > 0){
            editText_Results.append("Allow permissions and try again.");
            requestPermissions(neededPermissions, 0);
            return;
        }

        reader = new RFIDFunctions(this, portType);

        //"Port Name" is only used for Bluetooth readers, for Serial interface is ignored
        reader.setPortName(editText_ReaderName.getText().toString());

        //Set Protocol Type & Interface Type (according to selection in UI)
        if (radioHF.isChecked()) {
            reader.setProtocolType(ProtocolTypeEnum.Protocol_3000);
            reader.setInterfaceType(InterfaceTypeEnum.HF);
        }
        if (radioUHF.isChecked()) {
            reader.setProtocolType(ProtocolTypeEnum.Protocol_v4);
            reader.setInterfaceType(InterfaceTypeEnum.UHF);
        }
        if (radioLEGIC.isChecked()) {
            reader.setProtocolType(ProtocolTypeEnum.Protocol_LEGIC);
            reader.setInterfaceType(InterfaceTypeEnum.HF);

            //LEGIC FS only supported for LEGIC Protocol
            checkBox_legicFs.setEnabled(true);
        }
        else{
            checkBox_legicFs.setChecked(false);
            checkBox_legicFs.setEnabled(false);
        }

        //Enable and disable UI controls
        button_disconnect.setEnabled(true);
        button_connect.setEnabled(false);
        setEnabledRadioButtons(radioGroupPortType, false);
        setEnabledRadioButtons(radioGroupProt, false);
        editText_ReaderName.setEnabled(false);
        editText_Results.append("Connecting...");

        try {
            //Once the instance is configured, call "initialize" to connect / open the communication port
            reader.initialize();

            //"initialize" just starts the process. Used Thread to check if the connection procedure has finished, and the result
            startCheckConnectingThread();
        } catch (MssException ex){
            //Exception thrown by "initialize" if something was wrong (for example Bluetooth is disabled)
            ex.printStackTrace();
            editText_Results.append("Initialize Exception: " + ex);
        }
    }

    private void disconnect() {
        //Hide the Keyboard
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(editText_Results.getWindowToken(), 0);
        editText_Results.append("\n\n/----------/\n");

        //Check the instance is initialized
        if (reader!=null){
            if (reader.isConnected()){
                //If it is connected --> call "terminate" to close the communication
                editText_Results.append("Disconnecting. \n");
                reader.terminate();
            }
            else editText_Results.append("Not connected. \n");
        }
        else editText_Results.append("Error initializing variable \"reader\" \n");

        //Enable and disable UI controls
        button_disconnect.setEnabled(false);
        button_connect.setEnabled(true);
        setEnabledRadioButtons(radioGroupPortType, true);
        setEnabledRadioButtons(radioGroupProt, true);
        editText_ReaderName.setEnabled(true);
    }

    private void readReaderID() {
        //Hide the Keyboard
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(editText_Results.getWindowToken(), 0);
        editText_Results.append("\n\n/----------/\n");

        //Check the instance is initialized
        if (reader!=null){
            if (reader.isConnected()){
                //Reader connected
                editText_Results.append("readerID \n");

                //Reader the Reader ID
                ReaderIDInfo result = reader.readReaderID();
                if (result!=null){
                    //Reader ID is successfully read
                    editText_Results.append(result.toHexString() + "\n");
                    editText_Results.append(result.toString());
                }
                else editText_Results.append("Error reading \"Reader ID\" \n");
            }
            else editText_Results.append("Not connected. \n");
        }
        else editText_Results.append("Error initializing variable \"reader\"");
    }

    private void identify() {
        //Hide the Keyboard
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(editText_Results.getWindowToken(), 0);
        editText_Results.append("\n\n/----------/\n");

        //Check the instance is initialized
        if (reader!=null){
            if (reader.isConnected()){
                //Reader connected
                editText_Results.append("Identify \n");
                byte[] UID;
                try {
                    //Scan for a transponder identifier
                    UID = reader.identify();
                    if (UID!=null){
                        //Transponder found --> Show hexadecimal representation of identifier
                        editText_Results.append("UID found... (Hexadecimal):\n  ");
                        editText_Results.append(HelperFunctions.bytesToHexStr(UID));
                        editText_Results.append("\n");
                    }
                    else editText_Results.append("No TAG found. \n");
                } catch (Exception e) {
                    e.printStackTrace();
                    editText_Results.append(e + "\n");
                }
            }
            else editText_Results.append("Not connected. \n");
        }
        else editText_Results.append("Error initializing variable \"reader\" \n");
    }

    private void readBytes() {
        //Hide the Keyboard
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(editText_Results.getWindowToken(), 0);
        editText_Results.append("\n\n/----------/\n");

        //Check the instance is initialized
        if (reader!=null){
            if (reader.isConnected()){
                //Reader connected

                //Set / clear File System Mask depending of state of CheckBox
                if (checkBox_legicFs.isChecked())
                    reader.setSystemMask(reader.getSystemMask() | SystemMaskEnum.GROUP_LEGIC_FS);
                else
                    reader.setSystemMask(reader.getSystemMask() & ~SystemMaskEnum.GROUP_LEGIC_FS);
                //Set Page Number (Only supported for LEGIC FS and UHF)
                reader.setPage(Integer.parseInt(editText_pageNum.getText().toString()));

                editText_Results.append("Read 16 bytes:\n");
                byte[] UID;
                try {
                    //Scan for a transponder identifier
                    UID = reader.identify();
                    if (UID!=null){
                        //Transponder found
                        editText_Results.append("UID found... (Hexadecimal):\n  ");
                        editText_Results.append(HelperFunctions.bytesToHexStr(UID));
                        editText_Results.append("\n");

                        //Read data from the transponder memory (for example Bytes 0-15)
                        byte[] data = reader.readBytes(UID, 0, 16);
                        if (data!=null){
                            //Data read from transponder --> Show Hexadecimal representation
                            editText_Results.append("/--/\n16 bytes of data read... (Hexadecimal):\n  ");
                            editText_Results.append(HelperFunctions.bytesToHexStr(data));
                            editText_Results.append("\n");
                        }
                        else editText_Results.append("Error reading.\n");
                    }
                    else editText_Results.append("No TAG near the Reader. \n");
                } catch (Exception e1) {
                    e1.printStackTrace();
                    editText_Results.append(e1 + "\n");
                }

            }
            else editText_Results.append("Not connected. \n");
        }
        else editText_Results.append("Error initializing variable \"reader\" \n");
    }

    private void writeBytes() {
        //Hide the Keyboard
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(editText_Results.getWindowToken(), 0);
        editText_Results.append("\n\n/----------/\n");

        //Check the instance is initialized
        if (reader!=null){
            if (reader.isConnected()){
                //Reader connected
                //Set / clear File System Mask depending of state of CheckBox
                if (checkBox_legicFs.isChecked())
                    reader.setSystemMask(reader.getSystemMask() | SystemMaskEnum.GROUP_LEGIC_FS);
                else
                    reader.setSystemMask(reader.getSystemMask() & ~SystemMaskEnum.GROUP_LEGIC_FS);
                //Set Page Number (Only supported for LEGIC FS and UHF)
                reader.setPage(Integer.parseInt(editText_pageNum.getText().toString()));
                editText_Results.append("Trying to write 16 bytes. Value (ASCII): \"1234567890123456\"\n");

                byte[] UID;
                try {
                    //Scan for a transponder identifier
                    UID = reader.identify();
                    if (UID!=null){
                        //Transponder found
                        editText_Results.append("UID found... (Hexadecimal):\n  ");
                        editText_Results.append(HelperFunctions.bytesToHexStr(UID));
                        editText_Results.append("\n");

                        //Prepare data to write
                        String auxstr = "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890";
                        byte[] dataaux = auxstr.getBytes(); 		//Get bytes from the String
                        byte[] data = new byte[16]; 				//Create the byte array where the data will be saved
                        for (int i=0;i<data.length;i++){ 			//Save the data in the byte array
                            if (i<dataaux.length) data[i]=dataaux[i];
                            else data[i]=0x00; 						//if there is not enough bytes, fill byte array with zeros
                        }
                        editText_Results.append("Data to write: ");
                        editText_Results.append(HelperFunctions.bytesToHexStr(data));
                        editText_Results.append("\n");

                        //Try to write from Byte 0, 16 bytes into a TAG memory
                        if (reader.writeBytes(UID, 0, data, false)){
                            editText_Results.append("Data written successfully.\n");
                        }
                        else editText_Results.append("Error writing. \n");
                    }
                    else editText_Results.append("No TAG near the Reader. \n");
                } catch (Exception e1) {
                    e1.printStackTrace();
                    editText_Results.append(e1 + "\n");
                    if (e1.getClass() == ReaderErrorException.class){
                        try {
                            editText_Results.append("0x" + Integer.toString(((ReaderErrorException) e1).getErrorNumber(), 16) + "\n");
                        } catch (Exception ignore){}
                    }
                }
            }
            else editText_Results.append("Not connected. \n");
        }
        else editText_Results.append("Error initializing variable \"reader\" \n");
    }

    private void startCheckConnectingThread(){
        if (mCheckThread!=null){
            mCheckThread.cancel();
            mCheckThread=null;
        }
        mCheckThread = new CheckConnectingReader();
        mCheckThread.start();
    }
    private class CheckConnectingReader extends Thread {
        private boolean loop;

        CheckConnectingReader(){
            loop = true;
        }

        @Override
        public void run() {
            while (loop){
                if (reader.isConnecting()){
                    //Still trying to connect -> Wait and continue
                    try {
                        //noinspection BusyWait
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(() -> editText_Results.append("."));
                    continue;
                }
                //Connecting finished! Check if connected or not connected
                if (reader.isConnected()) {
                    runOnUiThread(() -> editText_Results.append("\n CONNECTED \n"));
                }
                else{
                    runOnUiThread(() -> editText_Results.append("\n Reader NOT connected \n  -> PRESS DISCONNECT BUTTON"));
                }

                //Stop this thread
                cancel();
            }
        }

        void cancel(){
            loop = false;
        }
    }
}