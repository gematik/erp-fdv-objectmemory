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

package de.gematik.erp.omemory.security

import de.gematik.erp.omemory.data.StorageMeta
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.server.ResponseStatusException


@Aspect
@Component
class ApiKeyAspect(
    @Value("\${X-GLOBAL-ACCESS-TOKEN}") private val globalApiKey: String
) {
    @Before("@annotation(RequireGlobalApiKey)")
    fun checkGlobalApiKey() {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        val apiKey = request.getHeader("X-GLOBAL-ACCESS-TOKEN")

        if (apiKey == null || apiKey != globalApiKey) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid global API key")
        }
    }

    fun checkUserApiKey(storageMeta: StorageMeta) {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        val apiKey = request.getHeader("X-USER-ACCESS-TOKEN")

        val userApiKey = storageMeta.accessToken
        if (apiKey != userApiKey || apiKey.isEmpty()) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid user API key")
        }

    }

}

@ControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        ex: ResponseStatusException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any>> {
        val body = mapOf(
            "status" to ex.statusCode.value(),
            "error" to ex.statusCode,
            "message" to (ex.reason ?: "Unexpected error"),
            "path" to request.requestURI
        )
        return ResponseEntity(body, ex.statusCode)
    }
}
