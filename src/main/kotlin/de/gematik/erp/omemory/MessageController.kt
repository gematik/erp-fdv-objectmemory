package de.gematik.erp.omemory

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class MessageController {
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
}