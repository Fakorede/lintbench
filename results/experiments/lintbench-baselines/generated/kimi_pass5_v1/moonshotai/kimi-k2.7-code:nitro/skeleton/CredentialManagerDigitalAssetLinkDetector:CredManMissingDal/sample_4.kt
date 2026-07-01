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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ASSET_STATEMENTS = "asset_statements"
        private const val HAS_CREDENTIAL_MANAGER_REQUEST = "hasCredentialManagerRequest"
        private val ASSET_LINKS_PATTERN =
            Regex("""<meta-data[^>]*android:name\s*=\s*["']$ASSET_STATEMENTS["']""")

        private val IMPLEMENTATION = Implementation(
            CredentialManagerDigitalAssetLinkDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, you must declare
                an asset statements string resource in the manifest using a `<meta-data>`
                element with `android:name="asset_statements"`. This is required to load
                the `assetlinks.json` file for Digital Asset Links verification.
                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? = listOf(
        "androidx.credentials.CreatePasswordRequest",
        "androidx.credentials.GetPasswordOption",
    )

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        context.partialResults.put(HAS_CREDENTIAL_MANAGER_REQUEST, true)
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        if (partialResults.getBoolean(HAS_CREDENTIAL_MANAGER_REQUEST, false)) {
            context.partialResults.put(HAS_CREDENTIAL_MANAGER_REQUEST, true)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (!context.partialResults.getBoolean(HAS_CREDENTIAL_MANAGER_REQUEST, false)) {
            return
        }

        val manifestFiles = context.mainProject.manifestFiles
        val hasAssetStatements = manifestFiles.any { file ->
            try {
                ASSET_LINKS_PATTERN.containsMatchIn(file.readText())
            } catch (_: Exception) {
                false
            }
        }

        if (!hasAssetStatements) {
            val manifest = context.mainProject.manifest
            val location = manifest?.let { Location.create(it) } ?: Location.NONE
            context.report(
                issue = ISSUE,
                location = location,
                message = "Missing Digital Asset Link for Credential Manager. Add a `<meta-data android:name=\"$ASSET_STATEMENTS\" android:resource=\"@string/asset_statements\" />` element to the `<application>` element in AndroidManifest.xml.",
            )
        }
    }
}