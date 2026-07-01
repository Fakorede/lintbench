package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegistrationDetector extends Detector
        implements Detector.JavaPsiScanner, Detector.XmlScanner {

    private static final String ANDROID_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_PROVIDER = "android.content.ContentProvider";

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the "
                    + "AndroidManifest.xml file using <activity>, <service> and <provider> tags. "
                    + "If your activity is simply a parent class intended to be subclassed by other "
                    + "\"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(RegistrationDetector.class,
                    Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)
    );

    private final List<ComponentInfo> mClasses = new ArrayList<>();
    private final Set<String> mRegistrations = new HashSet<>();

    private static class ComponentInfo {
        final String fqcn;
        final PsiClass node;
        final JavaContext context;
        final String type;

        ComponentInfo(String fqcn, PsiClass node, JavaContext context, String type) {
            this.fqcn = fqcn;
            this.node = node;
            this.context = context;
            this.type = type;
        }
    }

    @Override
    @NotNull
    public List<Class<? extends PsiElement>> getApplicableNodeTypes() {
        return Collections.<Class<? extends PsiElement>>singletonList(PsiClass.class);
    }

    @Override
    @NotNull
    public JavaElementVisitor createJavaVisitor(@NotNull final JavaContext context) {
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass node) {
                if (node.isInterface() || node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }
                String fqcn = node.getQualifiedName();
                if (fqcn == null) {
                    return;
                }
                PsiClass superclass = node.getSuperClass();
                while (superclass != null) {
                    String name = superclass.getQualifiedName();
                    if (ANDROID_ACTIVITY.equals(name)) {
                        mClasses.add(new ComponentInfo(fqcn, node, context, "activity"));
                        break;
                    } else if (ANDROID_SERVICE.equals(name)) {
                        mClasses.add(new ComponentInfo(fqcn, node, context, "service"));
                        break;
                    } else if (ANDROID_CONTENT_PROVIDER.equals(name)) {
                        mClasses.add(new ComponentInfo(fqcn, node, context, "provider"));
                        break;
                    }
                    superclass = superclass.getSuperClass();
                }
            }
        };
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service", "provider");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }
        String pkg = context.getMainProject().getPackage();
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        mRegistrations.add(normalize(resolve(name, pkg)));
    }

    @Override
    public void afterCheckRootProject(@NotNull Context context) {
        for (ComponentInfo info : mClasses) {
            String fqcn = normalize(info.fqcn);
            if (!mRegistrations.contains(fqcn)) {
                String message = String.format("%s is not registered in the manifest",
                        capitalize(info.type));
                info.context.report(ISSUE, info.context.getLocation(info.node), message);
            }
        }
    }

    private static String resolve(String name, String pkg) {
        if (name.startsWith(".")) {
            return pkg + name;
        }
        if (name.indexOf('.') < 0) {
            return pkg + "." + name;
        }
        return name;
    }

    private static String normalize(String fqcn) {
        return fqcn.replace('$', '.');
    }

    private static String capitalize(String type) {
        return Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }
}