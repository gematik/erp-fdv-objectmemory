package de.gematik.erp.omemory

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.gematik.erp.omemory.data.StorageContent
import de.gematik.erp.omemory.data.StorageContentRepository
import de.gematik.erp.omemory.data.StorageMeta
import de.gematik.erp.omemory.data.StorageMetaRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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

    fun storageExists(name: String): ResponseEntity<Any> {
        val jsonStrings = storageContentRepo.findByStorageName(name)
        if (jsonStrings.isEmpty()) {
            return ResponseEntity.ok().body("Storage with name $name does not exist")
        } else {
            return ResponseEntity.ok().build()
        }
    }


    @GetMapping("structure")
    fun listPharmacyStructure(@RequestParam("storageName") name: String): ResponseEntity<String> {
        val structure = storageMetaRepo.findStructureByName(name) ?: return ResponseEntity.badRequest()
            .body("Storage with name $name does not exist")
        return ResponseEntity.ok().body("$name storage should have the following fields: $structure")
    }

    @GetMapping("storage")
    fun listStorages(@RequestParam("storageName") name: String): ResponseEntity<Any> {
        val jsonStrings = storageContentRepo.findByStorageName(name)
        if (jsonStrings.isEmpty()) {
            return ResponseEntity.badRequest().body("Storage with name $name does not exist")
        }
        //convert every json String to JsonNode and return List<JsonNode>
        val jsonNodes = jsonStrings.map { jacksonObjectMapper.readTree(it) }
        //convert List<JsonNode> to a single JsonNode (which is a JsonArray aka ArrayNode)
        val jsonNode = jacksonObjectMapper.valueToTree<JsonNode>(jsonNodes)
        return ResponseEntity.ok(jsonNode)
    }

    @GetMapping("storage/{id}")
    fun listStorage(
        @RequestParam("storageName") name: String,
        @PathVariable("id") id: Long
    ): ResponseEntity<Any> {
        val response = storageExists(name)
        if (response.hasBody()) return response
        val jsonString =
            storageContentRepo.findByStorageNameAndStorageId(name, id) ?: return ResponseEntity.badRequest()
                .body("$name with id $id does not exist")
        val jsonNode = jacksonObjectMapper.readTree(jsonString)
        return ResponseEntity.ok(jsonNode)
    }

    @GetMapping("storageField/{id}")
    fun listStorageField(
        @RequestParam("storageName") name: String,
        @RequestParam("storageField") field: String,
        @PathVariable("id") id: Long
    ): ResponseEntity<Any> {
        val response = storageExists(name)
        if (response.hasBody()) return response
        val jsonString =
            storageContentRepo.findByStorageNameAndStorageId(name, id) ?: return ResponseEntity.badRequest()
                .body("$name with id $id does not exist")
        val jsonNode = jacksonObjectMapper.readTree(jsonString)
        val fieldNode = jsonNode.get(field) ?: return ResponseEntity.badRequest()
            .body("Field $field does not exist in $name storage")
        return ResponseEntity.ok("$field: $fieldNode")
    }

    @PutMapping("storage/register")
    fun registerStorage(@RequestParam("name") name: String, @RequestBody body: List<String>): ResponseEntity<String> {
        if (storageMetaRepo.findByStorageName(name) != null) {
            return ResponseEntity.badRequest().body("Storage with name $name already exists")
        }
        val storage = StorageMeta(0, name, body)
        storageMetaRepo.save(storage)
        return ResponseEntity.ok().body("Storage with name $name registered successfully")
    }

    @PutMapping("storage/put")
    fun putInStorage(@RequestParam("name") name: String, @RequestBody body: JsonNode): ResponseEntity<String> {
        val storageMeta =
            storageMetaRepo.findByStorageName(name) ?: return ResponseEntity.ok()
                .body("Storage with name $name does not exist")

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
        val storage = StorageContent(storageMeta = storageMeta, storageContent = jsonString)
        storageContentRepo.save(storage)
        return ResponseEntity.ok().body("New entry added to the $name storage")

    }

}