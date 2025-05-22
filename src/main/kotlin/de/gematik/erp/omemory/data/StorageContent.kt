package de.gematik.erp.omemory.data

import jakarta.persistence.*

@Entity
@Table(name = "storageInfos")
data class StorageContent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column
    val storageName: String = "",
    @Lob
    //@Column(columnDefinition = "jsonb")
    val storageContent: String = ""
)
