package de.gematik.erp.omemory

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.gematik.erp.omemory.data.StorageContent
import de.gematik.erp.omemory.data.StorageContentRepository
import de.gematik.erp.omemory.data.StorageMeta
import de.gematik.erp.omemory.data.StorageMetaRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("erp/omem")
class OmemController(
    private val storageMetaRepo: StorageMetaRepository,
    private val storageContentRepo: StorageContentRepository,
    private val jacksonObjectMapper: ObjectMapper
) {


    @GetMapping("structure")
    fun listPharmacyStructure(@RequestParam("storage") storage: String): StorageMeta {
        return storageMetaRepo.findByStorageName(storage)
    }

    @GetMapping("storage")
    fun listStorages(@RequestParam("name") name: String): ResponseEntity<JsonNode> {
        val jsonStrings = storageContentRepo.findByStorageName(name)
        //convert every json String to JsonNode and return List<JsonNode>
        val jsonNodes = jsonStrings.map { jacksonObjectMapper.readTree(it) }
        //convert List<JsonNode> to a single JsonNode (which is a JsonArray aka ArrayNode)
        val jsonNode = jacksonObjectMapper.valueToTree<JsonNode>(jsonNodes)
        return ResponseEntity.ok(jsonNode)
    }

    @PutMapping("storage/register")
    fun registerStorage(@RequestParam("name") name: String, @RequestBody body: List<String>) {
        val storage = StorageMeta(0, name, body)
        storageMetaRepo.save(storage)
    }

    @PutMapping("storage/put")
    fun putInStorage(@RequestParam("name") name: String, @RequestBody body: JsonNode): ResponseEntity<String> {
        val storageMeta = storageMetaRepo.findByStorageName(name)
        val storageStructure = storageMeta.structure
        val fieldNames = body.fieldNames().asSequence().toList()
        val extra = fieldNames - storageStructure
        val missing = storageStructure - fieldNames

        if (extra.isNotEmpty()) {
            return ResponseEntity.badRequest()
                .body("Invalid structure: structure of $name storage does not have parameters: $extra")
        }
        if (missing.isNotEmpty()) {
            return ResponseEntity.badRequest()
                .body("Invalid structure: structure of $name storage should have parameters: $missing")
        }
        //convert JSON (JsonNode) to JsonString
        val jsonString = jacksonObjectMapper.writeValueAsString(body)
        val storageContent = StorageContent(0, storageMeta, jsonString)
        storageContentRepo.save(storageContent)
        return ResponseEntity.ok().body("New entry added to $name storage")

    }

}