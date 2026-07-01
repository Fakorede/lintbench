package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.Location;
import com.intellij.psi.PsiModifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends " +
                    "`android.service.media.MediaBrowserService` with an `intent-filter` " +
                    "for the action `android.media.browse.MediaBrowserService` to be able " +
                    "to browse and play media.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static class ManifestService {
        final String fqn;
        final Location location;
        final boolean hasIntentFilter;

        ManifestService(String fqn, Location location, boolean hasIntentFilter) {
            this.fqn = fqn;
            this.location = location;
            this.hasIntentFilter = hasIntentFilter;
        }
    }

    private static class JavaServiceClass {
        final String fqn;
        final Location location;

        JavaServiceClass(String fqn, Location location) {
            this.fqn = fqn;
            this.location = location;
        }
    }

    private final List<ManifestService> mManifestServices = new ArrayList<>();
    private final List<JavaServiceClass> mJavaServices = new ArrayList<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mManifestServices.clear();
        mJavaServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            return;
        }
        String packageName = context.getProject().getPackage();
        String fqn = getFullyQualifiedName(name, packageName);

        boolean hasIntentFilter = false;
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child instanceof Element && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                org.w3c.dom.NodeList actionList = intentFilter.getElementsByTagName("action");
                for (int j = 0; j < actionList.getLength(); j++) {
                    Element action = (Element) actionList.item(j);
                    String actionName = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                    if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                        hasIntentFilter = true;
                        break;
                    }
                }
            }
            if (hasIntentFilter) {
                break;
            }
        }

        mManifestServices.add(new ManifestService(fqn, context.getLocation(element), hasIntentFilter));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                "android.service.media.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        String fqn = declaration.getQualifiedName();
        if (fqn != null) {
            mJavaServices.add(new JavaServiceClass(fqn, context.getNameLocation(declaration)));
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (JavaServiceClass javaService : mJavaServices) {
            ManifestService matchingManifestService = null;
            for (ManifestService manifestService : mManifestServices) {
                if (javaService.fqn.equals(manifestService.fqn)) {
                    matchingManifestService = manifestService;
                    break;
                }
            }

            if (matchingManifestService != null) {
                if (!matchingManifestService.hasIntentFilter) {
                    context.report(
                            ISSUE,
                            matchingManifestService.location,
                            "Service " + javaService.fqn + " is missing the required intent-filter " +
                            "for android.media.browse.MediaBrowserService");
                }
            } else {
                context.report(
                        ISSUE,
                        javaService.location,
                        "Service " + javaService.fqn + " should be declared in the manifest " +
                        "with an intent-filter for android.media.browse.MediaBrowserService");
            }
        }
    }

    private String getFullyQualifiedName(String className, String packageName) {
        if (className == null || className.isEmpty()) {
            return "";
        }
        if (packageName == null) {
            packageName = "";
        }
        if (className.startsWith(".")) {
            return packageName + className;
        }
        if (!className.contains(".")) {
            return packageName.isEmpty() ? className : packageName + "." + className;
        }
        return className;
    }
}