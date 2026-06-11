package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import java.util.*

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        val USE_ECB_MODE = Issue.create(
            "UseOfECBMode",
            "Cipher.getInstance should not be called with ECB as the cipher mode or without setting the cipher mode because the default mode on Android is ECB, which is insecure.",
            "Using ECB (Electronic Codebook) mode for encryption is considered insecure due to its deterministic nature. It's recommended to use more secure modes like CBC (Cipher Block Chaining), CTR (Counter Mode), GCM (Galois/Counter Mode), etc.",
            Severity.ERROR,
            Category.SECURITY,
            Implementation(
                CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val cipherClass = "javax.crypto.Cipher"
        if (method.containingClass?.qualifiedName != cipherClass) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            context.report(
                USE_ECB_MODE,
                node,
                context.getLocation(node),
                "Cipher.getInstance should not be called without setting the cipher mode."
            )
            return
        }

        for (arg in arguments) {
            if (arg is UastLiteralExpression && arg.value is String) {
                val argumentValue = arg.value as String
                if (argumentValue.contains("ECB", ignoreCase = true)) {
                    context.report(
                        USE_ECB_MODE,
                        node,
                        context.getLocation(node),
                        "Cipher.getInstance should not be called with ECB as the cipher mode."
                    )
                    return
                }
            }
        }
    }

    private val UastLiteralExpression.value: Any?
        get() = when (this) {
            is UastStringLiteralExpression -> stringValue
            else -> null
        }
}