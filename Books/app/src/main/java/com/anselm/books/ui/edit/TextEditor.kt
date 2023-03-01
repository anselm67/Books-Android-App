package com.anselm.books.ui.edit

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.databinding.EditFieldLayoutBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.reflect.KMutableProperty1

open class TextEditor<T>(
    fragment: Fragment,
    inflater: LayoutInflater,
    target: T,
    property: KMutableProperty1<T, String>,
    labelResourceId: Int,
    onChange: ((Editor<T, String>) -> Unit)? = null,
    val validator: ((String) -> Boolean)? = null,
): Editor<T, String>(fragment, inflater, target, property, labelResourceId, onChange) {
    private var _binding: EditFieldLayoutBinding? = null
    protected val editor get() = _binding!!

    override fun setup(container: ViewGroup?): View {
        super.setup(container)
        _binding = EditFieldLayoutBinding.inflate(inflater, container, false)
        editor.idEditLabel.text = fragment.getText(labelResourceId)
        editor.idEditText.let {
            it.setText(property.getter(target))
            it.addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }

                override fun afterTextChanged(s: Editable?) {
                    val value = s.toString().trim()
                    if (validator?.invoke(value) == false) {
                        setInvalid(it, editor.idUndoEdit)
                    } else if (value != property.getter(target) ) {
                        setChanged(it, editor.idUndoEdit)
                    } else {
                        setUnchanged(it, editor.idUndoEdit)
                    }
                }
            })
            // Sets up a layout listener to enable scrolling on this EditText.
            setupScrollEnableListener(it)
        }
        editor.idUndoEdit.setOnClickListener {
            editor.idEditText.setText(property.getter(target))
        }
        // Marks the field invalid immediately. This is for books that are being
        // manually inserted which have empty mandatory fields such as title.
        fragment.lifecycleScope.launch(Dispatchers.Main) {
            if (validator?.invoke(property.getter(target)) == false) {
                setInvalid(editor.idEditText, editor.idUndoEdit)
            }
        }
        return editor.root
    }

    private fun setupScrollEnableListener(editText: EditText) {
        editText.addOnLayoutChangeListener(object: View.OnLayoutChangeListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onLayoutChange(
                v: View?, left: Int, top: Int, right: Int,
                bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                val layoutLines = editText.layout?.lineCount ?: 0
                if (layoutLines  > editText.maxLines) {
                    // I have no idea what this does, I haven't read the docs. Sigh.
                    // What I can say is that it allows to scroll the EditText widget even
                    // though it is itself in a scrollable view:
                    // ScrollView > LinearLayout > Editor (EditText)
                    editText.setOnTouchListener { view, event ->
                        view.parent.requestDisallowInterceptTouchEvent(true)
                        if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                            view.parent.requestDisallowInterceptTouchEvent(false)
                        }
                        return@setOnTouchListener false
                    }
                }
            }
        })
    }

    override fun isChanged(): Boolean {
        return value != property.getter(target).trim()
    }

    override fun saveChange() {
        property.setter(target, editor.idEditText.text.toString().trim())
    }

    override fun isValid(): Boolean {
        return validator?.invoke(value) != false
    }

    override var value: String
        get() = editor.idEditText.text.toString().trim()
        set(value) {
            val thisValue = editor.idEditText.text.trim().toString()
            if (value.isNotEmpty() && thisValue != value) {
                app.postOnUiThread {
                    editor.idEditText.setText(value)
                    if (value != property.getter(target)) {
                        setChanged(editor.idEditText, editor.idUndoEdit)
                    } else {
                        setUnchanged(editor.root, editor.idUndoEdit)
                    }
                }
            }
        }
}
