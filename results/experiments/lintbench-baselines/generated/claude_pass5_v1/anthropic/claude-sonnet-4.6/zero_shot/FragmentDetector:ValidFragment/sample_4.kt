package com.android.tools.lint.checks

import com.android.SdkConstants.CLASS_FRAGMENT
import com.android.SdkConstants.CLASS_V4_FRAGMENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.ClassScanner
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.util.EnumSet

/**
 * Checks that Fragment subclasses can be instantiated via reflection,
 * meaning they must have a public no-argument constructor and must not
 * be non-static inner classes.
 */
class FragmentDetector : Detector(), ClassScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            FragmentDetector::class.java,
            EnumSet.of(Scope.CLASS_FILE)
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                From the Fragment documentation:
                **Every** fragment must have an empty constructor, so it can be \
                instantiated when restoring its activity's state. It is strongly \
                recommended that subclasses do not have other constructors with \
                parameters, since these constructors will not be called when the \
                fragment is re-instantiated; instead, arguments can be supplied by \
                the caller with `setArguments(Bundle)` and later retrieved by the \
                Fragment with `getArguments()`.

                Note that this is no longer true when you are using \
                `androidx.fragment.app.Fragment`; with the `FragmentFactory` you \
                can supply any arguments you want (as of version androidx version 1.1).
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            moreInfo = "https://developer.android.com/reference/android/app/Fragment.html#Fragment()",
            implementation = IMPLEMENTATION
        )

        private const val ANDROIDX_FRAGMENT_CLASS = "androidx/fragment/app/Fragment"

        // Old support library fragment
        private val FRAGMENT_CLASSES = listOf(
            CLASS_FRAGMENT.replace('.', '/'),
            CLASS_V4_FRAGMENT.replace('.', '/'),
            "android/support/v4/app/Fragment",
            "androidx/fragment/app/Fragment"
        )
    }

    override fun applicableSuperClasses(): List<String> {
        return FRAGMENT_CLASSES
    }

    override fun checkClass(context: ClassContext, classNode: ClassNode) {
        // Skip abstract classes - they don't need to be directly instantiated
        if (classNode.access and Opcodes.ACC_ABSTRACT != 0) {
            return
        }

        // Skip interfaces
        if (classNode.access and Opcodes.ACC_INTERFACE != 0) {
            return
        }

        // If this is an androidx fragment, skip the check since FragmentFactory
        // allows custom constructors as of androidx fragment 1.1
        if (isAndroidxFragment(context, classNode)) {
            return
        }

        // Check if the class is an inner class (non-static)
        if (isNonStaticInnerClass(classNode)) {
            val message = "${getSimpleName(classNode.name)}: Fragments should be static inner " +
                    "classes so they can be re-instantiated by the system, and anonymous classes " +
                    "are not allowed."
            context.report(ISSUE, context.getLocation(classNode), message)
            return
        }

        // Check that the class has a public no-argument constructor
        if (!hasPublicNoArgConstructor(classNode)) {
            val message = "This fragment should provide a default constructor (a public " +
                    "constructor with no arguments). When the system needs to re-instantiate " +
                    "a fragment, it will call the no-argument constructor of your fragment. " +
                    "If the no-argument constructor is not available, a runtime exception " +
                    "will occur in some cases during state restore."
            context.report(ISSUE, context.getLocation(classNode), message)
        }
    }

    /**
     * Checks if this fragment extends androidx.fragment.app.Fragment,
     * in which case we skip the check since FragmentFactory handles instantiation.
     */
    private fun isAndroidxFragment(context: ClassContext, classNode: ClassNode): Boolean {
        // Check superclass chain for androidx fragment
        val superName = classNode.superName ?: return false
        if (superName == ANDROIDX_FRAGMENT_CLASS) {
            return true
        }
        // Walk up the hierarchy
        try {
            val superClass = context.driver.findClass(context, superName, 0)
            if (superClass != null) {
                return isAndroidxFragment(context, superClass)
            }
        } catch (e: Exception) {
            // If we can't resolve, check by name heuristic
        }
        return superName.startsWith("androidx/fragment/")
    }

    /**
     * Returns true if this class is a non-static inner class.
     * Non-static inner classes cannot be instantiated by the system.
     */
    private fun isNonStaticInnerClass(classNode: ClassNode): Boolean {
        val name = classNode.name
        if (!name.contains('$')) {
            return false
        }

        // Anonymous classes are inner classes with $ followed by a number
        val lastDollar = name.lastIndexOf('$')
        val suffix = name.substring(lastDollar + 1)
        if (suffix.isNotEmpty() && suffix.all { it.isDigit() }) {
            // Anonymous class
            return true
        }

        // Check if it's a static nested class
        // Static nested classes have ACC_STATIC set
        if (classNode.access and Opcodes.ACC_STATIC != 0) {
            return false
        }

        // It's an inner class - check if it's static by looking at inner class info
        val innerClasses = classNode.innerClasses
        if (innerClasses != null) {
            for (innerClass in innerClasses) {
                val innerClassNode = innerClass as? org.objectweb.asm.tree.InnerClassNode ?: continue
                if (innerClassNode.name == name) {
                    // Found the inner class entry for this class
                    return innerClassNode.access and Opcodes.ACC_STATIC == 0
                }
            }
        }

        // If it has a $ in the name and we couldn't determine static status,
        // assume it might be non-static
        return true
    }

    /**
     * Returns true if the class has a public constructor with no arguments.
     */
    private fun hasPublicNoArgConstructor(classNode: ClassNode): Boolean {
        val methods = classNode.methods ?: return false
        var hasConstructor = false

        for (method in methods) {
            val methodNode = method as? MethodNode ?: continue
            if (methodNode.name == "<init>") {
                hasConstructor = true
                // Check if it's a no-arg constructor
                if (methodNode.desc == "()V") {
                    // Check if it's public
                    if (methodNode.access and Opcodes.ACC_PUBLIC != 0) {
                        return true
                    }
                }
            }
        }

        // If there are no constructors at all, Java provides a default public no-arg one
        if (!hasConstructor) {
            return true
        }

        return false
    }

    /**
     * Returns a simple class name from a fully qualified internal name.
     */
    private fun getSimpleName(internalName: String): String {
        val lastSlash = internalName.lastIndexOf('/')
        val lastDollar = internalName.lastIndexOf('$')
        val start = maxOf(lastSlash, lastDollar) + 1
        return internalName.substring(start)
    }
}