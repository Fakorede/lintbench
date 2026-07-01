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
            explanation = "When using password sign-in through Credential Manager, an asset statements string resource file that includes the assetlinks.json files to load must be declared in the manifest using a <meta-data> element.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    private var usesPasswordOption = false

    override fun beforeCheckRootProject(context: Context) {
        usesPasswordOption = false
    }

    override fun getApplicableConstructorTypes(): List<String>? =
        listOf("androidx.credentials.GetPasswordOption")

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        usesPasswordOption = true
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // No-op: state is tracked globally and verified in afterCheckRootProject
    }

    override fun afterCheckRootProject(context: Context) {
        if (!usesPasswordOption) return

        val manifest = context.mainProject.manifest ?: context.project.manifest
        if (manifest == null || !manifest.exists()) return

        val content = manifest.readText()
        val hasAssetStatements = content.contains("android:name=\"asset_statements\"") ||
            content.contains("android:name='asset_statements'")

        if (!hasAssetStatements) {
            context.report(
                ISSUE,
                Location.create(manifest),
                "Missing Digital Asset Link configuration. When using Credential Manager for password sign-in, you must declare a `<meta-data>` element with `android:name=\"asset_statements\"` in the manifest."
            )
        }
    }
}