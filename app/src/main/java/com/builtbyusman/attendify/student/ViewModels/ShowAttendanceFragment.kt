package com.builtbyusman.attendify.student.ViewModels

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.builtbyusman.attendify.student.Adapters.SingleStudentAttendanceAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.builtbyusman.attendify.student.R
import com.builtbyusman.attendify.student.databinding.FragmentShowAttendanceBinding
import java.text.SimpleDateFormat
import java.util.*

class ShowAttendanceFragment : Fragment() {

    private var _binding: FragmentShowAttendanceBinding? = null
    private val binding get() = _binding!!

    private lateinit var firestore: FirebaseFirestore
    private lateinit var progressDialog: Dialog
    private lateinit var userId: String
    private lateinit var classId: String
    private lateinit var subjectId: String
    private lateinit var rollNo: String

    private lateinit var className: String
    private lateinit var subjectName: String

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShowAttendanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // enable edge-to-edge
        requireActivity().enableEdgeToEdge()

        // Arguments
        userId    = arguments?.getString("Show User Id").orEmpty()
        classId   = arguments?.getString("Show Class Id").orEmpty()
        subjectId = arguments?.getString("Show Subject Id").orEmpty()
        rollNo    = arguments?.getString("Show Roll No").orEmpty()
        if (userId.isEmpty() || classId.isEmpty() || subjectId.isEmpty() || rollNo.isEmpty()) {
            requireActivity().finish()
            return
        }

        // Firestore & loading UI
        firestore = FirebaseFirestore.getInstance()
        progressDialog = Dialog(requireContext()).apply {
            setContentView(R.layout.progress_bar)
            setCancelable(false)
        }

        // Load headers and data
        loadClassAndSubjectNames()
        loadSingleStudentAttendance()
    }

    private fun loadClassAndSubjectNames() {
        // Class name
        firestore.collection("Teachers")
            .document(userId)
            .collection("Classes")
            .document(classId)
            .get()
            .addOnSuccessListener { doc ->
                className = doc.getString("Class Name").orEmpty()
                binding.tvClassName.text = className
            }
            .addOnFailureListener {
                binding.tvClassName.text = "-"
            }

        // Subject name
        firestore.collection("Teachers")
            .document(userId)
            .collection("Classes")
            .document(classId)
            .collection("Subjects")
            .document(subjectId)
            .get()
            .addOnSuccessListener { doc ->
                subjectName = doc.getString("Subject Name").orEmpty()
                binding.tvSubjectName.text = subjectName
            }
            .addOnFailureListener {
                binding.tvSubjectName.text = "-"
            }
    }

    private fun loadSingleStudentAttendance() {
        progressDialog.show()

        firestore.collection("Teachers")
            .document(userId)
            .collection("Classes")
            .document(classId)
            .collection("Subjects")
            .document(subjectId)
            .collection("Attendance")
            .get()
            .addOnSuccessListener { snapshot ->
                val dates = mutableSetOf<String>()
                val attendanceMap = mutableMapOf<String, String>()

                snapshot.documents.forEach { doc ->
                    val parts = doc.id.split("_")
                    if (parts.size >= 2 && parts[0] == rollNo) {
                        val dateKey = parts.subList(1, parts.size).joinToString("_")
                        dates.add(dateKey)
                        attendanceMap[dateKey] = doc.getString("status").orEmpty()
                    }
                }

                val sortedDates = dates
                    .map { it to dateFormat.parse(it) }
                    .filter { it.second != null }
                    .sortedBy { it.second }
                    .map { it.first }

                // Fetch the student’s name
                firestore.collection("Teachers")
                    .document(userId)
                    .collection("Classes")
                    .document(classId)
                    .collection("Subjects")
                    .document(subjectId)
                    .collection("Students")
                    .whereEqualTo("Roll No", rollNo)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { stuSnap ->
                        val studentName = stuSnap.documents
                            .firstOrNull()?.getString("Name").orEmpty()

                        val spanCount = sortedDates.size + 2
                        binding.recyclerViewAttendance.layoutManager =
                            GridLayoutManager(requireContext(), spanCount)
                        binding.recyclerViewAttendance.adapter =
                            SingleStudentAttendanceAdapter(
                                studentName   = studentName,
                                rollNumber    = rollNo,
                                dateHeaders   = sortedDates,
                                attendanceMap = attendanceMap
                            )
                        progressDialog.dismiss()
                    }
                    .addOnFailureListener {
                        progressDialog.dismiss()
                        Toast.makeText(
                            requireContext(),
                            "Failed to load student info",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                Toast.makeText(
                    requireContext(),
                    "Failed to load attendance",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
