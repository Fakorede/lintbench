package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ManifestScanner
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, ManifestScanner {

    companion object {
        private const val PASSWORD_USAGE_KEY = "password_usage"
        private const val ASSET_STATEMENTS_KEY = "asset_statements"
        private const val ASSET_STATEMENTS_NAME = "asset_statements"
        private const val ANDROID_NAME = "android:name"
        private const val NAME = "name"
        private const val APPLICATION_TAG = "application"
        private const val META_DATA_TAG = "meta-data"

        private val IMPLEMENTATION = Implementation(
            CredentialManagerDigitalAssetLinkDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When signing in with passwords using Credential Manager, you must host a
                valid Digital Asset Links file (`assetlinks.json`) and declare it in the
                `<application>` section of `AndroidManifest.xml` with a `<meta-data>`
                element whose `android:name` is `asset_statements`. Without this entry
                password credential operations may fail.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf(
        "androidx.credentials.GetPasswordOption",
        "androidx.credentials.CreatePasswordRequest",
    )

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        context.getPartialResults(ISSUE).setGlobalString(PASSWORD_USAGE_KEY, "true")
    }

    override fun getApplicableElements(): List<String> = listOf(META_DATA_TAG)

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.parentNode?.localName != APPLICATION_TAG) {
            return
        }

        val name = element.getAttribute(ANDROID_NAME).ifEmpty { element.getAttribute(NAME) }
        if (name == ASSET_STATEMENTS_NAME) {
            context.getPartialResults(ISSUE).setGlobalString(ASSET_STATEMENTS_KEY, "true")
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // The final report is produced once the whole root project has been analyzed.
    }

    override fun afterCheckRootProject(context: Context) {
        val partialResults = context.getPartialResults(ISSUE)
        val usesPassword = partialResults.getGlobalString(PASSWORD_USAGE_KEY) == "true"
        val hasAssetStatements = partialResults.getGlobalString(ASSET_STATEMENTS_KEY) == "true"

        if (!usesPassword || hasAssetStatements) {
            return
        }

        val manifest = context.mainProject.mergedManifest
        val location = if (manifest != null) Location.create(manifest) else Location.create(context.project.dir)

        context.report(
            ISSUE,
            location,
            "Password sign-in with Credential Manager requires an `asset_statements` meta-data entry in AndroidManifest.xml",
        )
    }
}