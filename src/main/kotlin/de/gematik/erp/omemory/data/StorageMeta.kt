package de.gematik.erp.omemory.data

import jakarta.persistence.*
import org.springframework.data.annotation.Reference

@Entity
@Table(name = "storages")
data class StorageMeta(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column
    @Reference
    val storageName: String = "",
    @Lob
    val structure: List<String> = emptyList()
)


