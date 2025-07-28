package de.gematik.erp.omemory.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import de.gematik.erp.omemory.data.StorageMeta
import de.gematik.erp.omemory.data.StorageMetaRepository
import de.gematik.erp.omemory.data.StorageUrl
import de.gematik.erp.omemory.data.StorageUrlRepository
import de.gematik.erp.omemory.security.RequireGlobalApiKey
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("erp/omem")
open class OmemController(
    private val storageMetaRepo: StorageMetaRepository,
    private val storageUrlRepo: StorageUrlRepository,
    private val jacksonObjectMapper: ObjectMapper
) {

    val storage = StorageOptions.getDefaultInstance().service
    val publicBucket = System.getenv("BUCKET_NAME_PUBLIC")
    val privateBucket = System.getenv("BUCKET_NAME_PRIVATE")
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")


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
                return buildResponse(200, "OK", "Here is your user AccessToken: $accessToken")
            } catch (ex: DataIntegrityViolationException) {
                println("Collision detected for id=$id, retrying...")
            }
        }
        throw IllegalStateException("Failed to generate a unique client ID after $maxAttempts attempts")
    }

    @RequireGlobalApiKey
    @GetMapping("storage/readById")
    open fun readById(
        @RequestParam actorName: String,
        @RequestParam telematikId: String,
        @RequestParam(required = false) dataType: String?,
        @RequestHeader(name = "X-Modified-Since", required = false) date: String?
    ): ResponseEntity<JsonNode> {
        val arrayNode = jacksonObjectMapper.createArrayNode()
        val storageUrls: List<StorageUrl>?
        return if (dataType == null) {
            storageUrls = storageUrlRepo.findByTelematikId(telematikId).orEmpty()
            for (storageUrl in storageUrls) {
                arrayNode.add(jacksonObjectMapper.createObjectNode().put(storageUrl.dataType, storageUrl.url))
            }
            ResponseEntity.ok(arrayNode)
        } else {
            val storageUrl = storageUrlRepo.findByTelematikIdAndDataType(telematikId, dataType)
            if (storageUrl == null) {
                return buildResponse(
                    400,
                    "BAD_REQUEST",
                    "pharmacy with telematikId $telematikId doesn't have data of type $dataType"
                )
            }
            if (date != null) {
                val clientTimeStamp: LocalDateTime = try {
                    LocalDateTime.parse(date, formatter)
                } catch (e: DateTimeParseException) {
                    return buildResponse(400, "BAD_REQUEST", "Date format should be yyyy-MM-dd HH:mm:ss ")
                } as LocalDateTime

                if (!storageUrl.updatedAt.isAfter(clientTimeStamp)) {
                    val jsonNode = jacksonObjectMapper.createObjectNode()
                    jsonNode.put("status", "NOT_MODIFIED")
                    jsonNode.put("statusCode", "304")
                    return buildResponse(304, "NOT_MODIFIED", "Requested object was not updated since $date")
                } else {
                    if (dataType.uppercase() == "TEAM_BILD") {
                        return generateSignedUrl(telematikId, dataType, "")
                    } else {
                        arrayNode.add(jacksonObjectMapper.valueToTree<JsonNode>(mapOf(dataType to storageUrl.url)))
                        return ResponseEntity.ok(arrayNode)
                    }
                }
            } else {
                if (dataType.uppercase() == "TEAM_BILD") {
                    return generateSignedUrl(telematikId, dataType, "")
                } else {
                    arrayNode.add(jacksonObjectMapper.valueToTree<JsonNode>(mapOf(dataType to storageUrl.url)))
                    return ResponseEntity.ok(arrayNode)
                }

            }

        }

    }

    open fun generateSignedUrl(
        telematikId: String,
        dataType: String,
        contentType: String
    ): ResponseEntity<JsonNode> {

        val arrayNode = jacksonObjectMapper.createArrayNode()
        val storageMeta = storageMetaRepo.findByTelematikId(telematikId)
        if (storageMeta == null) {
            return buildResponse(400, "BAD_REQUEST", "TelematikId is not registered or does not exist")
        }
        val objectName = "pharmacy/${storageMeta.actorId}/$dataType"
        var bucketName = publicBucket
        if (dataType.uppercase() == "TEAM_BILD") {
            bucketName = privateBucket
        }

        if (contentType.isEmpty()) {
            val blobInfo = BlobInfo.newBuilder(bucketName, objectName).build()
            val signedURL = storage.signUrl(
                blobInfo,
                30, TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                Storage.SignUrlOption.withV4Signature()
            )
            arrayNode.add(jacksonObjectMapper.valueToTree<JsonNode>(mapOf(dataType to signedURL)))
            return ResponseEntity.ok(arrayNode)
        } else {
            val blobInfo = BlobInfo.newBuilder(bucketName, objectName).setContentType(contentType).build()
            val signedURL = storage.signUrl(
                blobInfo,
                15, TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.withContentType()
            )
            val jsonNode = JsonNodeFactory.instance.objectNode()
            jsonNode.put("signedUrl", signedURL.toString())
            return ResponseEntity.ok(jsonNode)
        }
    }

    @GetMapping("storage/read")
    open fun readAll(
        @RequestParam actorName: String,
        @RequestParam(required = false) dataType: String?
    ): JsonNode {
        val arrayNode = jacksonObjectMapper.createArrayNode()
        val pharmacyMap = mutableMapOf<String, MutableMap<String, String>>()
        val storageUrls: List<StorageUrl>
        if (dataType == null) {
            storageUrls = storageUrlRepo.findAll()
        } else {
            storageUrls = storageUrlRepo.findByDataType(dataType)!!
        }
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

    @PutMapping("storage/signUrl")
    open fun writeToBucket(
        @RequestParam dataType: String, @RequestParam("telematikId") telematikId: String, @RequestBody body: JsonNode,
    ): ResponseEntity<JsonNode> {
        val storageMeta = storageMetaRepo.findByTelematikId(telematikId)
        if (storageMeta == null) {
            return buildResponse(400, "BAD_REQUEST", "TelematikId is not registered or does not exist")
        }

        val apiKey = body["accessToken"].asText()
        val userApiKey = storageMeta.accessToken
        if (apiKey != userApiKey) {
            return buildResponse(401, "UNAUTHORIZED", "Invalid user API key")
        }
        val contentType = body["contentType"].toString().trim('"')
        return generateSignedUrl(telematikId, dataType, contentType)
    }

    @PutMapping("storage/confirm-upload")
    open fun confirmUpload(@RequestParam telematikId: String, @RequestParam dataType: String) {
        val storageMeta = storageMetaRepo.findByTelematikId(telematikId)!!
        val actorId = storageMeta.actorId

        val encodedActorId = URLEncoder.encode(actorId, "UTF-8").replace("+", "%20")
        val encodedData = URLEncoder.encode(dataType, "UTF-8").replace("+", "%20")

        var url = "-"
        if (dataType.uppercase() != "TEAM_BILD") {
            // store URL to a public image in a database for future requests
            url = "https://storage.googleapis.com/${getBucketName(dataType)}/pharmacy/$encodedActorId/$encodedData"
        }
        val oldStorageUrl = storageUrlRepo.findByTelematikIdAndDataType(telematikId, dataType)
        if (oldStorageUrl != null) {
            oldStorageUrl.updatedAt = LocalDateTime.now()
            storageUrlRepo.save(oldStorageUrl)
        } else {
            val storageUrl = StorageUrl(0, storageMeta, url, dataType, telematikId)
            storageUrlRepo.save(storageUrl)
        }
    }

    @DeleteMapping("storage/delete")
    open fun deleteFromBucket(
        @RequestParam dataType: String,
        @RequestParam telematikId: String,
        @RequestHeader("X-USER_ACCESS_TOKEN") accessToken: String
    ): ResponseEntity<JsonNode> {
        val storageMeta = storageMetaRepo.findByTelematikId(telematikId)
        if (storageMeta == null) {
            return buildResponse(400, "BAD_REQUEST", "TelematikId is not registered or does not exist")
        }
        val userApiKey = storageMeta.accessToken
        if (accessToken != userApiKey || accessToken.isEmpty()) {
            return buildResponse(401, "UNAUTHORIZED", "Invalid user API key")
        }
        val storageUrl = storageUrlRepo.findByTelematikIdAndDataType(telematikId, dataType)
        if (storageUrl == null) {
            return buildResponse(
                400,
                "BAD_REQUEST",
                "pharmacy with telematikId $telematikId doesn't have data of type $dataType"
            )
        }
        val actorId = storageMeta.actorId
        val encodedActorId = URLEncoder.encode(actorId, "UTF-8").replace("+", "%20")
        val encodedData = URLEncoder.encode(dataType, "UTF-8").replace("+", "%20")
        val objectName = "pharmacy/$encodedActorId/$encodedData"
        val blobId = BlobId.of(getBucketName(dataType), objectName)
        //delete the file from the GCS-bucket
        storage.delete(blobId)
        //delete the URL of deleted file from the database
        storageUrlRepo.delete(storageUrl)
        return buildResponse(200, "OK", "Blob $objectName deleted")
    }

    open fun getBucketName(dataType: String): String {
        return if (dataType.uppercase() == "TEAM_BILD") {
            privateBucket
        } else {
            publicBucket
        }

    }
}