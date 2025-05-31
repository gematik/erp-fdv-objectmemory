package de.gematik.erp.omemory.data

import jakarta.persistence.*

@Entity
@Table(name = "storage_meta")
data class StorageMeta(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "storage_id")
    val id: Long = 0,

    @Column(name = "storage_name")
    val storageName: String = "",

    @Column
    val structure: List<String> = emptyList(),
)


