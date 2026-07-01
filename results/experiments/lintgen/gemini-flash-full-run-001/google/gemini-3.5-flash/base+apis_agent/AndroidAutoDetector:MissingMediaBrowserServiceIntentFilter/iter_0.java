package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "MissingMediaBrowserServiceIntentFilter",
        "Missing MediaBrowserService intent-filter",
        "An Automotive Media App requires an exported service that extends " +
        "`android.service.media.MediaBrowserService` with an `intent-filter` for the " +
        "action `android.media.browse.MediaBrowserService` to be able to browse " +
        "and play media.\n\n" +
        "To do this, add\n" +
        "`<intent-filter>`\n" +
        "    `<action android:name=\"android.media.browse.MediaBrowserService\" />`\n" +
        "`</intent-filter>`\n" +
        "to the service that extends `android.service.media.MediaBrowserService`",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(
            AndroidAutoDetector.class,
            Scope.JAVA_FILE_SCOPE
        )
    );

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
            "android.service.media.MediaBrowserService",
            "androidx.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        String fqName = declaration.getQualifiedName();
        if (fqName == null) {
            return;
        }

        Document manifest = context.getProject().getMergedManifest();
        if (manifest == null) {
            manifest = context.getMainProject().getMergedManifest();
        }
        if (manifest == null) {
            return;
        }

        boolean hasIntentFilter = false;

        NodeList services = manifest.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            Element serviceElement = (Element) services.item(i);
            String name = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name.isEmpty()) {
                name = serviceElement.getAttribute("android:name");
            }

            if (namesMatch(fqName, name)) {
                NodeList children = serviceElement.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node child = children.item(j);
                    if (child instanceof Element && "intent-filter".equals(child.getNodeName())) {
                        Element intentFilter = (Element) child;
                        NodeList actions = intentFilter.getElementsByTagName("action");
                        for (int k = 0; k < actions.getLength(); k++) {
                            Element action = (Element) actions.item(k);
                            String actionName = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                            if (actionName.isEmpty()) {
                                actionName = action.getAttribute("android:name");
                            }
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
                break;
            }
        }

        if (!hasIntentFilter) {
            String message = String.format(
                "Service `%1$s` is missing the required `intent-filter` for `android.media.browse.MediaBrowserService`.",
                declaration.getName()
            );
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                message
            );
        }
    }

    private boolean namesMatch(@NonNull String classFqName, @NonNull String manifestName) {
        if (manifestName.isEmpty()) {
            return false;
        }
        if (manifestName.startsWith(".")) {
            return classFqName.endsWith(manifestName);
        }
        if (!manifestName.contains(".")) {
            return classFqName.endsWith("." + manifestName);
        }
        return classFqName.equals(manifestName) || classFqName.replace('$', '.').equals(manifestName.replace('$', '.'));
    }
}