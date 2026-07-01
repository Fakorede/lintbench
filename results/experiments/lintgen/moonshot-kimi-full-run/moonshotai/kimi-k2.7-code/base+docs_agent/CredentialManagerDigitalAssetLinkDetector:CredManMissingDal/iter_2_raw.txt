package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Detector.XmlScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements string
                resource file that includes the assetlinks.json files to load must be declared in the
                manifest using a `<meta-data android:name="asset_statements" android:resource="@string/..." />`
                element.

                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
            )
        )

        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val ASSET_STATEMENTS = "asset_statements"

        private const val GET_PASSWORD_OPTION = "androidx.credentials.GetPasswordOption"
        private const val CREATE_PASSWORD_REQUEST = "androidx.credentials.CreatePasswordRequest"
    }

    private var credentialManagerUsage: Location? = null
    private var foundDalMetaData = false

    override fun beforeCheckRootProject(context: Context) {
        credentialManagerUsage = null
        foundDalMetaData = false
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(GET_PASSWORD_OPTION, CREATE_PASSWORD_REQUEST)
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        credentialManagerUsage = context.getLocation(node)
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("meta-data")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_NS, "name")
        if (name != ASSET_STATEMENTS) {
            return
        }

        val resource = element.getAttributeNS(ANDROID_NS, "resource")
        if (resource.isNotBlank() && resource.startsWith("@string/")) {
            foundDalMetaData = true
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val usage = credentialManagerUsage ?: return
        if (!foundDalMetaData) {
            context.report(
                ISSUE,
                usage,
                "Missing Digital Asset Link declaration: add a <meta-data android:name=\"asset_statements\" android:resource=\"@string/...\" /> element to AndroidManifest.xml"
            )
        }
    }
}