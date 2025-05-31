package de.gematik.erp.omemory.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface StorageMetaRepository: JpaRepository<StorageMeta, String> {
    fun findByStorageName(name: String): StorageMeta?
    @Query("SELECT s.structure FROM StorageMeta s WHERE s.storageName = :name")
    fun findStructureByName(name: String): String?

}