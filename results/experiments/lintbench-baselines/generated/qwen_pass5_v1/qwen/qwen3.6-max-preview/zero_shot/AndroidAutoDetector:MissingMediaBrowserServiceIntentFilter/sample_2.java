package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.SdkUtils;
import com.intellij.psi.PsiClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends " +
            "`android.service.media.MediaBrowserService` with an `intent-filter` for the action " +
            "`android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n" +
            "To do this, add\n" +
            "```xml\n" +
            "<intent-filter>\n" +
            "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n" +
            "</intent-filter>\n" +
            "```\n" +
            "to the service that extends `android.service.media.MediaBrowserService`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE));

    private static final String MEDIA_BROWSER_SERVICE = "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT = "androidx.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        String fqn = SdkUtils.resolveClassName(name, context.getPackageName());
        PsiClass psiClass = context.getEvaluator().findClass(fqn);
        if (psiClass == null) {
            return;
        }

        boolean extendsMediaBrowserService = context.getEvaluator().extendsClass(psiClass, MEDIA_BROWSER_SERVICE, false)
                || context.getEvaluator().extendsClass(psiClass, MEDIA_BROWSER_SERVICE_COMPAT, false);

        if (!extendsMediaBrowserService) {
            return;
        }

        boolean hasRequiredIntentFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getElementsByTagName(SdkConstants.TAG_ACTION);
                for (int j = 0, m = actions.getLength(); j < m; j++) {
                    Element action = (Element) actions.item(j);
                    String actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                        hasRequiredIntentFilter = true;
                        break;
                    }
                }
            }
            if (hasRequiredIntentFilter) {
                break;
            }
        }

        if (!hasRequiredIntentFilter) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Media browser services must provide an intent filter for the " +
                    "`android.media.browse.MediaBrowserService` action");
        }
    }
}