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
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    private var usesPasswordCredentials = false
    private var hasAssetStatementsMetadata = false
    private var applicationLocation: Location? = null

    override fun beforeCheckEachProject(context: Context) {
        usesPasswordCredentials = false
        hasAssetStatementsMetadata = false
        applicationLocation = null
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "androidx.credentials.GetPasswordOption",
            "androidx.credentials.CreatePasswordRequest",
            "androidx.credentials.PasswordCredential"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        usesPasswordCredentials = true
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("application", "meta-data")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "application" -> {
                applicationLocation = context.getNameLocation(element)
            }
            "meta-data" -> {
                val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                if (name == "asset_statements") {
                    hasAssetStatementsMetadata = true
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (usesPasswordCredentials && !hasAssetStatementsMetadata) {
            val location = applicationLocation ?: Location.create(context.project.dir)
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
                When using password sign-in through Credential Manager, you must associate your app \
                with a website by declaring a Digital Asset Link. This is done by adding a `<meta-data>` \
                element with `android:name="asset_statements"` pointing to a string resource containing \
                the asset statements in your `AndroidManifest.xml`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }
}