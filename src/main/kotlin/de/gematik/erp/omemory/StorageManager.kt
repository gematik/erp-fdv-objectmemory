package de.gematik.erp.omemory


sealed class OmemType {
    abstract fun getValue(): Any
    data class Text(private val value: String) : OmemType() {
        override fun getValue() = value
    }

    data class OmemNumber(private val value: Int) : OmemType() {
        override fun getValue() = value
    }

    data class Url(private val value: String) : OmemType() {
        override fun getValue() = value
    }

    data class Email(private val value: String) : OmemType() {
        override fun getValue() = value
    }

    data class Phone(private val value: String) : OmemType() {
        override fun getValue() = value
    }

    data class Image(private val value: String) : OmemType() {
        override fun getValue() = value
    }

    data class Address(
        private val street: String,
        private val houseNumber: Int,
        private val city: String,
        private val postalCode: Int
    ) : OmemType() {
        override fun getValue(): Any {
            return mapOf("street" to street, "houseNumber" to houseNumber, "city" to city, "postalCode" to postalCode)
        }
        fun getStreet() = street
        fun getHouseNumber() = houseNumber
        fun getCity() = city
        fun getPostalCode() = postalCode
    }

    data class TelematikId(private val value: String) : OmemType() {
        override fun getValue() = value
    }
}

interface OmemStorage {
    fun getAllStorages(): List<OmemStorage> {
        return emptyList() //this should be a default method returning all available storages
    }

    fun getStructure(): List<String>
    fun getValue(id: Int, key: String): Map<String, Any?>
    fun getStorage(id: Int): Map<String, Any>
    fun getStorage(vararg maps: Map<String, OmemType>): List<Map<String, Any>>
}
