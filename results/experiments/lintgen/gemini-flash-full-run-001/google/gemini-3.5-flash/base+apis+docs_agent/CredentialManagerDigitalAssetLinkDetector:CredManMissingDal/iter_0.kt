package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    private var hasAssetStatements = false
    private var passwordCredentialUsageLocation: Location? = null

    override fun beforeCheckEachProject(context: Context) {
        hasAssetStatements = false
        passwordCredentialUsageLocation = null
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "androidx.credentials.CreatePasswordRequest",
            "androidx.credentials.CreatePasswordRequest.Builder",
            "androidx.credentials.GetPasswordOption",
            "androidx.credentials.GetPasswordOption.Builder",
            "androidx.credentials.PasswordCredential"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (passwordCredentialUsageLocation == null) {
            passwordCredentialUsageLocation = context.getLocation(node)
        }
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("meta-data")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "meta-data") {
            val name = element.getAttributeNS(ANDROID_URI, "name")
            if (name == "asset_statements") {
                hasAssetStatements = true
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val location = passwordCredentialUsageLocation
        if (location != null && !hasAssetStatements) {
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Link declaration in AndroidManifest.xml for Credential Manager password sign-in"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements string \
                resource file that includes the `assetlinks.json` files to load must be declared \
                in the manifest using a `<meta-data>` element.
                """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
            )
        )
    }
}