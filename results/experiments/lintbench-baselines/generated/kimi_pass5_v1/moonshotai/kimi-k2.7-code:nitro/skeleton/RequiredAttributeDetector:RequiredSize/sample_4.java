package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UastCallKind;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class RequiredAttributeDetector extends LayoutDetector {

    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_STYLE = "style";

    private static final String LAYOUT_PARAMS_CLASS = "android.view.ViewGroup$LayoutParams";
    private static final String CONTEXT_CLASS = "android.content.Context";
    private static final String ATTRIBUTE_SET_CLASS = "android.util.AttributeSet";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RequiredAttributeDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing `layout_width` or `layout_height` attributes",
                    "All views must specify an explicit `android:layout_width` and "
                            + "`android:layout_height` attribute.  There is a runtime check for "
                            + "this, so if you fail to specify a size, an exception is thrown at "
                            + "runtime.  It is possible to specify these values via a `style` "
                            + "attribute.  `GridLayout`, as a special case, does not require a size.",
                    Category.CORRECTNESS,
                    4,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No cross-file state to finalize in this implementation.
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScannerConstants.ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (!isViewTagNeedingSize(tag)) {
            return;
        }

        boolean hasWidth = false;
        boolean hasHeight = false;
        boolean hasStyle = false;

        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            int length = attributes.getLength();
            for (int i = 0; i < length; i++) {
                Node attr = attributes.item(i);
                String localName = attr.getLocalName();
                if (localName == null) {
                    continue;
                }
                if (ATTR_LAYOUT_WIDTH.equals(localName)) {
                    hasWidth = true;
                } else if (ATTR_LAYOUT_HEIGHT.equals(localName)) {
                    hasHeight = true;
                } else if (ATTR_STYLE.equals(localName)) {
                    hasStyle = true;
                }
            }
        }

        if (hasStyle) {
            return;
        }
        if (hasWidth && hasHeight) {
            return;
        }

        List<String> missing = new ArrayList<>(2);
        if (!hasWidth) {
            missing.add("android:layout_width");
        }
        if (!hasHeight) {
            missing.add("android:layout_height");
        }

        String message =
                "Missing " + String.join(" and ", missing) + " attribute" + (missing.size() > 1 ? "s" : "");
        Location location = context.getElementLocation(element);
        context.report(ISSUE, location, message);
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if (node.getKind() != UastCallKind.CONSTRUCTOR_CALL) {
            return;
        }

        if (!context.getEvaluator().isMemberInSubClassOf(method, LAYOUT_PARAMS_CLASS, false)) {
            return;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
            String qualifiedName = containingClass.getQualifiedName();
            if (qualifiedName != null && qualifiedName.contains("GridLayout")) {
                return;
            }
        }

        PsiParameter[] parameters = method.getParameterList().getParameters();
        int count = parameters.length;

        if (count >= 2 && isInt(parameters[0]) && isInt(parameters[1])) {
            return;
        }

        if (count == 2 && isContext(parameters[0]) && isAttributeSet(parameters[1])) {
            return;
        }

        if (count == 1) {
            PsiClass parameterClass = getLayoutParamsClass(parameters[0]);
            if (parameterClass != null
                    && context.getEvaluator().extendsClass(parameterClass, LAYOUT_PARAMS_CLASS, false)) {
                return;
            }
        }

        context.report(
                ISSUE,
                context.getLocation(node),
                "This LayoutParams constructor does not specify a width and height");
    }

    private static boolean isViewTagNeedingSize(String tag) {
        if (tag == null) {
            return false;
        }
        if (tag.equals("include")
                || tag.equals("merge")
                || tag.equals("requestFocus")
                || tag.equals("fragment")
                || tag.equals("layout")
                || tag.equals("data")) {
            return false;
        }
        return !tag.equals("GridLayout") && !tag.endsWith(".GridLayout");
    }

    private static boolean isInt(PsiParameter parameter) {
        return parameter.getType() == PsiType.INT;
    }

    private static boolean isContext(PsiParameter parameter) {
        return CONTEXT_CLASS.equals(parameter.getType().getCanonicalText());
    }

    private static boolean isAttributeSet(PsiParameter parameter) {
        return ATTRIBUTE_SET_CLASS.equals(parameter.getType().getCanonicalText());
    }

    private static PsiClass getLayoutParamsClass(PsiParameter parameter) {
        PsiType type = parameter.getType();
        if (type instanceof PsiClassType) {
            return ((PsiClassType) type).resolve();
        }
        return null;
    }
}