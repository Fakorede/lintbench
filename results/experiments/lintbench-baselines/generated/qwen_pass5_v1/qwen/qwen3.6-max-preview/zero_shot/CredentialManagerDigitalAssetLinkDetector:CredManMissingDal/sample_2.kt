package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import com.intellij.openapi.util.Key
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.UastScanner, Detector.XmlScanner {

    companion object {
        private val KEY_USES_CRED_MAN = Key.create<Boolean>("CredManMissingDal_UsesCredMan")
        private val KEY_HAS_DAL = Key.create<Boolean>("CredManMissingDal_HasDal")
        private val KEY_APP_LOCATION = Key.create<Location>("CredManMissingDal_AppLocation")

        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = "When using password sign-in through Credential Manager, an asset statements string resource file that includes the `assetlinks.json` files to load must be declared in the manifest using a `<meta-data>` element.\n\n" +
                    "Reference: https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.KOTLIN_FILE, Scope.MANIFEST)
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java, UReferenceExpression::class.java)
    }

    override fun visitUastNode(context: JavaContext, node: UElement) {
        val project = context.project
        if (project.getUserData(KEY_USES_CRED_MAN) == true) return

        val resolved = when (node) {
            is UCallExpression -> node.resolve()
            is UReferenceExpression -> node.resolve()
            else -> null
        }

        if (resolved != null) {
            val qn = context.evaluator.getQualifiedName(resolved)
            if (qn != null && (qn == "androidx.credentials.CredentialManager" ||
                        qn == "androidx.credentials.GetPasswordOption" ||
                        qn == "androidx.credentials.CreatePasswordRequest" ||
                        qn == "androidx.credentials.PasswordCredential")) {
                project.putUserData(KEY_USES_CRED_MAN, true)
            }
        }
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(SdkConstants.TAG_APPLICATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val project = context.project
        project.putUserData(KEY_APP_LOCATION, context.getLocation(element))

        var hasDal = false
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == SdkConstants.TAG_META_DATA) {
                val name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name == "asset_statements") {
                    hasDal = true
                    break
                }
            }
        }
        project.putUserData(KEY_HAS_DAL, hasDal)
    }

    override fun afterCheckProject(context: Context) {
        val project = context.project
        val usesCredMan = project.getUserData(KEY_USES_CRED_MAN) ?: false
        val hasDal = project.getUserData(KEY_HAS_DAL) ?: false
        val appLocation = project.getUserData(KEY_APP_LOCATION)

        if (usesCredMan && !hasDal && appLocation != null) {
            context.report(
                ISSUE,
                appLocation,
                "Missing Digital Asset Links configuration for Credential Manager. " +
                "Add `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` " +
                "to the `<application>` tag in your manifest."
            )
        }
    }
}