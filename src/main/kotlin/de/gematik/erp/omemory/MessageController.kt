package de.gematik.erp.omemory

import Message
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
    @GetMapping("erp/omem/structure")
    fun listPharmacyStructure(@RequestParam("storage") storage: String): List<String>{
        //TODO need to be able to dynamically choose the structure of the storage based on the user input
        val pharmaStorage = PharmacyStorage()
        return pharmaStorage.getStructure()
    }
    @GetMapping("erp/omem/storage")
    fun listPharmaciesOrOtherStorage(@RequestParam("name") nameOfTheStorage: String): List<Map<String, Any>>{
        //TODO need to be able to dynamically choose what storage is needed based on user input
        val pharmaStorage = PharmacyStorage()
        return pharmaStorage.getStorage()
    }
    @GetMapping("erp/omem/storage/{id}")
    fun listPharmacy(@PathVariable id: Int, @RequestParam("name") name: String): Map<String, Any>{
        //need to be able to dynamically choose what storage is needed based on user input
        val pharmaStorage = PharmacyStorage()
        return pharmaStorage.getStorage(id)
    }
    @GetMapping("erp/omem/storage/value/{id}")
    fun listPharmacyValue(@PathVariable id: Int, @RequestParam ("name") name: String): Map<String, Any?>{
        //need to be able to dynamically choose what storage is needed based on user input
        val pharmaStorage = PharmacyStorage()
        return pharmaStorage.getValue(id, name)
    }
}