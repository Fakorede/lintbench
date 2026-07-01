package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.JavaEvaluator
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isString
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression

private const val PROVIDER_NAME = "BC"
private const val GET_INSTANCE = "getInstance"
private const val ADD_PROVIDER = "addProvider"
private const val BC_PROVIDER_CLASS = "org.bouncycastle.jce.provider.BouncyCastleProvider"

private val CRYPTO_CLASSES = listOf(
    "javax.crypto.Cipher",
    "javax.crypto.KeyGenerator",
    "javax.crypto.Mac",
    "javax.crypto.SecretKeyFactory",
    "java.security.KeyPairGenerator",
    "java.security.KeyStore",
    "java.security.MessageDigest",
    "java.security.Signature"
)

private const val MESSAGE =
    "The BouncyCastle (BC) provider is deprecated and will not be available when targetSdkVersion is Android P or higher."

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableCallNames(): List<String> = listOf(GET_INSTANCE, ADD_PROVIDER)

    override fun visitMethodCall(context: JavaContext, call: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            GET_INSTANCE -> {
                val containingClass = method.containingClass?.qualifiedName ?: return
                if (containingClass !in CRYPTO_CLASSES) return

                val providerArg = call.valueArguments.getOrNull(1) ?: return
                if (providerArg.referencesBouncyCastle(evaluator)) {
                    report(context, call)
                }
            }
            ADD_PROVIDER -> {
                if (!evaluator.isMemberInClass(method, "java.security.Security")) return

                val providerArg = call.valueArguments.getOrNull(0) ?: return
                if (providerArg.referencesBouncyCastle(evaluator)) {
                    report(context, call)
                }
            }
        }
    }

    private fun report(context: JavaContext, call: UCallExpression) {
        context.report(
            ISSUE,
            call,
            context.getCallLocation(call, includeReceiver = false, includeArguments = true),
            MESSAGE
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The BouncyCastle (BC) provider has been deprecated on Android and will not be
                available when targetSdkVersion is Android P (API 28) or higher. Use a standard
                built-in provider (for example, the default Android provider) instead.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html"
        )
    }
}

private fun UExpression.referencesBouncyCastle(evaluator: JavaEvaluator): Boolean {
    if (this is ULiteralExpression && isString() && value == PROVIDER_NAME) {
        return true
    }

    val expressionType = getExpressionType() as? PsiClassType ?: return false
    val psiClass = expressionType.resolve() ?: return false

    return evaluator.extendsClass(psiClass, BC_PROVIDER_CLASS, false)
}