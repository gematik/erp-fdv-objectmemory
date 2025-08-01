package de.gematik.erp.omemory

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.gematik.erp.omemory.data.StorageUrlRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
open class StorageService(
    private val storageUrlRepo: StorageUrlRepository,
    private val jacksonObjectMapper: ObjectMapper
) {

    @Cacheable("readAllCached")
    open fun getEfficientJson(actorName: String, dataType: String?): JsonNode {
        println("🚨 Cache MISS for actorName=$actorName, dataType=$dataType")
        val storageUrls = if (dataType == null) {
            storageUrlRepo.findAll()
        } else {
            storageUrlRepo.findByDataType(dataType) ?: emptyList()
        }

        val groupedMap = storageUrls.groupBy { it.telematikId }

        val outputList = groupedMap.map { (telematikId, urls) ->
            val entry = mutableMapOf<String, String>()
            entry["pharmacy"] = telematikId
            for (url in urls) {
                entry[url.dataType] = url.url
            }
            entry
        }

        return jacksonObjectMapper.valueToTree(outputList)
    }
}