package com.dailyroutine.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView

class AvatarSelectionActivity : AppCompatActivity() {

    private var selectedAvatarId: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_avatar_selection)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        findViewById<View>(R.id.rvAvatars)?.let { InsetHelper.applyBottomPadding(it) }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        selectedAvatarId = UserPreferencesStore.getUserAvatarId(this)

        val rvAvatars = findViewById<RecyclerView>(R.id.rvAvatars)
        rvAvatars.layoutManager = GridLayoutManager(this, 4)
        rvAvatars.adapter = AvatarAdapter()
    }

    inner class AvatarAdapter : RecyclerView.Adapter<AvatarAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_avatar, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val avatarId = position + 1
            AvatarHelper.applyAvatar(holder.ivAvatar, avatarId)

            val density = resources.displayMetrics.density
            val isSelected = avatarId == selectedAvatarId

            // Use ShapeableImageView native stroke for cleaner selection UI
            if (isSelected) {
                holder.ivAvatar.strokeWidth = 3 * density
                holder.ivAvatar.strokeColor = ContextCompat.getColorStateList(this@AvatarSelectionActivity, R.color.primary)
            } else {
                holder.ivAvatar.strokeWidth = 1 * density
                holder.ivAvatar.strokeColor = ContextCompat.getColorStateList(this@AvatarSelectionActivity, android.R.color.darker_gray)
            }

            holder.itemView.setOnClickListener {
                UserPreferencesStore.setUserAvatarId(this@AvatarSelectionActivity, avatarId)
                selectedAvatarId = avatarId
                notifyDataSetChanged()
                finish()
            }
        }

        override fun getItemCount(): Int = AvatarHelper.TOTAL_AVATARS

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivAvatar: ShapeableImageView = v.findViewById(R.id.ivAvatar)
        }
    }
}
