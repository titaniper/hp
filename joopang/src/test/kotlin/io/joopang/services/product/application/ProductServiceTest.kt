package io.joopang.services.product.application

import io.joopang.services.common.application.CacheService
import io.joopang.services.common.infrastructure.CacheServiceImpl
import io.joopang.services.product.domain.ProductItemStatus
import io.joopang.services.product.domain.ProductStatus
import io.joopang.services.product.infrastructure.ProductRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class ProductServiceTest {

    private lateinit var cacheService: CacheService
    private lateinit var productRepository: CountingProductRepository
    private lateinit var productService: ProductService

    @BeforeEach
    fun setUp() {
        cacheService = CacheServiceImpl()
        productRepository = CountingProductRepository()
        productService = ProductService(productRepository, cacheService)
    }

    @Test
    fun `getProducts caches result`() {
        val first = productService.getProducts()
        val second = productService.getProducts()

        assertThat(first).isNotEmpty()
        assertThat(second).isEqualTo(first)
        assertThat(productRepository.findAllCalls).isEqualTo(1)
    }

    @Test
    fun `createProduct invalidates cache`() {
        val categoryId = UUID.randomUUID()
        productService.getProducts(categoryId = categoryId)
        assertThat(productRepository.findAllCalls).isEqualTo(1)

        productService.createProduct(
            ProductService.CreateProductCommand(
                name = "테스트 상품",
                code = "TEST-001",
                description = "desc",
                content = null,
                status = ProductStatus.ON_SALE,
                sellerId = UUID.randomUUID(),
                categoryId = categoryId,
                price = BigDecimal("1000"),
                discountRate = null,
                items = listOf(
                    ProductService.CreateProductItemCommand(
                        name = "아이템",
                        unitPrice = BigDecimal("1000"),
                        description = null,
                        stock = BigDecimal("10"),
                        status = ProductItemStatus.ACTIVE,
                        code = "ITEM-1",
                    ),
                ),
            ),
        )

        productService.getProducts(categoryId = categoryId)

        assertThat(productRepository.findAllCalls).isEqualTo(2)
    }

    @Test
    fun `checkStock returns availability`() {
        val phones = productService.getProducts()
        val phone = phones.first()

        val result = productService.checkStock(phone.product.id, quantity = 1)

        assertThat(result.available).isTrue()
        assertThat(result.currentStock).isNotNull()
    }

    private class CountingProductRepository : ProductRepository() {
        var findAllCalls = 0

        override fun findAll(condition: io.joopang.services.product.domain.ProductSearchCondition): List<io.joopang.services.product.domain.ProductWithItems> {
            findAllCalls += 1
            return super.findAll(condition)
        }
    }
}
