
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
import android.content.ContentValues
import android.provider.MediaStore
import com.googlecode.tesseract.android.TessBaseAPI
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private val items = mutableListOf<Pair<String, Boolean>>()

    private lateinit var listLayout: LinearLayout
    private lateinit var input: EditText
    private lateinit var scanButton: Button

    private var cameraImageUri: Uri? = null

    private val prefs by lazy {
        getSharedPreferences("shopping_list", MODE_PRIVATE)
    }

    companion object {
        private const val PICK_RECEIPT = 1001
        private const val TAKE_RECEIPT = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadItems()
        buildScreen()
        refreshList()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val title = TextView(this).apply {
            text = "רשימת הקניות שלי"
            textSize = 25f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                -1,
                dp(55)
            )
        )

        input = EditText(this).apply {
            hint = "כתוב מוצר לקנייה"
            textSize = 18f
            isSingleLine = true
        }

        root.addView(
            input,
            LinearLayout.LayoutParams(-1, dp(55))
        )

        val addButton = Button(this).apply {
            text = "הוסף מוצר"
            setOnClickListener {
                addItem()
            }
        }

        root.addView(
            addButton,
            LinearLayout.LayoutParams(-1, dp(55))
        )

        scanButton = Button(this).apply {
            text = "סרוק קבלה"
            setOnClickListener {
                chooseReceipt()
            }
        }

        root.addView(
            scanButton,
            LinearLayout.LayoutParams(-1, dp(55))
        )

        val listTitle = TextView(this).apply {
            text = "המוצרים שלי:"
            textSize = 20f
            setTextColor(Color.BLACK)
            setPadding(0, dp(15), 0, dp(8))
        }

        root.addView(listTitle)

        val scroll = ScrollView(this)

        listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(listLayout)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        val clearButton = Button(this).apply {
            text = "נקה מוצרים שסומנו"
            setOnClickListener {
                items.removeAll { it.second }
                saveItems()
                refreshList()
            }
        }

        root.addView(
            clearButton,
            LinearLayout.LayoutParams(-1, dp(55))
        )

        setContentView(root)
    }

    private fun addItem() {
        val name = input.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(
                this,
                "כתוב שם של מוצר",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        items.add(Pair(name, false))

        input.text.clear()

        saveItems()
        refreshList()
    }

    private fun refreshList() {
        listLayout.removeAllViews()

        for (index in items.indices) {
            val name = items[index].first
            val checked = items[index].second

            val checkBox = CheckBox(this).apply {
                text = name
                textSize = 18f
                isChecked = checked
                setTextColor(
                    if (checked) Color.rgb(0, 140, 0)
                    else Color.BLACK
                )

                setOnCheckedChangeListener { _, isChecked ->
                    items[index] = Pair(name, isChecked)
                    setTextColor(
                        if (isChecked) Color.rgb(0, 140, 0)
                        else Color.BLACK
                    )
                    saveItems()
                }
            }

            listLayout.addView(
                checkBox,
                LinearLayout.LayoutParams(
                    -1,
                    dp(48)
                )
            )
        }
    }

    private fun saveItems() {
        val array = JSONArray()

        for (item in items) {
            val obj = JSONObject()
            obj.put("name", item.first)
            obj.put("checked", item.second)
            array.put(obj)
        }

        prefs.edit()
            .putString("items", array.toString())
            .apply()
    }

    private fun loadItems() {
        items.clear()

        val saved = prefs.getString("items", "[]") ?: "[]"

        try {
            val array = JSONArray(saved)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                items.add(
                    Pair(
                        obj.getString("name"),
                        obj.getBoolean("checked")
                    )
                )
            }
        } catch (e: Exception) {
            items.clear()
        }
    }

    private fun chooseReceipt() {
        val options = arrayOf(
            "📷 צילום קבלה במצלמה",
            "🖼️ בחירת תמונה מהגלריה"
        )

        AlertDialog.Builder(this)
            .setTitle("סריקת קבלה")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun openGallery() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
            }

            startActivityForResult(intent, PICK_RECEIPT)

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "לא ניתן לפתוח תמונות",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openCamera() {
        try {
            val values = ContentValues().apply {
                put(
                    MediaStore.Images.Media.TITLE,
                    "receipt"
                )
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "receipt_${System.currentTimeMillis()}.jpg"
                )
                put(
                    MediaStore.Images.Media.MIME_TYPE,
                    "image/jpeg"
                )
            }

            cameraImageUri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )

            if (cameraImageUri == null) {
                throw Exception("לא ניתן ליצור תמונה")
            }

            val intent = Intent(
                MediaStore.ACTION_IMAGE_CAPTURE
            ).apply {
                putExtra(
                    MediaStore.EXTRA_OUTPUT,
                    cameraImageUri
                )
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            startActivityForResult(intent, TAKE_RECEIPT)

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "לא ניתן לפתוח מצלמה: ${e.message}",
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

        if (resultCode != RESULT_OK) return

        when (requestCode) {
            PICK_RECEIPT -> {
                data?.data?.let {
                    scanReceipt(it)
                }
            }

            TAKE_RECEIPT -> {
                cameraImageUri?.let {
                    scanReceipt(it)
                }
            }
        }
    }

    private fun scanReceipt(uri: Uri) {
        scanButton.isEnabled = false
        scanButton.text = "מזהה את הקבלה..."

        Thread {
            var tess: TessBaseAPI? = null

            try {
                val bitmap = contentResolver
                    .openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: throw Exception(
                    "לא ניתן לקרוא את התמונה"
                )

                val dataPath = File(
                    filesDir,
                    "tesseract"
                )

                val tessData = File(
                    dataPath,
                    "tessdata"
                )

                tessData.mkdirs()

                val hebFile = File(
                    tessData,
                    "heb.traineddata"
                )

                if (!hebFile.exists()) {
                    assets.open(
                        "tessdata/heb.traineddata"
                    ).use { input ->
                        FileOutputStream(hebFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                tess = TessBaseAPI()

                if (!tess.init(
                        dataPath.absolutePath,
                        "heb"
                    )
                ) {
                    throw Exception(
                        "לא ניתן להפעיל זיהוי בעברית"
                    )
                }

                tess.setPageSegMode(
                    TessBaseAPI.PageSegMode.PSM_AUTO
                )

                tess.setImage(bitmap)

val recognizedText = tess.getUTF8Text() ?: ""
android.util.Log.d("OCR_RESULT", "זוהה טקסט: $recognizedText")                    tess.getUTF8Text() ?: ""

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
            .replace(
                Regex("[^א-תa-z0-9]"),
                ""
            )
    }

    private fun showReceiptResults(
        recognizedText: String
    ) {
        val normalizedReceipt = normalize(
            recognizedText
        )

        val matchedIndices = items.indices.filter { index ->
            val product = normalize(items[index].first)

            product.isNotEmpty() &&
            normalizedReceipt.contains(product)
        }

        if (matchedIndices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("תוצאות הסריקה")
                .setMessage(
                    "לא נמצאו מוצרים מהרשימה שלך בקבלה.\n\n" +
                    "הטקסט שזוהה:\n\n" +
                    recognizedText
                )
                .setPositiveButton("סגור", null)
                .show()

            return
        }

        val names = matchedIndices.map {
            items[it].first
        }.toTypedArray()

        val checked = BooleanArray(
            matchedIndices.size
        ) { true }

        AlertDialog.Builder(this)
            .setTitle("המוצרים שזוהו בקבלה")
            .setMultiChoiceItems(
                names,
                checked
            ) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(
                "אשר מוצרים שנקנו"
            ) { _, _ ->

                for (i in matchedIndices.indices) {
                    if (checked[i]) {
                        val itemIndex = matchedIndices[i]

                        items[itemIndex] = Pair(
                            items[itemIndex].first,
                            true
                        )
                    }
                }

                saveItems()
                refreshList()

                Toast.makeText(
                    this,
                    "הרשימה עודכנה",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("ביטול", null)
            .show()
    }
}
