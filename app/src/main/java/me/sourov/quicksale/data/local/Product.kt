package me.sourov.quicksale.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: Long,
    val name: String,
    /**
     * The product's brand, printed on its label under the name. Blank when the store doesn't put
     * the product in a brand — WooCommerce's brands are a taxonomy, so a product may have none.
     */
    val brand: String,
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
    /**
     * The smallest quantity the store sells this product in — its pack size (VE). Always at least
     * 1: a store that carries no minimum, or sends a zero, means "one unit", which is the same
     * thing an unrestricted product means.
     */
    val minOrderQuantity: Int = 1,
    /**
     * How the quantity may grow above [minOrderQuantity] — the case size a reorder comes in.
     * Always at least 1, so a product with no step still moves one unit at a time.
     */
    val orderQuantityStep: Int = 1,
    /**
     * WooCommerce's own post status — `publish`, `draft`, `pending`, `private`, `future`.
     *
     * The catalog syncs whatever the store holds, status and all, so the till's copy matches the
     * shop rather than a filtered view of it. What the status decides is what the counter may
     * *do* with a product: only a published one is searchable and orderable, and everything else
     * exists locally so a scan can say why it isn't.
     */
    val status: String = STATUS_PUBLISHED,
) {
    /** True when the store has this product live — the only state the counter may sell in. */
    val isPublished: Boolean get() = status == STATUS_PUBLISHED

    /** How to name this product's state to whoever just scanned it. */
    val statusLabel: String
        get() = when (status) {
            "draft" -> "a draft"
            "pending" -> "pending review"
            "private" -> "private"
            "future" -> "scheduled"
            "trash" -> "in the trash"
            STATUS_PUBLISHED -> "published"
            // An unknown status is a plugin's own; naming it beats calling it something it isn't.
            else -> "\"$status\""
        }

    val onSale: Boolean get() = salePrice.isNotBlank() && salePrice != regularPrice

    val hasMsrp: Boolean get() = msrp.isNotBlank()

    val hasBrand: Boolean get() = brand.isNotBlank()

    val categoryList: List<String>
        get() = categories.split(",").map { it.trim() }.filter { it.isNotBlank() }

    /**
     * The largest quantity the store will actually sell at or below [desired], or 0 when [desired]
     * falls short of the pack size (i.e. the line should go away rather than round up to a
     * quantity nobody asked for).
     *
     * Every orderable quantity is `minOrderQuantity + n × orderQuantityStep`, so this is what turns
     * a raw +1/−1 intent into a number WooCommerce will accept. Rounding *down* is deliberate: the
     * operator taps − to sell less, and snapping upward would make the button do the opposite of
     * what it says.
     */
    fun snapOrderQuantity(desired: Int): Int {
        val min = minOrderQuantity.coerceAtLeast(1)
        val step = orderQuantityStep.coerceAtLeast(1)
        if (desired < min) return 0
        return min + ((desired - min) / step) * step
    }

    companion object {
        /**
         * The one status the counter may sell in. Kept here and repeated literally in [ProductDao]'s
         * queries, which Room compiles as SQL and so cannot read a constant.
         */
        const val STATUS_PUBLISHED = "publish"
    }
}
