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
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should also register an intent-filter for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n\n" +
                    "To do this, add\n" +
                    "```xml\n" +
                    "<intent-filter>\n" +
                    "    <action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />\n" +
                    "</intent-filter>\n" +
                    "```\n" +
                    "to your `<activity>` or `<service>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No state initialization required
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        boolean hasMediaSearchFilter = false;
        NodeList intentFilters = element.getElementsByTagName("intent-filter");
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element filter = (Element) intentFilters.item(i);
            NodeList actions = filter.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String name = action.getAttributeNS(ANDROID_URI, "name");
                if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                    hasMediaSearchFilter = true;
                    break;
                }
            }
            if (hasMediaSearchFilter) break;
        }

        if (!hasMediaSearchFilter) {
            context.report(ISSUE, element, context.getLocation(element),
                "Missing intent-filter for `android.media.action.MEDIA_PLAY_FROM_SEARCH`");
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not applicable for this manifest-based check
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // Not applicable for this manifest-based check
    }
}