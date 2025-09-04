package com.builtbyusman.attendify.student.ViewModels

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.builtbyusman.attendify.student.R
import com.builtbyusman.attendify.student.databinding.FragmentSplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
class SplashFragment : Fragment() {
    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1) Initialize SharedPreferences first
        preferences = requireContext()
            .getSharedPreferences("Details", Context.MODE_PRIVATE)

        // 2) Now you can safely call setupVersioning()
        setupVersioning()

        viewLifecycleOwner.lifecycleScope.launch {
            delay(500)
            if (!isAdded) return@launch

            val isLoggedIn = preferences.getBoolean("Check", false)
            val action = if (isLoggedIn)
                R.id.action_splashFragment_to_homeFragment
            else
                R.id.action_splashFragment_to_signUpFragment

            findNavController().navigate(action)
        }
    }

    private fun setupVersioning() {
        val pm = requireContext().packageManager
        val pkg = requireContext().packageName

        val currentVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0).versionName
        }

        val savedVersion = preferences.getString("app_version", null)
        if (savedVersion != currentVersion) {
            // Clear all old data on first launch of a new version
            preferences.edit()
                .clear()
                .putString("app_version", currentVersion)
                .apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
