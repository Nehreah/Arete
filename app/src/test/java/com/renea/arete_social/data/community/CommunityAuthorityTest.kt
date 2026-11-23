package com.renea.arete_social.data.community

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityAuthorityTest {

    @Test
    fun testRoleHierarchyAndPermissions() {
        val owner = CommunityAuthority.Role.OWNER
        val leader = CommunityAuthority.Role.LEADER
        val admin = CommunityAuthority.Role.ADMIN
        val moderator = CommunityAuthority.Role.MODERATOR
        val member = CommunityAuthority.Role.MEMBER

        // 1. Jerarquía de modificación (rank menor = mayor poder)
        assertTrue(owner.canModify(leader))
        assertTrue(owner.canModify(admin))
        assertTrue(owner.canModify(member))

        assertTrue(leader.canModify(admin))
        assertTrue(leader.canModify(moderator))
        assertFalse(leader.canModify(leader))
        assertFalse(leader.canModify(owner))

        assertTrue(admin.canModify(member))
        assertFalse(admin.canModify(admin))
        assertFalse(admin.canModify(leader))
        assertFalse(admin.canModify(owner))

        assertFalse(member.canModify(member))

        // 2. Permisos específicos
        assertTrue(CommunityAuthority.hasPermission(owner, CommunityAuthority.Permission.TRANSFER_OWNERSHIP))
        assertFalse(CommunityAuthority.hasPermission(leader, CommunityAuthority.Permission.TRANSFER_OWNERSHIP))

        assertTrue(CommunityAuthority.hasPermission(admin, CommunityAuthority.Permission.EXPEL_MEMBER))
        assertTrue(CommunityAuthority.hasPermission(admin, CommunityAuthority.Permission.ROTATE_EPOCH))
        assertFalse(CommunityAuthority.hasPermission(moderator, CommunityAuthority.Permission.EXPEL_MEMBER))

        // 3. Validación de cambio de rol (prevención de escalada de privilegios)
        // El líder puede promover a un miembro a admin
        assertTrue(CommunityAuthority.validateRoleChange(leader, member, admin))
        // El líder NO puede promover a un miembro a líder o a dueño
        assertFalse(CommunityAuthority.validateRoleChange(leader, member, leader))
        assertFalse(CommunityAuthority.validateRoleChange(leader, member, owner))

        // El admin NO puede modificar al líder ni promover a nadie a líder
        assertFalse(CommunityAuthority.validateRoleChange(admin, leader, member))
        assertFalse(CommunityAuthority.validateRoleChange(admin, member, admin))
    }
}
