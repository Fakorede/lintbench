package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.CLASS_ACTIVITY;
import static com.android.SdkConstants.CLASS_PROVIDER;
import static com.android.SdkConstants.CLASS_SERVICE;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                            + "`<provider>` tags.\n\n"
                            + "If your activity is simply a parent class intended to be "
                            + "subclassed by other \"real\" activities, make it an abstract "
                            + "class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            RegistrationDetector.class,
                            Scope.MANIFEST_AND_JAVA_SCOPE
                    )
            ).addMoreInfo("https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    public RegistrationDetector() {}

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
        if (!context.file.getName().equals("AndroidManifest.xml")) {
            return;
        }
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name != null && !name.isEmpty()) {
            String fqName = resolveClassName(context, name);
            String normalized = normalizeClassName(fqName);
            context.getPartialResults(ISSUE).map().put("manifest:" + normalized, true);
        }
    }

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                CLASS_ACTIVITY,
                CLASS_SERVICE,
                CLASS_PROVIDER
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }
        if (!context.getEvaluator().isPublic(declaration)) {
            return;
        }
        String fqName = declaration.getQualifiedName();
        if (fqName == null) {
            return;
        }
        String normalized = normalizeClassName(fqName);
        context.getPartialResults(ISSUE).map().put("source:" + normalized, true);
        Location location = context.getNameLocation(declaration);
        context.getPartialResults(ISSUE).map().put("loc:" + normalized, location);
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context,
            @NonNull PartialResult partialResult) {
        Set<String> registered = new HashSet<>();
        Map<String, Location> sourceClasses = new HashMap<>();

        for (LintMap map : partialResult.maps()) {
            for (String key : map.keys()) {
                if (key.startsWith("manifest:")) {
                    registered.add(key.substring("manifest:".length()));
                } else if (key.startsWith("loc:")) {
                    String fqName = key.substring("loc:".length());
                    Location loc = map.getLocation(key);
                    if (loc != null) {
                        sourceClasses.put(fqName, loc);
                    }
                }
            }
        }

        for (Map.Entry<String, Location> entry : sourceClasses.entrySet()) {
            String fqName = entry.getKey();
            if (!registered.contains(fqName)) {
                Location location = entry.getValue();
                context.report(
                        ISSUE,
                        location,
                        "Class `" + fqName + "` is not registered in the manifest"
                );
            }
        }
    }

    private String resolveClassName(XmlContext context, String name) {
        if (name.contains("${applicationId}")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                name = name.replace("${applicationId}", pkg);
            }
        }
        if (name.startsWith(".")) {
            String pkg = getPackageName(context);
            if (pkg != null && !pkg.isEmpty()) {
                return pkg + name;
            }
        } else if (!name.contains(".")) {
            String pkg = getPackageName(context);
            if (pkg != null && !pkg.isEmpty()) {
                return pkg + "." + name;
            }
        }
        return name;
    }

    private String getPackageName(XmlContext context) {
        org.w3c.dom.Document document = context.document;
        if (document != null) {
            org.w3c.dom.Element root = document.getDocumentElement();
            if (root != null && root.hasAttribute("package")) {
                return root.getAttribute("package");
            }
        }
        return context.getProject().getPackage();
    }

    private String normalizeClassName(String name) {
        return name.replace('$', '.');
    }
}