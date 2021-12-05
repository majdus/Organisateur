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
        sharedPreferences = getSharedPreferences("organisateur", Context.MODE_PRIVATE)
        editText = findViewById(R.id.note)
        loadText()
    }

    private fun loadText() {
        val note = sharedPreferences.getString("note", "")
        editText.setText(note)
    }

    override fun onBackPressed() {
        with(sharedPreferences.edit()) {
            putString("note", editText.text.toString())
            apply()
        }
        super.onBackPressed()
    }
}