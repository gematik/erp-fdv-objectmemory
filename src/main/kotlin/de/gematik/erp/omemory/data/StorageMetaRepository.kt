package de.gematik.erp.omemory.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.query.Param

interface StorageMetaRepository : JpaRepository<StorageMeta, Long> {
    fun findByStorageName(name: String): StorageMeta?
    fun findByTelematikId(@Param("telematikId") telematikId: String): StorageMeta?
}