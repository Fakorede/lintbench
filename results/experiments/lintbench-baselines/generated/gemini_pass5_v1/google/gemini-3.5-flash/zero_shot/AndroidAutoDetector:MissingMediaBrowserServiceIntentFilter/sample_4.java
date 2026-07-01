package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends "
                    + "`android.service.media.MediaBrowserService` with an `intent-filter` for the "
                    + "action `android.media.browse.MediaBrowserService` to be able to browse "
                    + "and play media.\n\n"
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
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.service.media.MediaBrowserService");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.isAbstract(declaration)) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        Document manifest = context.getMainProject().getMergedManifest();
        if (manifest == null) {
            return;
        }

        NodeList services = manifest.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            Element serviceElement = (Element) services.item(i);
            String name = serviceElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);

            if (qualifiedName.equals(name) || (name.startsWith(".") && qualifiedName.endsWith(name))) {
                boolean hasAction = false;
                NodeList intentFilters = serviceElement.getElementsByTagName("intent-filter");
                for (int j = 0; j < intentFilters.getLength(); j++) {
                    Element filter = (Element) intentFilters.item(j);
                    NodeList actions = filter.getElementsByTagName("action");
                    for (int k = 0; k < actions.getLength(); k++) {
                        Element action = (Element) actions.item(k);
                        String actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                        if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                            hasAction = true;
                            break;
                        }
                    }
                    if (hasAction) {
                        break;
                    }
                }

                if (!hasAction) {
                    context.report(
                            ISSUE,
                            declaration,
                            context.getNameLocation(declaration),
                            "Service extending `MediaBrowserService` is missing the `android.media.browse.MediaBrowserService` intent-filter."
                    );
                }
                break;
            }
        }
    }
}