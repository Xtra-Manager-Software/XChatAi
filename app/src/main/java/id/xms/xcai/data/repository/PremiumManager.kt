package id.xms.xcai.data.repository

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class PremiumFeatures(
    val unlimitedMessages: Boolean = false,
    val prioritySupport: Boolean = false,
    val customModels: Boolean = false,
    val exportPDF: Boolean = false,
    val advancedAnalytics: Boolean = false
)

data class PremiumStatus(
    val isPremium: Boolean = false,
    val tier: String = "free",
    val maxRequests: Int = 20,
    val features: PremiumFeatures = PremiumFeatures(),
    val expiresAt: Long? = null
)

class PremiumManager {

    private val database = FirebaseDatabase.getInstance()
    private val premiumRef = database.getReference("premium_users")

    companion object {
        private const val TAG = "PremiumManager"
    }

    suspend fun checkPremiumStatus(userEmail: String): PremiumStatus {
        return try {
            val sanitizedEmail = userEmail.replace(".", ",")
            val snapshot = premiumRef.child(sanitizedEmail).get().await()

            if (snapshot.exists()) {
                val tier = snapshot.child("tier").getValue(String::class.java) ?: "free"
                val maxRequests = snapshot.child("maxRequests").getValue(Int::class.java) ?: 20
                val expiresAt = snapshot.child("expiresAt").getValue(Long::class.java)

                val isExpired = expiresAt != null && expiresAt < System.currentTimeMillis()

                if (isExpired) {
                    Log.d(TAG, "Premium subscription expired for $userEmail")
                    return PremiumStatus()
                }

                val featuresSnapshot = snapshot.child("features")
                val features = PremiumFeatures(
                    unlimitedMessages = featuresSnapshot.child("unlimitedMessages").getValue(Boolean::class.java) ?: false,
                    prioritySupport = featuresSnapshot.child("prioritySupport").getValue(Boolean::class.java) ?: false,
                    customModels = featuresSnapshot.child("customModels").getValue(Boolean::class.java) ?: false,
                    exportPDF = featuresSnapshot.child("exportPDF").getValue(Boolean::class.java) ?: false,
                    advancedAnalytics = featuresSnapshot.child("advancedAnalytics").getValue(Boolean::class.java) ?: false
                )

                Log.d(TAG, "User $userEmail is $tier with max $maxRequests requests")

                PremiumStatus(
                    isPremium = true,
                    tier = tier,
                    maxRequests = maxRequests,
                    features = features,
                    expiresAt = expiresAt
                )
            } else {
                Log.d(TAG, "User $userEmail is free tier")
                PremiumStatus()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking premium status: ${e.message}")
            PremiumStatus()
        }
    }

    fun observePremiumStatus(userEmail: String): Flow<PremiumStatus> = callbackFlow {
        val sanitizedEmail = userEmail.replace(".", ",")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val tier = snapshot.child("tier").getValue(String::class.java) ?: "free"
                    val maxRequests = snapshot.child("maxRequests").getValue(Int::class.java) ?: 20
                    val expiresAt = snapshot.child("expiresAt").getValue(Long::class.java)

                    val isExpired = expiresAt != null && expiresAt < System.currentTimeMillis()

                    if (isExpired) {
                        trySend(PremiumStatus())
                    } else {
                        val featuresSnapshot = snapshot.child("features")
                        val features = PremiumFeatures(
                            unlimitedMessages = featuresSnapshot.child("unlimitedMessages").getValue(Boolean::class.java) ?: false,
                            prioritySupport = featuresSnapshot.child("prioritySupport").getValue(Boolean::class.java) ?: false,
                            customModels = featuresSnapshot.child("customModels").getValue(Boolean::class.java) ?: false,
                            exportPDF = featuresSnapshot.child("exportPDF").getValue(Boolean::class.java) ?: false,
                            advancedAnalytics = featuresSnapshot.child("advancedAnalytics").getValue(Boolean::class.java) ?: false
                        )

                        trySend(PremiumStatus(
                            isPremium = true,
                            tier = tier,
                            maxRequests = maxRequests,
                            features = features,
                            expiresAt = expiresAt
                        ))
                    }
                } else {
                    trySend(PremiumStatus())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Premium status listener cancelled: ${error.message}")
            }
        }

        premiumRef.child(sanitizedEmail).addValueEventListener(listener)

        awaitClose {
            premiumRef.child(sanitizedEmail).removeEventListener(listener)
        }
    }

    fun getMaxRequests(premiumStatus: PremiumStatus): Int {
        return when {
            premiumStatus.maxRequests == -1 -> Int.MAX_VALUE
            premiumStatus.isPremium -> premiumStatus.maxRequests
            else -> 20
        }
    }

    fun getPremiumBadge(premiumStatus: PremiumStatus): String {
        return when (premiumStatus.tier) {
            "premium_plus" -> "💎 Premium Plus"
            "premium" -> "⭐ Premium"
            else -> "🆓 Free"
        }
    }
}
