package de.gematik.erp.omemory

class PharmacyStorage : OmemStorage {
    private val structure = listOf(
        "name",
        "id",
        "address",
        "phone",
        "email"
    )
    val pharmacy1 = mapOf(
        "name" to OmemType.Text("Apotheke am Flughafen"),
        "id" to OmemType.OmemNumber(1),
        "address" to OmemType.Address("Friedrichstr", 136, "Berlin", 10117),
        "phone" to OmemType.Phone("+123456"),
        "email" to OmemType.Email("apothekeAmFlughafen@gmail.com")
    )
    val pharmacy2 = mapOf(
        "name" to OmemType.Text("Apotheke an der Ecke"),
        "id" to OmemType.OmemNumber(2),
        "address" to OmemType.Address("AnDerEckeStr", 12, "Berlin", 13189),
        "phone" to OmemType.Phone("+543532"),
        "email" to OmemType.Email("apothekeAnDerEcke@gmail.com")
    )
    val pharmacy3 = mapOf(
        "name" to OmemType.Text("Apotheke Mall of Berlin"),
        "id" to OmemType.OmemNumber(3),
        "address" to OmemType.Address("PotsdamerPlatzStr", 32, "Berlin", 12800),
        "phone" to OmemType.Phone("+6563646"),
        "email" to OmemType.Email("apothekeMallOfBerlin@gmail.com")
    )

    //list simulates the database
    private val pharmacies = mutableListOf<Map<String, OmemType>>()

    init {
        pharmacies.add(pharmacy1)
        pharmacies.add(pharmacy2)
        pharmacies.add(pharmacy3)
    }


    override fun getStructure(): List<String> {
        return structure
    }

    override fun getValue(id: Int, key: String): Map<String, Any?> {
        val pharmacy = getStorage(id)
        return mapOf(key to (pharmacy.get(key)))

    }

    override fun getStorage(id: Int): Map<String, Any> {
        val pharmacy = pharmacies.filter { id == it.getValue("id").getValue() }[0]
        return getStorage(pharmacy)[0]
    }

    override fun getStorage(vararg maps: Map<String, OmemType>): List<Map<String, Any>> {
        val list: List<Map<String, OmemType>>
        if (maps.isEmpty()) {
            list = pharmacies
        } else {
            list = listOf(maps[0])
        }
        return list.map { omemMap ->
            omemMap.mapValues { (_, omem) ->
                when (omem) {
                    is OmemType.Text -> omem.getValue()
                    is OmemType.OmemNumber -> omem.getValue()
                    is OmemType.Url -> omem.getValue()
                    is OmemType.Email -> omem.getValue()
                    is OmemType.Phone -> omem.getValue()
                    is OmemType.Image -> omem.getValue()
                    is OmemType.TelematikId -> omem.getValue()
                    is OmemType.Address -> "${omem.getStreet()} ${omem.getHouseNumber()}, ${omem.getPostalCode()} ${omem.getCity()}"
                }
            }
        }
    }
}


