package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `android:onClick` attribute value must refer to a public method "
                            + "declared in the view's context that takes exactly one "
                            + "`android.view.View` parameter.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Set<String> mOnClickMethods = new HashSet<>();
    private final List<OnClickReference> mPendingReferences = new ArrayList<>();

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (OnClickReference reference : mPendingReferences) {
            if (!mOnClickMethods.contains(reference.name)) {
                reference.context.report(
                        ISSUE,
                        reference.location,
                        "The `android:onClick` value must be the name of a public method "
                                + "that takes exactly one `View` parameter. Could not find `"
                                + reference.name
                                + "`");
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singleton("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = resolveMethodName(attribute.getValue());
        if (value == null || value.isEmpty()) {
            return;
        }

        Location location = context.getValueLocation(attribute);
        mPendingReferences.add(new OnClickReference(value, context, location));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.support.v4.app.Fragment",
                "androidx.fragment.app.Fragment",
                "android.app.Fragment");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        PsiClass psiClass = declaration.getPsi();
        if (psiClass == null) {
            return;
        }

        for (PsiMethod method : psiClass.getMethods()) {
            if (method.isConstructor()
                    || !method.hasModifierProperty(PsiModifier.PUBLIC)
                    || method.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }

            if (method.getParameterList().getParametersCount() != 1) {
                continue;
            }

            PsiParameter parameter = method.getParameterList().getParameters()[0];
            String type = parameter.getType().getCanonicalText();
            if ("android.view.View".equals(type) || "View".equals(type)) {
                mOnClickMethods.add(method.getName());
            }
        }
    }

    private static String resolveMethodName(String value) {
        if (value == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case '\\':
                        sb.append('\\');
                        break;
                    case ';':
                        sb.append(';');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (i + 4 < value.length()) {
                            String hex = value.substring(i + 1, i + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                        break;
                    default:
                        sb.append(next);
                        break;
                }
            } else {
                sb.append(c);
            }
        }

        return sb.toString().trim();
    }

    private static final class OnClickReference {
        final String name;
        final XmlContext context;
        final Location location;

        OnClickReference(String name, XmlContext context, Location location) {
            this.name = name;
            this.context = context;
            this.location = location;
        }
    }
}