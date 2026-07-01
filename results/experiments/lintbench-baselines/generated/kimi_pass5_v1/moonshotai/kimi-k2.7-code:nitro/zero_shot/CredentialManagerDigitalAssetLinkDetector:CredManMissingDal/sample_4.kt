package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.w3c.dom.Element
import java.util.EnumSet

private const val EXPECTED_METADATA_NAME = "com.google.android.gms.credentials.asf_bit"

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.SourceCodeScanner, Detector.XmlScanner {

    private var usesPasswordCredentialManager = false
    private var hasDigitalAssetLink = false
    private var applicationLocation: Location? = null

    override fun getApplicableFiles(): EnumSet<Scope> =
        EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)

    override fun beforeCheckRootProject(context: Context) {
        usesPasswordCredentialManager = false
        hasDigitalAssetLink = false
        applicationLocation = null
    }

    override fun getApplicableUObjectTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val className = method.containingClass?.qualifiedName ?: return
                if (className.startsWith("androidx.credentials") && className.contains("Password")) {
                    usesPasswordCredentialManager = true
                }
            }
        }
    }

    override fun applicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_APPLICATION)

    override fun visitElement(context: XmlContext, element: Element) {
        if (applicationLocation == null) {
            applicationLocation = context.getElementLocation(element)
        }

        for (i in 0 until element.childNodes.length) {
            val child = element.childNodes.item(i) as? Element ?: continue
            if (child.tagName != SdkConstants.TAG_META_DATA) continue

            val name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            val resource = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_RESOURCE)
            if (name == EXPECTED_METADATA_NAME && resource.startsWith("@string/")) {
                hasDigitalAssetLink = true
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (usesPasswordCredentialManager && !hasDigitalAssetLink) {
            val location = applicationLocation ?: return
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Link declaration for Credential Manager password sign-in. " +
                    "Add a <meta-data> element with android:name=\"$EXPECTED_METADATA_NAME\" " +
                    "and android:resource=\"@string/...\" to the <application> tag."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, you must declare a
                Digital Asset Link string resource in the manifest using a <meta-data>
                element with android:name="com.google.android.gms.credentials.asf_bit"
                and android:resource="@string/...".
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            moreInfo = "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}