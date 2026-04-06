package com.coin996.wallet.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.coin996.wallet.R
import com.coin996.wallet.core.spv.TransactionItem
import com.coin996.wallet.databinding.ItemTransactionBinding
import com.coin996.wallet.utils.satoshiToNns
import com.coin996.wallet.utils.toDateString

class TransactionAdapter(
    private val onItemClick: (TransactionItem) -> Unit
) : ListAdapter<TransactionItem, TransactionAdapter.TxViewHolder>(TxDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TxViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TxViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TxViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TxViewHolder(
        private val binding: ItemTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tx: TransactionItem) {
            val ctx = binding.root.context

            // Direction
            if (tx.isSent) {
                binding.tvTxType.text = ctx.getString(R.string.tx_sent)
                binding.tvTxAmount.text = "-${String.format("%.8f", tx.amountSatoshi.satoshiToNns())} NNS"
                binding.tvTxAmount.setTextColor(ctx.getColor(R.color.accent_red))
                binding.ivTxDirection.setImageResource(R.drawable.ic_arrow_up)
                binding.ivTxDirection.setColorFilter(ctx.getColor(R.color.accent_red))
                binding.txIconBg.setBackgroundResource(R.drawable.bg_circle_red)
            } else {
                binding.tvTxType.text = ctx.getString(R.string.tx_received)
                binding.tvTxAmount.text = "+${String.format("%.8f", tx.amountSatoshi.satoshiToNns())} NNS"
                binding.tvTxAmount.setTextColor(ctx.getColor(R.color.accent_green))
                binding.ivTxDirection.setImageResource(R.drawable.ic_arrow_down)
                binding.ivTxDirection.setColorFilter(ctx.getColor(R.color.accent_green))
                binding.txIconBg.setBackgroundResource(R.drawable.bg_circle_green)
            }

            // Address
            val address = tx.toAddress ?: tx.txHash.take(20) + "…"
            binding.tvTxAddress.text = address

            // Date
            binding.tvTxDate.text = if (tx.timestamp > 0) tx.timestamp.toDateString()
                                    else "Unconfirmed"

            // Confirmations
            if (!tx.isConfirmed) {
                binding.tvPendingBadge.visibility = android.view.View.VISIBLE
                binding.tvTxConfirmations.text = ctx.getString(R.string.tx_pending)
            } else {
                binding.tvPendingBadge.visibility = android.view.View.GONE
                binding.tvTxConfirmations.text = ctx.getString(
                    R.string.tx_confirmations, tx.confirmations)
            }

            binding.root.setOnClickListener { onItemClick(tx) }
        }
    }

    private class TxDiffCallback : DiffUtil.ItemCallback<TransactionItem>() {
        override fun areItemsTheSame(a: TransactionItem, b: TransactionItem) = a.txHash == b.txHash
        override fun areContentsTheSame(a: TransactionItem, b: TransactionItem) = a == b
    }
}
