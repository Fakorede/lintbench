package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UElement

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                val containingClass = node.containingClass ?: return
                if (containingClass.isInterface) return
                if (node.hasModifierProperty(PsiModifier.STATIC)) return

                if (node.name == containingClass.name && !node.isConstructor) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This method looks like a constructor but is not a constructor"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "NotConstructor",
            "Not a Constructor",
            "This check catches methods that look like they were intended to be constructors, but aren't.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}