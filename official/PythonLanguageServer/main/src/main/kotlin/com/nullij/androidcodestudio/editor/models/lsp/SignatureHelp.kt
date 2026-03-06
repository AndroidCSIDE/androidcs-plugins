package com.nullij.androidcodestudio.editor.models.lsp

/**
 * Signature help represents the signature of something callable
 */
data class SignatureHelp(
    /** One or more signatures */
    val signatures: List<SignatureInformation>,
    
    /** The active signature. If omitted or index is out of range, defaults to zero */
    val activeSignature: Int = 0,
    
    /** The active parameter of the active signature */
    val activeParameter: Int = 0
) {
    /**
     * Get the currently active signature
     */
    fun getActiveSignatureInfo(): SignatureInformation? {
        return signatures.getOrNull(activeSignature)
    }

    /**
     * Get the currently active parameter
     */
    fun getActiveParameterInfo(): ParameterInformation? {
        return getActiveSignatureInfo()?.parameters?.getOrNull(activeParameter)
    }

    companion object {
        /**
         * Create signature help with a single signature
         */
        fun single(
            label: String,
            parameters: List<ParameterInformation> = emptyList(),
            documentation: String? = null,
            activeParameter: Int = 0
        ): SignatureHelp {
            return SignatureHelp(
                signatures = listOf(
                    SignatureInformation(
                        label = label,
                        documentation = documentation,
                        parameters = parameters
                    )
                ),
                activeParameter = activeParameter
            )
        }
    }
}

/**
 * Represents the signature of something callable
 */
data class SignatureInformation(
    /** The label of this signature */
    val label: String,
    
    /** The human-readable doc-comment of this signature */
    val documentation: String? = null,
    
    /** The parameters of this signature */
    val parameters: List<ParameterInformation> = emptyList(),
    
    /** The index of the active parameter */
    val activeParameter: Int? = null
) {
    /**
     * Get display text
     */
    fun getDisplayText(): String {
        return buildString {
            append(label)
            if (documentation != null) {
                append("\n")
                append(documentation)
            }
        }
    }
}

/**
 * Represents a parameter of a callable-signature
 */
data class ParameterInformation(
    /** The label of this parameter */
    val label: String,
    
    /** The human-readable doc-comment of this parameter */
    val documentation: String? = null
) {
    /**
     * Get display text
     */
    fun getDisplayText(): String {
        return if (documentation != null) {
            "$label: $documentation"
        } else {
            label
        }
    }

    companion object {
        /**
         * Create a parameter with name and type
         */
        fun create(name: String, type: String, documentation: String? = null): ParameterInformation {
            return ParameterInformation(
                label = "$name: $type",
                documentation = documentation
            )
        }
    }
}
