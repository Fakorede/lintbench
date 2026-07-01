package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    private var usesCredentialManagerPassword = false
    private val missingDalApplications = mutableListOf<Pair<XmlContext, Element>>()

    override fun beforeCheckProject(context: Context) {
        usesCredentialManagerPassword = false
        missingDalApplications.clear()
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf(
        "androidx.credentials.GetPasswordOption",
        "androidx.credentials.CreatePasswordRequest",
        "androidx.credentials.PasswordCredential"
    )

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        usesCredentialManagerPassword = true
    }

    override fun getApplicableElements(): Collection<String> = listOf("application")

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != "application") return
        if (!context.file.name.equals("AndroidManifest.xml", ignoreCase = true)) return

        var hasDal = false
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == "meta-data") {
                val meta = child as Element
                val name = meta.getAttributeNS(ANDROID_URI, "name")
                val resource = meta.getAttributeNS(ANDROID_URI, "resource")
                if (name == ASSETLINKS_METADATA_NAME &&
                    resource.isNotBlank() &&
                    resource.startsWith("@string/")
                ) {
                    hasDal = true
                    break
                }
            }
        }

        if (!hasDal) {
            missingDalApplications.add(context to element)
        }
    }

    override fun afterCheckProject(context: Context) {
        if (!usesCredentialManagerPassword || missingDalApplications.isEmpty()) return
        for ((xmlContext, app) in missingDalApplications) {
            val location = xmlContext.getElementLocation(app)
            xmlContext.report(
                ISSUE,
                location,
                "Missing Digital Asset Link declaration for Credential Manager password sign-in"
            )
        }
    }

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ASSETLINKS_METADATA_NAME = "assetlinks"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, you must declare a Digital Asset Link in the manifest using a `<meta-data>` element with `android:name="assetlinks"` and `android:resource` pointing to a string resource that contains the asset statements (including the `assetlinks.json` files to load).
            """.trimIndent(),
            moreInfo = "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }
}