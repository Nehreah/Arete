package com.renea.arete_social.data.community

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class EpochKeyManagerTest {

    @Test
    fun testEpochKeyDistributionAndMessageEncryption() {
        val managerAdmin = EpochKeyManager()
        val managerMember = EpochKeyManager()

        val communityId = ByteArray(32) { 0x11 }
        val epochId = ByteArray(32) { 0x22 }
        val messageId = ByteArray(32) { 0x33 }

        // 1. Admin genera Ke para la época
        val ke = managerAdmin.generateEpochKey()
        managerAdmin.storeEpochKey(communityId, epochId, ke)

        // 2. Miembro genera su par de claves de dispositivo DK-a
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val memberDkaKeyPair = kpg.generateKeyPair()
        val memberIk = ByteArray(65) { 0x04 }

        // 3. Admin crea EpochKeyShare para el miembro
        val share = managerAdmin.createEpochKeyShare(
            communityId = communityId,
            epochId = epochId,
            ke = ke,
            recipientIk = memberIk,
            recipientDkaPublic = memberDkaKeyPair.public
        )

        // 4. Miembro recibe y descifra el share con su clave privada DK-a
        val recoveredKe = managerMember.decryptEpochKeyShare(share, memberDkaKeyPair.private)
        assertArrayEquals(ke, recoveredKe)

        // 5. Admin cifra un mensaje de comunidad
        val plaintext = "Mensaje confidencial de la comunidad Aréte".toByteArray(Charsets.UTF_8)
        val encrypted = managerAdmin.encryptCommunityMessage(communityId, epochId, messageId, plaintext)

        // 6. Miembro descifra el mensaje usando la Ke recuperada
        val decrypted = managerMember.decryptCommunityMessage(
            communityId = communityId,
            epochId = encrypted.epochId,
            messageId = messageId,
            iv = encrypted.iv,
            ciphertext = encrypted.ciphertext
        )
        assertArrayEquals(plaintext, decrypted)
        assertEquals("Mensaje confidencial de la comunidad Aréte", String(decrypted, Charsets.UTF_8))

        // 7. Un tercero sin la clave de época no puede descifrar
        val managerThirdParty = EpochKeyManager()
        assertThrows(IllegalStateException::class.java) {
            managerThirdParty.decryptCommunityMessage(
                communityId = communityId,
                epochId = encrypted.epochId,
                messageId = messageId,
                iv = encrypted.iv,
                ciphertext = encrypted.ciphertext
            )
        }
    }
}
