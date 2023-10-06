package de.microsensys.sampleapp_andkotlin

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
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
    private lateinit var button_clearText: Button
    private lateinit var radioGroupPortType: RadioGroup
    private lateinit var radioUsb: RadioButton
    private lateinit var radioBT: RadioButton
    private lateinit var radioBle: RadioButton
    private lateinit var radioGroupProt: RadioGroup
    private lateinit var radioHF: RadioButton
    private lateinit var radioUHF: RadioButton
    private lateinit var radioLEGIC: RadioButton
    private lateinit var editText_ReaderName: EditText
    private lateinit var button_connect: Button
    private lateinit var button_disconnect: Button
    private lateinit var button_readReaderID: Button
    private lateinit var button_identify: Button
    private lateinit var button_readBytes: Button
    private lateinit var button_writeBytes: Button
    private lateinit var checkBox_legicFs: CheckBox
    private lateinit var editText_pageNum: EditText

    //Text box where the info will be shown
    private lateinit var editText_Results: EditText
    private var mCheckThread: CheckConnectingReader? = null

    //microsensys RFID package
    private var reader: RFIDFunctions? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Load Android-Views from xml and set "OnClickListener"
        button_clearText = findViewById(R.id.button_cleartext)
        button_clearText.setOnClickListener {
            clearText()
        }
        button_connect = findViewById(R.id.button_connect)
        button_connect.setOnClickListener {
            connect()
        }
        button_disconnect = findViewById(R.id.button_disconnect)
        button_disconnect.setOnClickListener {
            disconnect()
        }
        button_disconnect.isEnabled = false
        button_readReaderID = findViewById(R.id.button_readerID)
        button_readReaderID.setOnClickListener {
            readReaderID()
        }
        button_identify = findViewById(R.id.button_identify)
        button_identify.setOnClickListener {
            identify()
        }
        button_readBytes = findViewById(R.id.button_readbytes)
        button_readBytes.setOnClickListener {
            readBytes()
        }
        button_writeBytes = findViewById(R.id.button_writebytes)
        button_writeBytes.setOnClickListener {
            writeBytes()
        }
        checkBox_legicFs = findViewById(R.id.checkBox_legicFS)
        editText_pageNum = findViewById(R.id.editText_pageNum)
        editText_ReaderName = findViewById(R.id.editText_readerName)
        radioGroupPortType = findViewById(R.id.radiogroupPortType)
        radioUsb = findViewById(R.id.radio_Usb)
        radioBT = findViewById(R.id.radio_BtClassic)
        radioBle = findViewById(R.id.radio_Ble)
        radioBT.isChecked = true
        radioGroupProt = findViewById(R.id.radiogroupProtType)
        radioHF = findViewById(R.id.radio_HF)
        radioUHF = findViewById(R.id.radio_UHF)
        radioLEGIC = findViewById(R.id.radio_LEGIC)
        radioUHF.isChecked = true
        editText_Results = findViewById(R.id.editText_Results)
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
        editText_Results.setText("")
    }

    private fun setEnabledRadioButtons(_rg: RadioGroup, _enabled: Boolean) {
        for (i in 0 until _rg.childCount) {
            _rg.getChildAt(i).isEnabled = _enabled
        }
    }

    //Function to connect to the reader
    private fun connect() {
        editText_Results.setText("") //Text box clear.

        //Check if the reader was already connected
        if (reader != null) {
            if (reader!!.isConnected) {
                //Already connected --> call "disconnect" first
                editText_Results.append("Disconnect first.\n")
                return
            }
        }

        //Initialize object
        var portType = PortTypeEnum.USB
        if (radioBT.isChecked) portType = PortTypeEnum.Bluetooth
        if (radioBle.isChecked) {
            portType = PortTypeEnum.BluetoothLE
        }

        //Check if there are permissions that need to be requested (USB permission is requested first when "initialize" is called)
        val neededPermissions =
            PermissionFunctions.getNeededPermissions(applicationContext, portType)
        if (neededPermissions.isNotEmpty()) {
            editText_Results.append("Allow permissions and try again.")
            requestPermissions(neededPermissions, 0)
            return
        }
        reader = RFIDFunctions(this, portType)

        //"Port Name" is only used for Bluetooth readers, for Serial interface is ignored
        reader!!.portName = editText_ReaderName.text.toString()

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
            checkBox_legicFs.isEnabled = true
        } else {
            checkBox_legicFs.isChecked = false
            checkBox_legicFs.isEnabled = false
        }

        //Enable and disable UI controls
        button_disconnect.isEnabled = true
        button_connect.isEnabled = false
        setEnabledRadioButtons(radioGroupPortType, false)
        setEnabledRadioButtons(radioGroupProt, false)
        editText_ReaderName.isEnabled = false
        editText_Results.append("Connecting...")
        try {
            //Once the instance is configured, call "initialize" to connect / open the communication port
            reader!!.initialize()

            //"initialize" just starts the process. Used Thread to check if the connection procedure has finished, and the result
            startCheckConnectingThread(reader!!)
        } catch (ex: MssException) {
            //Exception thrown by "initialize" if something was wrong (for example Bluetooth is disabled)
            ex.printStackTrace()
            editText_Results.append("Initialize Exception: $ex")
        }
    }

    private fun disconnect() {
        //Hide the Keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText_Results.windowToken, 0)
        editText_Results.append("\n\n/----------/\n")

        //Check the instance is initialized
        if (reader != null) {
            if (reader!!.isConnected) {
                //If it is connected --> call "terminate" to close the communication
                editText_Results.append("Disconnecting. \n")
                reader!!.terminate()
            } else editText_Results.append("Not connected. \n")
        } else editText_Results.append("Error initializing variable \"reader\" \n")

        //Enable and disable UI controls
        button_disconnect.isEnabled = false
        button_connect.isEnabled = true
        setEnabledRadioButtons(radioGroupPortType, true)
        setEnabledRadioButtons(radioGroupProt, true)
        editText_ReaderName.isEnabled = true
    }

    private fun readReaderID() {
        //Hide the Keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText_Results.windowToken, 0)
        editText_Results.append("\n\n/----------/\n")

        //Check the instance is initialized
        if (reader != null) {
            if (reader!!.isConnected) {
                //Reader connected
                editText_Results.append("readerID \n")

                //Reader the Reader ID
                val result = reader!!.readReaderID()
                if (result != null) {
                    //Reader ID is successfully read
                    editText_Results.append(result.toHexString() + "\n")
                    editText_Results.append(result.toString())
                } else editText_Results.append("Error reading \"Reader ID\" \n")
            } else editText_Results.append("Not connected. \n")
        } else editText_Results.append("Error initializing variable \"reader\"")
    }

    private fun identify() {
        //Hide the Keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText_Results.windowToken, 0)
        editText_Results.append("\n\n/----------/\n")

        //Check the instance is initialized
        if (reader != null) {
            if (reader!!.isConnected) {
                //Reader connected
                editText_Results.append("Identify \n")
                val UID: ByteArray?
                try {
                    //Scan for a transponder identifier
                    UID = reader!!.identify()
                    if (UID != null) {
                        //Transponder found --> Show hexadecimal representation of identifier
                        editText_Results.append("UID found... (Hexadecimal):\n  ")
                        editText_Results.append(HelperFunctions.bytesToHexStr(UID))
                        editText_Results.append("\n")
                    } else editText_Results.append("No TAG found. \n")
                } catch (e: Exception) {
                    e.printStackTrace()
                    editText_Results.append(e.toString() + "\n")
                }
            } else editText_Results.append("Not connected. \n")
        } else editText_Results.append("Error initializing variable \"reader\" \n")
    }

    private fun readBytes() {
        //Hide the Keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText_Results.windowToken, 0)
        editText_Results.append("\n\n/----------/\n")

        //Check the instance is initialized
        if (reader != null) {
            if (reader!!.isConnected) {
                //Reader connected

                //Set / clear File System Mask depending of state of CheckBox
                if (checkBox_legicFs.isChecked) reader!!.systemMask =
                    reader!!.systemMask or SystemMaskEnum.GROUP_LEGIC_FS
                else
                    reader!!.systemMask = reader!!.systemMask and SystemMaskEnum.GROUP_LEGIC_FS.inv()
                //Set Page Number (Only supported for LEGIC FS and UHF)
                reader!!.page = editText_pageNum.text.toString().toInt()
                editText_Results.append("Read 16 bytes:\n")
                val UID: ByteArray?
                try {
                    //Scan for a transponder identifier
                    UID = reader!!.identify()
                    if (UID != null) {
                        //Transponder found
                        editText_Results.append("UID found... (Hexadecimal):\n  ")
                        editText_Results.append(HelperFunctions.bytesToHexStr(UID))
                        editText_Results.append("\n")

                        //Read data from the transponder memory (for example Bytes 0-15)
                        val data = reader!!.readBytes(UID, 0, 16)
                        if (data != null) {
                            //Data read from transponder --> Show Hexadecimal representation
                            editText_Results.append("/--/\n16 bytes of data read... (Hexadecimal):\n  ")
                            editText_Results.append(HelperFunctions.bytesToHexStr(data))
                            editText_Results.append("\n")
                        } else editText_Results.append("Error reading.\n")
                    } else editText_Results.append("No TAG near the reader!!. \n")
                } catch (e1: Exception) {
                    e1.printStackTrace()
                    editText_Results.append(e1.toString() + "\n")
                }
            } else editText_Results.append("Not connected. \n")
        } else editText_Results.append("Error initializing variable \"reader\" \n")
    }

    private fun writeBytes() {
        //Hide the Keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText_Results.windowToken, 0)
        editText_Results.append("\n\n/----------/\n")

        //Check the instance is initialized
        if (reader != null) {
            if (reader!!.isConnected) {
                //Reader connected
                //Set / clear File System Mask depending of state of CheckBox
                if (checkBox_legicFs.isChecked) reader!!.systemMask =
                    reader!!.systemMask or SystemMaskEnum.GROUP_LEGIC_FS.toLong() else reader!!.systemMask =
                    reader!!.systemMask and SystemMaskEnum.GROUP_LEGIC_FS.toLong().inv()
                //Set Page Number (Only supported for LEGIC FS and UHF)
                reader!!.page = editText_pageNum.text.toString().toInt()
                editText_Results.append("Trying to write 16 bytes. Value (ASCII): \"1234567890123456\"\n")
                val UID: ByteArray?
                try {
                    //Scan for a transponder identifier
                    UID = reader!!.identify()
                    if (UID != null) {
                        //Transponder found
                        editText_Results.append("UID found... (Hexadecimal):\n  ")
                        editText_Results.append(HelperFunctions.bytesToHexStr(UID))
                        editText_Results.append("\n")

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
                        editText_Results.append("Data to write: ")
                        editText_Results.append(HelperFunctions.bytesToHexStr(data))
                        editText_Results.append("\n")

                        //Try to write from Byte 0, 16 bytes into a TAG memory
                        if (reader!!.writeBytes(UID, 0, data, false)) {
                            editText_Results.append("Data written successfully.\n")
                        } else editText_Results.append("Error writing. \n")
                    } else editText_Results.append("No TAG near the reader!!. \n")
                } catch (e1: Exception) {
                    e1.printStackTrace()
                    editText_Results.append(e1.toString() +"\n")
                    if (e1.javaClass == ReaderErrorException::class.java) {
                        try {
                            editText_Results.append("0x" + (e1 as ReaderErrorException).errorNumber.toString(16))
                        } catch (ignore: Exception) { }
                    }
                }
            } else editText_Results.append("Not connected. \n")
        } else editText_Results.append("Error initializing variable \"reader\" \n")
    }

    private fun startCheckConnectingThread(rfidFunctions: RFIDFunctions) {
        if (mCheckThread != null) {
            mCheckThread!!.cancel()
            mCheckThread = null
        }
        mCheckThread = CheckConnectingReader(rfidFunctions)
        mCheckThread!!.start()
    }

    private inner class CheckConnectingReader constructor(private val rfidFunctions: RFIDFunctions) : Thread() {
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
                    runOnUiThread { editText_Results.append(".") }
                    continue
                }
                //Connecting finished! Check if connected or not connected
                if (rfidFunctions.isConnected) {
                    runOnUiThread { editText_Results.append("\n CONNECTED \n") }
                } else {
                    runOnUiThread { editText_Results.append("\n Reader NOT connected \n  -> PRESS DISCONNECT BUTTON") }
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