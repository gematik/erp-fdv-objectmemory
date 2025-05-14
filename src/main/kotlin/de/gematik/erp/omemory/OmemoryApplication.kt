package de.gematik.erp.omemory

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class OmemoryApplication

fun main(args: Array<String>) {
    runApplication<OmemoryApplication>(*args)
}
