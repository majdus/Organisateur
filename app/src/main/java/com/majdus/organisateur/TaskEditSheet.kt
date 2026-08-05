package com.majdus.organisateur

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.majdus.organisateur.data.Task

/**
 * Feuille de création / modification d'une tâche.
 *
 * Même contrat que [AlarmEditSheet]: aucun état passé par le constructeur, résultat remonté
 * par `FragmentResult`, donc la feuille survit à une rotation.
 */
class TaskEditSheet : BottomSheetDialogFragment() {

    private var original: Task? = null

    private lateinit var labelInput: TextInputEditText
    private lateinit var labelLayout: TextInputLayout
    private lateinit var doneSwitch: MaterialSwitch

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_task_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        original = readTask(requireArguments(), PREFIX_ORIGINAL)
        val isEdit = original != null

        labelInput = view.findViewById(R.id.taskLabel)
        labelLayout = view.findViewById(R.id.labelInputLayout)
        doneSwitch = view.findViewById(R.id.doneSwitch)
        val title: TextView = view.findViewById(R.id.sheetTitle)
        val doneRow: View = view.findViewById(R.id.doneRow)
        val saveButton: MaterialButton = view.findViewById(R.id.btnSave)
        val cancelButton: MaterialButton = view.findViewById(R.id.btnCancel)
        val deleteButton: MaterialButton = view.findViewById(R.id.btnDelete)

        if (savedInstanceState == null) {
            labelInput.setText(original?.title.orEmpty())
            labelInput.setSelection(labelInput.text?.length ?: 0)
            doneSwitch.isChecked = original?.isCompleted == true
        }

        title.setText(if (isEdit) R.string.task_edit_title else R.string.task_new_title)
        // Une tâche qui n'existe pas encore ne peut pas déjà être terminée.
        doneRow.visibility = if (isEdit) View.VISIBLE else View.GONE
        deleteButton.visibility = if (isEdit) View.VISIBLE else View.GONE

        labelInput.addTextChangedListener { labelLayout.error = null }
        cancelButton.setOnClickListener { dismiss() }
        saveButton.setOnClickListener { save() }
        deleteButton.setOnClickListener {
            val task = original ?: return@setOnClickListener
            sendResult(ACTION_DELETE, task)
        }

        applySoftInputMode()
    }

    /** La feuille doit se redimensionner au-dessus du clavier, pas être masquée par lui. */
    @Suppress("DEPRECATION") // SOFT_INPUT_ADJUST_RESIZE reste la seule option pour une Dialog.
    private fun applySoftInputMode() {
        labelInput.requestFocus()
        dialog?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun save() {
        val title = labelInput.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            labelLayout.error = getString(R.string.task_label_error)
            labelInput.requestFocus()
            return
        }
        val edited = original?.copy(title = title, isCompleted = doneSwitch.isChecked)
            ?: Task(title = title)
        sendResult(ACTION_SAVE, edited)
    }

    private fun sendResult(action: String, task: Task) {
        val result = bundleOf(KEY_ACTION to action)
        writeTask(result, PREFIX_TASK, task)
        original?.let { writeTask(result, PREFIX_ORIGINAL, it) }
        parentFragmentManager.setFragmentResult(REQUEST_KEY, result)
        dismiss()
    }

    companion object {
        const val REQUEST_KEY = "task_edit_result"
        const val ACTION_SAVE = "save"
        const val ACTION_DELETE = "delete"

        private const val KEY_ACTION = "action"
        private const val PREFIX_TASK = "task_"
        private const val PREFIX_ORIGINAL = "original_"

        fun newInstance(task: Task?): TaskEditSheet = TaskEditSheet().apply {
            arguments = Bundle().also { args -> task?.let { writeTask(args, PREFIX_ORIGINAL, it) } }
        }

        fun action(result: Bundle): String = result.getString(KEY_ACTION).orEmpty()

        fun editedTask(result: Bundle): Task? = readTask(result, PREFIX_TASK)

        fun isNew(result: Bundle): Boolean = readTask(result, PREFIX_ORIGINAL) == null

        // Le rang fait l'aller-retour comme les autres champs: la feuille renvoie une tâche
        // reconstruite de toutes pièces, et l'oublier ici la ferait remonter en tête de liste
        // à la moindre modification.
        private fun writeTask(bundle: Bundle, prefix: String, task: Task) {
            bundle.putString(prefix + "id", task.id)
            bundle.putString(prefix + "title", task.title)
            bundle.putBoolean(prefix + "completed", task.isCompleted)
            bundle.putLong(prefix + "timestamp", task.timestamp)
            bundle.putInt(prefix + "position", task.position)
        }

        private fun readTask(bundle: Bundle, prefix: String): Task? {
            val id = bundle.getString(prefix + "id") ?: return null
            return Task(
                id = id,
                title = bundle.getString(prefix + "title").orEmpty(),
                isCompleted = bundle.getBoolean(prefix + "completed"),
                timestamp = bundle.getLong(prefix + "timestamp"),
                position = bundle.getInt(prefix + "position")
            )
        }
    }
}
