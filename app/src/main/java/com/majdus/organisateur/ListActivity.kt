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

abstract class ListActivity : AppCompatActivity() {
    protected lateinit var sharedPreferences: SharedPreferences

    protected var list = ArrayList<String>()
    protected var set = HashSet<String>()

    protected var tag = ""
    protected var addItemMessage = ""
    protected var removeItemMessage = ""
    protected var removedToast = ""
    protected var successToast = ""
    protected var updateToast = ""
    protected var errorToast = ""

    protected fun loadItems() {
        set = sharedPreferences.getStringSet(tag, HashSet<String>()) as HashSet<String>
        list.addAll(set)
    }



    fun addAlarmItem() {
         val c: Calendar = Calendar.getInstance()
         val hour = c.get(Calendar.HOUR_OF_DAY)
         val minutes = c.get(Calendar.MINUTE)

         val fragmentManager = supportFragmentManager
         val alarmDialogFragment = AlarmDialogFragment(this, hour, minutes, null, null)
         alarmDialogFragment.show(fragmentManager, "Alarm")
     }

    protected fun editAlarmItem(text: String, oldText: String, hour: String, minute: String) {
        val fragmentManager = supportFragmentManager
        val alarmDialogFragment = AlarmDialogFragment(this, hour.trim().toInt(), minute.trim().toInt(), text, oldText)
        alarmDialogFragment.show(fragmentManager, "Alarm")
    }

    fun addNewItem(text: String) {
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
        notifyDataChanged()
        Toast.makeText(this, successToast, Toast.LENGTH_SHORT).show()
    }

     fun removeItem(text: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(removeItemMessage)
        builder.setMessage(text)
        builder.setPositiveButton(
            "Oui"
        ) { _, _ -> doRemoveItem(text) }
        builder.setNegativeButton(
            "Non"
        ) { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun doRemoveItem(text: String) {
        onItemRemoved(text)
        list.remove(text)
        set.remove(text)
        with (sharedPreferences.edit()) {
            remove(tag)
            apply()
            putStringSet(tag, set)
            apply()
            commit()
        }
        notifyDataChanged()
        Toast.makeText(this, removedToast, Toast.LENGTH_SHORT).show()
    }

    abstract fun edit(text: String);

    protected open fun onItemRemoved(text: String) {}
    protected open fun onItemUpdated(oldText: String, newText: String) {}

    open fun isTaskCompleted(text: String): Boolean = false
    open fun setTaskCompleted(text: String, completed: Boolean) {}

    fun updateItem(oldText: String, newText: String) {
        if (newText.isEmpty()) {
            Toast.makeText(this,errorToast, Toast.LENGTH_LONG).show()
            return
        }

        list.remove(oldText)
        set.remove(oldText)
        list.add(newText)
        set.add(newText)
        with (sharedPreferences.edit()) {
            remove(tag)
            apply()
            putStringSet(tag, set)
            apply()
            commit()
        }
        onItemUpdated(oldText, newText)
        notifyDataChanged()
        Toast.makeText(this, updateToast, Toast.LENGTH_SHORT).show()
    }

    abstract fun alarmStatusChanged(text: String, checked: Boolean)
    abstract fun notifyDataChanged()
}
