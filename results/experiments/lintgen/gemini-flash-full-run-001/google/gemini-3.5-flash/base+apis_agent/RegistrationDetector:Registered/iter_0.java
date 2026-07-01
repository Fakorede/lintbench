package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class RegistrationDetector extends Detector implements Detector.XmlScanner, Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the " +
            "`AndroidManifest.xml` file using `<activity>`, `<service>` and `<provider>` tags.\n\n" +
            "If your activity is simply a parent class intended to be subclassed by other " +
            "\"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    );

    private final Set<String> mRegistered = new HashSet<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mRegistered.clear();
    }

    // XmlScanner implementation

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service", "provider", "activity-alias");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        Document document = element.getOwnerDocument();
        Element root = document != null ? document.getDocumentElement() : null;
        String pkg = null;
        if (root != null && "manifest".equals(root.getTagName())) {
            pkg = root.getAttribute("package");
        }
        if (pkg == null || pkg.isEmpty()) {
            pkg = context.getProject().getPackage();
        }
        if (pkg == null) {
            pkg = "";
        }

        String fqcn = resolveClassName(pkg, name);
        mRegistered.add(fqcn);
        mRegistered.add(name);
    }

    private static String resolveClassName(@NonNull String pkg, @NonNull String className) {
        if (className.startsWith(".")) {
            return pkg + className;
        } else if (!className.contains(".")) {
            return pkg.isEmpty() ? className : pkg + "." + className;
        } else {
            return className;
        }
    }

    // SourceCodeScanner implementation

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
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        String fqcn = declaration.getQualifiedName();
        if (fqcn == null) {
            return;
        }

        // Skip non-static inner classes
        if (declaration.getContainingClass() != null && !context.getEvaluator().isStatic(declaration)) {
            return;
        }

        if (!mRegistered.contains(fqcn)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "The class `" + fqcn + "` is not registered in the manifest"
            );
        }
    }
}