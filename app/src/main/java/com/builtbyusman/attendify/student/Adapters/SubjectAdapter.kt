package com.builtbyusman.attendify.student.Adapters

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.builtbyusman.attendify.student.ViewModels.ShowAttendanceFragment
import com.builtbyusman.attendify.student.Models.SubjectStructure
import com.builtbyusman.attendify.student.R
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class SubjectAdapter(
    private var subjectList: MutableList<SubjectStructure>,
    private val context: Context,
    var rollNo: String,
    private val userId: String
) : RecyclerView.Adapter<SubjectAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val subjectNameText: TextView = itemView.findViewById(R.id.TvSubject)
        val subjectCard: CardView = itemView.findViewById(R.id.LayoutClassRow)
        val subjectMenu: ImageButton = itemView.findViewById(R.id.TvMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.subject_recycle_view, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = subjectList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val subject = subjectList[position]
        holder.subjectNameText.text = subject.subjectName

        holder.subjectCard.setOnClickListener {
            val fragment = ShowAttendanceFragment().apply {
                arguments = Bundle().apply {
                    putString("Show User Id", subject.userId)
                    putString("Show Class Id", subject.classId)
                    putString("Show Subject Id", subject.subjectId)
                    putString("Show Roll No", rollNo)
                }
            }
            (context as AppCompatActivity)
                .supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragmentContainerView, fragment)
                .addToBackStack(null)
                .commit()
        }

        holder.subjectMenu.setOnClickListener {
            showPopupMenu(holder.subjectMenu, subject)
        }
    }

    private fun showPopupMenu(view: View, subject: SubjectStructure) {
        val popupMenu = PopupMenu(context, view)
        popupMenu.menuInflater.inflate(R.menu.opt_subjects, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.DeleteSubject -> {
                    AlertDialog.Builder(context).apply {
                        setTitle("Delete Subject")
                        setMessage("Are you sure you want to delete this subject?")
                        setPositiveButton("Delete") { _, _ ->
                            FirebaseFirestore.getInstance().collection("Students")
                                .document(userId)
                                .collection("Subjects")
                                .document(subject.subjectId)
                                .delete()
                                .addOnSuccessListener {
                                    val index = subjectList.indexOf(subject)
                                    if (index != -1) {
                                        subjectList.removeAt(index)
                                        notifyItemRemoved(index)
                                    }
                                    Toast.makeText(context, "Subject Deleted", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                        setNegativeButton("Cancel", null)
                        show()
                    }
                    true
                }

                else -> false
            }
        }

        popupMenu.show()
    }



}
