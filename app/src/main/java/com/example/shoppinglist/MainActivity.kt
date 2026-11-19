
package com.example.shoppinglist

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    private val items = mutableListOf<Pair<String, Boolean>>()
    private lateinit var listLayout: LinearLayout
    private lateinit var input: EditText
    private val prefs by lazy {
        getSharedPreferences("shopping_list", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadItems()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 20)
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "רשימת הקניות שלי"
            textSize = 26f
            setTextColor(Color.rgb(30, 80, 50))
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 20)
        }
        root.addView(title)

        input = EditText(this).apply {
            hint = "כתוב מוצר להוספה..."
            textSize = 18f
           isSingleLine = true
        }
        root.addView(input)

        val addButton = Button(this).apply {
            text = "הוסף לרשימה"
            setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    items.add(name to false)
                    input.text.clear()
                    saveItems()
                    renderItems()
                }
            }
        }
        root.addView(addButton)

        val scroll = ScrollView(this)
        listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(listLayout)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val clearButton = Button(this).apply {
            text = "נקה רשימה"
            setOnClickListener {
                items.clear()
                saveItems()
                renderItems()
            }
        }
        root.addView(clearButton)

        setContentView(root)
        renderItems()
    }

    private fun renderItems() {
        listLayout.removeAllViews()

        items.forEachIndexed { index, item ->
            val row = CheckBox(this).apply {
                text = item.first
                textSize = 19f
                isChecked = item.second
                setPadding(12, 12, 12, 12)
                layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL

                if (item.second) {
                    setBackgroundColor(Color.rgb(210, 245, 215))
                    setTextColor(Color.rgb(20, 120, 50))
                } else {
                    setBackgroundColor(Color.rgb(245, 245, 245))
                    setTextColor(Color.DKGRAY)
                }

                setOnCheckedChangeListener { _, checked ->
                    items[index] = item.first to checked
                    saveItems()
                    renderItems()
                }
            }

            listLayout.addView(row)
        }
    }

    private fun saveItems() {
        val array = JSONArray()
        items.forEach {
            val obj = JSONObject()
            obj.put("name", it.first)
            obj.put("bought", it.second)
            array.put(obj)
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private fun loadItems() {
        val saved = prefs.getString("items", "[]") ?: "[]"
        val array = JSONArray(saved)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            items.add(
                obj.getString("name") to obj.getBoolean("bought")
            )
        }
    }
}
