package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.resources.ResourceFolderType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethodCallExpression
import org.jetbrains.uast.*

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        val USE_INNER_INSETS = Issue.create(
            id = "UseInnerInsets",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                Using internal inset dimension resources is not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI.
                
                To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes() =
        listOf(UResReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext) =
        InnerInsetsHandler(context)

    inner class InnerInsetsHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitResReferenceExpression(node: UResReferenceExpression) {
            if (node.resourceType == ResourceType.DIMEN && node.name?.contains("inner_inset") == true) {
                val resourceFolder = context.getAndroidFacet()?.resourceFolder
                if (resourceFolder != null) {
                    val psiFile = node.sourcePsi ?: return
                    val location = context.getLocation(node)
                    context.report(
                        USE_INNER_INSETS,
                        location,
                        "Using internal inset dimension resources is not a supported way to retrieve the relevant insets for your application. Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."
                    )
                }
            }
        }
    }

    override fun visitResource(res: ResourceFolderType, name: String, element: Element) {
        if (res == ResourceFolderType.DIMEN && name.contains("inner_inset")) {
            val location = Location.create(element)
            context.report(
                USE_INNER_INSETS,
                location,
                "Using internal inset dimension resources is not a supported way to retrieve the relevant insets for your application. Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."
            )
        }
    }

    override fun afterCheckProject(context: JavaContext) {
        super.afterCheckProject(context)
    }
}