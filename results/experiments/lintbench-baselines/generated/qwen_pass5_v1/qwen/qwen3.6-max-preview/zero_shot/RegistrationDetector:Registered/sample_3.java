package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegistrationDetector extends Detector implements Detector.UastScanner, Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the " +
            "`AndroidManifest.xml` file using `<activity>`, `<service>` and " +
            "`<provider>` tags.\n\n" +
            "If your activity is simply a parent class intended to be " +
            "subclassed by other \"real\" activities, make it an abstract class.",
            Category.CORRECTNESS, 6, Severity.WARNING,
            new Implementation(RegistrationDetector.class, Scope.JAVA_FILE, Scope.MANIFEST));

    private static final String KEY_REGISTERED = "RegisteredComponents";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service", "provider");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name != null && !name.isEmpty()) {
            String pkg = context.getPackageName();
            if (pkg == null) {
                pkg = "";
            }
            String fqcn = resolveClassName(name, pkg);
            getRegisteredSet(context).add(fqcn);
        }
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (node.isAbstract()) {
                    return;
                }

                PsiClass psiClass = node.getJavaPsi();
                if (psiClass == null) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                boolean isActivity = evaluator.extendsClass(psiClass, "android.app.Activity", false);
                boolean isService = evaluator.extendsClass(psiClass, "android.app.Service", false);
                boolean isProvider = evaluator.extendsClass(psiClass, "android.content.ContentProvider", false);

                if (!isActivity && !isService && !isProvider) {
                    return;
                }

                String fqcn = psiClass.getQualifiedName();
                if (fqcn == null) {
                    return;
                }

                if (!getRegisteredSet(context).contains(fqcn)) {
                    context.report(ISSUE, context.getLocation(node),
                            "Class is not registered in the manifest");
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private Set<String> getRegisteredSet(@NonNull Context context) {
        Set<String> set = (Set<String>) context.getProject().getClientProperty(KEY_REGISTERED);
        if (set == null) {
            set = new HashSet<>();
            context.getProject().putClientProperty(KEY_REGISTERED, set);
        }
        return set;
    }

    @NonNull
    private static String resolveClassName(@NonNull String name, @NonNull String pkg) {
        if (name.startsWith(".")) {
            return pkg + name;
        } else if (name.indexOf('.') == -1) {
            return pkg + "." + name;
        }
        return name;
    }
}