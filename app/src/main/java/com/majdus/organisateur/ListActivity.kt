package com.majdus.organisateur

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import android.app.TimePickerDialog
import android.widget.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

import android.app.TimePickerDialog.OnTimeSetListener

open class ListActivity : AppCompatActivity() {
    protected lateinit var sharedPreferences: SharedPreferences
    protected lateinit var adapter: ArrayAdapter<String>

    private var list = ArrayList<String>()
    protected var set = HashSet<String>()

    protected var tag = ""
    protected var addItemMessage = ""
    protected var removeItemMessage = ""
    protected var removedToast = ""
    protected var successToast = ""
    protected var errorToast = ""

    fun initList(activity: ListActivity) {
        adapter = InteractiveArrayAdapter(
            activity,
            list
        )
        sharedPreferences = getSharedPreferences("organisateur", Context.MODE_PRIVATE)
    }

    protected fun loadItems() {
        set = sharedPreferences.getStringSet(tag, HashSet<String>()) as HashSet<String>
        list.addAll(set)
    }

    protected fun addItem() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(addItemMessage)

        val input = EditText(this)
        builder.setView(input)
        builder.setPositiveButton(
            "Créer"
        ) { _, _ ->  addNewItem(input.text.toString()) }
        builder.setNegativeButton(
            "Annuler"
        ) { dialog, _ -> dialog.cancel() }

        builder.show()
    }

     fun addAlarmItem() {
         val timeSetListener = OnTimeSetListener { _, hourOfDay, minute ->
             addNewItem("${String.format("%2d", hourOfDay)}:${String.format("%02d", minute)}")
             createAlarm(hourOfDay, minute)
         }

        val c: Calendar = Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minutes = c.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(this, 0, timeSetListener, hour, minutes, true)
        timePickerDialog.show()
    }

    private fun createAlarm(hourOfDay: Int, minute: Int) {
//TODO create alarm
    }

    private fun addNewItem(text: String) {
        if (text.isEmpty()) {
            Toast.makeText(this,errorToast, Toast.LENGTH_LONG).show()
            return
        }
        list.add(text)
        set.add(text)
        with (sharedPreferences.edit()) {
            remove(tag)
            apply()
            putStringSet(tag, set)
            apply()
            commit()
        }
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "$successToast - $text", Toast.LENGTH_SHORT).show()
    }

     fun removeItem(text: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(removeItemMessage)
        builder.setMessage(text)
        builder.setPositiveButton(
            "Oui"
        ) { _, _ ->  adapter.remove(text)
            doRemoveItem(text)}
        builder.setNegativeButton(
            "Non"
        ) { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun doRemoveItem(text: String) {
        list.remove(text)
        set.remove(text)
        with (sharedPreferences.edit()) {
            remove(tag)
            apply()
            putStringSet(tag, set)
            apply()
            commit()
        }
        Toast.makeText(this, removedToast, Toast.LENGTH_SHORT).show()
    }
}
