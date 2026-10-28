package com.sentinel.quantum.security

internal object RecordingVaultNative {
    init {
        System.loadLibrary("recordingvault_r3")
    }

    external fun renameNoReplace(
        source: String,
        destination: String
    ): Int
}
