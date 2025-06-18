package de.gematik.erp.omemory

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
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
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("erp/omem")
class OmemController(
    private val storageMetaRepo: StorageMetaRepository,
    private val storageContentRepo: StorageContentRepository,
    private val jacksonObjectMapper: ObjectMapper
) {

    fun storageExists(name: String): Boolean {
        return storageContentRepo.findByStorageName(name).isEmpty()
        /*if (jsonStrings.isEmpty()) {
            return ResponseEntity.ok().body("Storage with name $name does not exist")
        } else {
            return ResponseEntity.ok().build()
        }*/
    }

    fun buildResponse(statusCode: Int, status: String, message: String): ResponseEntity<JsonNode> {
        val jsonNode = JsonNodeFactory.instance.objectNode()
        jsonNode.put("status", status)
        jsonNode.put("statusCode", statusCode)
        jsonNode.put("message", message)
        return ResponseEntity.status(statusCode).body(jsonNode)
    }


    @GetMapping("structure")
    fun listPharmacyStructure(@RequestParam("storageName") name: String): ResponseEntity<JsonNode> {
        val structure = storageMetaRepo.findStructureByName(name) ?: return buildResponse(
            400,
            "error",
            "storage with name $name does not exist"
        )
        return buildResponse(200, "success", "$name storage should have the following fields: $structure")
    }

    @GetMapping("storage")
    fun listStorages(@RequestParam("storageName") name: String): ResponseEntity<JsonNode> {
        val jsonStrings = storageContentRepo.findByStorageName(name)
        if (jsonStrings.isEmpty()) {
            return buildResponse(
                400,
                "error",
                "storage with name $name does not exist"
            )
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
    ): ResponseEntity<JsonNode> {
        if (storageContentRepo.findByStorageName(name).isEmpty()) {
            return buildResponse(400, "error", "storage with name $name does not exist")
        }
        val jsonString =
            storageContentRepo.findByStorageNameAndStorageId(name, id) ?: return buildResponse(
                400,
                "error",
                "$name with id $id does not exist"
            )
        val jsonNode = jacksonObjectMapper.readTree(jsonString)
        return ResponseEntity.ok(jsonNode)
    }

    @GetMapping("storageField/{id}")
    fun listStorageField(
        @RequestParam("storageName") name: String,
        @RequestParam("storageField") field: String,
        @PathVariable("id") id: Long
    ): ResponseEntity<JsonNode> {
        if (storageContentRepo.findByStorageName(name).isEmpty()) {
            return buildResponse(400, "error", "storage with name $name does not exist")
        }
        val jsonString =
            storageContentRepo.findByStorageNameAndStorageId(name, id) ?: return buildResponse(
                400,
                "error",
                "$name with id $id does not exist"
            )
        val jsonNode = jacksonObjectMapper.readTree(jsonString)
        val fieldNode =
            jsonNode.get(field) ?: return buildResponse(
                400,
                "error",
                "Field $field does not exist in $name storage"
            )
        val jsonField = JsonNodeFactory.instance.objectNode()
        jsonField.putIfAbsent(field, fieldNode)
        return ResponseEntity.ok(jsonField)
    }

    @PutMapping("storage/register")
    fun registerStorage(@RequestParam("name") name: String, @RequestBody body: List<String>): ResponseEntity<JsonNode> {
        if (storageMetaRepo.findByStorageName(name) != null) {
            return buildResponse(400, "error", "storage with name $name already exists")
        }
        val storage = StorageMeta(0, name, body)
        storageMetaRepo.save(storage)

        return buildResponse(200, "success", "storage with name $name registered successfully")
    }

    @PutMapping("storage/put")
    fun putInStorage(@RequestParam("name") name: String, @RequestBody body: JsonNode): ResponseEntity<JsonNode> {
        val storageMeta =
            storageMetaRepo.findByStorageName(name) ?: return buildResponse(
                400,
                "error",
                "storage with name $name does not exist"
            )
        val storageStructure = storageMeta.structure
        val fieldNames = body.fieldNames().asSequence().toList()
        val extra = fieldNames - storageStructure
        val missing = storageStructure - fieldNames

        if (extra.isNotEmpty()) {
            return buildResponse(
                400,
                "error",
                "Invalid structure: structure of $name storage does not have parameters: $extra"
            )
        }
        if (missing.isNotEmpty()) {
            return buildResponse(
                400,
                "error",
                "Invalid structure: structure of $name storage should have parameters: $missing"
            )
        }
        val storageContent = storageContentRepo.findByStorageName(name)
        val jsonStorageContent = storageContent.map { jacksonObjectMapper.readTree(it) }
        val entriesWithSameId = jsonStorageContent.stream().filter { it.get("id").equals(body.get("id")) }.findAny()
        if (!entriesWithSameId.isEmpty) {
            return buildResponse(400, "error", "entry with id ${body.get("id")} already exists")
        }
        //convert JSON (JsonNode) to JsonString
        val jsonString = jacksonObjectMapper.writeValueAsString(body)
        val storage = StorageContent(storageMeta = storageMeta, storageContent = jsonString)
        storageContentRepo.save(storage)
        return buildResponse(200, "success", "New entry added to the $name storage")

    }

    @PutMapping("storage/upload/direct")
    fun writeToGCSDirectly(@RequestBody content: ByteArray): ResponseEntity<JsonNode> {
        val bucketName = "omem_bucket"
        val objectName = "uploads/my-first-file.txt"

        val storage = StorageOptions.newBuilder().setCredentials(
            ServiceAccountCredentials.fromStream(
                FileInputStream("/Users/a.ibrokhimov/Downloads/magnetic-flare-462410-t3-b2153670acce.json")
            )
        ).build().service
        val blobInfo = BlobInfo.newBuilder(bucketName, objectName)
            .setContentType("text/plain")
            .build()

        storage.create(blobInfo, content)
        return buildResponse(200, "success", "\"✅ Uploaded as gs://$bucketName/$objectName\"")

    }

    @GetMapping("storage/read")
    fun readFromBucket(
        @RequestParam actorName: String,
        @RequestParam actorId: String,
        @RequestParam imageType: String
    ): ResponseEntity<JsonNode> {
        val storage: Storage = StorageOptions.newBuilder()
            .setCredentials(ServiceAccountCredentials.fromStream(FileInputStream("/Users/a.ibrokhimov/Downloads/magnetic-flare-462410-t3-b2153670acce.json")))
            .build()
            .service
        val bucketName = "omem_bucket"
        val objectName = "$actorName/$actorId/$imageType"
        val blobInfo = BlobInfo.newBuilder(bucketName, objectName).build()

        val signedUrl = storage.signUrl(
            blobInfo,
            1, // duration
            TimeUnit.MINUTES,
            Storage.SignUrlOption.withV4Signature()
        )
        return buildResponse(200, "success", "\"✅ Here is your URL to an object: $signedUrl")


    }

    @PutMapping("storage/upload")
    fun writeToBucket(
        @RequestParam actorName: String,
        @RequestParam actorId: String,
        @RequestParam imageType: String
    ): ResponseEntity<JsonNode> {
        val storage: Storage = StorageOptions.newBuilder()
            .setCredentials(ServiceAccountCredentials.fromStream(FileInputStream("/Users/a.ibrokhimov/Downloads/magnetic-flare-462410-t3-b2153670acce.json")))
            .build()
            .service
        val bucketName = "omem_bucket"
        val objectName = "$actorName/$actorId/$imageType"

        val blobInfo = BlobInfo.newBuilder(bucketName, objectName).build()

        val signedURL = storage.signUrl(
            blobInfo,
            15, TimeUnit.MINUTES,
            Storage.SignUrlOption.httpMethod(com.google.cloud.storage.HttpMethod.PUT),
            Storage.SignUrlOption.withV4Signature()
        )
        return buildResponse(200, "success", "✅ Here is your signed PUT URL: $signedURL")
    }

}