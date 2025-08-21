/*
 * Copyright 2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */

package de.gematik.erp.omemory

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.gematik.erp.omemory.data.StorageMeta
import de.gematik.erp.omemory.data.StorageMetaRepository
import de.gematik.erp.omemory.data.StorageUrlRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
open class StorageService(
    private val storageUrlRepo: StorageUrlRepository,
    private val jacksonObjectMapper: ObjectMapper,
    private val storageMetaRepo: StorageMetaRepository,
) {

    @Cacheable("readAllUrlsCached")
    open fun readAllCached(actorName: String, dataType: String?): JsonNode {
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

    @Cacheable("readUrlsCached")
    open fun readUrlsCached(storageUrlRepo: StorageUrlRepository, telematikId: String): JsonNode {
        val arrayNode = jacksonObjectMapper.createArrayNode()
        val storageUrls = storageUrlRepo.findByTelematikId(telematikId).orEmpty()
        for (storageUrl in storageUrls) {
            arrayNode.add(jacksonObjectMapper.createObjectNode().put(storageUrl.dataType, storageUrl.url))
        }
        return arrayNode
    }

    @Cacheable("readMetaCached")
    open fun readMetaCached(telematikId: String): StorageMeta? {
        return storageMetaRepo.findByTelematikId(telematikId)
    }
}