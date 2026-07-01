package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.concurrent.atomic.AtomicBoolean

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            CredentialManagerDigitalAssetLinkDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = "When using password sign-in through Credential Manager, you must declare a <meta-data> element in your AndroidManifest.xml with android:name=\"asset_statements\" pointing to a string resource containing your Digital Asset Links configuration. This allows Credential Manager to verify your app's association with your website for secure password autofill.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private const val META_DATA_ASSET_STATEMENTS = "android:name=\"asset_statements\""
    }

    private val usesPasswordApi = AtomicBoolean(false)

    override fun getApplicableConstructorTypes(): List<String>? = listOf(
        "androidx.credentials.GetPasswordRequest",
        "androidx.credentials.GetCredentialRequest"
    )

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        val qualifiedName = constructor.containingClass?.qualifiedName ?: return
        if (qualifiedName == "androidx.credentials.GetPasswordRequest" ||
            qualifiedName == "androidx.credentials.GetCredentialRequest") {
            usesPasswordApi.set(true)
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // State is tracked via a thread-safe AtomicBoolean shared across the analysis run.
        // No explicit partial result merging is required for this detector.
    }

    override fun afterCheckRootProject(context: Context) {
        if (!usesPasswordApi.get()) return

        val manifest = context.project.getManifest() ?: return
        val content = try {
            manifest.readText()
        } catch (e: Exception) {
            return
        }

        if (!content.contains(META_DATA_ASSET_STATEMENTS)) {
            context.report(
                ISSUE,
                Location.create(manifest),
                "Missing Digital Asset Links configuration. When using Credential Manager for password sign-in, you must declare a `<meta-data>` element with `android:name=\"asset_statements\"` in your AndroidManifest.xml."
            )
        }
    }
}