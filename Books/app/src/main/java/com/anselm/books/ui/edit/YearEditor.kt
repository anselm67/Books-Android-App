package com.anselm.books.ui.edit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.fragment.app.Fragment
import com.anselm.books.database.BookFields
import com.anselm.books.databinding.EditYearLayoutBinding
import kotlin.reflect.KMutableProperty1

class YearEditor<T>(
    fragment: Fragment,
    inflater: LayoutInflater,
    target: T,
    property: KMutableProperty1<T, String>,
    onChange: ((Editor<T, String>) -> Unit)? = null,
): Editor<T, String>(fragment, inflater, target, property, 0, onChange) {
    private var _binding: EditYearLayoutBinding? = null
    private val editor get() = _binding!!

    private fun getEditorValue(): Int {
        return (editor.yearPublished100Picker.value * 100
                + editor.yearPublished10Picker.value * 10
                + editor.yearPublished1Picker.value)
    }

    private fun setEditorValue(value: Int?) {
        if (value == null) {
            return
        }
        if (value in BookFields.MIN_PUBLISHED_YEAR..BookFields.MAX_PUBLISHED_YEAR) {
            editor.yearPublished100Picker.value = value / 100
            editor.yearPublished10Picker.value = (value / 10) % 10
            editor.yearPublished1Picker.value = value % 10
        }
    }

    override fun setup(container: ViewGroup?): View {
        super.setup(container)
        _binding = EditYearLayoutBinding.inflate(inflater, container, false)
        editor.yearPublished100Picker.minValue = BookFields.MIN_PUBLISHED_YEAR / 100
        editor.yearPublished100Picker.maxValue = BookFields.MAX_PUBLISHED_YEAR / 100
        editor.yearPublished10Picker.minValue = 0
        editor.yearPublished10Picker.maxValue = 9
        editor.yearPublished1Picker.minValue = 0
        editor.yearPublished1Picker.maxValue = 9
        setEditorValue(property.getter(target).toIntOrNull() ?: 0)
        val onValueChanged = NumberPicker.OnValueChangeListener { _, _, _ ->
            val newValue = getEditorValue()
            if (newValue != property.getter(target).toIntOrNull()) {
                setChanged(editor.yearPublishedView, editor.idUndoEdit)
            } else {
                setUnchanged(editor.yearPublishedView, editor.idUndoEdit)
            }
        }
        editor.yearPublished100Picker.setOnValueChangedListener(onValueChanged)
        editor.yearPublished10Picker.setOnValueChangedListener(onValueChanged)
        editor.yearPublished1Picker.setOnValueChangedListener(onValueChanged)
        editor.idUndoEdit.setOnClickListener {
            setEditorValue(property.getter(target).toIntOrNull() ?: 0)
            setUnchanged(editor.yearPublishedView, editor.idUndoEdit)
        }

        return editor.root
    }

    override fun isChanged(): Boolean {
        val value = getEditorValue()
        return (property.getter(target).toIntOrNull() ?: 0) != value
    }

    override fun saveChange() {
        val value = getEditorValue()
        property.setter(target, value.toString())
    }

    override var value
        get() = getEditorValue().toString()
        set(stringValue) {
            val value = stringValue.toIntOrNull()
            val thisValue = getEditorValue()
            if (value != null && value != thisValue) {
                setEditorValue(value)
                if (stringValue != property.getter(target)) {
                    setChanged(editor.yearPublishedView, editor.idUndoEdit)
                } else {
                    setUnchanged(editor.yearPublishedView, editor.idUndoEdit)
                }
            }
        }
}
