package com.majdus.organisateur

import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater

class InteractiveArrayAdapter(val context: TaskList, val list: List<String>) :
    ArrayAdapter<String>(context, 0, list) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val text = getItem(position)
        if (convertView == null) {
            convertView =
                LayoutInflater.from(getContext()).inflate(R.layout.layout_task, parent, false)
        }
        val textView: TextView = convertView!!.findViewById(R.id.label)
        val deleteButton: ImageButton = convertView.findViewById(R.id.delete)
        textView.setText(text)
        deleteButton.setOnClickListener { removeItem(text!!) }
        return convertView
    }

    private fun removeItem(text: String) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Supprimer cette tache?")

        builder.setPositiveButton(
            "Oui"
        ) { dialog, which ->  remove(text)
        context.removeTask(text)}
        builder.setNegativeButton(
            "Non"
        ) { dialog, which -> dialog.cancel() }

        builder.show()
    }
}
