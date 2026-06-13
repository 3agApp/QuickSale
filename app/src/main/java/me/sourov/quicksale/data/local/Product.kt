package me.sourov.quicksale.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: Long,
    val name: String,
    val sku: String,
    val price: String,
    val regularPrice: String,
    val salePrice: String,
    val stockStatus: String,
    val stockQuantity: Int?,
    val imageUrl: String?,
    val categories: String,
    val description: String,
) {
    val onSale: Boolean get() = salePrice.isNotBlank() && salePrice != regularPrice

    val categoryList: List<String>
        get() = categories.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
