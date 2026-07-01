package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE =
            Issue.create(
                id = "NotConstructor",
                briefDescription = "Not a constructor",
                explanation =
                    """
                        This method has the same name as the surrounding class, which makes it look like a constructor. 
                        However, it has a return type and is therefore just an ordinary method. 
                        If it was intended to be a constructor, remove the return type.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.WARNING,
                implementation = Implementation(WrongConstructorDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) return

                val className = node.containingClass?.name ?: return
                if (node.name != className) return

                if (node.modifierList.hasModifierProperty(PsiModifier.STATIC)) return

                val message = "Method '${node.name}' looks like it should be a constructor"
                val location = context.getLocation(node)
                context.report(Incident(ISSUE, node, location, message))
            }
        }
    }
}