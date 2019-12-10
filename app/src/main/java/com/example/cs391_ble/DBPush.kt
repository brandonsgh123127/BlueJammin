package com.example.cs391_ble

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

class DBPush
{
    private var mFirebase = FirebaseFirestore.getInstance()
    var mRssi = mFirebase.collection("rssi").document("rssi")
    var mDistance = mFirebase.collection("rssi").document("distance")
    var mPlot = mFirebase.collection("rssi").document("plotting")
    var mGraphing = mFirebase.collection("rssi").document("graphing")
    fun saveRSSIDB(data: HashMap<String, Int>, data2: HashMap<String,Double>,data3:HashMap<String,Double>){
        mRssi.set(data).addOnSuccessListener() {Log.d("Database","SUCCESS IN UPLOADING RSSI") }
        mDistance.set(data2).addOnSuccessListener { Log.d("Database","SUCCESS IN UPLOADING DISTANCE") }
        mPlot.set(data3).addOnSuccessListener { Log.d("Database","SUCCESS IN UPLOADING DISTANCE") }
    }
    fun saveGraphDB(data:HashMap<String,Double>){
        mGraphing.set(data).addOnSuccessListener() {Log.d("Database","SUCCESS IN UPLOADING GRAPH DATA") }
    }

}