package de.gematik.erp.omemory

import Message
import com.fasterxml.jackson.databind.JsonNode
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
    private val storageContentRepo: StorageContentRepository
) {
    @GetMapping("/erp/example/m1")
    fun index(@RequestParam("name") name: String) = "Hello, $name!"

    @GetMapping("/erp/example/m2")
    fun myMethod2(@RequestBody body: String) = "Hi $body!!!"

    @GetMapping("erp/example/m3")
    fun listMessages(): List<Message> = listOf(
        Message("1", "Hello!"),
        Message("2", "Bonjour!"),
        Message("3", "Privet!"),
    )

    @GetMapping("structure")
    fun listPharmacyStructure(@RequestParam("storage") storage: String): StorageMeta {
        return storageMetaRepo.findByStorageName(storage)
    }

    /*@GetMapping("storage")
    fun listPharmaciesOrOtherStorage(@RequestParam("name") nameOfTheStorage: String): List<Map<String, Any>> {
        //TODO need to be able to dynamically choose what storage is needed based on user input
        val pharmaStorage = PharmacyStorage()
        return pharmaStorage.getStorage()
    }*/

    /*@GetMapping("storage/{id}")
    fun listPharmacy(@PathVariable id: Int, @RequestParam("name") name: String): Map<String, Any> {
        //need to be able to dynamically choose what storage is needed based on user input
        val pharmaStorage = PharmacyStorage()
        return pharmaStorage.getStorage(id)
    }

    @GetMapping("storage/value/{id}")
    fun listPharmacyValue(@PathVariable id: Int, @RequestParam("name") name: String): Map<String, Any?> {
        //need to be able to dynamically choose what storage is needed based on user input
        val pharmaStorage = PharmacyStorage()
        return pharmaStorage.getValue(id, name)
    }*/

    @GetMapping("storage")
    fun listPharmaciesFromDB(@RequestParam("name") name: String): StorageContent {
        return storageContentRepo.findByStorageName(name)
    }

    @PutMapping("storage/register")
    fun registerStorage(@RequestParam("name") name: String, @RequestBody body: List<String>) {
        val storage = StorageMeta(0, name, body)
        storageMetaRepo.save(storage)
    }

    @PutMapping("storage/put")
    fun putInStorage(@RequestParam("name") name: String, @RequestBody body: JsonNode): ResponseEntity<String> {
        val storage = storageMetaRepo.findByStorageName(name)
        val storageStructure = storage.structure
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
        val jsonString = body.toString()
        val storageContent = StorageContent(0, name, jsonString)
        storageContentRepo.save(storageContent)
        return ResponseEntity.ok().body("New entry added to $name storage")

    }

}