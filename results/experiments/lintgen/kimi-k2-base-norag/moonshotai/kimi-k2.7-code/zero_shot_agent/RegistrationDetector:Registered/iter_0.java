package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.SdkConstants;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegistrationDetector extends Detector implements Detector.SourceCodeScanner, Detector.XmlScanner {

    private static final String ANDROID_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_PROVIDER = "android.content.ContentProvider";

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the AndroidManifest.xml file using <activity>, <service> and <provider> tags. If your activity is simply a parent class intended to be subclassed by other \"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(RegistrationDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)
    );

    private final List<ComponentInfo> mClasses = new ArrayList<>();
    private final Set<String> mRegistrations = new HashSet<>();

    private static class ComponentInfo {
        final String fqcn;
        final UClass node;
        final JavaContext context;
        final String type;

        ComponentInfo(String fqcn, UClass node, JavaContext context, String type) {
            this.fqcn = fqcn;
            this.node = node;
            this.context = context;
            this.type = type;
        }
    }

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    @NotNull
    public UElementHandler createUastHandler(@NotNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (node.isInterface() || node.isAnnotationType() || node.isEnum()) {
                    return;
                }
                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }
                String fqcn = node.getQualifiedName();
                if (fqcn == null) {
                    return;
                }
                if (context.getEvaluator().extendsClass(node, ANDROID_ACTIVITY, false)) {
                    mClasses.add(new ComponentInfo(fqcn, node, context, "activity"));
                } else if (context.getEvaluator().extendsClass(node, ANDROID_SERVICE, false)) {
                    mClasses.add(new ComponentInfo(fqcn, node, context, "service"));
                } else if (context.getEvaluator().extendsClass(node, ANDROID_CONTENT_PROVIDER, false)) {
                    mClasses.add(new ComponentInfo(fqcn, node, context, "provider"));
                }
            }
        };
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_ACTIVITY, SdkConstants.TAG_SERVICE, SdkConstants.TAG_PROVIDER);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
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
                info.context.report(ISSUE, info.context.getNameLocation(info.node), message);
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