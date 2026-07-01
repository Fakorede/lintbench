package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should also register an "
                            + "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH` "
                            + "to your `<activity>` or `<service>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasMediaBrowserService = false;
    private boolean mHasMediaPlayFromSearch = false;
    private final List<Location> mServiceLocations = new ArrayList<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("service", "activity");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasMediaBrowserService = false;
        mHasMediaPlayFromSearch = false;
        mServiceLocations.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("service".equals(tagName)) {
            if (hasIntentFilterAction(element, "android.media.browse.MediaBrowserService")) {
                mHasMediaBrowserService = true;
                mServiceLocations.add(context.getLocation(element));
            }
        }
        if (hasIntentFilterAction(element, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
            mHasMediaPlayFromSearch = true;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.browse.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat",
                "android.support.v4.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mHasMediaBrowserService = true;
        mServiceLocations.add(context.getNameLocation(declaration));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasMediaBrowserService && !mHasMediaPlayFromSearch) {
            if (!mServiceLocations.isEmpty()) {
                for (Location location : mServiceLocations) {
                    context.report(
                            ISSUE,
                            location,
                            "Missing intent-filter for MediaPlayFromSearch"
                    );
                }
            } else {
                context.report(
                        ISSUE,
                        Location.create(context.file),
                        "Missing intent-filter for MediaPlayFromSearch"
                );
            }
        }
    }

    private boolean hasIntentFilterAction(Element element, String actionName) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList filterChildren = intentFilter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node filterChild = filterChildren.item(j);
                    if (filterChild instanceof Element && "action".equals(filterChild.getNodeName())) {
                        Element action = (Element) filterChild;
                        String name = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                        if (name.isEmpty()) {
                            name = action.getAttribute("android:name");
                        }
                        if (actionName.equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}