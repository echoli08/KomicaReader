package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.komica.reader.databinding.ItemBoardCategoryBinding
import com.komica.reader.databinding.ItemSettingBinding

class SettingsAdapter(private val onClick: (String) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<SettingItem>()

    fun SubmitItems(newItems: List<SettingItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = if (items[position] is SettingItem.Section) ViewTypeSection else ViewTypeRow

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == ViewTypeSection) SectionViewHolder(ItemBoardCategoryBinding.inflate(inflater, parent, false)) else RowViewHolder(ItemSettingBinding.inflate(inflater, parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
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
    }
}
