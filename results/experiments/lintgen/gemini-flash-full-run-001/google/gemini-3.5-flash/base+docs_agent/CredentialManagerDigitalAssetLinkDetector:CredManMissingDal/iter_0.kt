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
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    private val usages = mutableListOf<Usage>()
    private var hasAssetStatements = false

    private class Usage(val location: Location, val context: JavaContext)

    override fun beforeCheckEachProject(context: Context) {
        usages.clear()
        hasAssetStatements = false
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "androidx.credentials.GetPasswordOption",
            "androidx.credentials.CreatePasswordRequest"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        candidate: PsiMethod
    ) {
        usages.add(Usage(context.getLocation(node), context))
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("meta-data")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "meta-data") {
            val name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name")
            if (name == "asset_statements") {
                hasAssetStatements = true
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (usages.isNotEmpty() && !hasAssetStatements) {
            for (usage in usages) {
                usage.context.report(
                    ISSUE,
                    usage.location,
                    "Missing Digital Asset Link declaration in AndroidManifest.xml"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, you must associate your \
                app with your website by declaring an asset statements string resource file \
                that includes the `assetlinks.json` files to load. This is done by adding a \
                `<meta-data>` element with `android:name="asset_statements"` in your `AndroidManifest.xml`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
            )
        )
    }
}