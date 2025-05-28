package de.gematik.erp.omemory.data

import jakarta.persistence.*

@Entity
@Table(name = "storages")
data class StorageContent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @JoinColumn(name = "storage_id")
    @ManyToOne(fetch = FetchType.LAZY)
    val storageMeta: StorageMeta,

    //@Column(columnDefinition = "jsonb")
    @Lob
    val storageContent: String = "",
)
