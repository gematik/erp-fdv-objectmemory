package de.gematik.erp.omemory.data

import jakarta.persistence.*

@Entity
@Table(name = "storages")
data class StorageContent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @Column
    val storageContent: String,

    @JoinColumn(name = "storage_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    val storageMeta: StorageMeta


){
    // Required by JPA
    protected constructor() : this(0, "", StorageMeta())
}
