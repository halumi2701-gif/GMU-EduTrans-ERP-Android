package site.garsyanimultiusaha.gawone.mitra

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal sealed interface Stage4GDeepLinkTarget {
    data class Offer(val offerId: String) : Stage4GDeepLinkTarget
    data class Assignment(val assignmentId: String) : Stage4GDeepLinkTarget
    data class Order(val orderId: String) : Stage4GDeepLinkTarget
    data class Chat(val orderId: String) : Stage4GDeepLinkTarget
    data object Wallet : Stage4GDeepLinkTarget
    data object Kyc : Stage4GDeepLinkTarget
    data object Verification : Stage4GDeepLinkTarget
}

internal object Stage4GDeepLinkRouter {
    private val mutableTarget = MutableStateFlow<Stage4GDeepLinkTarget?>(null)
    val target: StateFlow<Stage4GDeepLinkTarget?> = mutableTarget

    fun accept(uri: Uri?) {
        if (uri == null || uri.scheme != "gawone" || uri.host != "mitra") return
        val parts = uri.pathSegments
        val parsed = when {
            parts.size >= 2 && parts[0] == "offer" -> Stage4GDeepLinkTarget.Offer(parts[1])
            parts.size >= 2 && parts[0] == "assignment" -> Stage4GDeepLinkTarget.Assignment(parts[1])
            parts.size >= 2 && parts[0] == "order" -> Stage4GDeepLinkTarget.Order(parts[1])
            parts.size >= 2 && parts[0] == "chat" -> Stage4GDeepLinkTarget.Chat(parts[1])
            parts.firstOrNull() == "wallet" -> Stage4GDeepLinkTarget.Wallet
            parts.firstOrNull() == "kyc" -> Stage4GDeepLinkTarget.Kyc
            parts.firstOrNull() == "verification" -> Stage4GDeepLinkTarget.Verification
            else -> null
        }
        if (parsed != null) mutableTarget.value = parsed
    }

    fun consume(expected: Stage4GDeepLinkTarget) {
        if (mutableTarget.value == expected) mutableTarget.value = null
    }
}
