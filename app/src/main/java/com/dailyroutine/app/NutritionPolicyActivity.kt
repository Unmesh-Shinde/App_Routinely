package com.dailyroutine.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NutritionPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nutrition_policy)

        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomMargin(findViewById(R.id.cardAcknowledge))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
        toolbar.setNavigationOnClickListener { finish() }

        setupAcknowledgmentSection()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 101, 0, "Download PDF").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setIcon(android.R.drawable.ic_menu_save)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 101) {
            handlePdfDownload()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun handlePdfDownload() {
        val pdfFile = NutritionPdfExporter.generatePdf(this)
        if (pdfFile != null && pdfFile.exists()) {
            val root = findViewById<View>(android.R.id.content)
            Snackbar.make(root, "PDF downloaded to Downloads folder", Snackbar.LENGTH_LONG)
                .setAction("Open") {
                    try {
                        val uri: Uri = FileProvider.getUriForFile(
                            this,
                            "$packageName.fileprovider",
                            pdfFile
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, "Open Fair Use Policy PDF"))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Downloaded: ${pdfFile.name}", Toast.LENGTH_LONG).show()
                    }
                }.show()
        } else {
            Toast.makeText(this, "Failed to generate PDF. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAcknowledgmentSection() {
        val cbAck = findViewById<CheckBox>(R.id.cbAcknowledgePolicy)
        val btnAck = findViewById<Button>(R.id.btnAcknowledgePolicy)
        val tvStatusDate = findViewById<TextView>(R.id.tvAckDateStatus)

        val isAlreadyAck = UserSettingsStore.hasAcknowledgedNutritionPolicy(this)
        val ackDate = UserSettingsStore.getNutritionPolicyAcknowledgedDate(this)

        if (isAlreadyAck) {
            cbAck.isChecked = true
            cbAck.isEnabled = false
            btnAck.visibility = View.GONE
            tvStatusDate.visibility = View.VISIBLE
            tvStatusDate.text = "✓ Acknowledged on ${ackDate ?: "record"}"
        } else {
            cbAck.setOnCheckedChangeListener { _, isChecked ->
                btnAck.isEnabled = isChecked
            }
            btnAck.setOnClickListener {
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                UserSettingsStore.setNutritionPolicyAcknowledged(this, dateStr)

                cbAck.isChecked = true
                cbAck.isEnabled = false
                btnAck.visibility = View.GONE
                tvStatusDate.visibility = View.VISIBLE
                tvStatusDate.text = "✓ Acknowledged on $dateStr"

                Toast.makeText(this, "Nutrition Fair Use Policy Acknowledged!", Toast.LENGTH_LONG).show()
            }
        }
    }
}
