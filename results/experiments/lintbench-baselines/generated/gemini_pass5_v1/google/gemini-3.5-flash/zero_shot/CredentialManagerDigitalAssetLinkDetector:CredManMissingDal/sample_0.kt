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
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Element

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.XmlScanner, Detector.SourceCodeScanner {

    private var usesCredentialManager = false
    private var hasAssetStatements = false
    private var manifestLocation: Location? = null

    override fun beforeCheckProject(context: Context) {
        usesCredentialManager = false
        hasAssetStatements = false
        manifestLocation = null
    }

    // --- XmlScanner implementation ---

    override fun getApplicableElements(): Collection<String> {
        return listOf("meta-data", "application")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "application") {
            manifestLocation = context.getNameLocation(element)
        } else if (element.tagName == "meta-data") {
            val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                .ifEmpty { element.getAttribute("android:name") }
            if (name == "asset_statements") {
                hasAssetStatements = true
            }
        }
    }

    // --- SourceCodeScanner implementation ---

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("GetPasswordOption", "CreatePasswordRequest")
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        val fqName = (referenced as? PsiClass)?.qualifiedName
        if (fqName == "androidx.credentials.GetPasswordOption" ||
            fqName == "androidx.credentials.CreatePasswordRequest") {
            usesCredentialManager = true
        }
    }

    // --- Reporting ---

    override fun afterCheckProject(context: Context) {
        if (usesCredentialManager && !hasAssetStatements) {
            val location = manifestLocation ?: Location.create(context.project.dir)
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Link declaration in AndroidManifest.xml"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements string resource file \
                that includes the `assetlinks.json` files to load must be declared in the manifest using a \
                `<meta-data>` element.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_AND_RESOURCE_FILES
            )
        )
    }
}