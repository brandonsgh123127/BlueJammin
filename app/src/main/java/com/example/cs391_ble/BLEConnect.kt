package com.example.cs391_ble

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.*
import android.content.Context
import android.net.ConnectivityManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatDrawableManager.get
import kotlinx.android.synthetic.main.activity_main.*
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import androidx.core.app.ComponentActivity
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.icu.text.SimpleDateFormat
import android.text.format.Time
import android.view.View
import androidx.core.content.PermissionChecker
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.error.SpotifyAppRemoteException
import java.lang.Thread.sleep
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.system.measureNanoTime


private val TAG = BLEConnect::class.java.simpleName

private const val STATE_DISCONNECTED = 0
private const val STATE_CONNECTING = 1
private const val STATE_CONNECTED = 2
private const val SCAN_PERIOD: Long = 5000
const val ACTION_GATT_CONNECTED = "com.example.cs391_ble.ACTION_GATT_CONNECTED"
const val ACTION_GATT_DISCONNECTED = "com.example.cs391_ble.ACTION_GATT_DISCONNECTED"
const val ACTION_GATT_SERVICES_DISCOVERED =
    "com.example.cs391_ble.ACTION_GATT_SERVICES_DISCOVERED"
/**
 * Coordinate-based system!
 */
private val BEACON1_COORD = Pair(1,0) //beacon 1 on right of diagram
private val BEACON2_COORD = Pair(0,2) // beacon 2 in middle
private val BEACON3_COORD = Pair(-1,0) // beacon 3 on left

var rssi1:Int = 0
var rssi2:Int = 0
var rssi3:Int = 0

private const val SYS_DELAY = 0.11111 // Reading each Device's rssi creates lag...  about 8 ns lag
private const val SIGNAL_S = .299792    //Speed(m) of signal per ns....

var time1Lst:MutableList<Long> = mutableListOf()
var time2Lst:MutableList<Long> = mutableListOf()
var time3Lst:MutableList<Long> = mutableListOf()

var curPlaylist = ""

/**
 * As of now, everything will be implemented inside the onCreate function, as there is
 * not enough time to fully implement everything with ease.
 */
class BLEConnect: AppCompatActivity()  {
    override fun onCreate(savedInstanceState: Bundle?) {
        //finish()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SpotifyAPIBUTTON.visibility = View.INVISIBLE
        progressBar.visibility = View.INVISIBLE
        /**
         * Listener to check and see if switch is pressed.  Will change mode from RSSI to TDOA
         */
        on_Switch.setOnCheckedChangeListener{buttonView, isChecked ->
            if(isChecked==false){
                on_Switch.text= getResources().getString(R.string.Beacon_RSSI)
            }
            else if(isChecked==true)
            {
                on_Switch.text= getResources().getString(R.string.Beacon_TDOA)
                val intent = Intent(this, TDOAConnect::class.java)
                startActivity(intent)
            }
        }
        initBLERSSI()
    }



    // RSSI Calculated-method
    private fun initBLERSSI(){
        isConnectedText.setText("Connected!")
        Toast.makeText(this, "Done", Toast.LENGTH_LONG).show()
        // Represents bluetooth device on phone.
        val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        }
        //Now, Initialize a BL Adapter for usage later on...
        // With bluetoothAdapter, one is able to interact with bluetooth devices
        bluetoothAdapter?.takeIf { !it.isEnabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 1)
        }
        /**
         * Now, Let's connect to a GATT server, aka the BLE devices...
         * Here is where the fun begins...
         */
        // FIRST BLE DEVICES..........
        var bluetoothLEScanner = bluetoothAdapter?.getBluetoothLeScanner()
        var device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice("80:6F:B0:6C:94:2B")
        var device2: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice("E0:7D:EA:2D:29:AB")
        var device3: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice("80:6F:B0:6C:8F:B6")
        var connectionState = STATE_DISCONNECTED
        /**
         * value of rssi resorts here to the callback!!
         */
        var bluetoothGatt: BluetoothGatt? = null
        var gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                /**
                 * INTENTS TO BE IMPLEMENTED
                 */
                val intentAction: String
                when (newState) {
                    //if conected state, change variables
                    BluetoothProfile.STATE_CONNECTED -> {
                        intentAction = ACTION_GATT_CONNECTED
                        connectionState = STATE_CONNECTED
                        broadcastUpdate(intentAction)
                        Log.i(TAG, "Connected to GATT server.")
                        Log.i(
                            TAG, "Attempting to start service discovery: " +
                                    bluetoothGatt?.discoverServices()
                        )
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        intentAction = ACTION_GATT_DISCONNECTED
                        connectionState = STATE_DISCONNECTED
                        Log.i(TAG, "Disconnected from GATT server.")
                        broadcastUpdate(intentAction)
                    }
                }
            }

        }

        // retrieves rssi below and device info...
        bluetoothGatt = device?.connectGatt(this, true, gattCallback)
        var bluetoothGatt2 = device2?.connectGatt(this,true,gattCallback)
        var bluetoothGatt3 = device3?.connectGatt(this,true,gattCallback)
        Log.i(TAG, "Trying to connect")

        // Stores all of the important info in this callback
        var scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType : Int, result:ScanResult) {
                gattCallback.onReadRemoteRssi(bluetoothGatt,rssi1,0)
                gattCallback.onReadRemoteRssi(bluetoothGatt2,rssi2,0)
                gattCallback.onReadRemoteRssi(bluetoothGatt3,rssi3,0)
                var isConnected:Boolean? = bluetoothGatt?.readRemoteRssi()
                Log.d("Time1!"," ")
                var isConnected2:Boolean? = bluetoothGatt2?.readRemoteRssi()
                var isConnected3:Boolean? = bluetoothGatt3?.readRemoteRssi()
                if(isConnected==true && isConnected2==true && isConnected3==true)
                    connectionState = STATE_CONNECTED
                else
                    connectionState = STATE_DISCONNECTED
                Log.d("isConnect","${isConnected}, ${isConnected2}, ${isConnected3}.")
                //Setting rssi ..... First implementation...
                if(result?.device?.address == "80:6F:B0:6C:94:2B")
                    time1Lst.add(measureNanoTime{rssi1 = result.getRssi()})
                    //Log.d("time1!!!","${measureNanoTime {rssi1 = result.getRssi()}}")
                else if(result?.device?.address == "E0:7D:EA:2D:29:AB")
                    time2Lst.add(measureNanoTime{rssi2 = result.getRssi()})
                    //Log.d("time2!!!","${measureNanoTime {rssi2 = result.getRssi()}}")
                else if(result?.device?.address == "80:6F:B0:6C:8F:B6")
                    time3Lst.add(measureNanoTime{rssi3 = result.getRssi()})
                    //Log.d("time3!!!","${measureNanoTime {rssi3 = result.getRssi()}}")
                Beacon1RSSI.text=Integer.toString(rssi1) + " dBm"
                Beacon2RSSI.text=Integer.toString(rssi2) + " dBm"
                Beacon3RSSI.text=Integer.toString(rssi3) + " dBm"
                angleCalc(bluetoothGatt)
            }
        }
        /**
         * Allows for user to accept location permission for location...
         */
        when (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            PackageManager.PERMISSION_GRANTED -> {
                bluetoothLEScanner?.startScan(scanCallback)
            }
            else -> requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
        bluetoothGatt?.connect()
        bluetoothGatt2?.connect()
        bluetoothGatt3?.connect()
        sleep(1000)
        Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT)
        gattCallback.onReadRemoteRssi(bluetoothGatt,rssi1,0)
        gattCallback.onReadRemoteRssi(bluetoothGatt2,rssi2,0)
        gattCallback.onReadRemoteRssi(bluetoothGatt3,rssi3,0)

        fixedRateTimer("timer", false, 0L, 10 * 1000) { //EVERY 5 SECONDS!
            this@BLEConnect.runOnUiThread {
                val avg1 = time1Lst.average() * SYS_DELAY * SIGNAL_S
                val avg2 = time2Lst.average() * SYS_DELAY * SIGNAL_S
                val avg3 = time3Lst.average() * SYS_DELAY * SIGNAL_S

                // THIS IS THE PART WHERE PLAYLIST WILL CHANGGE
                if(avg1 > avg2 && avg2 > avg3) // 2nd sector
                    if(SpotifyService.getPlayllist() != "spotify:playlist:71JXQ7EwfZMKmLPrzKZAB4" )
                        SpotifyService.play("spotify:playlist:71JXQ7EwfZMKmLPrzKZAB4")
                else if(avg2>avg1 && avg1> avg3)//sector 3
                    if(SpotifyService.getPlayllist() != "spotify:playlist:37i9dQZF1DX1PfYnYcpw8w" )
                        SpotifyService.play("spotify:playlist:37i9dQZF1DX1PfYnYcpw8w")
                else if(avg2>avg1 && avg3 > avg1)// sector 4
                    if(SpotifyService.getPlayllist() != "spotify:playlist:37i9dQZF1DXbYM3nMM0oPk" )
                        SpotifyService.play("spotify:playlist:37i9dQZF1DXbYM3nMM0oPk")
                else if(avg3>avg2 && avg1 > avg2) //1st sector
                    if(SpotifyService.getPlayllist() != "spotify:playlist:37i9dQZF1DWTlgzqHpWg4m" )
                        SpotifyService.play("spotify:playlist:37i9dQZF1DWTlgzqHpWg4m")
                time1Lst = mutableListOf()
                time2Lst = mutableListOf()
                time3Lst = mutableListOf()
            }
        }
    }

    /**
     * USES TDOA TO CALCULATE LOCATION
     */
    fun angleCalc(gatt:BluetoothGatt?){
        Log.d("list1 time", time1Lst.average().toString())
        Log.d("list2 time", time2Lst.average().toString())
        Log.d("list3 time", time3Lst.average().toString())
    }



    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }


}
class TDOAConnect:AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }
}









