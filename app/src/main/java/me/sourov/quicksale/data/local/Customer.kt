package me.sourov.quicksale.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val company: String,
    val city: String,
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { email.ifBlank { "Customer #$id" } }

    val initials: String
        get() = listOf(firstName, lastName)
            .filter { it.isNotBlank() }
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { (email.firstOrNull()?.uppercaseChar() ?: '?').toString() }
}
