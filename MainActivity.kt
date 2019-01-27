package com.krzyszotf.tempstation

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files.size
import android.content.Intent
import android.content.res.Resources
import android.os.Handler
import java.nio.charset.Charset
import java.util.*


class MainActivity : AppCompatActivity() {

    var mBluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    lateinit var outputStream: OutputStream
    lateinit var inputStream: InputStream
    var mmDevice: BluetoothDevice? = null
    var unit = "℃"
    var calculateFahrenheit = false
    lateinit var mmSocket: BluetoothSocket
    @Volatile
    var stopWorker: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        open.setOnClickListener {
            try {
                findBluetooth()
                openBluetoothConnection()
            } catch (ex: IOException) {
            }
        }

        close.setOnClickListener {
            try {
                closeBluetoothConnection()

            }
            catch (ex: IOException){}
        }

        celsiusButton.setOnClickListener {
            unit = resources.getString(R.string.celsius)
            calculateFahrenheit = false
        }

        fahrenheitButton.setOnClickListener {
            unit = resources.getString(R.string.fahrenheit)
            calculateFahrenheit = true
        }

    }

    private fun findBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null) {
            statusLabel.text = "Adapter bluetooth nie jest dostępny"
            return
        }

        if (!mBluetoothAdapter!!.isEnabled) {
            val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetooth, 0)
        }

        val pairedDevices = mBluetoothAdapter!!.bondedDevices
        if (pairedDevices.size > 0) {
            for (device in pairedDevices) {
                if (device.name == "raspberrypi") {
                    mmDevice = device
                    break
                }
            }
        }
        statusLabel.text = "Znaleziono stację"
    }

    private fun openBluetoothConnection() {
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        mmSocket = mmDevice!!.createRfcommSocketToServiceRecord(uuid)
        mmSocket.connect()
        outputStream = mmSocket.outputStream
        inputStream = mmSocket.inputStream

        beginListenForData()

        statusLabel.text = "Połączono ze stacją"
    }

    private fun beginListenForData() {
        val handler = Handler()
        val delimiter: Byte = 10 //Kod ASCII następnej linijki
        stopWorker = false
        var readBufferPosition = 0
        val readBuffer = ByteArray(1024)
        val workerThread = Thread(Runnable {
            while (!Thread.currentThread().isInterrupted && !stopWorker) {
                try {
                    val bytesAvailable = inputStream.available()
                    if (bytesAvailable > 0) {
                        val packetBytes = ByteArray(bytesAvailable)
                        inputStream.read(packetBytes)
                        packetBytes.let {
                            for (i in it) {
                                if (i == delimiter) {
                                    val encodedBytes = ByteArray(readBufferPosition)
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.count())
                                    var data = String(encodedBytes, Charset.forName("US-ASCII"))
                                    readBufferPosition = 0
                                    data = finalTemperature(data.toFloat()).toString()
                                    handler.post {
                                        tempTextView.text = "$data $unit"
                                    }
                                } else {
                                    readBuffer[readBufferPosition++] = i
                                }
                            }
                        }
                    }
                } catch (ex: IOException) {
                    stopWorker = true
                }
            }
        })
        workerThread.start()
    }

    private fun finalTemperature(currentTemperature: Float): Float{
        if(calculateFahrenheit){
            return currentTemperature * 1.8f + 32
        }
        return currentTemperature
    }

    private fun closeBluetoothConnection() {
        stopWorker = true
        outputStream.close()
        inputStream.close()
        mmSocket.close()
        statusLabel.text = "Zamknięto połączenie"
    }

}
