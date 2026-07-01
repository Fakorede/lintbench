package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an `intent-filter` "
                            + "for the action `android.media.browse.MediaBrowserService` to be able "
                            + "to browse and play media.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private final Set<String> mServicesWithIntentFilter = new HashSet<>();
    private final Set<String> mServicesDeclaredInManifest = new HashSet<>();

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mServicesWithIntentFilter.clear();
        mServicesDeclaredInManifest.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String serviceName = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }
        String pkg = context.getProject().getPackage();
        String fqName = serviceName;
        if (serviceName.startsWith(".")) {
            fqName = pkg + serviceName;
        } else if (!serviceName.contains(".")) {
            fqName = pkg + "." + serviceName;
        }

        mServicesDeclaredInManifest.add(fqName);

        boolean hasFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    Node actionNode = actions.item(j);
                    if (actionNode instanceof Element) {
                        Element action = (Element) actionNode;
                        String actionName = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                        if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                            hasFilter = true;
                            break;
                        }
                    }
                }
            }
            if (hasFilter) {
                break;
            }
        }

        if (hasFilter) {
            mServicesWithIntentFilter.add(fqName);
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.service.media.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        if ("android.service.media.MediaBrowserService".equals(qualifiedName)
                || "androidx.media.MediaBrowserServiceCompat".equals(qualifiedName)) {
            return;
        }

        if (!mServicesWithIntentFilter.contains(qualifiedName)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Service " + declaration.getName() + " missing intent-filter for action "
                            + "android.media.browse.MediaBrowserService"
            );
        }
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // No-op, required to be overridden per specification
    }
}