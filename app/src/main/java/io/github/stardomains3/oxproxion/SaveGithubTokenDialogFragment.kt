package io.github.stardomains3.oxproxion

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SaveGithubTokenDialogFragment : DialogFragment() {

    private val viewModel: ChatViewModel by activityViewModels()

    companion object {
        const val TAG = "SaveGithubTokenDialogFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_save_github_token, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog?.window?.setDimAmount(0.8f)

        val sharedPreferencesHelper = SharedPreferencesHelper(requireContext())
        val editTextToken = view.findViewById<TextInputEditText>(R.id.edit_text_github_token)
        val buttonSave = view.findViewById<MaterialButton>(R.id.button_save_github_token)
        val buttonCancel = view.findViewById<MaterialButton>(R.id.button_cancel_github_token)
        val linkTextView = view.findViewById<TextView>(R.id.github_token_link)

        // Pre-populate with existing token if any
        val existingToken = sharedPreferencesHelper.getApiKeyFromPrefs("github_token")
        if (existingToken.isNotBlank()) {
            editTextToken.setText(existingToken)
        }

        linkTextView.setOnClickListener {
            val url = "https://github.com/settings/tokens/new?scopes=repo"
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "No browser found to open link", Toast.LENGTH_SHORT).show()
            }
        }

        buttonSave.setOnClickListener {
            val token = editTextToken.text.toString().trim()
            if (token.isNotBlank()) {
                sharedPreferencesHelper.saveApiKey("github_token", token)
                Toast.makeText(requireContext(), "GitHub Token saved.", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                editTextToken.error = "Token cannot be empty"
            }
        }

        buttonCancel.setOnClickListener {
            dismiss()
        }

        editTextToken.requestFocus()
    }
}
