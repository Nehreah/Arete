package com.renea.arete_social.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.renea.arete_social.data.local.room.entity.LocalIdentityEntity

@Dao
interface IdentityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveIdentity(identity: LocalIdentityEntity)

    @Query("SELECT * FROM local_identity WHERE id = 1")
    suspend fun getIdentity(): LocalIdentityEntity?

    @Query("DELETE FROM local_identity WHERE id = 1")
    suspend fun deleteIdentity()

    @Query("UPDATE local_identity SET device_epoch = :newEpoch WHERE id = 1")
    suspend fun incrementDeviceEpoch(newEpoch: Int)
}
