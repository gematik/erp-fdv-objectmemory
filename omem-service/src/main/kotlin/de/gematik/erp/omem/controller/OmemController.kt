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

package de.gematik.erp.omem.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import de.gematik.erp.omem.StorageService
import de.gematik.erp.omem.data.StorageMeta
import de.gematik.erp.omem.data.StorageMetaRepository
import de.gematik.erp.omem.data.StorageUrl
import de.gematik.erp.omem.data.StorageUrlRepository
import de.gematik.erp.omem.security.ApiKeyAspect
import de.gematik.erp.omem.security.RequireGlobalApiKey
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
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
@RequestMapping("erp")
open class OmemController(
    private val storageMetaRepo: StorageMetaRepository,
    private val storageUrlRepo: StorageUrlRepository,
    private val jacksonObjectMapper: ObjectMapper,
    private val storageService: StorageService,
    private val apiKeyAspect: ApiKeyAspect
) {

    private val storage = StorageOptions.getDefaultInstance().service
    private val publicBucket = System.getenv("BUCKET_NAME_PUBLIC")
    private val privateBucket = System.getenv("BUCKET_NAME_PRIVATE")
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val dataTypes= mutableListOf("AUSSENANSICHT", "INNENANSICHT_1", "INNENANSICHT_2", "TEAM_BILD", "LOGO")


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
                return buildResponse(200, "OK", "USER_ACCESS_TOKEN: $accessToken")
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
        val storageMeta = storageService.readMetaCached(telematikId)
        if (storageMeta == null) {
            return buildResponse(400, "BAD_REQUEST", "TelematikId is not registered or does not exist")
        }
        return if (dataType == null) {
            val entries = storageService.readUrlsCached(storageUrlRepo, telematikId)
            ResponseEntity.ok(entries)
        } else {
            val clientTimeStamp = try {
                getClientTimeStamp(date)
            } catch (e: DateTimeParseException) {
                return buildResponse(400, "BAD_REQUEST", "Date format should be yyyy-MM-dd HH:mm:ss ")

            }
            val storageUrl = storageUrlRepo.findByTelematikIdAndDataType(telematikId, dataType)
            if (storageUrl == null) {
                return buildResponse(
                    400,
                    "BAD_REQUEST",
                    "pharmacy with telematikId $telematikId doesn't have data of type $dataType"
                )
            }
            val wasUpdated = checkIfUpdated(storageUrl.updatedAt, clientTimeStamp)
            if (!wasUpdated) {
                return buildResponse(304, "NOT_MODIFIED", "Requested object was not updated since $date")
            } else if (dataType.uppercase() == "TEAM_BILD") {
                return generateSignedUrl(storageMeta.actorId, "", dataType)
            } else {
                arrayNode.add(jacksonObjectMapper.valueToTree<JsonNode>(mapOf(dataType to storageUrl.url)))
                return ResponseEntity.ok(arrayNode)
            }
        }
    }

    @GetMapping("storage/read")
    open fun readAllEfficient(
        @RequestParam actorName: String,
        @RequestParam(required = false) dataType: String?
    ): ResponseEntity<JsonNode> {
        if(actorName != "pharmacy"){
            return buildResponse(400, "BAD_REQUEST", "actor $actorName not found")
        }
        if (dataType != null && !dataTypes.contains(dataType.uppercase())) {
            return buildResponse(400, "BAD_REQUEST", "Data type $dataType is not supported")
        }
        val res = storageService.readAllCached(actorName, dataType)
        return ResponseEntity.ok(res)
    }


    @PutMapping("storage/signUrl")
    open fun signUrl(
        @RequestParam dataType: String,
        @RequestParam("telematikId") telematikId: String,
        @RequestParam contentType: String,
    ): ResponseEntity<JsonNode> {
        val storageMeta = storageService.readMetaCached(telematikId)
        if (storageMeta == null) {
            return buildResponse(400, "BAD_REQUEST", "TelematikId is not registered or does not exist")
        }
        apiKeyAspect.checkUserApiKey(storageMeta)
        return generateSignedUrl(storageMeta.actorId, contentType, dataType)
    }

    @PutMapping("storage/confirm-upload")
    open fun confirmUpload(@RequestParam telematikId: String, @RequestParam dataType: String) {
        val storageMeta = storageService.readMetaCached(telematikId)!!
        apiKeyAspect.checkUserApiKey(storageMeta)
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
        @RequestParam telematikId: String
    ): ResponseEntity<JsonNode> {
        val storageMeta = storageService.readMetaCached(telematikId)
        if (storageMeta == null) {
            return buildResponse(400, "BAD_REQUEST", "TelematikId is not registered or does not exist")
        }
        apiKeyAspect.checkUserApiKey(storageMeta)
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

    private fun getBucketName(dataType: String): String {
        return if (dataType.uppercase() == "TEAM_BILD") {
            privateBucket
        } else {
            publicBucket
        }

    }

    private fun buildResponse(statusCode: Int, status: String, message: String): ResponseEntity<JsonNode> {
        val jsonNode = JsonNodeFactory.instance.objectNode()
        jsonNode.put("status", status)
        jsonNode.put("statusCode", statusCode)
        jsonNode.put("message", message)
        return ResponseEntity.status(statusCode).body(jsonNode)
    }

    private fun generateRandomId(length: Int = 6): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

    private fun checkIfUpdated(updatedAt: LocalDateTime, clientTimeStamp: LocalDateTime): Boolean {
        return updatedAt.isAfter(clientTimeStamp)
    }

    private fun getClientTimeStamp(date: String?): LocalDateTime {
        if (date != null) {
            val res = LocalDateTime.parse(date, formatter)
            return res
        }
        return LocalDateTime.MIN
    }

    private fun generateSignedUrl(actorId: String, contentType: String, dataType: String): ResponseEntity<JsonNode> {
        val arrayNode = jacksonObjectMapper.createArrayNode()
        val objectName = "pharmacy/$actorId/$dataType"
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
}