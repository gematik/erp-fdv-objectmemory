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
import de.gematik.erp.omemory.data.StorageUrl
import de.gematik.erp.omemory.data.StorageUrlRepository
import de.gematik.erp.omemory.security.RequireGlobalApiKey
import de.gematik.erp.omemory.security.RequireUserApiKey
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.FileInputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("erp/omem")
open class OmemController(
    private val storageMetaRepo: StorageMetaRepository,
    private val storageContentRepo: StorageContentRepository,
    private val storageUrlRepo: StorageUrlRepository,
    private val jacksonObjectMapper: ObjectMapper
) {
    val bucketName = "omem_bucket"
    val dataTypes: MutableList<String> =
        mutableListOf("LOGO", "TEAM_BILD", "AUSSENANSICHT", "INNENANSICHT", "INNENANSICHT_2")


    fun buildResponse(statusCode: Int, status: String, message: String): ResponseEntity<JsonNode> {
        val jsonNode = JsonNodeFactory.instance.objectNode()
        jsonNode.put("status", status)
        jsonNode.put("statusCode", statusCode)
        jsonNode.put("message", message)
        return ResponseEntity.status(statusCode).body(jsonNode)
    }

    fun generateRandomId(length: Int = 6): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

    @RequireUserApiKey
    @GetMapping("storage/get")
    open fun listStorages(@RequestParam("storageName") name: String): ResponseEntity<JsonNode> {
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

    @GetMapping("storage/get/{id}")
    open fun listStorage(
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
    open fun listStorageField(
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

    @RequireUserApiKey
    @PutMapping("storage/put")
    open fun putInStorage(@RequestParam("name") name: String, @RequestBody body: JsonNode): ResponseEntity<JsonNode> {
        val storageMeta =
            storageMetaRepo.findByStorageName(name) ?: return buildResponse(
                400,
                "error",
                "storage with name $name does not exist"
            )

        val storageContent = storageContentRepo.findByStorageName(name)
        val jsonStorageContent = storageContent.map { jacksonObjectMapper.readTree(it) }
        val entriesWithSameId = jsonStorageContent.stream().filter { it.get("id").equals(body.get("id")) }.findAny()
        if (!entriesWithSameId.isEmpty) {
            return buildResponse(400, "error", "entry with id ${body.get("id")} already exists")
        }
        //convert JSON (JsonNode) to JsonString
        val jsonString = jacksonObjectMapper.writeValueAsString(body)
        val storage = StorageContent(0, storageMeta = storageMeta, storageContent = jsonString)
        storageContentRepo.save(storage)
        return buildResponse(200, "success", "New entry added to the $name storage")

    }

    @RequireGlobalApiKey
    @PutMapping("storage/register")
    open fun registerActor(
        @RequestParam("actorName") name: String,
        @RequestParam("telematikId") telematikId: String
    ): ResponseEntity<JsonNode> {
        val maxAttempts = 5
        val accessToken = UUID.randomUUID().toString()

        repeat(maxAttempts) {
            val id = generateRandomId()
            try {
                val storageMeta = StorageMeta(0, id, name, telematikId, accessToken)
                storageMetaRepo.save(storageMeta)
                return buildResponse(200, "success", "Here is your user AccessToken: $accessToken")
            } catch (ex : DataIntegrityViolationException) {
                println("Collision detected for id=$id, retrying...")
            }
        }
        throw IllegalStateException("Failed to generate a unique client ID after $maxAttempts attempts")
    }

    @RequireGlobalApiKey
    @GetMapping("storage/read/id")
    open fun readFromBucketById(
        @RequestParam actorName: String,
        @RequestParam telematikId: String,
        @RequestParam dataType: String
    ): ResponseEntity<JsonNode> {
        val storage: Storage = StorageOptions.newBuilder()
            .setCredentials(ServiceAccountCredentials.fromStream(FileInputStream("/Users/a.ibrokhimov/Downloads/magnetic-flare-462410-t3-b2153670acce.json")))
            .build()
            .service

        val storageId = storageMetaRepo.findByTelematikId(telematikId)!!.id
        println("STORAGEID: $storageId")
        val url = storageUrlRepo.findStorageUrl(storageId, dataType)
        return if (url == null) {
            buildResponse(400, "error", "No such object: $dataType for actor $actorName with telematikId $telematikId")
        } else {
            buildResponse(200, "success", url)
        }
    }

    @GetMapping("storage/read")
    open fun readFromBucket(
        @RequestParam actorName: String,
        @RequestParam dataType: String
    ): JsonNode {
        val storage: Storage = StorageOptions.newBuilder()
            .setCredentials(ServiceAccountCredentials.fromStream(FileInputStream("/Users/a.ibrokhimov/Downloads/magnetic-flare-462410-t3-b2153670acce.json")))
            .build()
            .service

        val arrayNode = jacksonObjectMapper.createArrayNode()
        val pharmacyMap = mutableMapOf<String, MutableMap<String, String>>()
        val storageUrls = storageUrlRepo.findAll()
        for (storageUrl in storageUrls) {
            val imageMap = pharmacyMap.getOrPut(storageUrl.telematikId) {
                mutableMapOf("pharmacy" to storageUrl.telematikId)
            }
            imageMap[storageUrl.dataType] = storageUrl.url
        }

        // Convert to JsonNode
        for (pharmacyEntry in pharmacyMap.values) {
            val node = jacksonObjectMapper.valueToTree<JsonNode>(pharmacyEntry)
            arrayNode.add(node)
        }
        return arrayNode
    }

    //@RequireUserApiKey
    @PutMapping("storage/upload")
    open fun writeToBucket(
        @RequestParam dataType: String, @RequestBody body: JsonNode,
    ): ResponseEntity<JsonNode> {
        val storage: Storage = StorageOptions.newBuilder()
            .setCredentials(ServiceAccountCredentials.fromStream(FileInputStream("/Users/a.ibrokhimov/Downloads/magnetic-flare-462410-t3-b2153670acce.json")))
            .build()
            .service

        val telematikId = body.get("telematikId").toString().trim('"')
        val accessToken = body.get("accessToken").toString().trim('"')
        //val accessTokenFromDb = storageMetaRepo.findAccessToken(telematikId)
        val accessTokenFromDb = storageMetaRepo.findByTelematikId(telematikId)?.accessToken
        if (accessToken != accessTokenFromDb) {
            return buildResponse(401, "error", "Access token is incorrect")
        }
        //val actorId = storageMetaRepo.findActorId(telematikId)
        val actorId = storageMetaRepo.findByTelematikId(telematikId)?.actorId

        val objectName = "pharmacy/$actorId/$dataType"
        val contentType = body.get("contentType").toString().trim('"')
        val blobInfo = BlobInfo.newBuilder(bucketName, objectName).setContentType(contentType).build()

        val signedURL = storage.signUrl(
            blobInfo,
            15, TimeUnit.MINUTES,
            Storage.SignUrlOption.httpMethod(com.google.cloud.storage.HttpMethod.PUT),
            Storage.SignUrlOption.withV4Signature(),
            Storage.SignUrlOption.withContentType()
        )
        val jsonNode = JsonNodeFactory.instance.objectNode()
        jsonNode.put("signedUrl", signedURL.toString())
        return ResponseEntity.ok(jsonNode)
    }

    @PutMapping("storage/confirm-upload")
    open fun confirmUpload(@RequestParam telematikId: String, @RequestParam dataType: String) {
        val storageMeta = storageMetaRepo.findByTelematikId(telematikId)!!
        val actorId = storageMeta.actorId

        val encodedActorId = URLEncoder.encode(actorId, "UTF-8").replace("+", "%20")
        val encodedData = URLEncoder.encode(dataType, "UTF-8").replace("+", "%20")

        // store URL to an image in a database for future requests
        val url = "https://storage.googleapis.com/$bucketName/pharmacy/$encodedActorId/$encodedData"
        val storageUrl = StorageUrl(0, storageMeta, url, dataType, telematikId)
        storageUrlRepo.save(storageUrl)
    }
}