package com.majdus.organisateur

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.EditText

class Notes : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editText: EditText
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)
        
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        
        sharedPreferences = getSharedPreferences("organisateur", Context.MODE_PRIVATE)
        editText = findViewById(R.id.note)
        loadText()
    }

    override fun onPause() {
        super.onPause()
        saveText()
    }

    private fun loadText() {
        val note = sharedPreferences.getString("note", "")
        editText.setText(note)
    }

    private fun saveText() {
        with(sharedPreferences.edit()) {
            putString("note", editText.text.toString())
            apply()
        }
    }
}