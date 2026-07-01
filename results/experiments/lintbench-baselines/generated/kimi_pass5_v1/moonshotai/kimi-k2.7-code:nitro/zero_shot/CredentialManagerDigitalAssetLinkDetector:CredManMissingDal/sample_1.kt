package com.android.tools.lint.checks

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
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

private const val FQCN_CREDENTIAL_MANAGER = "androidx.credentials.CredentialManager"
private const val META_DATA_NAME = "assetlinks"
private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    private var hasCredentialManagerUsage = false
    private var hasDalMetaData = false
    private var manifestLocation: Location? = null

    override fun beforeCheckProject(context: Context) {
        hasCredentialManagerUsage = false
        hasDalMetaData = false
        manifestLocation = null
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getCredential")

    override fun visitMethodCall(context: JavaContext, call: UCallExpression, method: PsiMethod) {
        if (method.containingClass?.qualifiedName == FQCN_CREDENTIAL_MANAGER) {
            hasCredentialManagerUsage = true
        }
    }

    override fun getApplicableElements(): Collection<String> = listOf("manifest", "meta-data")

    override fun visitElement(context: XmlContext, element: Element) {
        if (manifestLocation == null && element.tagName == "manifest") {
            manifestLocation = context.getLocation(element)
            return
        }

        if (element.tagName != "meta-data") {
            return
        }

        val parent = element.parentNode
        if (parent == null || parent.nodeName != "application") {
            return
        }

        val name = element.getAttributeNS(ANDROID_URI, "name")
        val resource = element.getAttributeNS(ANDROID_URI, "resource")
        if (name == META_DATA_NAME && resource.startsWith("@string/")) {
            hasDalMetaData = true
        }
    }

    override fun afterCheckProject(context: Context) {
        if (hasCredentialManagerUsage && !hasDalMetaData && manifestLocation != null) {
            context.report(
                ISSUE,
                manifestLocation!!,
                ISSUE.getBriefDescription(TextFormat.TEXT)
            )
        }
    }

    companion object {
        private const val ISSUE_ID = "CredManMissingDal"
        private const val DESCRIPTION = "Missing Digital Asset Link for Credential Manager"
        private const val EXPLANATION = """
            When using password sign-in through Credential Manager, a Digital Asset Link
            must be declared in the manifest as a <meta-data> element that points to a
            string resource containing the assetlinks.json contents.

            Please add a <meta-data android:name="assetlinks" android:resource="@string/assetlinks" />
            element inside the <application> tag of your AndroidManifest.xml.

            See the documentation for more details.
        """
        private const val REF_URL =
            "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"

        @JvmField
        val ISSUE: Issue = Issue.create(
            ISSUE_ID,
            DESCRIPTION,
            EXPLANATION,
            Category.SECURITY,
            9,
            Severity.WARNING,
            Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE
            ),
            REF_URL
        )
    }
}