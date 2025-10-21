package io.hhplus.tdd.point

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/point")
class PointController(
    private val pointService: PointService,
) {

    @GetMapping("{id}")
    fun point(
        @PathVariable id: Long,
    ): UserPointResponse {
        return UserPointResponse.from(pointService.get(id))
    }

    @GetMapping("{id}/histories")
    fun history(
        @PathVariable id: Long,
    ): List<PointHistoryResponse> {
        return pointService.history(id).map(PointHistoryResponse::from)
    }

    @PatchMapping("{id}/charge")
    fun charge(
        @PathVariable id: Long,
        @RequestBody request: PointAmountRequest,
    ): UserPointResponse {
        return UserPointResponse.from(pointService.charge(id, request.amount))
    }

    @PatchMapping("{id}/use")
    fun use(
        @PathVariable id: Long,
        @RequestBody request: PointAmountRequest,
    ): UserPointResponse {
        return UserPointResponse.from(pointService.use(id, request.amount))
    }
}
