package com.android.tools.lint.checks;

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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an intent-filter for the action "
                    + "android.media.action.MEDIA_PLAY_FROM_SEARCH. To do this, add an <intent-filter> with "
                    + "<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" /> to your <activity> or <service>.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE));

    @Override
    public boolean appliesTo(Context context, com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        // Initialization hook for project-wide analysis
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        boolean hasFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element filter = (Element) child;
                NodeList actions = filter.getElementsByTagName(TAG_ACTION);
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    String name = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                        hasFilter = true;
                        break;
                    }
                }
            }
            if (hasFilter) break;
        }
        if (!hasFilter) {
            context.report(ISSUE, element, context.getLocation(element),
                    "To support voice searches on Android Auto, register an intent-filter for "
                            + "`android.media.action.MEDIA_PLAY_FROM_SEARCH` in this <" + element.getTagName() + ">");
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("android.service.media.MediaBrowserService", "androidx.media.MediaBrowserServiceCompat");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        boolean hasMethod = false;
        for (PsiMethod method : declaration.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                hasMethod = true;
                break;
            }
        }
        if (!hasMethod) {
            context.report(ISSUE, declaration, context.getNameLocation(declaration),
                    "To support voice searches on Android Auto, implement `onPlayFromSearch` and ensure the manifest "
                            + "registers an intent-filter for `android.media.action.MEDIA_PLAY_FROM_SEARCH`");
        }
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
        // Hook for method-level analysis if required
    }
}