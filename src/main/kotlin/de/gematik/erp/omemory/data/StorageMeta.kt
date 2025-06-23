package de.gematik.erp.omemory.data

import jakarta.persistence.*

@Entity
@Table(name = "storage_meta")
data class StorageMeta(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long,

    @Column(name = "actor_id", unique = true)
    val actorId: String,

    @Column(name = "storage_name")
    val storageName: String,

    @Column(name = "telematik_id")
    val telematikId: String,

    @Column(unique = true)
    val accessToken: String
){
    // Required by JPA
    constructor() : this(0, "", "", "", "")
}


