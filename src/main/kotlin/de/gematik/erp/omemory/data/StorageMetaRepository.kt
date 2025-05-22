package de.gematik.erp.omemory.data

import org.springframework.data.jpa.repository.JpaRepository

interface StorageMetaRepository: JpaRepository<StorageMeta, String> {
    fun findByStorageName(name: String): StorageMeta
}