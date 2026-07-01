package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.EnumSet;
import java.util.List;

/**
 * Checks that custom View classes provide constructors required for XML inflation.
 */
public class ViewConstructorDetector extends Detector implements ClassScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a " +
            "constructor with one of the following signatures:\n" +
            " * `View(Context context)`\n" +
            " * `View(Context context, AttributeSet attrs)`\n" +
            " * `View(Context context, AttributeSet attrs, int defStyle)`\n" +
            "\n" +
            "If your custom view needs to perform initialization which does " +
            "not apply when used in a layout editor, you can surround the " +
            "given code with a check to see if `View#isInEditMode()` is " +
            "false, since that method will return `false` at runtime but " +
            "true within a user interface editor.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    ViewConstructorDetector.class,
                    Scope.CLASS_FILE_SCOPE));

    private static final String CONTEXT_CONSTRUCTOR =
            "(Landroid/content/Context;)V";
    private static final String CONTEXT_ATTRS_CONSTRUCTOR =
            "(Landroid/content/Context;Landroid/util/AttributeSet;)V";
    private static final String CONTEXT_ATTRS_DEFSTYLE_CONSTRUCTOR =
            "(Landroid/content/Context;Landroid/util/AttributeSet;I)V";

    private static final String ANDROID_VIEW_VIEW = "android/view/View";
    private static final String CONSTRUCTOR_NAME = "<init>";

    /** Constructs a new {@link ViewConstructorDetector} */
    public ViewConstructorDetector() {
    }

    // ---- Implements ClassScanner ----

    @Override
    public void checkClass(ClassContext context, ClassNode classNode) {
        // Only check concrete classes (not abstract, not interfaces)
        if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }
        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            return;
        }

        // Only check classes that extend android.view.View (directly or indirectly)
        if (!extendsView(context, classNode)) {
            return;
        }

        // Check if the class is anonymous
        if (classNode.name != null && classNode.name.contains("$")) {
            // Could be anonymous or inner class; skip anonymous classes
            // Anonymous classes have a numeric suffix after the $
            String simpleName = classNode.name.substring(classNode.name.lastIndexOf('$') + 1);
            if (simpleName.matches("\\d+")) {
                // Anonymous class, skip
                return;
            }
        }

        // Check constructors
        boolean hasValidConstructor = false;

        @SuppressWarnings("unchecked")
        List<MethodNode> methods = classNode.methods;
        if (methods != null) {
            for (MethodNode method : methods) {
                if (CONSTRUCTOR_NAME.equals(method.name)) {
                    String desc = method.desc;
                    if (CONTEXT_CONSTRUCTOR.equals(desc)
                            || CONTEXT_ATTRS_CONSTRUCTOR.equals(desc)
                            || CONTEXT_ATTRS_DEFSTYLE_CONSTRUCTOR.equals(desc)) {
                        hasValidConstructor = true;
                        break;
                    }
                }
            }
        }

        if (!hasValidConstructor) {
            String className = classNode.name.replace('/', '.').replace('$', '.');
            context.report(ISSUE, context.getLocation(classNode),
                    "Custom view " + className + " is missing constructor used by tools: " +
                    "(Context) or (Context, AttributeSet) " +
                    "or (Context, AttributeSet, int)");
        }
    }

    /**
     * Checks whether the given class node extends android.view.View, either directly
     * or through a superclass chain.
     */
    private boolean extendsView(ClassContext context, ClassNode classNode) {
        String superName = classNode.superName;
        while (superName != null && !superName.equals("java/lang/Object")) {
            if (superName.equals(ANDROID_VIEW_VIEW)) {
                return true;
            }
            // Check common View subclass packages
            if (superName.startsWith("android/view/")
                    || superName.startsWith("android/widget/")
                    || superName.startsWith("android/webkit/")
                    || superName.startsWith("android/support/")
                    || superName.startsWith("androidx/")) {
                // Assume these extend View
                return true;
            }

            // Try to load the superclass to continue the chain
            ClassNode superClass = context.getDriver().findClass(context, superName, 0);
            if (superClass == null) {
                // Can't find the superclass; we can't be sure
                // Use heuristic: if the class name contains "View" it probably extends View
                if (superName.contains("View") || superName.contains("Layout")
                        || superName.contains("Widget")) {
                    return true;
                }
                break;
            }
            superName = superClass.superName;
        }

        return false;
    }
}