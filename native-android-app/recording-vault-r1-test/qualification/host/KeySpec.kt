package android.security.keystore
import java.security.spec.AlgorithmParameterSpec
class KeyGenParameterSpec : AlgorithmParameterSpec {
    class Builder(val alias: String, val purposes: Int) {
        fun setBlockModes(vararg values: String) = this
        fun setEncryptionPaddings(vararg values: String) = this
        fun setKeySize(value: Int) = this
        fun build() = KeyGenParameterSpec()
    }
}
object KeyProperties {
    const val KEY_ALGORITHM_AES = "AES"
    const val PURPOSE_ENCRYPT = 1
    const val PURPOSE_DECRYPT = 2
    const val BLOCK_MODE_GCM = "GCM"
    const val ENCRYPTION_PADDING_NONE = "NoPadding"
}
