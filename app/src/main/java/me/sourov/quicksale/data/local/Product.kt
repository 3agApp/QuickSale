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

    /**
     * How many units the store can actually supply, or null when that has no number.
     *
     * Null is "sell as many as you like", and it covers the two ways a store says so: a product it
     * doesn't count at all (`manage_stock` off, so [stockQuantity] is null), and one it marks
     * `onbackorder`, which is the shop stating outright that this may be ordered past its count.
     * Neither is a shortage, so neither should ever raise a warning.
     *
     * A count the store has already let run negative reads as 0 — there is no such thing as less
     * than nothing on the shelf, and the operator only needs to know there is none.
     */
    val availableStock: Int?
        get() = when {
            stockStatus == STOCK_ON_BACKORDER -> null
            stockQuantity != null -> stockQuantity.coerceAtLeast(0)
            stockStatus == STOCK_OUT_OF_STOCK -> 0
            else -> null
        }

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
     * The smallest quantity the store will sell this product in — [minOrderQuantity] as everything
     * downstream must read it, with a missing rule or a store's leftover zero read as one unit.
     *
     * Every quantity decision goes through this rather than the raw column, so a zero can't reach
     * the counter as "a pack of none".
     */
    val packSize: Int get() = minOrderQuantity.coerceAtLeast(1)

    /** How quantities grow above [packSize], with a missing or zero step read as one unit. */
    val quantityStep: Int get() = orderQuantityStep.coerceAtLeast(1)

    /**
     * The largest quantity the store will actually sell at or below [desired], or 0 when [desired]
     * falls short of the pack size (i.e. the line should go away rather than round up to a
     * quantity nobody asked for).
     *
     * Every orderable quantity is `packSize + n × quantityStep`, so this is what turns a raw +1/−1
     * intent into a number WooCommerce will accept. Rounding *down* is deliberate: the operator
     * taps − to sell less, and snapping upward would make the button do the opposite of what it
     * says.
     */
    fun snapOrderQuantity(desired: Int): Int = snapToPackSize(desired, packSize, quantityStep)

    companion object {
        /**
         * The one status the counter may sell in. Kept here and repeated literally in [ProductDao]'s
         * queries, which Room compiles as SQL and so cannot read a constant.
         */
        const val STATUS_PUBLISHED = "publish"

        /** WooCommerce's `stock_status` for a product the shop has none of. */
        const val STOCK_OUT_OF_STOCK = "outofstock"

        /** WooCommerce's `stock_status` for one the shop will take orders on regardless. */
        const val STOCK_ON_BACKORDER = "onbackorder"
    }
}

/**
 * The largest quantity a store selling in [packSize]s of [step] will take at or below [desired],
 * or 0 when [desired] falls short of a single pack.
 *
 * Lives outside [Product] because a line being edited on a *placed* order carries the two numbers
 * without carrying the product they came from — and the two screens must snap identically, or the
 * order the counter can build is not the order it can then correct. Both arguments are the already
 * coerced [Product.packSize] / [Product.quantityStep], never the raw columns.
 */
fun snapToPackSize(desired: Int, packSize: Int, step: Int): Int {
    val pack = packSize.coerceAtLeast(1)
    val by = step.coerceAtLeast(1)
    if (desired < pack) return 0
    return pack + ((desired - pack) / by) * by
}

/**
 * How [quantity] disagrees with a store selling in [packSize]s of [step], or null when it sits on
 * the rule.
 *
 * Phrased as the quantities the store *will* take rather than as a rule to decode: at a counter
 * with someone waiting, "6 or 12, not 7" is a decision, while "step 6, minimum 6" is homework.
 *
 * Lives here, beside the arithmetic it describes, because both screens that move a quantity show
 * it — and a cart line and an order line reading differently about the same product is worse than
 * either wording on its own.
 */
fun packSizeNote(quantity: Int, packSize: Int, step: Int): String? {
    val below = snapToPackSize(quantity, packSize, step)
    if (below == quantity) return null
    val sells = if (below == 0) {
        "${packSize.coerceAtLeast(1)}"
    } else {
        "$below or ${below + step.coerceAtLeast(1)}"
    }
    return "Store sells $sells, not $quantity"
}

/**
 * [quantity] moved [steps] cases of [step], landing on a quantity a store selling in [packSize]s
 * will take. 0 means the line has dropped below a single pack and belongs off the order.
 *
 * A quantity that was never on the lattice — a line billed before the store had a pack size at all
 * — comes *down onto* it rather than straight through it: − on 7 of a six-pack means 6, not "off
 * the order". On every quantity the app itself produced this is plain arithmetic.
 */
fun stepPackSize(quantity: Int, steps: Int, packSize: Int, step: Int): Int {
    val by = step.coerceAtLeast(1)
    val onLattice = snapToPackSize(quantity, packSize, by)
    if (steps < 0 && onLattice < quantity) {
        return snapToPackSize(onLattice + (steps + 1) * by, packSize, by)
    }
    return snapToPackSize(quantity + steps * by, packSize, by)
}
