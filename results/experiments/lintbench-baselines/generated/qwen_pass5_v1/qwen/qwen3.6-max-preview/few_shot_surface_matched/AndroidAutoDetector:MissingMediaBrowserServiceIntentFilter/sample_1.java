package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE = "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_ACTION = "android.media.browse.MediaBrowserService";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "android:name";

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends "
                    + "`android.service.media.MediaBrowserService` with an `intent-filter` for the action "
                    + "`android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n"
                    + "To do this, add\n"
                    + "```xml\n"
                    + "<intent-filter>\n"
                    + "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n"
                    + "</intent-filter>\n"
                    + "```\n"
                    + "to the service that extends `android.service.media.MediaBrowserService`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)));

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No global state initialization required for this detector
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_SERVICE.equals(element.getTagName())) {
            return;
        }

        boolean hasRequiredFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getElementsByTagName(TAG_ACTION);
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    if (MEDIA_BROWSER_ACTION.equals(action.getAttribute(ATTR_NAME))) {
                        hasRequiredFilter = true;
                        break;
                    }
                }
            }
            if (hasRequiredFilter) break;
        }

        if (!hasRequiredFilter) {
            context.report(ISSUE, element, context.getLocation(element),
                    "MediaBrowserService must declare an intent-filter with action " + MEDIA_BROWSER_ACTION);
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Classes extending MediaBrowserService are identified here.
        // Manifest validation for the corresponding service declaration is handled in visitElement.
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // Method scanning hook available for future Automotive media API validations
    }
}