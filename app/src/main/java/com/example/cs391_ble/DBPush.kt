package com.example.cs391_ble

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

class DBPush
{
    private var mDocRef:DocumentReference = FirebaseFirestore.getInstance().document("rssi/rssi")
    fun saveDB(data: HashMap<String, Int>){
        mDocRef.set(data).addOnSuccessListener() {
            Log.d("Database","SUCCESS IN UPLOAD")
        }
    }

}