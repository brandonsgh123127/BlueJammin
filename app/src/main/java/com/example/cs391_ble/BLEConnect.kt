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
import com.google.firebase.firestore.DocumentReference
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.error.SpotifyAppRemoteException
import java.lang.Math.log
import java.lang.Math.sqrt
import java.lang.Thread.sleep
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.fixedRateTimer
import kotlin.system.measureNanoTime


private val TAG = BLEConnect::class.java.simpleName

private const val STATE_DISCONNECTED = 0
private const val STATE_CONNECTING = 1
private const val STATE_CONNECTED = 2
private const val SCAN_PERIOD: Long = 5000
const val ACTION_GATT_CONNECTED = "com.example.cs391_ble.ACTION_GATT_CONNECTED"
const val ACTION_GATT_DISCONNECTED = "com.example.cs391_ble.ACTION_GATT_DISCONNECTED"
/**
 * Coordinate-based system!
 */
private val BEACON1_COORD = Pair(2,0) //beacon 1 on right of diagram
private val BEACON2_COORD = Pair(0,2) // beacon 2 in middle
private val BEACON3_COORD = Pair(0,0) // beacon 3 on left
private val width = 10.5
private val height = 10.5

 var rssi1:Int = 0
var rssi2:Int = 0
var rssi3:Int = 0
private var rA = 0.0
private var rB = 0.0
private var rC = 0.0
private var ABx = BEACON2_COORD.first - BEACON1_COORD.first
private var ABy = BEACON2_COORD.second - BEACON1_COORD.second

private var ACx = BEACON3_COORD.first - BEACON1_COORD.first
private var ACy = BEACON3_COORD.second - BEACON1_COORD.second

private var BCx = BEACON3_COORD.first - BEACON2_COORD.first
private var BCy = BEACON3_COORD.second - BEACON2_COORD.second

// VECTOR! square root of sum of squares
private var AB = sqrt(Math.pow(ABx.toDouble(), 2.0) + Math.pow(ABy.toDouble(), 2.0))
private var AC = sqrt(Math.pow(ACx.toDouble(), 2.0) + Math.pow(ACy.toDouble(), 2.0))
private var BC = sqrt(Math.pow(BCx.toDouble(), 2.0) + Math.pow(BCy.toDouble(), 2.0))

//(radius squared plus ab squared - radius squared) divided by 2AB
private var ax =0.0
private var ay =0.0
private var bx =0.0
private var by =0.0
private var cx = 0.0
private var cy =0.0

// SAVE DATA TO THIS VARIABLE GIVEN
// FIREBASE DB...
private const val RSSIA_KEY="rssiA"
private const val RSSIB_KEY="rssiB"
private const val RSSIC_KEY="rssiC"
var rssiSave:HashMap<String,Int> = HashMap<String,Int>()










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
                on_Switch.text= getResources().getString(R.string.Beacon_SUCCESS)
                val intent = Intent(this, PACKETConnect::class.java)
                startActivity(intent)
            }
        }
        initBLERSSI()
    }


    // RSSI Calculated-method
    // USED FOR SHORT RANGE COMMUNICATION...
    private fun initBLERSSI(){
        val intent = Intent(this, DBPush::class.java)  // used for later usage...

        isConnectedText.setText("Connected!")
        Toast.makeText(this, "Done", Toast.LENGTH_LONG).show()
    // Represents bluetooth Adapter for phone to connect to services...
        val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        }
        // INPUT MAC ADDRESS FOR USAGE...
        var bluetoothLEScanner = bluetoothAdapter?.getBluetoothLeScanner()
        var device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice("80:6F:B0:6C:94:2B")
        var device2: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice("E0:7D:EA:2D:29:AB")
        var device3: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice("80:6F:B0:6C:8F:B6")
        var connectionState = STATE_DISCONNECTED
        //Now, request permission for bluetooth...
        bluetoothAdapter?.takeIf { !it.isEnabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 1)
        }
        /*
         * Now, Let's connect to a GATT server, aka the BLE devices...
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
        Log.i(TAG, "GATT Connection...")

        // Stores all of the important info in this callback
        // Allows reading of RSSI of each device in each scan event...
        var scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType : Int, result:ScanResult) {
                gattCallback.onReadRemoteRssi(bluetoothGatt,rssi1,0)
                gattCallback.onReadRemoteRssi(bluetoothGatt2,rssi2,0)
                gattCallback.onReadRemoteRssi(bluetoothGatt3,rssi3,0)
                var isConnected:Boolean? = bluetoothGatt?.readRemoteRssi()
                var isConnected2:Boolean? = bluetoothGatt2?.readRemoteRssi()
                var isConnected3:Boolean? = bluetoothGatt3?.readRemoteRssi()
                Log.d("isConnect","${isConnected}, ${isConnected2}, ${isConnected3}.")
                //Setting rssi ..... First implementation...
                if(result?.device?.address == "80:6F:B0:6C:94:2B")
                    rssi1 = result.getRssi()
                //Log.d("time1!!!","${measureNanoTime {rssi1 = result.getRssi()}}")
                else if(result?.device?.address == "E0:7D:EA:2D:29:AB")
                    rssi2 = result.getRssi()
                //Log.d("time2!!!","${measureNanoTime {rssi2 = result.getRssi()}}")
                else if(result?.device?.address == "80:6F:B0:6C:8F:B6")
                    rssi3 = result.getRssi()
                // display RSSI to user
                Beacon1RSSI.text=Integer.toString(rssi1) + " dBm"
                Beacon2RSSI.text=Integer.toString(rssi2) + " dBm"
                Beacon3RSSI.text=Integer.toString(rssi3) + " dBm"
            }
        }
        /*
         * Allows for user to accept location permission for location...
         */
        when (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            PackageManager.PERMISSION_GRANTED -> {
                bluetoothLEScanner?.startScan(scanCallback)
            }
            else -> requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
        // Connect to Gatt server again...
        bluetoothGatt?.connect()
        bluetoothGatt2?.connect()
        bluetoothGatt3?.connect()
        sleep(1000)
        Toast.makeText(this, "Connected to Gatt", Toast.LENGTH_SHORT)
        gattCallback.onReadRemoteRssi(bluetoothGatt,rssi1,0)
        gattCallback.onReadRemoteRssi(bluetoothGatt2,rssi2,0)
        gattCallback.onReadRemoteRssi(bluetoothGatt3,rssi3,0)

        /*
        * Below is a timer for every 10 seconds, which will retrieve the RSSI, then based on
        * it, it will play a specific playlist.  There are 4 sectors in the room, where
        * beacon A will be the bottom right corner, beacon B is the top middle, and
        * beacon C is the bottom right corner.
        */
        fixedRateTimer("timer", false, 0L, 10 * 1000) { //EVERY 10 SECONDS!
            this@BLEConnect.runOnUiThread {
                /*
                CALCULATING DISTANCE OF EACH BEACON IN VECTOR FORMAT
                 */
                 rA = Math.pow(10.0,((rssi1-(-70))/(-10*(4.0))))
                rB = Math.pow(10.0,((rssi2-(-70))/(-10*(4.0))))
                rC = Math.pow(10.0,((rssi3-(-70))/(-10*(4.0))))


                //(radius squared plus ab squared - radius squared) divided by 2AB
                ax = (Math.pow(rA.toDouble(), 2.0) + Math.pow(AB, 2.0) - Math.pow(rB.toDouble(),2.0)) / (2 * AB)
                ay = Math.pow(rA.toDouble(), 2.0) - Math.pow(ax, 2.0)
                bx = (Math.pow(rB.toDouble(),2.0) + Math.pow(BC, 2.0) - Math.pow(rC.toDouble(),2.0)) / (2 * BC)
                by = Math.pow(rB.toDouble(),2.0) - Math.pow(bx, 2.0)
                cx = (Math.pow(rC.toDouble(),2.0) + Math.pow(AC, 2.0) - Math.pow(rA.toDouble(),2.0)) / (2 * AC)
                cy = Math.pow(rC.toDouble(),2.0) - Math.pow(cx, 2.0)
                if (ay > 0 || by > 0 || cy > 0) {
                    ay = sqrt(ay);by = sqrt(by);cy = sqrt(cy)
                }
                //UNIT VECTOR
                var eax = ABx / AB
                var ebx = BCx / BC
                var ecx = ACx / AC
                var eay = ABy / AB
                var eby = BCy / BC
                var ecy = ACy / AC
                var nax = -eay
                var nbx = -eby
                var ncx = -ecy
                var nay = eax
                var nby = ebx
                var ncy = ecx


                //polynomial representation of point from beacons....
                var Q1ax = BEACON1_COORD.first + ax * eax
                var Q1bx = BEACON2_COORD.first + bx * ebx
                var Q1cx = BEACON3_COORD.first + cx * ecx
                var Q1ay = BEACON1_COORD.second + ax * eay
                var Q1by = BEACON2_COORD.second + bx * eby
                var Q1cy = BEACON2_COORD.second + cx * ecy
                //if multiple intersection
                //if(ay != 0.0 && by != 0.0 && cy != 0.0) {
                var Q2ax = Q1ax - ay * nax
                var Q2bx = Q1bx - by * nbx
                var Q2cx = Q1cx - cy * ncx
                var Q2ay = Q1ay - ay * nay
                var Q2by = Q1by - by * nby
                var Q2cy = Q1cy - cy * ncy
                Q1ax += ay * nax
                Q1bx += by * nbx
                Q1cx += cy * ncx
                Q1ay += ay * nay
                Q1by += by * nby
                Q1cy += cy * ncy

                /**
                 * Q1a,Q1b,Q1c,Q2a,Q2b,Q2c are used here to check quadrants!!!
                 *         |            B          | Trilateration...
                 *         |        //  |  \\      |
                 *         |_____ //____| ___\\____|
                 *         |    //      |      \\  | Problem:  Facing away beacon in sector...
                 *         |  //________|_______ \\|
                 *           C                    A
                 */
                // THIS IS THE PART WHERE PLAYLIST WILL CHANGGE
                if (rA < rB && (rA+ rC)/2 < ((rC + rB)/2))// bottom RIGHT '90s'
                {
                    if (SpotifyService.getPlayllist() != "spotify:playlist:71JXQ7EwfZMKmLPrzKZAB4")
                        SpotifyService.play("spotify:playlist:71JXQ7EwfZMKmLPrzKZAB4")
                }
                else if(rC < rB && (rA + rB)/2 > ((rC + rB)/2)) //Bottom LEFT 'cali'
                {
                    if (SpotifyService.getPlayllist() != "spotify:playlist:37i9dQZF1DWTlgzqHpWg4m")
                        SpotifyService.play("spotify:playlist:37i9dQZF1DWTlgzqHpWg4m")
                }
                else if (rB < rA && (rA + rB) / 2 < Math.sqrt(Math.pow(rB, 2.0) + Math.pow(rC, 2.0)) && rC> rA)//top RIGHT 'ari'
                {
                    if (SpotifyService.getPlayllist() != "spotify:playlist:37i9dQZF1DX1PfYnYcpw8w")
                        SpotifyService.play("spotify:playlist:37i9dQZF1DX1PfYnYcpw8w")
                } else if (rB <= rC && rB < Math.sqrt(Math.pow(rA, 2.0) + Math.pow(rC, 2.0))&& rC< rA)// top LEFT 'mega'
                {
                    if (SpotifyService.getPlayllist() != "spotify:playlist:37i9dQZF1DXbYM3nMM0oPk")
                            SpotifyService.play("spotify:playlist:37i9dQZF1DXbYM3nMM0oPk")
                }
            }
            // AT THE END OF TIMER TASK, UPDATE TO DB...
            rssiSave.put(RSSIA_KEY, rssi1)
            rssiSave.put(RSSIB_KEY, rssi2)
            rssiSave.put(RSSIC_KEY, rssi3)
            DBPush().saveDB(rssiSave) //save...

        }
    }
    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    fun getRSSIData(): HashMap<String, Int> {
        return rssiSave
    }


}

/**
 * THIS CLASS IS USED FOR WHEN THE SWITCH IS PRESSED TO DO LONG DISTANCE BEACON LOCATION:
 * THIS CLASS USES 'SUCCESS PACKETS' TO CONFIGURE LOCATIONS...
 *
 */
class PACKETConnect:AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}









