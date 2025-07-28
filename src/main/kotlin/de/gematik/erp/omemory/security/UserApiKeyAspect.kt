package de.gematik.erp.omemory.security

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
    @Value("\${X-GLOBAL_ACCESS_TOKEN}") private val globalApiKey: String
) {
    @Before("@annotation(RequireGlobalApiKey)")
    fun checkGlobalApiKey() {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        val apiKey = request.getHeader("X-GLOBAL-ACCESS-TOKEN")

        if (apiKey == null || apiKey != globalApiKey) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid global API key")
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
