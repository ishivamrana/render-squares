package com.merabills.question3.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.merabills.question3.R
import com.merabills.question3.model.Square

class SquareAdapter(
    private val onSquareClick: (rowId: Int, squareId: Int) -> Unit
) : RecyclerView.Adapter<SquareAdapter.SquareViewHolder>() {
    private var squares: List<Square> = emptyList()
    override fun getItemCount(): Int = squares.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SquareViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_square, parent, false)
        return SquareViewHolder(view, onSquareClick)
    }

    override fun onBindViewHolder(holder: SquareViewHolder, position: Int) {
        holder.bind(squares[position])
    }

    fun setSquares(newSquares: List<Square>) {
        if (squares === newSquares) return
        if (squares.isEmpty()) {
            squares = newSquares
            notifyItemRangeInserted(0, newSquares.size)
            return
        }
        val diffResult = DiffUtil.calculateDiff(SquareDiffCallback(squares, newSquares))
        squares = newSquares
        diffResult.dispatchUpdatesTo(this)
    }

    fun removeSquare(squareId: Int, updatedSquares: List<Square>) {
        if (squares === updatedSquares) return

        val index = squares.indexOfFirst { it.id == squareId }
        if (index == -1) {
            if (squares.size == updatedSquares.size) {
                squares = updatedSquares
            } else {
                setSquares(updatedSquares)
            }
            return
        }
        squares = updatedSquares
        notifyItemRemoved(index)
    }

    private class SquareDiffCallback(
        private val oldList: List<Square>, private val newList: List<Square>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    class SquareViewHolder(
        itemView: View, private val onSquareClick: (rowId: Int, squareId: Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvSquare: TextView = itemView.findViewById(R.id.tv_square)

        init {
            tvSquare.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                val rowId =
                    (itemView.parent as? RecyclerView)?.tag as? Int ?: return@setOnClickListener
                val squareId = itemView.tag as? Int ?: return@setOnClickListener
                onSquareClick(rowId, squareId)
            }
        }

        fun bind(square: Square) {
            if (itemView.tag != square.id) {
                itemView.tag = square.id
                tvSquare.setBackgroundColor(square.color)
                tvSquare.text = square.value.toString()
            }
        }
    }
}
