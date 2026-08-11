package me.sourov.quicksale.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: Long,
    val name: String,
    val sku: String,
    /**
     * The product's barcode number — WooCommerce's `global_unique_id` (GTIN/UPC/EAN/ISBN), or the
     * equivalent meta a barcode plugin writes. Blank when the store doesn't carry one.
     */
    val ean: String,
    val price: String,
    val regularPrice: String,
    val salePrice: String,
    /**
     * The manufacturer's suggested retail price, when the store carries one. Blank when it doesn't:
     * `msrp` comes from a plugin rather than WooCommerce core, so most catalogs never send it.
     */
    val msrp: String,
    val stockStatus: String,
    val stockQuantity: Int?,
    val imageUrl: String?,
    val categories: String,
    val description: String,
) {
    val onSale: Boolean get() = salePrice.isNotBlank() && salePrice != regularPrice

    val hasMsrp: Boolean get() = msrp.isNotBlank()

    val categoryList: List<String>
        get() = categories.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
