package com.builtbyusman.attendify.student.ViewModels

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.builtbyusman.attendify.student.R
import com.builtbyusman.attendify.student.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var preferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        preferences = requireContext().getSharedPreferences("Details", Context.MODE_PRIVATE)

        binding.LoginButtonLogin.setOnClickListener {
            performLogin()
        }

        binding.LoginTvSignUp.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_signUpFragment)
        }

        binding.LoginTvForgetPass.setOnClickListener {
            handlePasswordReset()
        }
    }

    private fun performLogin() {
        val email = binding.LoginEtEmail.text.toString().trim()
        val password = binding.LoginEtPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter all credentials", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = createProgressDialog()
        dialog.show()

        lifecycleScope.launch {
            try {
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val user = authResult.user
                if (user != null) {
                    if (user.isEmailVerified) {
                        checkUserRole(email, user, dialog)
                    } else {
                        dialog.dismiss()
                        showEmailVerificationDialog(user)
                    }
                } else {
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                dialog.dismiss()
                Toast.makeText(requireContext(), "Details are incorrect or not registered", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun checkUserRole(email: String, user: FirebaseUser, dialog: Dialog) {
        try {
            val doc = auth.uid?.let {
                firestore.collection("Credentials").document(it).get().await()
            }
            dialog.dismiss()
//            val role = doc.getString("Role")
//            if (role == "Teacher") {
//                Toast.makeText(requireContext(), "This email is registered in the Teacher app", Toast.LENGTH_SHORT).show()
//                return
//            }

            preferences.edit().apply {
                putBoolean("Check", true)
                putString("userid", user.uid)
                apply()
            }
            findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
        } catch (e: Exception) {
            dialog.dismiss()
            Toast.makeText(requireContext(), "Error Try Again", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handlePasswordReset() {
        val email = binding.LoginEtEmail.text.toString().trim()
        if (email.isEmpty()) {
            binding.LoginEtEmail.error = "Please enter your email"
            binding.LoginEtEmail.requestFocus()
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.LoginEtEmail.error = "Enter a valid email"
            binding.LoginEtEmail.requestFocus()
            return
        }

        lifecycleScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                Toast.makeText(requireContext(), "Password reset email sent.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Reset failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEmailVerificationDialog(user: FirebaseUser) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.email_verification)
        dialog.setCancelable(true)

        val verifyButton = dialog.findViewById<MaterialButton>(R.id.emailVerificationButton)
        verifyButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    user.sendEmailVerification().await()
                    Toast.makeText(requireContext(), "Verification email sent", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Failed to send verification email", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    private fun createProgressDialog(): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.progress_bar)
        dialog.setCancelable(false)
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
