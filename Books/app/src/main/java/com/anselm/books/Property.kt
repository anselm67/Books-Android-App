package com.anselm.books

import android.util.Log
import com.anselm.books.database.Label
import kotlin.reflect.KMutableProperty0

class Property {

    companion object {
        fun isEmpty(propertyValue: Any?): Boolean {
            return when (propertyValue) {
                null -> { true }
                is String -> { propertyValue.isEmpty() }
                is Label -> { false }
                is List<*> -> { propertyValue.isEmpty() }
                else -> { true }
            }
        }

        fun isNotEmpty(propertyValue: Any?): Boolean {
            return ! isEmpty(propertyValue)
        }

        fun setIfEmpty(prop: KMutableProperty0<*>, value: Any?) {
            val currentValue = prop.getter()
            if (isEmpty(currentValue) && ! isEmpty(value)) {
                @Suppress("UNCHECKED_CAST")
                (prop.setter as (Any) -> Unit)(value!!)
            }
        }

        fun setIfEmpty(vararg props: Pair<KMutableProperty0<*>, Any?>) {
            props.forEach { (prop, value) ->
                try {
                    setIfEmpty(prop, value)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set ${prop.name} to $value (ignored).", e)
                }
            }
        }

    }
}