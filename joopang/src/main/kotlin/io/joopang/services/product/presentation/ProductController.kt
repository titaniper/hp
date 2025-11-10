package io.joopang.services.product.presentation

import io.joopang.services.product.application.ProductService
import io.joopang.services.product.domain.ProductItemStatus
import io.joopang.services.product.domain.ProductSort
import io.joopang.services.product.domain.ProductStatus
import io.joopang.services.product.domain.StockQuantity
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
    private val productService: ProductService,
) {

    @GetMapping
    fun getProducts(
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(defaultValue = "NEWEST") sort: String,
    ): List<ProductResponse> {
        val sortOption = parseSort(sort)
        return productService
            .getProducts(categoryId = categoryId, sort = sortOption)
            .map { it.toResponse() }
    }

    @GetMapping("/top")
    fun getTopProducts(
        @RequestParam(defaultValue = "3") days: Long,
        @RequestParam(defaultValue = "5") limit: Int,
    ): TopProductsResponse =
        productService
            .getTopProducts(days = days, limit = limit)
            .let { result ->
                TopProductsResponse(
                    period = result.period,
                    products = result.products.map { topProduct ->
                        TopProductResponse(
                            rank = topProduct.rank,
                            product = topProduct.product.toResponse(),
                        )
                    },
                )
            }

    @GetMapping("/{id}")
    fun getProduct(
        @PathVariable("id") productId: UUID,
    ): ProductResponse =
        productService
            .getProduct(productId)
            .toResponse()

    @PostMapping
    fun createProduct(
        @RequestBody request: ProductUpsertRequest,
    ): ProductResponse =
        productService
            .createProduct(request.toCreateCommand())
            .toResponse()

    @PutMapping("/{id}")
    fun updateProduct(
        @PathVariable("id") productId: UUID,
        @RequestBody request: ProductUpsertRequest,
    ): ProductResponse =
        productService
            .updateProduct(productId, request.toUpdateCommand())
            .toResponse()

    @GetMapping("/{id}/stock")
    fun checkStock(
        @PathVariable("id") productId: UUID,
        @RequestParam("quantity") quantity: Long,
    ): StockCheckResponse =
        productService
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

    private fun ProductUpsertRequest.toCreateCommand(): ProductService.CreateProductCommand =
        ProductService.CreateProductCommand(
            name = name,
            code = code,
            description = description,
            content = content,
            status = parseProductStatus(status),
            sellerId = sellerId,
            categoryId = categoryId,
            price = price,
            discountRate = discountRate,
            items = items.map { it.toCreateCommand() },
        )

    private fun ProductUpsertRequest.toUpdateCommand(): ProductService.UpdateProductCommand =
        ProductService.UpdateProductCommand(
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

    private fun ProductItemUpsertRequest.toCreateCommand(): ProductService.CreateProductItemCommand =
        ProductService.CreateProductItemCommand(
            name = name,
            unitPrice = unitPrice,
            description = description,
            stock = stock,
            status = parseProductItemStatus(status),
            code = code,
        )

    private fun ProductItemUpsertRequest.toUpdateCommand(): ProductService.UpdateProductItemCommand =
        ProductService.UpdateProductItemCommand(
            id = id,
            name = name,
            unitPrice = unitPrice,
            description = description,
            stock = stock,
            status = parseProductItemStatus(status),
            code = code,
        )

    private fun ProductService.Output.toResponse(): ProductResponse =
        ProductResponse(
            id = id,
            name = name,
            code = code,
            description = description,
            content = content,
            status = status.name,
            sellerId = sellerId,
            categoryId = categoryId,
            price = price.toBigDecimal(),
            discountRate = discountRate,
            version = version,
            viewCount = viewCount,
            salesCount = salesCount,
            items = items.map { item ->
                ProductItemResponse(
                    id = item.id,
                    name = item.name,
                    unitPrice = item.unitPrice.toBigDecimal(),
                    description = item.description,
                    stock = item.stock.toBigDecimal(),
                    status = item.status.name,
                    code = item.code,
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
