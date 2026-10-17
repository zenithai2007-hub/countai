package com.zenithai.countai.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zenithai.countai.R
import com.zenithai.countai.databinding.FragmentAuthBinding
import com.zenithai.countai.di.ServiceLocator
import com.zenithai.countai.sync.SupabaseConfig
import kotlinx.coroutines.launch

/**
 * Account screen: sign in / create account / magic link / sign out.
 *
 * Signing in is OPTIONAL — it only unlocks cloud backup of counting history.
 * Everything in the app works without an account. When Supabase isn't
 * configured in this build, the form hides and explains offline mode.
 */
class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSignin.setOnClickListener {
            val (email, pass) = credentials() ?: return@setOnClickListener
            runAuth { ServiceLocator.authRepository(requireContext()).signIn(email, pass) }
        }
        binding.btnSignup.setOnClickListener {
            val (email, pass) = credentials() ?: return@setOnClickListener
            runAuth { ServiceLocator.authRepository(requireContext()).signUp(email, pass) }
        }
        binding.btnMagic.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            if (email.isEmpty()) {
                showError(getString(R.string.auth_need_email))
                return@setOnClickListener
            }
            runAuth(
                work = { ServiceLocator.authRepository(requireContext()).sendMagicLink(email) },
                successMessage = getString(R.string.auth_check_email),
            )
        }
        binding.btnSignout.setOnClickListener {
            lifecycleScope.launch {
                ServiceLocator.authRepository(requireContext()).signOut()
                refreshUi()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun credentials(): Pair<String, String>? {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val pass = binding.etPassword.text?.toString().orEmpty()
        if (email.isEmpty() || pass.isEmpty()) {
            showError(getString(R.string.auth_need_both))
            return null
        }
        return email to pass
    }

    private fun runAuth(
        successMessage: String? = null,
        work: suspend () -> Result<Unit>,
    ) {
        hideError()
        setBusy(true)
        lifecycleScope.launch {
            val result = try {
                work()
            } catch (e: Exception) {
                Result.failure<Unit>(e)
            }
            setBusy(false)
            result
                .onSuccess {
                    if (successMessage != null) {
                        android.widget.Toast.makeText(
                            requireContext(), successMessage, android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    refreshUi()
                }
                .onFailure { e ->
                    showError(e.message ?: getString(R.string.auth_failed_generic))
                }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.btnSignin.isEnabled = !busy
        binding.btnSignup.isEnabled = !busy
        binding.btnMagic.isEnabled = !busy
    }

    private fun showError(text: String) {
        binding.tvAuthError.text = text
        binding.tvAuthError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvAuthError.visibility = View.GONE
    }

    private fun refreshUi() {
        val ctx = requireContext()
        if (!SupabaseConfig.isConfigured) {
            binding.authForm.visibility = View.GONE
            binding.authSignedin.visibility = View.GONE
            binding.tvAuthDesc.text = getString(R.string.auth_not_configured)
            return
        }
        val repo = ServiceLocator.authRepository(ctx)
        if (repo.isSignedIn) {
            binding.authForm.visibility = View.GONE
            binding.authSignedin.visibility = View.VISIBLE
            binding.tvSignedInAs.text =
                getString(R.string.auth_signed_in_as, repo.currentEmail ?: "")
        } else {
            binding.authForm.visibility = View.VISIBLE
            binding.authSignedin.visibility = View.GONE
            binding.tvAuthDesc.text = getString(R.string.auth_desc)
        }
    }
}
