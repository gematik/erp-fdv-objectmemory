package de.gematik.erp.omemory.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StorageUrlRepository : JpaRepository<StorageUrl, Long> {
    @Query("SELECT s.url FROM StorageUrl s WHERE s.storageMeta.id = :storageId AND s.dataType = :dataType")
    fun findStorageUrl(@Param("storageId") storageId: Long, @Param ("dataType") dataType: String): String?
}