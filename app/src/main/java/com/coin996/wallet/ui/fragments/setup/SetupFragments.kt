package com.coin996.wallet.ui.fragments.setup

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.coin996.wallet.R
import com.coin996.wallet.databinding.*
import com.coin996.wallet.ui.activities.SetupActivity
import com.coin996.wallet.ui.viewmodels.SetupState
import com.coin996.wallet.ui.viewmodels.SetupViewModel
import com.coin996.wallet.utils.SecurePreferences
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─── WelcomeFragment ──────────────────────────────────────────────────────────

@AndroidEntryPoint
class WelcomeFragment : Fragment() {
    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentWelcomeBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnCreateWallet.setOnClickListener {
            findNavController().navigate(R.id.action_welcome_to_createWallet)
        }
        binding.btnImportWif.setOnClickListener {
            findNavController().navigate(R.id.action_welcome_to_importWif)
        }
        binding.btnRestoreWallet.setOnClickListener {
            findNavController().navigate(R.id.action_welcome_to_restoreWallet)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── CreateWalletFragment ─────────────────────────────────────────────────────

@AndroidEntryPoint
class CreateWalletFragment : Fragment() {
    private var _binding: FragmentCreateWalletBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SetupViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentCreateWalletBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Trigger wallet creation
        viewModel.createNewWallet()

        binding.cbWrittenDown.setOnCheckedChangeListener { _, checked ->
            binding.btnNext.isEnabled = checked
        }

        binding.btnCopySeed.setOnClickListener {
            val words = viewModel.generatedWords.value.joinToString(" ")
            val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Seed", words))
            com.google.android.material.snackbar.Snackbar.make(
                binding.root, "Seed phrase copied!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
        }

        binding.btnNext.setOnClickListener {
            findNavController().navigate(R.id.action_create_to_verifyWords)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.generatedWords.collectLatest { words ->
                if (words.isNotEmpty()) setupWordGrid(words)
            }
        }
    }

    private fun setupWordGrid(words: List<String>) {
        val adapter = SeedWordAdapter(words)
        binding.rvSeedWords.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvSeedWords.adapter = adapter
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── SeedWordAdapter (inline, simple) ────────────────────────────────────────

class SeedWordAdapter(private val words: List<String>) :
    RecyclerView.Adapter<SeedWordAdapter.WordVH>() {

    inner class WordVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNumber = itemView.findViewById<android.widget.TextView>(R.id.tv_word_number)
        val tvWord   = itemView.findViewById<android.widget.TextView>(R.id.tv_word)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_seed_word, parent, false)
        return WordVH(v)
    }

    override fun onBindViewHolder(holder: WordVH, position: Int) {
        holder.tvNumber.text = "${position + 1}."
        holder.tvWord.text   = words[position]
    }

    override fun getItemCount() = words.size
}

// ─── VerifyWordsFragment ──────────────────────────────────────────────────────

@AndroidEntryPoint
class VerifyWordsFragment : Fragment() {
    private var _binding: FragmentVerifyWordsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SetupViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentVerifyWordsBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val words = viewModel.generatedWords.value
        if (words.isEmpty()) { findNavController().navigateUp(); return }

        // Pick 3 random indices to verify
        val indices = (0 until 12).shuffled().take(3).sorted()
        val targetWords = indices.map { words[it] }

        binding.tvVerifyHint.text =
            "Enter words #${indices[0]+1}, #${indices[1]+1}, and #${indices[2]+1}"

        binding.btnVerify.setOnClickListener {
            val entered = listOf(
                binding.etWord1.text.toString().trim().lowercase(),
                binding.etWord2.text.toString().trim().lowercase(),
                binding.etWord3.text.toString().trim().lowercase()
            )
            if (entered == targetWords) {
                findNavController().navigate(R.id.action_verify_to_setPin)
            } else {
                binding.tvVerifyError.visibility = View.VISIBLE
                binding.tvVerifyError.text = "Words don't match. Please check your backup."
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── RestoreWalletFragment ────────────────────────────────────────────────────

@AndroidEntryPoint
class RestoreWalletFragment : Fragment() {
    private var _binding: FragmentRestoreWalletBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SetupViewModel by activityViewModels()
    private var selectedDate: Long? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentRestoreWalletBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etSeedPhrase.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val count = s.toString().trim().split("\\s+".toRegex())
                    .filter { it.isNotEmpty() }.size
                binding.tvWordCount.text = "$count / 12 words"
                binding.btnRestore.isEnabled = count == 12
            }
        })

        binding.etCreationDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Wallet creation date")
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                selectedDate = millis / 1000
                binding.etCreationDate.setText(
                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        .format(Date(millis))
                )
            }
            picker.show(parentFragmentManager, "date_picker")
        }

        binding.btnRestore.setOnClickListener {
            val words = binding.etSeedPhrase.text.toString()
                .trim().lowercase().split("\\s+".toRegex())
            viewModel.restoreWallet(words, selectedDate)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.setupState.collectLatest { state ->
                when (state) {
                    is SetupState.Loading -> {
                        binding.loadingContainer.visibility = View.VISIBLE
                        binding.btnRestore.isEnabled = false
                        binding.tvError.visibility = View.GONE
                    }
                    is SetupState.WalletRestored -> {
                        binding.loadingContainer.visibility = View.GONE
                        findNavController().navigate(R.id.action_restore_to_setPin)
                    }
                    is SetupState.Error -> {
                        binding.loadingContainer.visibility = View.GONE
                        binding.btnRestore.isEnabled = true
                        binding.tvError.visibility = View.VISIBLE
                        binding.tvError.text = state.message
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

// ─── SetPinFragment ───────────────────────────────────────────────────────────

@AndroidEntryPoint
class SetPinFragment : Fragment() {
    private var _binding: FragmentSetPinBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var securePreferences: SecurePreferences

    private val pin = StringBuilder()
    private var isConfirming = false
    private var firstPin = ""

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSetPinBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNumpad()
    }

    private fun setupNumpad() {
        val keys = listOf(
            binding.key1 to "1", binding.key2 to "2", binding.key3 to "3",
            binding.key4 to "4", binding.key5 to "5", binding.key6 to "6",
            binding.key7 to "7", binding.key8 to "8", binding.key9 to "9",
            binding.key0 to "0"
        )
        keys.forEach { (btn, digit) ->
            btn.setOnClickListener { addDigit(digit) }
        }
        binding.keyBackspace.setOnClickListener { removeDigit() }
    }

    private fun addDigit(d: String) {
        if (pin.length >= 6) return
        pin.append(d)
        updateDots()
        if (pin.length == 6) onPinComplete()
    }

    private fun removeDigit() {
        if (pin.isNotEmpty()) { pin.deleteCharAt(pin.length - 1); updateDots() }
    }

    private fun updateDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3,
                          binding.dot4, binding.dot5, binding.dot6)
        dots.forEachIndexed { i, dot ->
            dot.setBackgroundResource(
                if (i < pin.length) R.drawable.bg_pin_dot_filled
                else R.drawable.bg_pin_dot_empty
            )
        }
    }

    private fun onPinComplete() {
        if (!isConfirming) {
            firstPin = pin.toString()
            pin.clear()
            isConfirming = true
            updateDots()
            binding.tvPinSubtitle.text = getString(R.string.setup_pin_confirm)
            binding.tvPinError.visibility = View.GONE
        } else {
            if (pin.toString() == firstPin) {
                securePreferences.savePin(firstPin)
                securePreferences.setWalletSetup(true)
                findNavController().navigate(R.id.action_setPin_to_complete)
            } else {
                binding.tvPinError.visibility = View.VISIBLE
                binding.tvPinError.text = getString(R.string.setup_pin_mismatch)
                pin.clear()
                isConfirming = false
                firstPin = ""
                binding.tvPinSubtitle.text = "Choose a 6-digit PIN to secure your wallet"
                updateDots()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── SetupCompleteFragment ────────────────────────────────────────────────────

@AndroidEntryPoint
class SetupCompleteFragment : Fragment() {
    private var _binding: FragmentSetupCompleteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSetupCompleteBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Play success Lottie animation
        binding.lottieSuccess.apply {
            setAnimation("success_animation.json")  // place in res/raw/
            repeatCount = 0
            playAnimation()
        }

        binding.btnGoToWallet.setOnClickListener {
            (requireActivity() as SetupActivity).navigateToMain()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
