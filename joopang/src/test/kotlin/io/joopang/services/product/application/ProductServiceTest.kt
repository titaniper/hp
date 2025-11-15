package io.joopang.services.product.application

import io.joopang.services.product.domain.ProductItemStatus
import io.joopang.services.product.domain.ProductStatus
import io.joopang.services.product.domain.ProductSort
import io.joopang.services.product.infrastructure.ProductRepository
import io.joopang.support.IntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProductServiceTest @Autowired constructor(
    private val productService: ProductService,
) : IntegrationTestSupport() {

    @SpyBean
    private lateinit var productRepository: ProductRepository

    @Test
    fun `getProducts caches result`() {
        val first = productService.getProducts()
        val second = productService.getProducts()

        assertThat(first).isNotEmpty()
        assertThat(second).isEqualTo(first)
        Mockito.verify(productRepository, Mockito.times(1))
            .findProducts(null, ProductSort.NEWEST)
    }

    @Test
    fun `createProduct invalidates cache`() {
        val categoryId = 300L
        productService.getProducts(categoryId = categoryId)
        Mockito.verify(productRepository, Mockito.times(1))
            .findProducts(categoryId, ProductSort.NEWEST)

        Mockito.reset(productRepository)
        productService.createProduct(
            ProductService.CreateProductCommand(
                name = "테스트 상품",
                code = "TEST-${System.nanoTime()}",
                description = "desc",
                content = null,
                status = ProductStatus.ON_SALE,
                sellerId = 200L,
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
                        code = "ITEM-${System.nanoTime()}",
                    ),
                ),
            ),
        )

        productService.getProducts(categoryId = categoryId)
        Mockito.verify(productRepository, Mockito.times(1))
            .findProducts(categoryId, ProductSort.NEWEST)
    }

    @Test
    fun `checkStock returns availability`() {
        val phones = productService.getProducts()
        val phone = phones.first()

        val productId = phone.id
        val result = productService.checkStock(productId, quantity = 1)

        assertThat(result.available).isTrue()
        assertThat(result.currentStock).isNotNull()
    }
}
