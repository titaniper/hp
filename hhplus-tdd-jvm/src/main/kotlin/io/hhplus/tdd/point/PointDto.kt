package io.hhplus.tdd.point

data class PointAmountRequest(
    val amount: Long,
)

data class UserPointResponse(
    val id: Long,
    val point: Long,
    val updateMillis: Long,
) {
    companion object {
        fun from(userPoint: UserPoint): UserPointResponse {
            return UserPointResponse(
                id = userPoint.id,
                point = userPoint.point,
                updateMillis = userPoint.updateMillis,
            )
        }
    }
}

data class PointHistoryResponse(
    val id: Long,
    val userId: Long,
    val type: TransactionType,
    val amount: Long,
    val timeMillis: Long,
) {
    companion object {
        fun from(history: PointHistory): PointHistoryResponse {
            return PointHistoryResponse(
                id = history.id,
                userId = history.userId,
                type = history.type,
                amount = history.amount,
                timeMillis = history.timeMillis,
            )
        }
    }
}
