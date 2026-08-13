package me.sourov.quicksale.data.settings

/**
 * One field of a country's address form, exactly as WooCommerce defines it for that country with
 * the shop's own checkout customisations applied.
 *
 * The app renders from this rather than hand-writing an address form, because a hand-written one
 * is wrong in a different way in every country.
 */
data class AddressField(
    /** Submitted under this name inside the request's `shipping` or `billing` block. */
    val name: String,
    val label: String,
    /**
     * Taken from the served form and never second-guessed against WooCommerce's published
     * defaults, and never carried across from the other shape. On a *delivery* address the shop
     * relaxes two of them on purpose: `last_name` is not required (an address belongs to a place
     * as often as to a person — "Warehouse North" has no surname, so the place name goes in
     * `first_name`), and neither is `phone`, whatever the checkout's phone setting says, because
     * that setting is a rule about the person buying. Billing keeps WooCommerce's own answers for
     * both. Reading either from the wrong form marks a field optional that the store then
     * requires, which is a refusal at the counter over a rule the screen said did not apply.
     */
    val required: Boolean,
    val hidden: Boolean,
    /** `text`, `tel`, `country`, `state`, … — drives the keyboard and the control. */
    val type: String,
    /**
     * Present only on `state`, and only where the country has a fixed list. When present render a
     * picker and submit the key; when absent free text is what the checkout renders too.
     */
    val options: Map<String, String> = emptyMap(),
) {
    val hasOptions: Boolean get() = options.isNotEmpty()
}

/**
 * The shop's address forms, one per country it ships to, in both shapes. Synced alongside the
 * organization snapshot and revalidated with an ETag; they change only when WooCommerce or the
 * shop's settings do.
 *
 * Both shapes are kept because the till edits both — a one-off delivery address on an order and a
 * location, and the billing address on a company — and they are not interchangeable however alike
 * they look. See [AddressField.required].
 */
data class AddressForms(
    /** The shop's base country, for preselecting. */
    val defaultCountry: String = "",
    /** The shop's ship-to list (code → name), not every country in the world. */
    val countries: Map<String, String> = emptyMap(),
    /**
     * The shop's sell-to list (code → name). Not the same as [countries]: a shop sells to more
     * places than it ships to as soon as one customer's invoices go somewhere its couriers don't.
     * Empty against a store too old to serve it, and then [billingCountries] falls back.
     */
    val sellToCountries: Map<String, String> = emptyMap(),
    /** Delivery address definitions, per ship-to country. */
    val forms: Map<String, List<AddressField>> = emptyMap(),
    /** Billing address definitions, per sell-to country. Empty against a store too old to serve them. */
    val billingForms: Map<String, List<AddressField>> = emptyMap(),
) {
    val isEmpty: Boolean get() = countries.isEmpty() || forms.isEmpty()

    /** The sell-to list, falling back to the ship-to one against a store that serves only that. */
    val billingCountries: Map<String, String>
        get() = sellToCountries.ifEmpty { countries }

    /** Countries the shop ships to, in name order, ready for a picker. */
    val countryChoices: List<Pair<String, String>>
        get() = countries.choices()

    /** Countries the shop sells to, in name order, ready for a billing picker. */
    val billingCountryChoices: List<Pair<String, String>>
        get() = billingCountries.choices()

    private fun Map<String, String>.choices(): List<Pair<String, String>> =
        entries.map { it.key to it.value }.sortedBy { it.second.lowercase() }

    /** The visible delivery fields for [countryCode], in WooCommerce's display order. */
    fun fieldsFor(countryCode: String): List<AddressField> =
        forms[countryCode].orEmpty().filterNot { it.hidden }

    /**
     * The visible billing fields for [countryCode], in WooCommerce's display order.
     *
     * Falls back to the delivery shape against a store that predates `billing_forms`, which is
     * what the app did for every form before that existed: imperfect in exactly the known way —
     * a surname marked optional that the store requires — rather than an empty form that cannot
     * be filled in at all.
     */
    fun billingFieldsFor(countryCode: String): List<AddressField> =
        (billingForms[countryCode] ?: forms[countryCode]).orEmpty().filterNot { it.hidden }

    fun countryName(code: String): String = countries[code] ?: code

    fun billingCountryName(code: String): String = billingCountries[code] ?: code
}
