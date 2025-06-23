package de.gematik.erp.omemory.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "storage_urls")
data class StorageUrl(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @JoinColumn(name = "storage_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    val storageMeta: StorageMeta,

    @Column
    val url: String,

    @Column
    val dataType: String,

    @Column(name = "telematik_id")
    val telematikId: String

){
    // Required by JPA
    protected constructor() : this(0, StorageMeta(), "", "", "")
}