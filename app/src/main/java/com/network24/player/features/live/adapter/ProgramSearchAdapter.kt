package com.network24.player.features.live.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.network24.player.R
import com.network24.player.databinding.ItemProgramSearchResultBinding
import com.network24.player.features.live.models.ProgramSearchResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProgramSearchAdapter(
    private val onSelected: (ProgramSearchResult) -> Unit
) : RecyclerView.Adapter<ProgramSearchAdapter.ResultViewHolder>() {

    private val results = mutableListOf<ProgramSearchResult>()

    inner class ResultViewHolder(
        val binding: ItemProgramSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        return ResultViewHolder(
            ItemProgramSearchResultBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int = results.size

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val result = results[position]
        val program = result.program

        holder.binding.imgChannelLogo.load(result.channel.stream_icon) {
            placeholder(R.drawable.app_logo)
            error(R.drawable.app_logo)
            crossfade(true)
        }

        holder.binding.txtProgramTitle.text = program.title ?: "Program information unavailable"
        holder.binding.txtChannelName.text = result.channel.name ?: "Unknown channel"
        holder.binding.txtSchedule.text = scheduleText(program.startTimestamp, program.stopTimestamp)

        holder.binding.cardRoot.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.cardRoot.strokeWidth = if (hasFocus) dp(holder, 2) else dp(holder, 1)
        }

        holder.binding.root.setOnClickListener {
            val selectedPosition = holder.bindingAdapterPosition
            if (selectedPosition != RecyclerView.NO_POSITION) {
                onSelected(results[selectedPosition])
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitResults(newResults: List<ProgramSearchResult>) {
        results.clear()
        results.addAll(newResults)
        notifyDataSetChanged()
    }

    private fun scheduleText(start: Long?, stop: Long?): String {
        val now = System.currentTimeMillis()
        val startTime = formatTime(start)
        val stopTime = formatTime(stop)

        return if (start != null && stop != null && start <= now && stop > now) {
            "ON NOW  •  Ends $stopTime"
        } else {
            "STARTS $startTime  •  Ends $stopTime"
        }
    }

    private fun formatTime(timeMs: Long?): String {
        if (timeMs == null || timeMs <= 0L) return "--"
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timeMs))
    }

    private fun dp(holder: ResultViewHolder, value: Int): Int {
        return (value * holder.itemView.resources.displayMetrics.density).toInt()
    }
}
