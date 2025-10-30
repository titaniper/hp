package io.joopang.api.product

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/products")
class ProductController {
    @GetMapping
    fun getProducts(): List<ProductDto> = MOCK_PRODUCTS

    @GetMapping("/{productId}")
    fun getProduct(@PathVariable productId: UUID): ProductDto = MOCK_PRODUCTS.firstOrNull { it.id == productId }
        ?: throw IllegalArgumentException("상품을 찾을 수 없습니다.")
}

private val MOCK_PRODUCTS = listOf(
    ProductDto(
        id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        name = "프리미엄 드립 커피 세트",
        code = "PROD-DRIP-SET-001",
        description = "핸드드립을 위한 원두와 드리퍼 풀 패키지",
        content = "싱글 오리진 원두 3종과 세라믹 드리퍼, 필터 40매 포함",
        status = ProductStatus.ON_SALE,
        sellerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        categoryId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
        price = MoneyDto(
            amount = BigDecimal("39800"),
            currency = "KRW",
        ),
        discountRate = BigDecimal("0.10"),
        version = 3,
        items = listOf(
            ProductItemDto(
                id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                name = "싱글 오리진 원두 200g",
                code = "SKU-DRIP-001",
                description = "중배전 싱글 오리진 원두",
                status = ProductItemStatus.ACTIVE,
                stock = BigDecimal("150"),
                unitPrice = BigDecimal("14800"),
                price = MoneyDto(
                    amount = BigDecimal("14800"),
                    currency = "KRW",
                ),
            ),
            ProductItemDto(
                id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                name = "세라믹 드리퍼 세트",
                code = "SKU-DRIP-002",
                description = "드립서버, 드리퍼, 필터 40매 포함",
                status = ProductItemStatus.ACTIVE,
                stock = BigDecimal("80"),
                unitPrice = BigDecimal("25000"),
                price = MoneyDto(
                    amount = BigDecimal("25000"),
                    currency = "KRW",
                ),
            ),
        ),
    ),
    ProductDto(
        id = UUID.fromString("44444444-4444-4444-4444-444444444444"),
        name = "에센셜 핸드크림 3종 세트",
        code = "PROD-HAND-SET-002",
        description = "보습과 향을 동시에 챙길 수 있는 핸드크림 세트",
        content = "라벤더, 시더우드, 화이트머스크 30ml 튜브 구성",
        status = ProductStatus.ON_SALE,
        sellerId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
        categoryId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
        price = MoneyDto(
            amount = BigDecimal("25900"),
            currency = "KRW",
        ),
        discountRate = BigDecimal("0.00"),
        version = 1,
        items = listOf(
            ProductItemDto(
                id = UUID.fromString("55555555-5555-5555-5555-555555555555"),
                name = "라벤더 핸드크림 30ml",
                code = "SKU-HAND-001",
                description = "은은한 라벤더 향의 보습 핸드크림",
                status = ProductItemStatus.ACTIVE,
                stock = BigDecimal("300"),
                unitPrice = BigDecimal("8900"),
                price = MoneyDto(
                    amount = BigDecimal("8900"),
                    currency = "KRW",
                ),
            ),
        ),
    ),
)
