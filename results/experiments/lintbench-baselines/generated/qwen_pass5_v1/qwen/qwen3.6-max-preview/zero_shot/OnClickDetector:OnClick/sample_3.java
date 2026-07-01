package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OnClickDetector extends Detector implements Detector.XmlScanner, Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "`onClick` method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.\n\n" +
            "Must be a string value, using '\\;' to escape characters such as '\\n' or " +
            "'\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE))
    );

    private final Map<String, Location> mOnClickMethods = new HashMap<>();
    private final Set<String> mValidMethods = new HashSet<>();
    private Context mContext;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mContext == null) {
            mContext = context;
        }
        String onClick = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ON_CLICK);
        if (onClick != null && !onClick.isEmpty()) {
            Node attrNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ON_CLICK);
            if (attrNode != null) {
                mOnClickMethods.put(onClick, context.getLocation(attrNode));
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        if (mContext == null) {
            mContext = context;
        }
        String name = method.getName();
        if (mOnClickMethods.containsKey(name)) {
            PsiType returnType = method.getReturnType();
            List<UParameter> parameters = method.getUastParameters();
            if (method.hasModifierProperty(PsiModifier.PUBLIC) &&
                    PsiType.VOID.equals(returnType) &&
                    parameters.size() == 1) {
                PsiType paramType = parameters.get(0).getType();
                if (paramType != null && context.getEvaluator().extendsClass(paramType, "android.view.View", true)) {
                    mValidMethods.add(name);
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Project project) {
        if (mContext != null) {
            for (Map.Entry<String, Location> entry : mOnClickMethods.entrySet()) {
                String methodName = entry.getKey();
                if (!mValidMethods.contains(methodName)) {
                    Location location = entry.getValue();
                    mContext.report(ISSUE, location, String.format(
                            "Corresponding method handler `public void %s(android.view.View)` not found", methodName));
                }
            }
        }
        mOnClickMethods.clear();
        mValidMethods.clear();
        mContext = null;
    }
}