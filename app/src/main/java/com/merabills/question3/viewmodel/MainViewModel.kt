package com.merabills.question3.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merabills.question3.model.Row
import com.merabills.question3.model.Square
import com.merabills.question3.model.SquareRemovalResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel : ViewModel() {
    private val _rows = MutableLiveData<List<Row>>()
    val rows: LiveData<List<Row>> get() = _rows
    private var rowsState: List<Row>? = null
    private val updateLock = Any()

    companion object {
        private const val TOTAL_SQUARES = 10000
        private const val MIN_PER_ROW = 1
        private const val MAX_PER_ROW = 100

        //pre defined colors
        private val COLOR_PALETTE = intArrayOf(
            0xFFE57373.toInt(),
            0xFF81C784.toInt(),
            0xFF64B5F6.toInt(),
            0xFFFFD54F.toInt(),
            0xFFBA68C8.toInt(),
            0xFF4DD0E1.toInt(),
            0xFFFF8A65.toInt(),
            0xFF90A4AE.toInt(),
            0xFFA1A1A1.toInt(),
            0xFFF06292.toInt(),
        )
    }

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val generatedRows = generateRows()
            synchronized(updateLock) {
                rowsState = generatedRows
            }
            _rows.postValue(generatedRows)
        }
    }

    private fun generateRows(): List<Row> {
        val rows = mutableListOf<Row>()
        var squaresRemaining = TOTAL_SQUARES
        var globalIndex = 0
        var globalSquareId = 0
        var rowId = 0

        while (squaresRemaining > 0) {
            val countInRow =
                Random.nextInt(MIN_PER_ROW, MAX_PER_ROW + 1).coerceAtMost(squaresRemaining)

            val squares = List(countInRow) {
                Square(
                    id = globalSquareId++,
                    color = COLOR_PALETTE[Random.nextInt(COLOR_PALETTE.size)],
                    value = globalIndex++
                )
            }

            rows.add(Row(id = rowId++, squares = squares))
            squaresRemaining -= countInRow
        }

        return rows
    }

    fun removeSquare(rowId: Int, squareId: Int): SquareRemovalResult? {
        synchronized(updateLock) {
            val currentRows = rowsState ?: return null
            val rowIndex = currentRows.indexOfFirst { it.id == rowId }
            if (rowIndex == -1) return null

            val row = currentRows[rowIndex]
            val updatedSquares = row.squares.filter { it.id != squareId }
            if (updatedSquares.size == row.squares.size) return null

            val rowRemoved = updatedSquares.isEmpty()
            val updatedRows = if (rowRemoved) {
                buildList(currentRows.size - 1) {
                    currentRows.forEachIndexed { index, existingRow ->
                        if (index != rowIndex) add(existingRow)
                    }
                }
            } else {
                currentRows.toMutableList().apply {
                    this[rowIndex] = row.copy(squares = updatedSquares)
                }
            }
            rowsState = updatedRows
            return SquareRemovalResult(
                rowId = rowId,
                squareId = squareId,
                rowRemoved = rowRemoved,
                updatedSquares = updatedSquares,
                updatedRows = updatedRows
            )
        }
    }
}