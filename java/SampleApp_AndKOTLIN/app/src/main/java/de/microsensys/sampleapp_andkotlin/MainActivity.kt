package de.microsensys.sampleapp_andkotlin

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import de.microsensys.exceptions.MssException
import de.microsensys.exceptions.ReaderErrorException
import de.microsensys.functions.RFIDFunctions
import de.microsensys.utils.HelperFunctions
import de.microsensys.utils.InterfaceTypeEnum
import de.microsensys.utils.PermissionFunctions
import de.microsensys.utils.PortTypeEnum
import de.microsensys.utils.ProtocolTypeEnum
import de.microsensys.utils.SystemMaskEnum

class MainActivity : AppCompatActivity() {

    //Buttons to send the commands
    private lateinit var buttonClearText: Button
    private lateinit var radioGroupPortType: RadioGroup
    private lateinit var radioUsb: RadioButton
    private lateinit var radioBT: RadioButton
    private lateinit var radioGroupProt: RadioGroup
    private lateinit var radioHF: RadioButton
    private lateinit var radioUHF: RadioButton
    private lateinit var radioLEGIC: RadioButton
    private lateinit var editTextReaderName: EditText
    private lateinit var buttonConnect: Button
    private lateinit var buttonDisconnect: Button
    private lateinit var buttonReadReaderID: Button
    private lateinit var buttonIdentify: Button
    private lateinit var buttonReadBytes: Button
    private lateinit var buttonWriteBytes: Button
    private lateinit var checkBoxLegicFs: CheckBox
    private lateinit var editTextPageNum: EditText

    //Text box where the info will be shown
    private lateinit var editTextResults: EditText

    //microsensys RFID package
    private var reader: RFIDFunctions? = null

    private var mCheckThread: CheckConnectingReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //Load Android-Views from xml and set "OnClickListener"
        buttonClearText = findViewById(R.id.button_cleartext)
        buttonClearText.setOnClickListener {
            clearText()
        }
        buttonConnect = findViewById(R.id.button_connect)
        buttonConnect.setOnClickListener {
            connect()
        }
        buttonDisconnect = findViewById(R.id.button_disconnect)
        buttonDisconnect.setOnClickListener {
            disconnect()
        }
        buttonDisconnect.isEnabled = false
        buttonReadReaderID = findViewById(R.id.button_readerID)
        buttonReadReaderID.setOnClickListener {
            readReaderID()
        }
        buttonIdentify = findViewById(R.id.button_identify)
        buttonIdentify.setOnClickListener {
            identify()
        }
        buttonReadBytes = findViewById(R.id.button_readbytes)
        buttonReadBytes.setOnClickListener {
            readBytes()
        }
        buttonWriteBytes = findViewById(R.id.button_writebytes)
        buttonWriteBytes.setOnClickListener {
            writeBytes()
        }
        checkBoxLegicFs = findViewById(R.id.checkBox_legicFS)
        editTextPageNum = findViewById(R.id.editText_pageNum)
        editTextReaderName = findViewById(R.id.editText_readerName)
        radioGroupPortType = findViewById(R.id.radiogroupPortType)
        radioUsb = findViewById(R.id.radio_Usb)
        radioBT = findViewById(R.id.radio_Bt)
        radioBT.isChecked = true
        radioGroupProt = findViewById(R.id.radiogroupProtType)
        radioHF = findViewById(R.id.radio_HF)
        radioUHF = findViewById(R.id.radio_UHF)
        radioLEGIC = findViewById(R.id.radio_LEGIC)
        radioUHF.isChecked = true
        editTextResults = findViewById(R.id.editText_Results)
    }

    override fun onStop() {
        //For this sample, the reader must be not connected when stopped
        if (reader != null) {
            reader!!.terminate()
            reader = null
        }
        super.onStop()
    }

    private fun clearText() {
        editTextResults.setText("")
    }

    private fun setEnabledRadioButtons(rg: RadioGroup, enabled: Boolean) {
        for (i in 0 until rg.childCount) {
            rg.getChildAt(i).isEnabled = enabled
        }
    }

    //Function to connect to the reader
    private fun connect() {
        editTextResults.setText("") //Text box clear.

        //Check if the reader was already connected
        if (reader != null) {
            if (reader!!.isConnected) {
                //Already connected --> call "disconnect" first
                editTextResults.append("Disconnect first.\n")
                return
            }
        }

        //Initialize object
        var portType = PortTypeEnum.USB
        if (radioBT.isChecked) portType = PortTypeEnum.Bluetooth

        //Check if there are permissions that need to be requested (USB permission is requested first when "initialize" is called)
        val neededPermissions =
            PermissionFunctions.getNeededPermissions(applicationContext, portType)
        if (neededPermissions.isNotEmpty()) {
            editTextResults.append("Allow permissions and try again.")
            requestPermissions(neededPermissions, 0)
            return
        }
        reader = RFIDFunctions(this, portType)

        //"Port Name" is only used for Bluetooth readers, for Serial interface is ignored
        reader!!.portName = editTextReaderName.text.toString()

        //Set Protocol Type & Interface Type (according to selection in UI)
        if (radioHF.isChecked) {
            reader!!.protocolType = ProtocolTypeEnum.Protocol_3000
            reader!!.interfaceType = InterfaceTypeEnum.HF
        }
        if (radioUHF.isChecked) {
            reader!!.protocolType = ProtocolTypeEnum.Protocol_v4
            reader!!.interfaceType = InterfaceTypeEnum.UHF
        }
        if (radioLEGIC.isChecked) {
            reader!!.protocolType = ProtocolTypeEnum.Protocol_LEGIC
            reader!!.interfaceType = InterfaceTypeEnum.HF

            //LEGIC FS only supported for LEGIC Protocol
            checkBoxLegicFs.isEnabled = true
        } else {
            checkBoxLegicFs.isChecked = false
            checkBoxLegicFs.isEnabled = false
        }

        //Enable and disable UI controls
        buttonDisconnect.isEnabled = true
        buttonConnect.isEnabled = false
        setEnabledRadioButtons(radioGroupPortType, false)
        setEnabledRadioButtons(radioGroupProt, false)
        editTextReaderName.isEnabled = false
        editTextResults.append("Connecting...")
        try {
            //Once the instance is configured, call "initialize" to connect / open the communication port
            reader!!.initialize()

            //"initialize" just starts the process. Used Thread to check if the connection procedure has finished, and the result
            startCheckConnectingThread(reader!!)
        } catch (ex: MssException) {
            //Exception thrown by "initialize" if something was wrong (for example Bluetooth is disabled)
            ex.printStackTrace()
            editTextResults.append("Initialize Exception: $ex")
        }
    }

    private fun disconnect() {
        //Hide the Keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editTextResults.windowToken, 0)
        editTextResults.append("\n\n/----------/\n")

        //Check the instance is initialized
        if (reader != null) {
            if (reader!!.isConnected) {
                //If it is connected --> call "terminate" to close the communication
                editTextResults.append("Disconnecting. \n")
                reader!!.terminate()
            } else editTextResults.append("Not connected. \n")
        } else editTextResults.append("Error initializing variable \"reader\" \n")

        //Enable and disable UI controls
        buttonDisconnect.isEnabled = false
        buttonConnect.isEnabled = true
        setEnabledRadioButtons(radioGroupPortType, true)
        setEnabledRadioButtons(radioGroupProt, true)
        editTextReaderName.isEnabled = true
    }

    private fun readReaderID() {
        //Hide the Keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editTextResults.windowToken, 0)
        editTextResults.append("\n\n/----------/\n")

        //Check the instance is initialized
        if (reader != null) {
            if (reader!!.isConnected) {
                //Reader connected
                editTextResults.append("readerID \n")

                //Reader the Reader ID
                val result = reader!!.readReaderID()
                if (result != null) {
                    //Reader ID is successfully read
                    editTextResults.append(result.toHexString() + "\n")
                    editTextResults.append(result.toString())
                } else editTextResults.append("Error reading \"Reader ID\" \n")
            } else editTextResults.append("Not connected. \n")
        } else editTextResults.append("Error initializing variable \"reader\"")
    }

    private fun identify() {
        //Hide the Keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editTextResults.windowToken, 0)
        editTextResults.append("\n\n/----------/\n")

        //Check the instance is initialized
        if (reader != null) {
            if (reader!!.isConnected) {
                //Reader connected
                editTextResults.append("Identify \n")
                val UID: ByteArray?
                try {
                    //Scan for a transponder identifier
                    UID = reader!!.identify()
                    if (UID != null) {
                        //Transponder found --> Show hexadecimal representation of identifier
                        editTextResults.append("UID found... (Hexadecimal):\n  ")
                        editTextResults.append(HelperFunctions.bytesToHexStr(UID))
                        editTextResults.append("\n")
                    } else editTextResults.append("No TAG found. \n")
                } catch (e: Exception) {
                    e.printStackTrace()
                    editTextResults.append(e.toString() + "\n")
                }
            } else editTextResults.append("Not connected. \n")
        } else editTextResults.append("Error initializing variable \"reader\" \n")
    }

    private fun readBytes() {
        //Hide the Keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editTextResults.windowToken, 0)
        editTextResults.append("\n\n/----------/\n")

        //Check the instance is initialized
        if (reader != null) {
            if (reader!!.isConnected) {
                //Reader connected

                //Set / clear File System Mask depending of state of CheckBox
                if (checkBoxLegicFs.isChecked) reader!!.systemMask =
                    reader!!.systemMask or SystemMaskEnum.GROUP_LEGIC_FS
                else
                    reader!!.systemMask = reader!!.systemMask and SystemMaskEnum.GROUP_LEGIC_FS.inv()
                //Set Page Number (Only supported for LEGIC FS and UHF)
                reader!!.page = editTextPageNum.text.toString().toInt()
                editTextResults.append("Read 16 bytes:\n")
                try {
                    //Scan for a transponder identifier
                    val uid = reader!!.identify()
                    if (uid != null) {
                        //Transponder found
                        editTextResults.append("UID found... (Hexadecimal):\n  ")
                        editTextResults.append(HelperFunctions.bytesToHexStr(uid))
                        editTextResults.append("\n")

                        //Read data from the transponder memory (for example Bytes 0-15)
                        val data = reader!!.readBytes(uid, 0, 16)
                        if (data != null) {
                            //Data read from transponder --> Show Hexadecimal representation
                            editTextResults.append("/--/\n16 bytes of data read... (Hexadecimal):\n  ")
                            editTextResults.append(HelperFunctions.bytesToHexStr(data))
                            editTextResults.append("\n")
                        } else editTextResults.append("Error reading.\n")
                    } else editTextResults.append("No TAG near the reader!!. \n")
                } catch (e1: Exception) {
                    e1.printStackTrace()
                    editTextResults.append(e1.toString() + "\n")
                }
            } else editTextResults.append("Not connected. \n")
        } else editTextResults.append("Error initializing variable \"reader\" \n")
    }

    private fun writeBytes() {
        //Hide the Keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editTextResults.windowToken, 0)
        editTextResults.append("\n\n/----------/\n")

        //Check the instance is initialized
        if (reader != null) {
            if (reader!!.isConnected) {
                //Reader connected
                //Set / clear File System Mask depending of state of CheckBox
                if (checkBoxLegicFs.isChecked) reader!!.systemMask =
                    reader!!.systemMask or SystemMaskEnum.GROUP_LEGIC_FS.toLong() else reader!!.systemMask =
                    reader!!.systemMask and SystemMaskEnum.GROUP_LEGIC_FS.toLong().inv()
                //Set Page Number (Only supported for LEGIC FS and UHF)
                reader!!.page = editTextPageNum.text.toString().toInt()
                editTextResults.append("Trying to write 16 bytes. Value (ASCII): \"1234567890123456\"\n")
                try {
                    //Scan for a transponder identifier
                    val uid = reader!!.identify()
                    if (uid != null) {
                        //Transponder found
                        editTextResults.append("UID found... (Hexadecimal):\n  ")
                        editTextResults.append(HelperFunctions.bytesToHexStr(uid))
                        editTextResults.append("\n")

                        //Prepare data to write
                        val auxstr =
                            "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"
                        val dataaux = auxstr.toByteArray() //Get bytes from the String
                        val data =
                            ByteArray(16) //Create the byte array where the data will be saved
                        for (i in data.indices) {            //Save the data in the byte array
                            if (i < dataaux.size) data[i] = dataaux[i] else data[i] =
                                0x00 //if there is not enough bytes, fill byte array with zeros
                        }
                        editTextResults.append("Data to write: ")
                        editTextResults.append(HelperFunctions.bytesToHexStr(data))
                        editTextResults.append("\n")

                        //Try to write from Byte 0, 16 bytes into a TAG memory
                        if (reader!!.writeBytes(uid, 0, data, false)) {
                            editTextResults.append("Data written successfully.\n")
                        } else editTextResults.append("Error writing. \n")
                    } else editTextResults.append("No TAG near the reader!!. \n")
                } catch (e1: Exception) {
                    e1.printStackTrace()
                    editTextResults.append(e1.toString() +"\n")
                    if (e1.javaClass == ReaderErrorException::class.java) {
                        try {
                            editTextResults.append("0x" + (e1 as ReaderErrorException).errorNumber.toString(16))
                        } catch (_: Exception) { }
                    }
                }
            } else editTextResults.append("Not connected. \n")
        } else editTextResults.append("Error initializing variable \"reader\" \n")
    }

    private fun startCheckConnectingThread(rfidFunctions: RFIDFunctions) {
        if (mCheckThread != null) {
            mCheckThread!!.cancel()
            mCheckThread = null
        }
        mCheckThread = CheckConnectingReader(rfidFunctions)
        mCheckThread!!.start()
    }

    private inner class CheckConnectingReader(private val rfidFunctions: RFIDFunctions) : Thread() {
        private var loop = true
        override fun run() {
            while (loop) {
                if (rfidFunctions.isConnecting) {
                    //Still trying to connect -> Wait and continue
                    try {
                        sleep(200)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                    runOnUiThread { editTextResults.append(".") }
                    continue
                }
                //Connecting finished! Check if connected or not connected
                if (rfidFunctions.isConnected) {
                    runOnUiThread { editTextResults.append("\n CONNECTED \n") }
                } else {
                    runOnUiThread { editTextResults.append("\n Reader NOT connected \n  -> PRESS DISCONNECT BUTTON") }
                }

                //Stop this thread
                cancel()
            }
        }

        fun cancel() {
            loop = false
        }
    }
}