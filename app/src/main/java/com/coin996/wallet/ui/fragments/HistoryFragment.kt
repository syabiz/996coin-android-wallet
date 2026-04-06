package com.coin996.wallet.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.coin996.wallet.core.spv.TransactionItem
import com.coin996.wallet.databinding.FragmentHistoryBinding
import com.coin996.wallet.ui.adapters.TransactionAdapter
import com.coin996.wallet.ui.viewmodels.HomeViewModel
import com.coin996.wallet.utils.gone
import com.coin996.wallet.utils.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var txAdapter: TransactionAdapter

    private var allTransactions: List<TransactionItem> = emptyList()
    private var currentFilter = Filter.ALL

    enum class Filter { ALL, RECEIVED, SENT, PENDING }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilterChips()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        txAdapter = TransactionAdapter { tx ->
            val url = "https://996coin.com/explorer/tx/${tx.txHash}"
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url)))
        }
        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = txAdapter
        }
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnCheckedChangeListener { _, checked ->
            if (checked) { currentFilter = Filter.ALL; applyFilter() }
        }
        binding.chipReceived.setOnCheckedChangeListener { _, checked ->
            if (checked) { currentFilter = Filter.RECEIVED; applyFilter() }
        }
        binding.chipSent.setOnCheckedChangeListener { _, checked ->
            if (checked) { currentFilter = Filter.SENT; applyFilter() }
        }
        binding.chipPending.setOnCheckedChangeListener { _, checked ->
            if (checked) { currentFilter = Filter.PENDING; applyFilter() }
        }
    }

    private fun applyFilter() {
        val filtered = when (currentFilter) {
            Filter.ALL      -> allTransactions
            Filter.RECEIVED -> allTransactions.filter { !it.isSent }
            Filter.SENT     -> allTransactions.filter { it.isSent }
            Filter.PENDING  -> allTransactions.filter { !it.isConfirmed }
        }
        showTransactions(filtered)
    }

    private fun showTransactions(list: List<TransactionItem>) {
        if (list.isEmpty()) {
            binding.rvTransactions.gone()
            binding.emptyState.visible()
        } else {
            binding.rvTransactions.visible()
            binding.emptyState.gone()
            txAdapter.submitList(list)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.transactions.collectLatest { txList ->
                allTransactions = txList
                applyFilter()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
