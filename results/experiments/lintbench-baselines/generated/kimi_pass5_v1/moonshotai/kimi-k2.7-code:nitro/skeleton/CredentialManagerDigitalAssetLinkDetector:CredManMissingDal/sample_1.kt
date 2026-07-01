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
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val GET_PASSWORD_REQUEST = "androidx.credentials.GetPasswordRequest"
        private const val ASSET_STATEMENTS = "asset_statements"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private const val KEY_USES_PASSWORD_CREDENTIAL_MANAGER =
            "uses.password.credential.manager"
        private const val KEY_HAS_ASSET_STATEMENTS = "has.asset.statements"

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
                When using password sign-in through Credential Manager you must declare a \
                `<meta-data android:name="asset_statements" android:resource="@string/..." />` \
                element in the application manifest. This tells the system where to load the \
                Digital Asset Link statements (assetlinks.json) that associate your app with \
                your domain.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(GET_PASSWORD_REQUEST)
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        context.getPartialResults(ISSUE)
            .setGlobalData(KEY_USES_PASSWORD_CREDENTIAL_MANAGER, true)
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("meta-data")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val name = element.getAttributeNS(ANDROID_URI, "name").takeIf { it.isNotEmpty() }
            ?: element.getAttribute("android:name").takeIf { it.isNotEmpty() }
            ?: element.getAttribute("name")

        if (name == ASSET_STATEMENTS) {
            context.getPartialResults(ISSUE).setGlobalData(KEY_HAS_ASSET_STATEMENTS, true)
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Aggregated decision is made in afterCheckRootProject.
    }

    override fun afterCheckRootProject(context: Context) {
        val partialResults = context.getPartialResults(ISSUE)
        val usesPasswordCredentialManager = partialResults.getGlobalData(
            KEY_USES_PASSWORD_CREDENTIAL_MANAGER,
            Boolean::class.java,
        ) ?: false
        val hasAssetStatements = partialResults.getGlobalData(
            KEY_HAS_ASSET_STATEMENTS,
            Boolean::class.java,
        ) ?: false

        if (usesPasswordCredentialManager && !hasAssetStatements) {
            val message = "Missing Digital Asset Link for Credential Manager. " +
                "Add `<meta-data android:name=\"asset_statements\" " +
                "android:resource=\"@string/asset_statements\" />` to the manifest."

            val location = context.project.manifestFiles.firstOrNull()?.let { Location.create(it) }
                ?: Location.create(context.file)

            context.report(ISSUE, location, message)
        }
    }
}