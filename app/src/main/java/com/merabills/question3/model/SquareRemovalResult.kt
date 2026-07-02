package com.merabills.question3.model

data class SquareRemovalResult(
    val rowId: Int,
    val squareId: Int,
    val rowRemoved: Boolean,
    val updatedSquares: List<Square>,
    val updatedRows: List<Row>
)
