package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.komica.reader.databinding.ItemBoardCategoryBinding
import com.komica.reader.databinding.ItemSettingBinding

class SettingsAdapter(private val onClick: (String) -> Unit) : ListAdapter<SettingsAdapter.SettingItem, RecyclerView.ViewHolder>(DiffCallback) {

    fun SubmitItems(newItems: List<SettingItem>) {
        submitList(newItems)
    }

    override fun getItemViewType(position: Int): Int = if (getItem(position) is SettingItem.Section) ViewTypeSection else ViewTypeRow

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == ViewTypeSection) SectionViewHolder(ItemBoardCategoryBinding.inflate(inflater, parent, false)) else RowViewHolder(ItemSettingBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SettingItem.Section -> (holder as SectionViewHolder).Bind(item.title)
            is SettingItem.Row -> (holder as RowViewHolder).Bind(item)
        }
    }

    private class SectionViewHolder(private val binding: ItemBoardCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun Bind(title: String) {
            binding.categoryTitle.text = title
            binding.categoryMeta.text = ""
        }
    }

    private inner class RowViewHolder(private val binding: ItemSettingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun Bind(row: SettingItem.Row) {
            binding.settingTitle.text = row.title
            binding.settingDescription.text = row.description
            binding.settingValue.text = row.value
            binding.root.setOnClickListener { onClick(row.key) }
        }
    }

    sealed interface SettingItem {
        data class Section(val title: String) : SettingItem
        data class Row(val key: String, val title: String, val value: String, val description: String) : SettingItem
    }

    companion object {
        private const val ViewTypeSection = 1
        private const val ViewTypeRow = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<SettingItem>() {
            override fun areItemsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean {
                return when {
                    oldItem is SettingItem.Section && newItem is SettingItem.Section -> oldItem.title == newItem.title
                    oldItem is SettingItem.Row && newItem is SettingItem.Row -> oldItem.key == newItem.key
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
