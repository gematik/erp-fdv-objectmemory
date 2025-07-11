package de.gematik.erp.omemory.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StorageUrlRepository : JpaRepository<StorageUrl, Long> {
    @Query("SELECT s FROM StorageUrl s WHERE s.dataType = :dataType")
    fun findByDataType(@Param("dataType") dataType: String): List<StorageUrl>?
    fun findByTelematikId(@Param("telematikId") telematikId: String): List<StorageUrl>?
    fun findByTelematikIdAndDataType(
        @Param("telematikId") telematikId: String,
        @Param("dataType") dataType: String
    ): StorageUrl?
    fun findByUrl(@Param("url") url: String): StorageUrl?
}