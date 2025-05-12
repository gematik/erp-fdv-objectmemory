package de.gematik.erp.omemory

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OmemoryApplication

fun main(args: Array<String>) {
	runApplication<OmemoryApplication>(*args)
}
