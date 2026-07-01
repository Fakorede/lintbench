package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class RegistrationDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "Registered",
        "Class is not registered in the manifest",
        "Activities, services and content providers should be registered in the " +
        "`AndroidManifest.xml` file using `<activity>`, `<service>` and " +
        "`<provider>` tags. If your activity is simply a parent class intended to be " +
        "subclassed by other \"real\" activities, make it an abstract class.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(
            RegistrationDetector.class,
            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE_LEVEL)
        )
    );

    private final Set<String> mRegistered = new HashSet<>();

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            mRegistered.clear();
        }
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service", "provider", "activity-alias");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("activity".equals(tagName) || "service".equals(tagName) || "provider".equals(tagName)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                register(context, name);
            }
        } else if ("activity-alias".equals(tagName)) {
            String target = element.getAttributeNS(SdkConstants.ANDROID_URI, "targetActivity");
            if (target != null && !target.isEmpty()) {
                register(context, target);
            }
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                register(context, name);
            }
        }
    }

    private void register(XmlContext context, String name) {
        String resolved = resolveClassName(context, name);
        mRegistered.add(resolved.replace('$', '.'));
        if (name.contains(".") && !name.startsWith(".")) {
            String pkg = getPackage(context);
            if (pkg != null) {
                mRegistered.add((pkg + "." + name).replace('$', '.'));
            }
        }
    }

    private String resolveClassName(XmlContext context, String className) {
        if (className.startsWith(".")) {
            String pkg = getPackage(context);
            return pkg != null ? pkg + className : className;
        } else if (!className.contains(".")) {
            String pkg = getPackage(context);
            return pkg != null ? pkg + "." + className : className;
        }
        return className;
    }

    @Nullable
    private String getPackage(XmlContext context) {
        Document document = context.document;
        if (document != null) {
            Element root = document.getDocumentElement();
            if (root != null) {
                String pkg = root.getAttribute("package");
                if (pkg != null && !pkg.isEmpty()) {
                    return pkg;
                }
            }
        }
        return context.getProject().getPackage();
    }

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
            "android.app.Activity",
            "android.app.Service",
            "android.content.ContentProvider"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }
        if (declaration.isInterface()) {
            return;
        }
        String fqName = declaration.getQualifiedName();
        if (fqName == null) {
            return;
        }
        String normalized = fqName.replace('$', '.');
        if (!mRegistered.contains(normalized)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Class is not registered in the manifest"
            );
        }
    }
}