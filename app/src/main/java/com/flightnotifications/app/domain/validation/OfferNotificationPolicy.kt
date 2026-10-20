package com.flightnotifications.app.domain.validation

/** Price limit applied after an itinerary has a verified COP round-trip fare. */
object OfferNotificationPolicy {
    data class EligibleOffer<T>(val value: T, val priceCop: Long)

    fun isEligible(priceCop: Long, maximumCop: Long): Boolean =
        priceCop > 0 && maximumCop > 0 && priceCop <= maximumCop

    /** Returns the cheapest positive-price offer; equal prices keep the source's stable order. */
    fun <T> selectCheapestEligible(offers: Iterable<EligibleOffer<T>>): EligibleOffer<T>? =
        offers.asSequence().filter { it.priceCop > 0L }.minByOrNull { it.priceCop }
}
