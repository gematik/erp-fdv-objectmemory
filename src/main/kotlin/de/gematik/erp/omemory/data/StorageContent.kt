package de.gematik.erp.omemory.data

import jakarta.persistence.*

@Entity
@Table(name = "storages")
data class StorageContent(

    @JoinColumn(name = "storage_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    val storageMeta: StorageMeta,

    val storageContent: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
