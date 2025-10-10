package com.shibaprasadsahu.billingkit.internal

import android.util.Base64
import java.io.IOException
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec

private const val KEY_FACTORY_ALGORITHM = "RSA"
private const val SIGNATURE_ALGORITHM = "SHA1withRSA"

/**
 * Security utility for verifying Google Play purchase signatures
 * This helps prevent purchase tampering and fraud
 */
internal object Security {

    /**
     * Verifies that the purchase signature is valid
     *
     * @param base64PublicKey Base64-encoded public key from Google Play Console
     * @param signedData The signed JSON purchase data
     * @param signature The signature string
     * @return true if verification succeeds, false otherwise
     */
    fun verifyPurchase(
        base64PublicKey: String?,
        signedData: String,
        signature: String
    ): Boolean {
        // If no public key is provided, skip verification (user's choice)
        if (base64PublicKey.isNullOrEmpty()) {
            BillingLogger.warn("Purchase signature verification skipped - no public key configured")
            return true
        }

        if (signedData.isEmpty() || signature.isEmpty()) {
            BillingLogger.error("Purchase verification failed: missing signed data or signature")
            return false
        }

        return try {
            val publicKey = generatePublicKey(base64PublicKey)
            val isValid = verify(publicKey, signedData, signature)

            if (isValid) {
                BillingLogger.info("Purchase signature verified successfully")
            } else {
                BillingLogger.error("Purchase signature verification FAILED - possible tampering detected!")
            }

            isValid
        } catch (e: IOException) {
            BillingLogger.error("Error generating PublicKey from encoded key: ${e.message}")
            false
        } catch (e: Exception) {
            BillingLogger.error("Unexpected error during purchase verification: ${e.message}")
            false
        }
    }

    /**
     * Generates a PublicKey from Base64-encoded key string
     */
    @Throws(IOException::class)
    private fun generatePublicKey(encodedPublicKey: String): PublicKey {
        try {
            val decodedKey = Base64.decode(encodedPublicKey, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
            return keyFactory.generatePublic(X509EncodedKeySpec(decodedKey))
        } catch (e: NoSuchAlgorithmException) {
            // "RSA" is guaranteed to be available
            throw RuntimeException(e)
        } catch (e: InvalidKeySpecException) {
            val error = "Invalid Key Spec: ${e.message}"
            BillingLogger.error(error)
            throw IOException(error)
        }
    }

    /**
     * Verifies the signature using RSA/SHA1
     */
    private fun verify(publicKey: PublicKey, signedData: String, signature: String): Boolean {
        val signatureBytes: ByteArray = try {
            Base64.decode(signature, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            BillingLogger.error("Base64 decoding failed for signature")
            return false
        }

        try {
            val signatureAlgorithm = Signature.getInstance(SIGNATURE_ALGORITHM)
            signatureAlgorithm.initVerify(publicKey)
            signatureAlgorithm.update(signedData.toByteArray())

            return signatureAlgorithm.verify(signatureBytes)
        } catch (e: NoSuchAlgorithmException) {
            // "SHA1withRSA" should be available
            throw RuntimeException(e)
        } catch (e: InvalidKeyException) {
            BillingLogger.error("Invalid key specification: ${e.message}")
            return false
        } catch (e: SignatureException) {
            BillingLogger.error("Signature exception: ${e.message}")
            return false
        }
    }
}
