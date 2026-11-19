
package com.example.shoppinglist

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private val items = mutableListOf<Pair<String, Boolean>>()
    private lateinit var listLayout: LinearLayout
    private lateinit var input: EditText
    private lateinit var scanButton: Button

    private val prefs by lazy {
        getSharedPreferences("shopping_list", MODE_PRIVATE)
    }

    companion object {
        private const val PICK_RECEIPT = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadItems()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 20)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
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

        scanButton = Button(this).apply {
            text = "סרוק קבלה"
            setOnClickListener {
                chooseReceipt()
            }
        }
        root.addView(scanButton)

        val scroll = ScrollView(this)
        listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(listLayout)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

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

    private fun chooseReceipt() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }

        try {
            startActivityForResult(
                Intent.createChooser(intent, "בחר תמונת קבלה"),
                PICK_RECEIPT
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "לא ניתן לפתוח תמונות",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Deprecated("Using legacy activity result API for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_RECEIPT &&
            resultCode == RESULT_OK &&
            data?.data != null
        ) {
            scanReceipt(data.data!!)
        }
    }

    private fun scanReceipt(uri: Uri) {
        scanButton.isEnabled = false
        scanButton.text = "מזהה את הקבלה..."

        Thread {
            var tess: TessBaseAPI? = null

            try {
                val bitmap = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: throw Exception("לא ניתן לקרוא את התמונה")

                val dataPath = File(filesDir, "tesseract")
                val tessData = File(dataPath, "tessdata")
                tessData.mkdirs()

                val hebFile = File(tessData, "heb.traineddata")

                if (!hebFile.exists()) {
                    assets.open("tessdata/heb.traineddata").use { input ->
                        FileOutputStream(hebFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                tess = TessBaseAPI()

                if (!tess.init(dataPath.absolutePath, "heb")) {
                    throw Exception("לא ניתן להפעיל זיהוי בעברית")
                }

                tess.setPageSegMode(
                    TessBaseAPI.PageSegMode.PSM_AUTO
                )

                tess.setImage(bitmap)
                val recognizedText = tess.getUTF8Text() ?: ""

                bitmap.recycle()

                runOnUiThread {
                    scanButton.isEnabled = true
                    scanButton.text = "סרוק קבלה"

                    if (recognizedText.isBlank()) {
                        Toast.makeText(
                            this,
                            "לא זוהה טקסט בתמונה",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        showReceiptResults(recognizedText)
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    scanButton.isEnabled = true
                    scanButton.text = "סרוק קבלה"

                    Toast.makeText(
                        this,
                        "שגיאה בסריקה: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                tess?.recycle()
            }
        }.start()
    }

    private fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[^א-תa-z0-9]"), "")
    }

    private fun showReceiptResults(receiptText: String) {
        val normalizedReceipt = normalize(receiptText)

        val checkboxes = mutableListOf<Pair<Int, CheckBox>>()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 10, 20, 10)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val explanation = TextView(this).apply {
            text = "אלה המוצרים שזוהו ברשימה שלך. בדוק וסמן מה באמת נקנה:"
            textSize = 17f
            setPadding(0, 0, 0, 12)
        }
        content.addView(explanation)

        items.forEachIndexed { index, item ->
            val normalizedName = normalize(item.first)

            val found = normalizedName.isNotEmpty() &&
                normalizedReceipt.contains(normalizedName)

            val check = CheckBox(this).apply {
                text = item.first
                textSize = 18f
                isChecked = item.second || found
                setPadding(8, 8, 8, 8)
            }

            checkboxes.add(index to check)
            content.addView(check)
        }

        val receiptTitle = TextView(this).apply {
            text = "הטקסט שזוהה בקבלה:"
            textSize = 17f
            setPadding(0, 18, 0, 6)
        }
        content.addView(receiptTitle)

        val receiptPreview = TextView(this).apply {
            text = receiptText
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(10, 10, 10, 10)
            setBackgroundColor(Color.rgb(245, 245, 245))
        }
        content.addView(receiptPreview)

        val innerScroll = ScrollView(this)
        innerScroll.addView(content)

        AlertDialog.Builder(this)
            .setTitle("תוצאות סריקת הקבלה")
            .setView(innerScroll)
         .setPositiveButton("אשר מוצרים שנקנו") { dialog: android.content.DialogInterface, which: Int ->
                checkboxes.forEach { (index, check) ->
                    items[index] = items[index].first to check.isChecked
                }

                saveItems()
                renderItems()

                Toast.makeText(
                    this,
                    "הרשימה עודכנה",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun renderItems() {
        listLayout.removeAllViews()

        items.forEachIndexed { index, item ->
            val row = CheckBox(this).apply {
                text = item.first
                textSize = 19f
                isChecked = item.second
                setPadding(12, 12, 12, 12)
                layoutDirection = View.LAYOUT_DIRECTION_RTL

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

        prefs.edit()
            .putString("items", array.toString())
            .apply()
    }

    private fun loadItems() {
        val saved = prefs.getString("items", "[]") ?: "[]"
        val array = JSONArray(saved)

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)

            items.add(
                obj.getString("name") to
                    obj.getBoolean("bought")
            )
        }
    }
}
