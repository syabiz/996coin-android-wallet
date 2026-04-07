package com.coin996.wallet.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.coin996.wallet.R
import com.coin996.wallet.databinding.FragmentSendBinding
import com.coin996.wallet.ui.viewmodels.SendResult
import com.coin996.wallet.ui.viewmodels.SendViewModel
import com.coin996.wallet.utils.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SendFragment : Fragment() {

    private var _binding: FragmentSendBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SendViewModel by viewModels()

    // QR scanner launcher
    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            val payment = parsePaymentUri(result.contents) ?: run {
                // plain address
                binding.etAddress.setText(result.contents)
                return@registerForActivityResult
            }
            binding.etAddress.setText(payment.address)
            payment.amount?.let { binding.etAmount.setText(it.toString()) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Pre-fill from deep link or navigation args
        arguments?.let { args ->
            args.getString("address")?.takeIf { it.isNotEmpty() }?.let {
                binding.etAddress.setText(it)
            }
            val amount = args.getFloat("amount", 0f)
            if (amount > 0) binding.etAmount.setText(amount.toString())
        }

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        // QR scan icon in address field
        binding.tilAddress.setEndIconOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan 996coin address QR")
                setBeepEnabled(false)
                setOrientationLocked(false)
            }
            qrScanLauncher.launch(options)
        }

        // MAX button
        binding.tilAmount.setEndIconOnClickListener {
            val balance = viewModel.walletState.value.balance.value
            // Menggunakan default fee 0.0001 NNS (10,000 satoshi)
            val estimatedFee = 10_000L
            val maxAmount = (balance - estimatedFee).coerceAtLeast(0).satoshiToNns()
            binding.etAmount.setText(if (maxAmount > 0) String.format("%.8f", maxAmount) else "0")
        }

        // USD preview
        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val amount = s.toString().toDoubleOrNull() ?: 0.0
                val priceUsd = viewModel.priceUsd.value
                val usd = amount * priceUsd
                binding.tvAmountUsd.text = "≈ ${String.format("$%.4f", usd)} USD"
            }
        })

        // Send button
        binding.btnSend.setOnClickListener {
            val address = binding.etAddress.text.toString().trim()
            val amountStr = binding.etAmount.text.toString().trim()

            // Validate
            if (!isValid996coinAddress(address)) {
                binding.tilAddress.error = getString(R.string.send_error_address)
                return@setOnClickListener
            } else {
                binding.tilAddress.error = null
            }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                binding.tilAmount.error = getString(R.string.send_error_amount)
                return@setOnClickListener
            } else {
                binding.tilAmount.error = null
            }

            val balance = viewModel.walletState.value.balance.value.satoshiToNns()
            if (amount > balance) {
                binding.tilAmount.error = getString(R.string.send_error_insufficient)
                return@setOnClickListener
            }

            // Show confirm dialog
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.send_confirm_title)
                .setMessage(getString(R.string.send_confirm_msg,
                    String.format("%.8f", amount), address))
                .setPositiveButton(R.string.btn_confirm) { _, _ ->
                    viewModel.send(address, amount)
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.walletState.collectLatest { state ->
                binding.tvAvailableBalance.text =
                    "${String.format("%.8f", state.balance.value.satoshiToNns())} NNS"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isSending.collectLatest { sending ->
                binding.btnSend.isEnabled = !sending
                binding.loadingContainer.visibility = if (sending) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.sendResult.collectLatest { result ->
                result ?: return@collectLatest
                when (result) {
                    is SendResult.Success -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.send_success_title)
                            .setMessage(getString(R.string.send_success_txid, result.txId))
                            .setPositiveButton("OK") { _, _ ->
                                // Clear form
                                binding.etAddress.text?.clear()
                                binding.etAmount.text?.clear()
                                viewModel.clearResult()
                            }
                            .show()
                    }
                    is SendResult.Error -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Transaction Failed")
                            .setMessage(result.message)
                            .setPositiveButton("OK") { _, _ -> viewModel.clearResult() }
                            .show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
