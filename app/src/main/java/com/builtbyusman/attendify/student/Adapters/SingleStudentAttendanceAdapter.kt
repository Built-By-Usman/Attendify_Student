package com.builtbyusman.attendify.student.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.builtbyusman.attendify.student.R

class SingleStudentAttendanceAdapter(
    private val studentName: String,
    private val rollNumber: String,
    private val dateHeaders: List<String>,
    private val attendanceMap: Map<String, String>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM   = 1
    }

    // Total spans: Name + RollNo + one per date
    private val columnsPerRow: Int
        get() = dateHeaders.size + 2

    override fun getItemCount(): Int = columnsPerRow * 2
    override fun getItemViewType(position: Int): Int =
        if (position < columnsPerRow) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_attendance_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_attendance_row, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.bind(position)
        } else {
            val itemPos    = position - columnsPerRow
            val columnIndex = itemPos % columnsPerRow
            (holder as ItemViewHolder).bind(columnIndex)
        }
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tv = itemView.findViewById<TextView>(R.id.tvHeader)
        fun bind(pos: Int) {
            tv.text = when (pos) {
                0 -> "Name"
                1 -> "Roll No"
                else -> dateHeaders.getOrNull(pos - 2) ?: "-"
            }
        }
    }

    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tv = itemView.findViewById<TextView>(R.id.tvData)
        fun bind(col: Int) {
            tv.text = when (col) {
                0 -> studentName
                1 -> rollNumber
                else -> {
                    val dateKey = dateHeaders[col - 2]
                    attendanceMap[dateKey].takeIf { it?.isNotEmpty() == true } ?: "-"
                }
            }
        }
    }
}