package io.joopang.presentation.product

import io.joopang.application.product.ProductUseCase
import io.joopang.domain.product.ProductItemStatus
import io.joopang.domain.product.ProductSort
import io.joopang.domain.product.ProductStatus
import io.joopang.domain.product.ProductWithItems
import io.joopang.domain.product.StockQuantity
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productUseCase: ProductUseCase,
) {

    @GetMapping
    fun getProducts(
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(defaultValue = "NEWEST") sort: String,
    ): List<ProductResponse> {
        val sortOption = parseSort(sort)
        return productUseCase
            .getProducts(categoryId = categoryId, sort = sortOption)
            .map { it.toResponse() }
    }

    @GetMapping("/top")
    fun getTopProducts(
        @RequestParam(defaultValue = "3") days: Long,
        @RequestParam(defaultValue = "5") limit: Int,
    ): TopProductsResponse =
        productUseCase
            .getTopProducts(days = days, limit = limit)
            .let { result ->
                TopProductsResponse(
                    period = result.period,
                    products = result.products.map { topProduct ->
                        TopProductResponse(
                            rank = topProduct.rank,
                            product = topProduct.aggregate.toResponse(),
                        )
                    },
                )
            }

    @GetMapping("/{id}")
    fun getProduct(
        @PathVariable("id") productId: UUID,
    ): ProductResponse =
        productUseCase
            .getProduct(productId)
            .toResponse()

    @PostMapping
    fun createProduct(
        @RequestBody request: ProductUpsertRequest,
    ): ProductResponse =
        productUseCase
            .createProduct(request.toCreateCommand())
            .toResponse()

    @PutMapping("/{id}")
    fun updateProduct(
        @PathVariable("id") productId: UUID,
        @RequestBody request: ProductUpsertRequest,
    ): ProductResponse =
        productUseCase
            .updateProduct(productId, request.toUpdateCommand())
            .toResponse()

    @GetMapping("/{id}/stock")
    fun checkStock(
        @PathVariable("id") productId: UUID,
        @RequestParam("quantity") quantity: Long,
    ): StockCheckResponse =
        productUseCase
            .checkStock(productId, quantity)
            .let { result ->
                StockCheckResponse(
                    productId = productId,
                    available = result.available,
                    currentStock = result.currentStock.toBigDecimal(),
                    requested = result.requested.toBigDecimal(),
                )
            }

    private fun parseSort(value: String): ProductSort =
        runCatching { ProductSort.valueOf(value.uppercase()) }
            .getOrElse {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported sort option: $value",
                )
            }

    private fun parseProductStatus(value: String?): ProductStatus =
        value
            ?.let {
                runCatching { ProductStatus.valueOf(it.uppercase()) }
                    .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported product status: $value") }
            } ?: ProductStatus.ON_SALE

    private fun parseProductItemStatus(value: String?): ProductItemStatus =
        value
            ?.let {
                runCatching { ProductItemStatus.valueOf(it.uppercase()) }
                    .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported product item status: $value") }
            } ?: ProductItemStatus.ACTIVE

    private fun ProductUpsertRequest.toCreateCommand(): ProductUseCase.CreateProductCommand =
        ProductUseCase.CreateProductCommand(
            name = name,
            code = code,
            description = description,
            content = content,
            status = parseProductStatus(status),
            sellerId = sellerId,
            categoryId = categoryId,
            price = price,
            discountRate = discountRate,
            version = version ?: 0,
            items = items.map { it.toCreateCommand() },
        )

    private fun ProductUpsertRequest.toUpdateCommand(): ProductUseCase.UpdateProductCommand =
        ProductUseCase.UpdateProductCommand(
            name = name,
            code = code,
            description = description,
            content = content,
            status = parseProductStatus(status),
            sellerId = sellerId,
            categoryId = categoryId,
            price = price,
            discountRate = discountRate,
            version = version
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "version is required for update"),
            items = items.map { it.toUpdateCommand() },
        )

    private fun ProductItemUpsertRequest.toCreateCommand(): ProductUseCase.CreateProductItemCommand =
        ProductUseCase.CreateProductItemCommand(
            name = name,
            unitPrice = unitPrice,
            description = description,
            stock = stock,
            status = parseProductItemStatus(status),
            code = code,
        )

    private fun ProductItemUpsertRequest.toUpdateCommand(): ProductUseCase.UpdateProductItemCommand =
        ProductUseCase.UpdateProductItemCommand(
            id = id,
            name = name,
            unitPrice = unitPrice,
            description = description,
            stock = stock,
            status = parseProductItemStatus(status),
            code = code,
        )

    private fun ProductWithItems.toResponse(): ProductResponse =
        ProductResponse(
            id = product.id,
            name = product.name,
            code = product.code.value,
            description = product.description,
            content = product.content,
            status = product.status.name,
            sellerId = product.sellerId,
            categoryId = product.categoryId,
            price = product.price.toBigDecimal(),
            discountRate = product.discountRate?.value,
            version = product.version,
            viewCount = product.viewCount,
            salesCount = product.salesCount,
            items = items.map { item ->
                ProductItemResponse(
                    id = item.id,
                    name = item.name,
                    unitPrice = item.unitPrice.toBigDecimal(),
                    description = item.description,
                    stock = item.stock.toBigDecimal(),
                    status = item.status.name,
                    code = item.code.value,
                )
            },
            totalStock = items
                .map { it.stock }
                .fold(StockQuantity.ZERO) { acc, stock -> acc + stock }
                .toBigDecimal(),
        )
}

data class ProductResponse(
    val id: UUID,
    val name: String,
    val code: String,
    val description: String?,
    val content: String?,
    val status: String,
    val sellerId: UUID,
    val categoryId: UUID,
    val price: BigDecimal,
    val discountRate: BigDecimal?,
    val version: Int,
    val viewCount: Int,
    val salesCount: Int,
    val totalStock: BigDecimal,
    val items: List<ProductItemResponse>,
)

data class ProductItemResponse(
    val id: UUID,
    val name: String,
    val unitPrice: BigDecimal,
    val description: String?,
    val stock: BigDecimal,
    val status: String,
    val code: String,
)

data class TopProductsResponse(
    val period: String,
    val products: List<TopProductResponse>,
)

data class TopProductResponse(
    val rank: Int,
    val product: ProductResponse,
)

data class StockCheckResponse(
    val productId: UUID,
    val available: Boolean,
    val currentStock: BigDecimal,
    val requested: BigDecimal,
)

data class ProductUpsertRequest(
    val name: String,
    val code: String,
    val description: String?,
    val content: String?,
    val status: String?,
    val sellerId: UUID,
    val categoryId: UUID,
    val price: BigDecimal,
    val discountRate: BigDecimal?,
    val version: Int?,
    val items: List<ProductItemUpsertRequest>,
)

data class ProductItemUpsertRequest(
    val id: UUID?,
    val name: String,
    val unitPrice: BigDecimal,
    val description: String?,
    val stock: BigDecimal,
    val status: String?,
    val code: String,
)
