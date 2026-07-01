package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.XmlScanner, Detector.SourceCodeScanner {

    private var hasAssetStatements = false
    private var hasPasswordCredentialUsage = false
    private val passwordUsageLocations = mutableListOf<Location>()

    override fun beforeCheckEachProject(context: Context) {
        hasAssetStatements = false
        hasPasswordCredentialUsage = false
        passwordUsageLocations.clear()
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(SdkConstants.TAG_META_DATA)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == SdkConstants.TAG_META_DATA) {
            val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == "asset_statements") {
                hasAssetStatements = true
            }
        }
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "androidx.credentials.CreatePasswordRequest",
            "androidx.credentials.GetPasswordOption"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        hasPasswordCredentialUsage = true
        passwordUsageLocations.add(context.getLocation(node))
    }

    override fun afterCheckEachProject(context: Context) {
        if (hasPasswordCredentialUsage && !hasAssetStatements) {
            val location = passwordUsageLocations.firstOrNull() ?: Location.create(context.file)
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Link declaration in AndroidManifest.xml for Credential Manager password integration"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = "When using password sign-in through Credential Manager, an asset statements string resource file that includes the assetlinks.json files to load must be declared in the manifest using a <meta-data> element.",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }
}