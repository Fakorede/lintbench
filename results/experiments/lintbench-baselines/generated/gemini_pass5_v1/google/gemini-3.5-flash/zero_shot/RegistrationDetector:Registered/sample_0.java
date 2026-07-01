package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import com.intellij.psi.PsiModifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class RegistrationDetector extends Detector implements Detector.XmlScanner, Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the " +
            "`AndroidManifest.xml` file using `<activity>`, `<service>` and " +
            "`<provider>` tags.\n\n" +
            "If your activity is simply a parent class intended to be " +
            "subclassed by other \"real\" activities, make it an abstract " +
            "class.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                    Scope.JAVA_FILE_SCOPE
            )
    );

    private static class ClassInfo {
        final String qualifiedName;
        final Location location;
        final String type;

        ClassInfo(String qualifiedName, Location location, String type) {
            this.qualifiedName = qualifiedName;
            this.location = location;
            this.type = type;
        }
    }

    private final Map<String, ClassInfo> mDeclaredClasses = new HashMap<>();
    private final Set<String> mRegisteredClasses = new HashSet<>();
    private String mPackageName = null;

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        mDeclaredClasses.clear();
        mRegisteredClasses.clear();
        mPackageName = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "activity", "service", "provider");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            String pkg = element.getAttribute("package");
            if (pkg != null && !pkg.isEmpty()) {
                mPackageName = pkg;
            }
        } else if ("activity".equals(tagName) || "service".equals(tagName) || "provider".equals(tagName)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                String pkg = context.getProject().getPackage();
                if (pkg == null || pkg.isEmpty()) {
                    pkg = mPackageName;
                }
                String resolved = resolveClassName(name, pkg);
                if (resolved != null) {
                    mRegisteredClasses.add(resolved);
                }
            }
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider"
        );
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (context.isTestSource()) {
            return;
        }
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        String type = "Class";
        if (context.getEvaluator().inheritsFrom(declaration, "android.app.Activity", false)) {
            type = "Activity";
        } else if (context.getEvaluator().inheritsFrom(declaration, "android.app.Service", false)) {
            type = "Service";
        } else if (context.getEvaluator().inheritsFrom(declaration, "android.content.ContentProvider", false)) {
            type = "ContentProvider";
        }

        Location location = context.getNameLocation(declaration);
        mDeclaredClasses.put(qualifiedName, new ClassInfo(qualifiedName, location, type));
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        Set<String> normalizedRegistered = new HashSet<>();
        for (String reg : mRegisteredClasses) {
            normalizedRegistered.add(normalize(reg));
        }

        for (Map.Entry<String, ClassInfo> entry : mDeclaredClasses.entrySet()) {
            String fqName = entry.getKey();
            String normalizedFqName = normalize(fqName);
            if (!normalizedRegistered.contains(normalizedFqName)) {
                ClassInfo info = entry.getValue();
                String message = String.format("%1$s `%2$s` is not registered in the manifest", info.type, info.qualifiedName);
                context.report(ISSUE, info.location, message);
            }
        }
    }

    private String resolveClassName(String name, String packageName) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (name.startsWith(".")) {
            if (packageName != null && !packageName.isEmpty()) {
                return packageName + name;
            }
        } else if (!name.contains(".")) {
            if (packageName != null && !packageName.isEmpty()) {
                return packageName + "." + name;
            }
        }
        return name;
    }

    private String normalize(String className) {
        if (className == null) {
            return "";
        }
        return className.replace('$', '.');
    }
}