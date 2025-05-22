package de.gematik.erp.omemory.data

import org.springframework.data.jpa.repository.JpaRepository

interface StorageContentRepository : JpaRepository<StorageContent, Long> {
    fun findByStorageName(name: String): StorageContent
}