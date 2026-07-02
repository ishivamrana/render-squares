package com.merabills.question3.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.merabills.question3.R
import com.merabills.question3.model.Row
import com.merabills.question3.model.Square
import com.merabills.question3.model.SquareRemovalResult

class RowAdapter(
    private val onSquareClick: (rowId: Int, squareId: Int) -> Unit
) : RecyclerView.Adapter<RowAdapter.RowViewHolder>() {

    private var rows: List<Row> = emptyList()
    private var attachedRecyclerView: RecyclerView? = null
    private val sharedRecycledViewPool = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(0, 100)
    }

    override fun getItemCount(): Int = rows.size

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (attachedRecyclerView === recyclerView) {
            attachedRecyclerView = null
        }
    }

    fun setRows(newRows: List<Row>) {
        if (rows === newRows) return
        if (rows.isEmpty()) {
            rows = newRows
            notifyItemRangeInserted(0, newRows.size)
            return
        }

        val diffResult = DiffUtil.calculateDiff(RowDiffCallback(rows, newRows))
        rows = newRows
        diffResult.dispatchUpdatesTo(this)
    }

    fun applyRemoval(removal: SquareRemovalResult) {
        val position = rows.indexOfFirst { it.id == removal.rowId }
        if (position == -1) return

        rows = removal.updatedRows

        if (removal.rowRemoved) {
            notifyItemRemoved(position)
            return
        }

        (attachedRecyclerView?.findViewHolderForAdapterPosition(position) as? RowViewHolder)?.removeSquare(
            removal.squareId, removal.updatedSquares
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val squareSizePx = parent.context.resources.getDimensionPixelSize(R.dimen.square_size)
        val innerRecyclerView = RecyclerView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, squareSizePx
            )
            layoutManager = LinearLayoutManager(
                parent.context, LinearLayoutManager.HORIZONTAL, false
            ).apply {
                recycleChildrenOnDetach = true
                initialPrefetchItemCount = 0
                isItemPrefetchEnabled = false
            }
            setRecycledViewPool(sharedRecycledViewPool)
            setHasFixedSize(true)
            isNestedScrollingEnabled = true
            itemAnimator = null
            setItemViewCacheSize(10)
        }

        return RowViewHolder(innerRecyclerView, onSquareClick)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(rows[position])
    }

    private class RowDiffCallback(
        private val oldList: List<Row>, private val newList: List<Row>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].squares === newList[newItemPosition].squares
        }
    }

    class RowViewHolder(
        private val recyclerView: RecyclerView, onSquareClick: (rowId: Int, squareId: Int) -> Unit
    ) : RecyclerView.ViewHolder(recyclerView) {
        private val squareAdapter = SquareAdapter(onSquareClick)

        init {
            recyclerView.adapter = squareAdapter
        }

        fun bind(row: Row) {
            recyclerView.tag = row.id
            squareAdapter.setSquares(row.squares)
        }

        fun removeSquare(squareId: Int, squares: List<Square>) {
            squareAdapter.removeSquare(squareId, squares)
        }
    }
}
