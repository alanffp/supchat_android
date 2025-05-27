package com.example.supchat.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.supchat.R
import com.example.supchat.models.response.messageprivate.MessageFichier

class ConversationFilesAdapter(
    private val context: Context,
    private val onFileClick: (MessageFichier) -> Unit
) : RecyclerView.Adapter<ConversationFilesAdapter.FileViewHolder>() {

    private var files: List<MessageFichier> = emptyList()

    fun updateFiles(newFiles: List<MessageFichier>) {
        files = newFiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount(): Int = files.size

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val fileNameText: TextView = view.findViewById(R.id.file_name_text)
        private val fileSizeText: TextView = view.findViewById(R.id.file_size_text)

        fun bind(file: MessageFichier) {
            fileNameText.text = file.nom
            fileSizeText.text = formatFileSize(file.taille)

            itemView.setOnClickListener {
                onFileClick(file)
            }
        }

        private fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> "${size / (1024 * 1024)} MB"
            }
        }
    }
}