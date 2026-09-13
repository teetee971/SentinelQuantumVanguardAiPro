data class ActivationResult(val success: Boolean, val orgId: String?, val message: String)

object OrganizationActivationManager {
    fun activateByCode(orgCode: String, paymentToken: String): ActivationResult {
        return if (orgCode.isNotBlank() && paymentToken.isNotBlank()) {
            ActivationResult(true, "ORG-${orgCode.take(6).uppercase()}", "Organisation activée avec succès.")
        } else {
            ActivationResult(false, null, "Code d'organisation ou jeton de paiement invalide.")
        }
    }
}
