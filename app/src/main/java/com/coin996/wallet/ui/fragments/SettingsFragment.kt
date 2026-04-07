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
            showPinDialog {
                val bundle = Bundle().apply { putBoolean("is_change_pin", true) }
                androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    .navigate(R.id.setPinFragment, bundle)
            }
        }

        binding.rowImportWif.setOnClickListener {
            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                .navigate(R.id.action_settings_to_importWif)
        }

        binding.rowRescan.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Rescan Blockchain")
                .setMessage("This will delete the local blockchain data and re-download it from peers. This may take a while.\n\nContinue?")
                .setPositiveButton("Rescan") { _, _ ->
                    lifecycleScope.launch {
                        viewModel.walletRepository.walletManager.rescanBlockchain()
                        android.widget.Toast.makeText(requireContext(),
                            "Rescan started. The app is re-syncing from genesis/checkpoint.",
                            android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        binding.rowShowSeed.setOnClickListener {
            showPinDialog {
                showSeedWords()
            }
        }

        binding.rowGithub.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, 
                android.net.Uri.parse("https://github.com/996coin/996coin"))
            startActivity(intent)
        }
    }

    private fun showPinDialog(onSuccess: () -> Unit) {
        val dialogBinding = com.coin996.wallet.databinding.DialogPinConfirmBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.btnConfirm.setOnClickListener {
            val enteredPin = dialogBinding.etPin.text.toString()
            if (securePreferences.verifyPin(enteredPin)) {
                dialog.dismiss()
                onSuccess()
            } else {
                dialogBinding.tilPin.error = "Incorrect PIN"
            }
        }
        dialog.show()
    }

    private fun showSeedWords() {
        val words = viewModel.walletRepository.walletManager.getMnemonicWords()?.joinToString(" ")
            ?: "Mnemonic not available"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Your Recovery Phrase")
            .setMessage(words)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.walletState.collectLatest { state ->
                binding.tvPeerCount.text = "${state.peerCount} peers"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
