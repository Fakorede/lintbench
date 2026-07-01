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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should register an "
                            + "intent-filter for the action "
                            + "android.media.action.MEDIA_PLAY_FROM_SEARCH in your media browser "
                            + "service or activity.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Set<String> mMediaBrowserServices = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.ANDROID_MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaBrowserServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!isMediaBrowserService(context, element)) {
            return;
        }

        if (!hasIntentFilterForAction(element, MEDIA_PLAY_FROM_SEARCH)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Add an intent-filter for "
                            + MEDIA_PLAY_FROM_SEARCH
                            + " to support voice searches on Android Auto.");
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.browse.MediaBrowserService",
                "android.support.v4.media.MediaBrowserServiceCompat",
                "androidx.media.MediaBrowserServiceCompat");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mMediaBrowserServices.add(qualifiedName);
        }
    }

    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // Not needed for this check.
    }

    private boolean isMediaBrowserService(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("service".equals(tag) || "activity".equals(tag)) {
            if (hasIntentFilterForAction(element, MEDIA_BROWSER_SERVICE_ACTION)) {
                return true;
            }

            if ("service".equals(tag)) {
                String name = element.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    String packageName = context.getMainProject().getPackage();
                    String fullName = getFullClassName(name, packageName);
                    if (mMediaBrowserServices.contains(fullName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasIntentFilterForAction(@NonNull Element element, @NonNull String action) {
        NodeList filters = element.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            NodeList actions = filter.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                Element actionElement = (Element) actions.item(j);
                String name = actionElement.getAttribute("name");
                if (action.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getFullClassName(@NonNull String name, @NonNull String packageName) {
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.contains(".")) {
            return name;
        }
        return packageName + "." + name;
    }
}