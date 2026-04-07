package com.coin996.wallet.ui.fragments.setup

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.coin996.wallet.R
import com.coin996.wallet.databinding.*
import com.coin996.wallet.ui.activities.MainActivity
import com.coin996.wallet.ui.viewmodels.SetupState
import com.coin996.wallet.ui.viewmodels.SetupViewModel
import com.coin996.wallet.utils.SecurePreferences
import com.coin996.wallet.utils.copyToClipboard
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WelcomeFragment : Fragment() {
    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SetupViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnCreateWallet.setOnClickListener {
            viewModel.createNewWallet()
        }
        binding.btnImportWif.setOnClickListener {
            findNavController().navigate(R.id.action_welcome_to_importWif)
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.setupState.collectLatest { state ->
                if (state is SetupState.WalletRestored) {
                    findNavController().navigate(R.id.action_welcome_to_setPin)
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

@AndroidEntryPoint
class ImportWifFragment : Fragment() {
    private var _binding: FragmentImportWifBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SetupViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentImportWifBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.etWif.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.btnImport.isEnabled = !s.isNullOrBlank()
                binding.tvWifError.visibility = View.GONE
            }
        })

        binding.btnImport.setOnClickListener {
            val wif = binding.etWif.text.toString()
            if (wif.isNotEmpty()) {
                viewModel.importWifKey(wif)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.setupState.collectLatest { state ->
                when (state) {
                    is SetupState.Loading -> {
                        binding.loadingContainer.visibility = View.VISIBLE
                        binding.btnImport.isEnabled = false
                        binding.tvWifError.visibility = View.GONE
                    }
                    is SetupState.WifImported -> {
                        binding.loadingContainer.visibility = View.GONE
                        binding.cardDerivedAddresses.visibility = View.VISIBLE
                        binding.btnImport.visibility = View.GONE
                        binding.btnContinue.visibility = View.VISIBLE

                        binding.tvLegacyAddr.text = state.legacyAddress
                        binding.tvNestedAddr.text = state.nestedSegwitAddress
                        binding.tvNativeAddr.text = state.nativeSegwitAddress

                        setupCopyButtons(state)
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

        binding.btnContinue.setOnClickListener {
            findNavController().navigate(R.id.action_importWif_to_setPin)
        }
    }

    private fun setupCopyButtons(state: SetupState.WifImported) {
        binding.btnCopyLegacy.setOnClickListener { copy(state.legacyAddress) }
        binding.btnCopyNested.setOnClickListener { copy(state.nestedSegwitAddress) }
        binding.btnCopyNative.setOnClickListener { copy(state.nativeSegwitAddress) }
    }

    private fun copy(text: String) {
        if (text.isEmpty()) return
        context?.copyToClipboard("996coin Address", text)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

@AndroidEntryPoint
class SetPinFragment : Fragment() {
    private var _binding: FragmentSetPinBinding? = null
    private val binding get() = _binding!!
    
    @Inject lateinit var securePreferences: SecurePreferences
    @Inject lateinit var walletManager: com.coin996.wallet.core.spv.WalletManager
    
    private val pin = StringBuilder()
    private var isConfirming = false
    private var firstPin = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSetPinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNumpad()
    }

    private fun setupNumpad() {
        val buttons = listOf(
            binding.key0, binding.key1, binding.key2, binding.key3, binding.key4,
            binding.key5, binding.key6, binding.key7, binding.key8, binding.key9
        )
        buttons.forEach { btn ->
            btn.setOnClickListener { addDigit(btn.text.toString()) }
        }
        binding.keyBackspace.setOnClickListener { removeDigit() }
    }

    private fun addDigit(digit: String) {
        if (pin.length < 6) {
            pin.append(digit)
            updateDots()
            if (pin.length == 6) onPinComplete()
        }
    }

    private fun removeDigit() {
        if (pin.isNotEmpty()) {
            pin.deleteCharAt(pin.length - 1)
            updateDots()
        }
    }

    private fun updateDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4, binding.dot5, binding.dot6)
        dots.forEachIndexed { index, view ->
            view.setBackgroundResource(if (index < pin.length) R.drawable.bg_pin_dot_filled else R.drawable.bg_pin_dot_empty)
        }
    }

    private fun onPinComplete() {
        val enteredPin = pin.toString()
        if (!isConfirming) {
            firstPin = enteredPin
            isConfirming = true
            pin.setLength(0)
            updateDots()
            // Set instruction text if we had a dedicated title view
        } else {
            if (enteredPin == firstPin) {
                viewLifecycleOwner.lifecycleScope.launch {
                    binding.loadingContainer.visibility = View.VISIBLE
                    walletManager.encryptWallet(enteredPin)
                    securePreferences.savePin(enteredPin)
                    
                    val isChangePin = arguments?.getBoolean("is_change_pin", false) ?: false
                    if (isChangePin) {
                        android.widget.Toast.makeText(requireContext(), "PIN changed successfully", android.widget.Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    } else {
                        securePreferences.setWalletSetup(true)
                        findNavController().navigate(R.id.action_setPin_to_complete)
                    }
                }
            } else {
                pin.setLength(0)
                updateDots()
                binding.tvPinError.visibility = View.VISIBLE
                binding.tvPinError.text = getString(R.string.setup_pin_mismatch)
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

@AndroidEntryPoint
class SetupCompleteFragment : Fragment() {
    private var _binding: FragmentSetupCompleteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSetupCompleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnGoToWallet.setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
