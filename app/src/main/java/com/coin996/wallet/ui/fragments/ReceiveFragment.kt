package com.coin996.wallet.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.coin996.wallet.R
import com.coin996.wallet.core.spv.AddressType
import com.coin996.wallet.databinding.FragmentReceiveBinding
import com.coin996.wallet.ui.viewmodels.ReceiveViewModel
import com.coin996.wallet.utils.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReceiveFragment : Fragment() {

    private var _binding: FragmentReceiveBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReceiveViewModel by viewModels()

    private var currentAddress = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReceiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAddressTypeTabs()
        setupListeners()
        observeViewModel()
    }

    private fun setupAddressTypeTabs() {
        // Chip group untuk pilih jenis alamat
        binding.chipLegacy.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.selectAddressType(AddressType.LEGACY)
        }
        binding.chipNestedSegwit.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.selectAddressType(AddressType.NESTED_SEGWIT)
        }
        binding.chipNativeSegwit.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.selectAddressType(AddressType.NATIVE_SEGWIT)
        }
        // Default: Legacy
        binding.chipLegacy.isChecked = true
    }

    private fun setupListeners() {
        binding.btnCopy.setOnClickListener {
            requireContext().copyToClipboard("996coin Address", currentAddress)
        }
        binding.btnShare.setOnClickListener { shareAddress() }
        binding.btnNewAddress.setOnClickListener { viewModel.generateNewAddress() }
        binding.btnUpdateQr.setOnClickListener {
            val amount = binding.etRequestAmount.text.toString().toDoubleOrNull()
            updateQr(currentAddress, amount)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.address.collectLatest { address ->
                if (address.isNotEmpty()) {
                    currentAddress = address
                    binding.tvAddress.text = address
                    updateQr(address)
                    // Update label deskripsi tipe alamat
                    updateAddressTypeLabel(viewModel.selectedType.value)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedType.collectLatest { type ->
                updateAddressTypeLabel(type)
            }
        }
    }

    private fun updateAddressTypeLabel(type: AddressType) {
        binding.tvAddressTypeDesc.text = when (type) {
            AddressType.LEGACY        -> getString(R.string.receive_legacy_desc)
            AddressType.NESTED_SEGWIT -> getString(R.string.receive_nested_desc)
            AddressType.NATIVE_SEGWIT -> getString(R.string.receive_native_desc)
        }
    }

    private fun updateQr(address: String, amount: Double? = null) {
        if (address.isEmpty() || address == "Loading…") return
        val uri = buildPaymentUri(address, amount)
        try {
            binding.ivQrCode.setImageBitmap(generateQrCode(uri, 512))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shareAddress() {
        val typeLabel = when (viewModel.selectedType.value) {
            AddressType.LEGACY        -> "Legacy"
            AddressType.NESTED_SEGWIT -> "Nested SegWit"
            AddressType.NATIVE_SEGWIT -> "Native SegWit"
        }
        val text = "My 996coin Address ($typeLabel):\n$currentAddress"
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, "996coin Address")
            }, getString(R.string.receive_share)
        ))
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
