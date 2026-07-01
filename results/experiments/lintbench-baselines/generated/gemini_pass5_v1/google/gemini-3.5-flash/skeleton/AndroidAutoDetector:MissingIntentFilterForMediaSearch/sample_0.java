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
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should also register an "
                            + "intent-filter for the action android.media.action.MEDIA_PLAY_FROM_SEARCH "
                            + "to your activity or service.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final List<ServiceDeclaration> mMediaServices = new ArrayList<>();
    private boolean mHasMediaPlayFromSearch = false;

    private static class ServiceDeclaration {
        final Location location;
        final String name;

        ServiceDeclaration(Location location, String name) {
            this.location = location;
            this.name = name;
        }
    }

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
        mMediaServices.clear();
        mHasMediaPlayFromSearch = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("service".equals(tagName)) {
            if (hasAction(element, "android.media.browse.MediaBrowserService")) {
                String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                mMediaServices.add(new ServiceDeclaration(context.getLocation(element), name));
            }
        }

        if ("service".equals(tagName) || "activity".equals(tagName)) {
            if (hasAction(element, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                mHasMediaPlayFromSearch = true;
            }
        }
    }

    private boolean hasAction(Element element, String actionName) {
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                NodeList filterChildren = child.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node filterChild = filterChildren.item(j);
                    if (filterChild.getNodeType() == Node.ELEMENT_NODE && "action".equals(filterChild.getNodeName())) {
                        Element actionElement = (Element) filterChild;
                        String name = actionElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                        if (actionName.equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mMediaServices.isEmpty() && !mHasMediaPlayFromSearch) {
            for (ServiceDeclaration service : mMediaServices) {
                context.report(
                        ISSUE,
                        service.location,
                        "Missing `android.media.action.MEDIA_PLAY_FROM_SEARCH` intent-filter"
                );
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Handled via manifest analysis
    }
}