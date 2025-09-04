package com.builtbyusman.attendify.student

import android.content.Context
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast

object Utils {

    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun showProgress(progressBar: ProgressBar) {
        progressBar.visibility = View.VISIBLE
    }

    fun hideProgress(progressBar: ProgressBar) {
        progressBar.visibility = View.GONE
    }
}
