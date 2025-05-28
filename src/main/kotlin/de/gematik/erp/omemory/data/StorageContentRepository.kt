package de.gematik.erp.omemory.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface StorageContentRepository : JpaRepository<StorageContent, Long> {
@Query("SELECT s.storageContent FROM StorageContent s WHERE s.storageMeta.storageName = :name")
    fun findByStorageName(name: String): List<String>
}