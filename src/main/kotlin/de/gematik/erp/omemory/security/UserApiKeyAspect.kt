package de.gematik.erp.omemory.security

import de.gematik.erp.omemory.data.StorageMetaRepository
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
class UserApiKeyAspect(
    @Value("\${GLOBAL_API_KEY}") private val globalApiKey: String,
    private val storageMetaRepo: StorageMetaRepository
) {
    @Before("@annotation(RequireGlobalApiKey)")
    fun checkGlobalApiKey() {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        val apiKey = request.getHeader("X-GLOBAL-API-KEY")

        if (apiKey == null || apiKey != globalApiKey) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid global API key")
        }
    }

    @Before("@annotation(RequireUserApiKey)")
    fun checkUserApiKey() {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        val apiKey = request.getHeader("X-USER-API-KEY")
        val telematikId = request.getParameter("telematikId")

        if(telematikId == null){
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "telematikId is required")
        }
        val userApiKey = storageMetaRepo.findByTelematikId(telematikId)?.accessToken

        if (apiKey == null || apiKey != userApiKey) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid User API key")
        }
    }
}
@ControllerAdvice
class GlobalExceptionHandler{
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
