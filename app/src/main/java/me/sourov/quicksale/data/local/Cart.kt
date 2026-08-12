package me.sourov.quicksale.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * One product in the cart that is currently being built, as it survives the app being killed.
 *
 * Only the id and the quantity: the product's name, price and pack size are read back from the
 * catalog on restore, so a cart left overnight prices itself from today's sync rather than from
 * whatever was true when the visitor first pointed at the shelf.
 */
@Entity(tableName = "cart_lines")
data class CartLineRecord(
    @PrimaryKey val productId: Long,
    val quantity: Int,
    /** Keeps the restored cart in the order things were scanned. */
    val addedAtMillis: Long,
)

/**
 * Who the cart in progress is for, if that has been decided yet — a single row, id always 0.
 *
 * Nullable because the cart is filled *before* the customer is chosen; that is the whole point of
 * the Sell tab.
 */
@Entity(tableName = "cart_customer")
data class CartCustomerRecord(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val organizationId: Long?,
    val memberUserId: Long?,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

@Dao
interface CartDao {

    @Query("SELECT * FROM cart_lines ORDER BY addedAtMillis")
    suspend fun lines(): List<CartLineRecord>

    @Query("SELECT * FROM cart_customer WHERE id = ${CartCustomerRecord.SINGLETON_ID}")
    suspend fun customer(): CartCustomerRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<CartLineRecord>)

    @Query("DELETE FROM cart_lines")
    suspend fun clearLines()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCustomer(customer: CartCustomerRecord)

    @Query("DELETE FROM cart_customer")
    suspend fun clearCustomer()

    /**
     * Rewrites the whole cart in one transaction.
     *
     * The cart is small — a fair order is a handful of lines — so replacing it wholesale is both
     * simpler and safer than diffing: there is no state in which the table holds half of one cart
     * and half of the next.
     */
    @Transaction
    suspend fun replace(lines: List<CartLineRecord>, customer: CartCustomerRecord?) {
        clearLines()
        clearCustomer()
        if (lines.isNotEmpty()) insertLines(lines)
        if (customer != null) setCustomer(customer)
    }
}

/** The cart that outlives the process. See [CartDao.replace] for why it is written wholesale. */
class CartRepository(private val dao: CartDao) {

    suspend fun load(): Pair<List<CartLineRecord>, CartCustomerRecord?> =
        dao.lines() to dao.customer()

    suspend fun save(lines: List<CartLineRecord>, customer: CartCustomerRecord?) =
        dao.replace(lines, customer)
}
