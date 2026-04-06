package com.coin996.wallet.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.coin996.wallet.R
import com.coin996.wallet.databinding.FragmentHomeBinding
import com.coin996.wallet.ui.adapters.TransactionAdapter
import com.coin996.wallet.ui.viewmodels.HomeViewModel
import com.coin996.wallet.utils.*
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var txAdapter: TransactionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupChart()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        txAdapter = TransactionAdapter { tx ->
            // Open block explorer for this transaction
            val url = "https://996coin.com/explorer/tx/${tx.txHash}"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url))
            startActivity(intent)
        }
        binding.rvRecentTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = txAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupChart() {
        binding.priceChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 8, 0, 0)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#9CA3AF")
                textSize = 9f
                granularity = 1f
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#11000000")
                textColor = Color.parseColor("#9CA3AF")
                textSize = 9f
            }
            axisRight.isEnabled = false
        }
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener {
            findNavController().navigate(R.id.sendFragment)
        }
        binding.btnReceive.setOnClickListener {
            findNavController().navigate(R.id.receiveFragment)
        }
        binding.tvSeeAll.setOnClickListener {
            findNavController().navigate(R.id.historyFragment)
        }
        binding.btnCopyAddress.setOnClickListener {
            val addr = binding.tvAddress.text.toString()
            requireContext().copyToClipboard("996coin Address", addr)
        }
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshAll()
        }
        binding.swipeRefresh.setColorSchemeColors(
            requireContext().getColor(R.color.primary)
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.walletState.collectLatest { state ->
                // Balance
                val balanceNns = state.balance.value.toDouble() / 100_000_000.0
                binding.tvBalanceNns.text = "${formatNns(balanceNns)} NNS"
                binding.tvAddress.text = state.addresses[state.activeAddressType] ?: "..."

                // Pending
                val pendingNns = state.pendingBalance.value.toDouble() / 100_000_000.0
                if (pendingNns > 0) {
                    binding.pendingRow.visibility = View.VISIBLE
                    binding.tvPending.text = "${formatNns(pendingNns)} NNS"
                } else {
                    binding.pendingRow.visibility = View.GONE
                }

                // Sync indicator
                if (state.isSyncing) {
                    binding.syncProgress.visibility = View.VISIBLE
                    binding.syncProgress.progress = state.syncProgress
                    binding.tvSyncStatus.text = getString(R.string.sync_progress, state.syncProgress)
                } else {
                    binding.syncProgress.visibility = View.GONE
                    binding.tvSyncStatus.text = getString(R.string.sync_done)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.priceData.collectLatest { price ->
                price ?: return@collectLatest
                binding.tvPriceUsd.text = price.priceUsd.formatPrice()
                binding.tvPriceChange.text = price.change24hPercent.formatChange()
                binding.tvHigh24h.text = "H: ${price.high24h.formatPrice()}"
                binding.tvLow24h.text  = "L: ${price.low24h.formatPrice()}"
                binding.tvVolume24h.text = "Vol: ${String.format("%,.0f", price.volume24h)}"

                // Color the change badge
                if (price.isPositive) {
                    binding.tvPriceChange.setBackgroundResource(R.drawable.bg_pill_green)
                    binding.tvPriceChange.setTextColor(requireContext().getColor(R.color.accent_green))
                } else {
                    binding.tvPriceChange.setBackgroundResource(R.drawable.bg_pill_red)
                    binding.tvPriceChange.setTextColor(requireContext().getColor(R.color.accent_red))
                }

                // Balance in USD
                val balanceNns = viewModel.walletState.value.balance.value.satoshiToNns()
                val balanceUsd = balanceNns * price.priceUsd
                binding.tvBalanceUsd.text = "≈ ${String.format("$%.4f", balanceUsd)} USD"

                binding.swipeRefresh.isRefreshing = false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.priceHistory.collectLatest { history ->
                if (history.isEmpty()) return@collectLatest
                updateChart(history)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.transactions.collectLatest { txList ->
                val recent = txList.take(5)
                if (recent.isEmpty()) {
                    binding.rvRecentTransactions.gone()
                    binding.emptyTransactions.visible()
                } else {
                    binding.rvRecentTransactions.visible()
                    binding.emptyTransactions.gone()
                    txAdapter.submitList(recent)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { loading ->
                if (!loading) binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun updateChart(history: List<Pair<Long, Double>>) {
        val entries = history.mapIndexed { i, (_, price) ->
            Entry(i.toFloat(), price.toFloat())
        }
        val labels = history.map { (ts, _) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        }

        val dataSet = LineDataSet(entries, "NNS/USDT").apply {
            color = Color.parseColor("#FF6B00")
            setCircleColor(Color.parseColor("#FF6B00"))
            circleRadius = 0f
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
            fillColor = Color.parseColor("#33FF6B00")
            setDrawFilled(true)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        binding.priceChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.priceChart.data = LineData(dataSet)
        binding.priceChart.animateX(500)
        binding.priceChart.invalidate()
    }

    private fun formatNns(value: Double): String {
        return if (value == 0.0) "0.00000000"
        else String.format("%,.8f", value).trimEnd('0').trimEnd('.').let {
            if (!it.contains('.')) "$it.00000000".take(it.length + 9)
            else it.padEnd(it.indexOf('.') + 9, '0').take(it.indexOf('.') + 9)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
