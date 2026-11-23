package com.renea.arete_social.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renea.arete_social.data.local.room.entity.PeerBitacoraEntity
import com.renea.arete_social.data.local.room.entity.PeerGustosEntity
import com.renea.arete_social.data.local.room.entity.PeerLibreriaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerSectionDao {

    // --- Gustos ---
    @Query("SELECT * FROM peer_gustos WHERE usuario_id = :usuarioId LIMIT 1")
    suspend fun getGustos(usuarioId: String): PeerGustosEntity?

    @Query("SELECT * FROM peer_gustos WHERE usuario_id = :usuarioId LIMIT 1")
    fun observeGustos(usuarioId: String): Flow<PeerGustosEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateGustos(gustos: PeerGustosEntity)

    // --- Bitácora ---
    @Query("SELECT * FROM peer_bitacora WHERE usuario_id = :usuarioId ORDER BY fecha DESC")
    suspend fun getBitacora(usuarioId: String): List<PeerBitacoraEntity>

    @Query("SELECT * FROM peer_bitacora WHERE usuario_id = :usuarioId ORDER BY fecha DESC")
    fun observeBitacora(usuarioId: String): Flow<List<PeerBitacoraEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBitacora(estados: List<PeerBitacoraEntity>)

    @Query("DELETE FROM peer_bitacora WHERE usuario_id = :usuarioId")
    suspend fun clearBitacora(usuarioId: String)

    // --- Librería ---
    @Query("SELECT * FROM peer_libreria WHERE usuario_id = :usuarioId ORDER BY actualizado_en DESC")
    suspend fun getLibreria(usuarioId: String): List<PeerLibreriaEntity>

    @Query("SELECT * FROM peer_libreria WHERE usuario_id = :usuarioId ORDER BY actualizado_en DESC")
    fun observeLibreria(usuarioId: String): Flow<List<PeerLibreriaEntity>>

    @Query("SELECT * FROM peer_libreria WHERE obra_id = :obraId LIMIT 1")
    suspend fun getObra(obraId: String): PeerLibreriaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLibreria(obras: List<PeerLibreriaEntity>)

    @Query("UPDATE peer_libreria SET contenido = :contenido WHERE obra_id = :obraId")
    suspend fun updateContenidoObra(obraId: String, contenido: String)

    // --- Limpieza general ---
    @Query("DELETE FROM peer_gustos WHERE usuario_id = :usuarioId")
    suspend fun deleteGustos(usuarioId: String)

    @Query("DELETE FROM peer_libreria WHERE usuario_id = :usuarioId")
    suspend fun deleteLibreria(usuarioId: String)
}
