package com.coin996.wallet.ui.fragments.setup

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.coin996.wallet.R
import com.coin996.wallet.core.spv.WifImportResult
import com.coin996.wallet.databinding.FragmentImportWifBinding
import com.coin996.wallet.ui.viewmodels.SetupState
import com.coin996.wallet.ui.viewmodels.SetupViewModel
import com.coin996.wallet.utils.copyToClipboard
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ImportWifFragment : Fragment() {

    private var _binding: FragmentImportWifBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SetupViewModel by activityViewModels()

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { binding.etWif.setText(it.trim()) }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentImportWifBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Enable tombol import hanya kalau WIF tidak kosong
        binding.etWif.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.btnImport.isEnabled = s.toString().trim().length > 30
                binding.tvWifError.visibility = View.GONE
            }
        })

        // Scan QR untuk WIF
        binding.tilWif.setEndIconOnClickListener {
            qrLauncher.launch(ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan QR WIF Private Key")
                setBeepEnabled(false)
            })
        }

        // Tombol import
        binding.btnImport.setOnClickListener {
            val wif = binding.etWif.text.toString().trim()
            viewModel.importWifKey(wif)
        }

        // Tombol lanjut ke set PIN
        binding.btnContinue.setOnClickListener {
            findNavController().navigate(R.id.action_importWif_to_setPin)
        }

        // Copy address buttons
        binding.btnCopyLegacy.setOnClickListener {
            requireContext().copyToClipboard("Legacy Address",
                binding.tvLegacyAddr.text.toString())
        }
        binding.btnCopyNested.setOnClickListener {
            requireContext().copyToClipboard("Nested SegWit Address",
                binding.tvNestedAddr.text.toString())
        }
        binding.btnCopyNative.setOnClickListener {
            requireContext().copyToClipboard("Native SegWit Address",
                binding.tvNativeAddr.text.toString())
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.setupState.collectLatest { state ->
                when (state) {
                    is SetupState.Loading -> {
                        binding.loadingContainer.visibility = View.VISIBLE
                        binding.btnImport.isEnabled = false
                        binding.tvWifError.visibility = View.GONE
                        binding.cardDerivedAddresses.visibility = View.GONE
                    }
                    is SetupState.WifImported -> {
                        binding.loadingContainer.visibility = View.GONE
                        binding.cardDerivedAddresses.visibility = View.VISIBLE
                        binding.btnContinue.visibility = View.VISIBLE
                        binding.btnImport.visibility = View.GONE

                        binding.tvLegacyAddr.text  = state.legacyAddress
                        binding.tvNestedAddr.text  = state.nestedSegwitAddress
                        binding.tvNativeAddr.text  = state.nativeSegwitAddress
                    }
                    is SetupState.Error -> {
                        binding.loadingContainer.visibility = View.GONE
                        binding.btnImport.isEnabled = true
                        binding.tvWifError.visibility = View.VISIBLE
                        binding.tvWifError.text = state.message
                    }
                    else -> {
                        binding.loadingContainer.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
