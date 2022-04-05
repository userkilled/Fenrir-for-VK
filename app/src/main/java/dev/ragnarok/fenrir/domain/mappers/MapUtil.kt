package dev.ragnarok.fenrir.domain.mappers

import dev.ragnarok.fenrir.api.model.VkApiConversation
import dev.ragnarok.fenrir.model.Conversation
import dev.ragnarok.fenrir.nonNullNoEmpty
import dev.ragnarok.fenrir.util.Utils

object MapUtil {
    fun calculateConversationAcl(conversation: VkApiConversation): Int {
        var result = 0
        if (conversation.settings != null && conversation.settings.acl != null) {
            val acl = conversation.settings.acl
            result =
                Utils.addFlagIf(result, Conversation.AclFlags.CAN_CHANGE_INFO, acl.can_change_info)
            result = Utils.addFlagIf(
                result,
                Conversation.AclFlags.CAN_CHANGE_INVITE_LINK,
                acl.can_change_invite_link
            )
            result =
                Utils.addFlagIf(result, Conversation.AclFlags.CAN_CHANGE_PIN, acl.can_change_pin)
            result = Utils.addFlagIf(result, Conversation.AclFlags.CAN_INVITE, acl.can_invite)
            result = Utils.addFlagIf(
                result,
                Conversation.AclFlags.CAN_PROMOTE_USERS,
                acl.can_promote_users
            )
            result = Utils.addFlagIf(
                result,
                Conversation.AclFlags.CAN_SEE_INVITE_LINK,
                acl.can_see_invite_link
            )
        }
        return result
    }

    inline fun <reified O, R> mapAndAdd(
        orig: Collection<O>?,
        function: (O) -> R,
        target: MutableCollection<R>
    ) {
        if (orig != null) {
            for (o in orig) {
                target.add(function.invoke(o))
            }
        }
    }

    inline fun <reified O, R> mapAllMutable(
        orig: Collection<O>?,
        function: (O) -> R
    ): MutableList<R> {
        return if (orig.nonNullNoEmpty()) {
            if (orig.size == 1) {
                return mutableListOf(function.invoke(orig.iterator().next()))
            }
            val list: MutableList<R> = ArrayList(orig.size)
            for (o in orig) {
                list.add(function.invoke(o))
            }
            return list
        } else {
            ArrayList(0)
        }
    }

    inline fun <reified O, R> mapAll(orig: Collection<O>?, function: (O) -> R): List<R> {
        return if (orig.nonNullNoEmpty()) {
            if (orig.size == 1) {
                return listOf(function.invoke(orig.iterator().next()))
            }
            val list: MutableList<R> = ArrayList(orig.size)
            for (o in orig) {
                list.add(function.invoke(o))
            }
            return list
        } else {
            emptyList()
        }
    }


    fun <O, R> mapAllJava(orig: Collection<O>?, function: (O) -> R): List<R> {
        return if (orig != null && orig.isNotEmpty()) {
            if (orig.size == 1) {
                return listOf(function.invoke(orig.iterator().next()))
            }
            val list: MutableList<R> = ArrayList(orig.size)
            for (o in orig) {
                list.add(function.invoke(o))
            }
            return list
        } else {
            emptyList()
        }
    }
}