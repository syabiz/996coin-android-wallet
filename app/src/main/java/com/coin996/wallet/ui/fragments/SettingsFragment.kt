package com.coin996.wallet.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.coin996.wallet.BuildConfig
import com.coin996.wallet.R
import com.coin996.wallet.databinding.FragmentSettingsBinding
import com.coin996.wallet.ui.viewmodels.HomeViewModel
import com.coin996.wallet.utils.SecurePreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    @Inject lateinit var securePreferences: SecurePreferences

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvVersion.text = "Version ${BuildConfig.VERSION_NAME}"
        binding.switchBiometric.isChecked = securePreferences.isBiometricEnabled()

        // Check biometric availability
        val biometricManager = BiometricManager.from(requireContext())
        val canUseBiometric = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
        binding.rowBiometric.isEnabled = canUseBiometric
        binding.switchBiometric.isEnabled = canUseBiometric

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            securePreferences.setBiometricEnabled(isChecked)
        }

        binding.rowChangePin.setOnClickListener {
            // TODO: navigate to change PIN flow
            requireContext().let { ctx ->
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("Change PIN")
                    .setMessage("PIN change flow will be implemented here.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        binding.rowShowSeed.setOnClickListener {
            // Show seed words behind PIN confirmation
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Show Recovery Phrase")
                .setMessage("⚠️ Never share your recovery phrase with anyone.\n\nMake sure no one is watching your screen.")
                .setPositiveButton("Show") { _, _ ->
                    showSeedWords()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        binding.rowRescan.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Rescan Blockchain")
                .setMessage("This will delete the local blockchain data and re-download it from peers. This may take a while.\n\nContinue?")
                .setPositiveButton("Rescan") { _, _ ->
                    // TODO: implement rescan (delete chain file, restart SPV)
                    requireContext().let { ctx ->
                        android.widget.Toast.makeText(ctx,
                            "Rescan started. The app will restart sync.",
                            android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
    }

    private fun showSeedWords() {
        // In production: require PIN/biometric before showing
        val words = viewModel.walletState.value.let {
            // get from wallet manager
            "Word list will be displayed here after PIN confirmation"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Your Recovery Phrase")
            .setMessage(words)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.walletState.collectLatest { state ->
                // peer count from SPV
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
