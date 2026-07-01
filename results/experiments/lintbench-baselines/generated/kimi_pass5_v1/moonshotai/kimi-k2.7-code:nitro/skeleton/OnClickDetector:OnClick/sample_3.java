package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector implements Detector.ClassScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    private static final String ATTR_ON_CLICK = "onClick";
    private static final String CLASS_ACTIVITY = "android.app.Activity";
    private static final String CLASS_VIEW = "android.view.View";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String EXPLANATION =
            "The `android:onClick` attribute value must name a public method that is "
                    + "available in the View's context (typically the Activity). The method "
                    + "must accept exactly one `android.view.View` parameter. If the named "
                    + "method is missing or does not have the correct signature, the app will "
                    + "throw an exception at runtime when the view is clicked.";

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    EXPLANATION,
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private Set<String> mOnClickMethods;
    private Map<String, List<Attr>> mReferences;
    private Map<Attr, XmlContext> mAttributeContexts;

    @Override
    public void afterCheckRootProject(Context context) {
        if (mReferences == null || mOnClickMethods == null) {
            return;
        }

        for (Map.Entry<String, List<Attr>> entry : mReferences.entrySet()) {
            String methodName = entry.getKey();
            if (mOnClickMethods.contains(methodName)) {
                continue;
            }

            List<Attr> attrs = entry.getValue();
            for (Attr attr : attrs) {
                XmlContext xmlContext = mAttributeContexts.get(attr);
                if (xmlContext == null) {
                    continue;
                }

                String value = attr.getValue();
                int length = value != null ? value.length() : 0;
                Location location = xmlContext.getRangeLocation(attr, 0, length);

                xmlContext.report(
                        ISSUE,
                        attr,
                        location,
                        "The `onClick` handler `"
                                + methodName
                                + "` does not appear to be a public method that takes a single "
                                + "`View` parameter in the activity");
            }
        }

        // Reset state in case this detector instance is reused.
        mOnClickMethods = null;
        mReferences = null;
        mAttributeContexts = null;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (context.getMainProject() != context.getProject()) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.startsWith("@") || value.startsWith("?")) {
            return;
        }

        String namespace = attribute.getNamespaceURI();
        if (namespace != null && !ANDROID_URI.equals(namespace)) {
            return;
        }

        if (mReferences == null) {
            mReferences = new HashMap<>();
            mAttributeContexts = new HashMap<>();
        }

        List<Attr> list = mReferences.get(value);
        if (list == null) {
            list = new ArrayList<>();
            mReferences.put(value, list);
        }
        list.add(attribute);
        mAttributeContexts.put(attribute, context);
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_ACTIVITY);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            if (method == null) {
                continue;
            }

            if (!method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }

            if (method.getParameterList().getParametersCount() != 1) {
                continue;
            }

            PsiParameter parameter = method.getParameterList().getParameter(0);
            if (parameter == null) {
                continue;
            }

            PsiType type = parameter.getType();
            if (type == null) {
                continue;
            }

            if (!CLASS_VIEW.equals(type.getCanonicalText())) {
                continue;
            }

            if (mOnClickMethods == null) {
                mOnClickMethods = new HashSet<>();
            }
            mOnClickMethods.add(method.getName());
        }
    }
}