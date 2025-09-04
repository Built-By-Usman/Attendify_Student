package com.builtbyusman.attendify.student.ViewModels

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.builtbyusman.attendify.student.R
import com.builtbyusman.attendify.student.databinding.FragmentSignUpBinding
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

class SignUpFragment : Fragment() {
    private var _binding: FragmentSignUpBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var preferences: SharedPreferences

    companion object {
        const val STUDENTS_COLLECTION = "Students"
        const val ROLE_COLLECTION = "Credentials"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        preferences = requireContext().getSharedPreferences("Details", Context.MODE_PRIVATE)

        binding.SignUpButtonSignUp.setOnClickListener { handleSignUp() }
        binding.SignUpTvLogin.setOnClickListener {
            findNavController().navigate(R.id.action_signUpFragment_to_loginFragment)
        }
    }

    private fun handleSignUp() {
        val name = binding.SignUpEtName.text.toString().trim()
        val rollNo = binding.SignUpEtRollNo.text.toString().trim()
        val email = binding.SignUpEtEmail.text.toString().trim()
        val password = binding.SignUpEtPassword.text.toString().trim()
        val confirm = binding.SignUpEtConfirmPassword.text.toString().trim()

        when {
            name.isEmpty() || rollNo.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty() -> {
                showToast("Please fill in all fields")
                return
            }
            rollNo.length < 13 -> {
                showToast("Enter complete Roll No")
                return
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.SignUpEtEmail.error = "Invalid email"
                binding.SignUpEtEmail.requestFocus()
                return
            }
            password.length < 6 -> {
                showToast("Password must be at least 6 characters")
                return
            }
            password != confirm -> {
                showToast("Passwords do not match")
                return
            }
        }

        val dialog = createProgressDialog()
        dialog.show()

        lifecycleScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user ?: throw Exception("User creation failed")

                user.sendEmailVerification().await()

                val studentData = mapOf("Name" to name, "Roll No" to rollNo)
                firestore.collection(STUDENTS_COLLECTION).document(user.uid).set(studentData).await()

                val roleData = mapOf("Role" to "Student",
                    "is Login" to false)
                auth.uid?.let {
                    firestore.collection(ROLE_COLLECTION).document(it).set(roleData).await() }

                preferences.edit().apply {
                    putString("name", name)
                    putString("rollNo", rollNo)
                    apply()
                }

                dialog.dismiss()
                showToast("Verification email sent!")
                findNavController().navigate(R.id.action_signUpFragment_to_loginFragment)

            } catch (e: Exception) {
                dialog.dismiss()
                val msg = when {
                    e.message?.contains("email address is already in use", true) == true ->
                        "Email already registered. Try logging in."
                    e.message?.contains("network error", true) == true ->
                        "Network error. Please try again."
                    else -> "Sign-up failed: ${e.localizedMessage}"
                }
                showToast(msg)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun createProgressDialog(): Dialog = Dialog(requireContext()).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.progress_bar)
        setCancelable(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
