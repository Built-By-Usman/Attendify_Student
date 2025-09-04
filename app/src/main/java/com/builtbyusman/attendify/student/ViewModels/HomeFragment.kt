package com.builtbyusman.attendify.student.ViewModels

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.builtbyusman.attendify.student.Adapters.SubjectAdapter
import com.builtbyusman.attendify.student.Models.SubjectStructure
import com.builtbyusman.attendify.student.R
import com.builtbyusman.attendify.student.databinding.FragmentHomeBinding
import com.google.firebase.firestore.SetOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.builtbyusman.attendify.student.Utils
import com.builtbyusman.attendify.student.Utils.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext



class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: SharedPreferences
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val subjects = mutableListOf<SubjectStructure>()
    private lateinit var adapter: SubjectAdapter
    private lateinit var loadingDialog: Dialog

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    private val currentUserId: String
        get() = prefs.getString("userid", "").orEmpty()

    private var studentName: String
        get() = prefs.getString("name", "").orEmpty()
        set(value) = prefs.edit().putString("name", value).apply()

    private var studentRoll: String
        get() = prefs.getString("rollNo", "").orEmpty()
        set(value) = prefs.edit().putString("rollNo", value).apply()

    // Barcode scanner client for attendance
    private val scanner: GmsBarcodeScanner by lazy {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(requireActivity(), options)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)
        prefs = requireContext().getSharedPreferences("Details", Context.MODE_PRIVATE)

        setupLoadingDialog()
        setupRecyclerView()
        setupClickListeners()
        loadInitialData()
    }

    private fun setupLoadingDialog() {
        loadingDialog = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.progress_bar)
            setCancelable(false)
        }
    }

    private fun setupRecyclerView() {
        adapter = SubjectAdapter(subjects, requireContext(), studentRoll, currentUserId)
        binding.HomeRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HomeFragment.adapter
        }
    }

    private fun setupClickListeners() {
        binding.HomeImgBtnEditName.setOnClickListener { showEditProfileDialog() }
        binding.HomeImgBtnAddSubject.setOnClickListener { checkCameraPermissionAndScan() }
        binding.DeleteMenu.setOnClickListener { showAccountMenu(it) }
        binding.HomeMarkAttendance.setOnClickListener { markAttendance() }
    }

    // Initiates QR scan for attendance
    private fun markAttendance() {
        if (studentName.isBlank() || studentRoll.isBlank()) {
            toast("Load your profile before scanning.")
            return
        }
        scanner.startScan()
            .addOnSuccessListener { code -> handleAttendanceScanResult(code.rawValue?.trim()) }
            .addOnFailureListener { e -> toast("Scan failed: ${e.message}") }
    }

    private fun handleAttendanceScanResult(raw: String?) {
        if (raw.isNullOrEmpty()) {
            toast("No data found in QR code.")
            return
        }
        // Expected format: uid||classId||subjectId||date||timestamp
        val parts = raw.split("||").map { it.trim() }
        if (parts.size != 5) {
            toast("Invalid QR code format.")
            return
        }
        val (uid, classId, subjectId, date, timeStr) = parts
        val timeLong = timeStr.toLongOrNull()
        val currentTime = System.currentTimeMillis()
        if (timeLong == null || currentTime - timeLong > 20 * 60 * 1000) {
            toast("QR code has expired.")
            return
        }
        // Proceed to verify and mark attendance
        verifyAndMarkAttendance(uid, classId, subjectId, date)
    }

    private fun verifyAndMarkAttendance(uid: String, cid: String, sid: String, date: String) {
        // 1) verify student enrolled in this subject
        firestore.collection("Students").document(currentUserId)
            .collection("Subjects").document(sid)
            .get().addOnSuccessListener { studentDoc ->
                if (!studentDoc.exists()) {
                    toast("You are not enrolled in this subject.")
                    return@addOnSuccessListener
                }
                // 2) verify in teacher's class list
                firestore.collection("Teachers").document(uid)
                    .collection("Classes").document(cid)
                    .collection("Subjects").document(sid)
                    .collection("Students").document(studentRoll.uppercase())
                    .get().addOnSuccessListener { classDoc ->
                        if (!classDoc.exists()) {
                            toast("Your roll number is not registered in this class.")
                            return@addOnSuccessListener
                        }
                        val recordedName = classDoc.getString("Name") ?: ""
                        if (!recordedName.equals(studentName, ignoreCase = true)) {
                            toast("Your name does not match the record.")
                            return@addOnSuccessListener
                        }
                        // 3) mark or update attendance
                        proceedAttendance(uid, cid, sid, date)
                    }
            }
    }

    private fun proceedAttendance(uid: String, cid: String, sid: String, date: String) {
        val attendanceRef = firestore.collection("Teachers").document(uid)
            .collection("Classes").document(cid)
            .collection("Subjects").document(sid)
            .collection("Attendance").document("${studentRoll}_$date")

        attendanceRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val status = doc.getString("status")
                if (status == "P") {
                    toast("Already marked present for $date.")
                } else {
                    attendanceRef.update(mapOf(
                        "status" to "P",
                        "timestamp" to FieldValue.serverTimestamp()
                    )).addOnSuccessListener {
                        toast("Attendance updated to Present.")
                    }
                }
            } else {
                val data = mapOf(
                    "studentName" to studentName,
                    "rollNo" to studentRoll,
                    "date" to date,
                    "status" to "P",
                    "timestamp" to FieldValue.serverTimestamp()
                )
                attendanceRef.set(data).addOnSuccessListener {
                    toast("Attendance marked successfully.")
                }
            }
        }
    }

    private fun loadInitialData() {
        viewLifecycleOwner.lifecycleScope.launch {
            loadProfile()
            loadSubjects()
        }
    }

    private fun showEditProfileDialog() {
        val dialog = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.edit_name_rollno_dialog)
            setCancelable(true)
        }

        val etName = dialog.findViewById<EditText>(R.id.dialogEtName)
        val etRoll = dialog.findViewById<EditText>(R.id.dialogEtRollNo)
        val btnAdd = dialog.findViewById<MaterialButton>(R.id.dialogBtnAdd)

        etName.setText(studentName)
        etRoll.setText(studentRoll)

        btnAdd.setOnClickListener {
            val newName = etName.text.toString().trim()
            val newRoll = etRoll.text.toString().trim().uppercase()
            if (newName.isEmpty() || newRoll.isEmpty()) {
                toast("Please enter both name and roll number.")
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                runWithLoading {
                    updateProfile(newName, newRoll)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private suspend fun updateProfile(newName: String, newRoll: String) {
        firestore.collection("Students")
            .document(currentUserId)
            .set(mapOf("Name" to newName, "Roll No" to newRoll), SetOptions.merge())
            .await()
        studentName = newName
        studentRoll = newRoll
        adapter.rollNo = newRoll
        binding.HomeTvName.text = newName
        binding.HomeTvRollNo.text = newRoll
        toast("Profile updated successfully.")
    }

    private fun checkCameraPermissionAndScan() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED -> startQrScan()
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                toast("Camera access is needed to scan QR codes.")
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
            }
            else -> requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startQrScan()
            } else {
                toast("Camera permission denied.")
            }
        } else super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun startQrScan() {
        if (studentName.isBlank() || studentRoll.isBlank()) {
            toast("Load your profile before scanning.")
            return
        }
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(requireActivity(), options)
            .startScan()
            .addOnSuccessListener { code ->
                handleScanResult(code.rawValue?.trim())
            }
            .addOnFailureListener {
                toast("Scan failed. Please try again.")
            }
    }

    private fun handleScanResult(raw: String?) {
        if (raw.isNullOrEmpty()) {
            toast("No data found in QR code.")
            return
        }
        val parts = raw.split("|", limit = 3)
        if (parts.size != 3) {
            toast("Invalid QR code format.")
            return
        }
        val (uid, classId, subjectId) = parts
        viewLifecycleOwner.lifecycleScope.launch {
            loadingDialog.show()
            try {
                validateAndAddSubject(uid, classId, subjectId)
            } catch (e: Exception) {
                // regardless of the underlying exception (subject not found, name mismatch, etc.)
                // show the same “Invalid QR code format.” message:
                toast("Invalid QR code format.")
            } finally {
                loadingDialog.dismiss()
            }
        }

    }

    private suspend fun validateAndAddSubject(uid: String, classId: String, subjectId: String) {
        runWithLoading {
            val studentDoc = firestore.collection("Teachers")
                .document(uid)
                .collection("Classes")
                .document(classId)
                .collection("Subjects")
                .document(subjectId)
                .collection("Students")
                .document(studentRoll)
                .get()
                .await()

            if (!studentDoc.exists()) throw Exception(getString(R.string.roll_not_registered))
            if (!studentDoc.getString("Name").equals(studentName, true)) {
                throw Exception(getString(R.string.name_mismatch))
            }

            val subjDoc = firestore.collection("Teachers")
                .document(uid)
                .collection("Classes")
                .document(classId)
                .collection("Subjects")
                .document(subjectId)
                .get()
                .await()

            if (!subjDoc.exists()) throw Exception(getString(R.string.subject_not_found))

            val studentSubRef = firestore.collection("Students")
                .document(currentUserId)
                .collection("Subjects")
                .document(subjectId)

            if (studentSubRef.get().await().exists()) {
                showToast(requireContext(),getString(R.string.subject_already_added))
                return@runWithLoading
            }

            studentSubRef.set(
                mapOf(
                    "User ID" to uid,
                    "Class ID" to classId,
                    "Subject ID" to subjectId,
                    "Time Stamp" to FieldValue.serverTimestamp()
                )
            ).await()

            showToast(requireContext(),getString(R.string.subject_added))
            loadSubjects()
        }
    }

    private suspend fun loadProfile() {
        binding.HomeTvName.text = studentName
        binding.HomeTvRollNo.text = studentRoll
        if (studentName.isBlank() || studentRoll.isBlank()) {
            runWithLoading {
                val doc = firestore.collection("Students")
                    .document(currentUserId)
                    .get().await()
                studentName = doc.getString("Name").orEmpty()
                studentRoll = doc.getString("Roll No").orEmpty()
                binding.HomeTvName.text = studentName
                binding.HomeTvRollNo.text = studentRoll
                adapter.rollNo = studentRoll
            }
        }
    }

    private suspend fun loadSubjects() = runWithLoading {
        try {
            subjects.clear()
            val snapshot = firestore.collection("Students")
                .document(currentUserId)
                .collection("Subjects")
                .orderBy("Time Stamp", Query.Direction.ASCENDING)
                .get()
                .await()

            snapshot.documents.forEach { doc ->
                val uid = doc.getString("User ID").orEmpty()
                val classId = doc.getString("Class ID").orEmpty()
                val subjectId = doc.getString("Subject ID").orEmpty()

                // Get subject details from the teacher's collection
                val subjectDoc = firestore.collection("Teachers")
                    .document(uid)
                    .collection("Classes")
                    .document(classId)
                    .collection("Subjects")
                    .document(subjectId)
                    .get()
                    .await()

                subjectDoc.getString("Subject Name")?.let { name ->
                    subjects.add(SubjectStructure(uid, classId, subjectId, name))
                }
            }

            // Update UI on main thread
            withContext(Dispatchers.Main) {
                adapter.notifyDataSetChanged()
                binding.HomeTvAddSubject.visibility = if (subjects.isEmpty())
                    View.VISIBLE else View.GONE
            }
        } catch (e: Exception) {
            toast("Failed to load subjects: ${e.message}")
        }
    }

    private fun showAccountMenu(anchor: View) {
        androidx.appcompat.widget.PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.account_delete, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.SignOut -> signOut()
                    R.id.DeleteAccount -> confirmDeleteAccount()
                }
                true
            }
        }.show()
    }

    private fun signOut() {
        auth.signOut()
        prefs.edit().clear().apply()
        toast("Signed out.")
        findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
    }

    private fun confirmDeleteAccount() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete account?")
            .setMessage("Are you sure you want to delete your account?")
            .setPositiveButton("Yes") { _, _ -> showReauthDialog() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showReauthDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_reauth, null)
        val pwField = view.findViewById<EditText>(R.id.etPassword)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Re-enter password")
            .setView(view)
            .setPositiveButton("Confirm") { dialog, _ ->
                val pw = pwField.text.toString()
                if (pw.isNotBlank()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        runWithLoading { reauthenticateAndDelete(pw) }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun reauthenticateAndDelete(password: String) {
        val user = auth.currentUser ?: run {
            toast("No user to delete.")
            return
        }
        try {
            val cred = EmailAuthProvider.getCredential(user.email!!, password)
            user.reauthenticate(cred).await()
            deleteAccount(user)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            toast("Wrong password.")
        }
    }

    private suspend fun deleteAccount(user: FirebaseUser) {
        val uid = user.uid

        // 1) delete Credentials doc
        firestore.collection("Credentials").document(uid).delete()

        // 3) delete Teacher doc
        firestore.collection("Students").document(uid).delete()

        // 4) delete auth user
        user.delete().await()

        // 5) clear prefs and navigate home
        prefs.edit { clear() }
        withContext(Dispatchers.Main) {
            Utils.showToast(requireContext(),"Account deleted.")
            findNavController().navigate(R.id.action_homeFragment_to_signUpFragment)
        }
    }


    private suspend fun <T> runWithLoading(block: suspend () -> T): T? {
        loadingDialog.show()
        return try {
            block()
        } catch (e: Exception) {
            // log if you want: Log.e("runWithLoading", "error in block", e)
            toast("Something went wrong. Please try again.")
            null    // <— swallow it
        } finally {
            loadingDialog.dismiss()
        }
    }

    private fun toast(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
