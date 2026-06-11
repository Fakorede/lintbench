package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UastScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import org.jetbrains.uast.UCallExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AlwaysShowActionDetector extends Detector implements UastScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Using `showAsAction=always` in menu XML or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide.",
            "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead. If `always` is used sparingly there are usually no problems and behavior is roughly equivalent to `ifRoom` but with preference over other `ifRoom` items. Using it more than twice in the same menu is a bad idea.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    private Map<String, Integer> alwaysCount = new HashMap<>();
    private boolean ifRoomUsed;

    @Override
    public void beforeCheckFile(Context context) {
        super.beforeCheckFile(context);
        alwaysCount.clear();
        ifRoomUsed = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr showAsActionAttr = element.getAttributeNode("showAsAction");
        if (showAsActionAttr != null) {
            String value = showAsActionAttr.getValue();
            if ("always".equals(value)) {
                alwaysCount.put(element.getTagName(), alwaysCount.getOrDefault(element.getTagName(), 0) + 1);
            } else if ("ifRoom".equals(value)) {
                ifRoomUsed = true;
            }
        }
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        for (Map.Entry<String, Integer> entry : alwaysCount.entrySet()) {
            String tagName = entry.getKey();
            int count = entry.getValue();

            if ((count > 2 || (!ifRoomUsed && count > 0)) && !context.getDriver().isIncremental()) {
                context.report(ISSUE,
                        Location.create(context.getFile(), document),
                        "More than two `always` actions or no `ifRoom` action found in menu XML");
            }
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setShowAsAction");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node) {
        if (node.getValueArgumentCount() > 0 && "MenuItem.SHOW_AS_ACTION_ALWAYS".equals(node.getValueArgument(0).getPsi().getText())) {
            alwaysCount.put("java", alwaysCount.getOrDefault("java", 0) + 1);
        } else if ("MenuItem.SHOW_AS_ACTION_IF_ROOM".equals(node.getValueArgument(0).getPsi().getText())) {
            ifRoomUsed = true;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        int javaCount = alwaysCount.getOrDefault("java", 0);
        if ((javaCount > 2 || (!ifRoomUsed && javaCount > 0)) && !context.getDriver().isIncremental()) {
            context.report(ISSUE,
                    Location.create(context.getFile()),
                    "More than two `always` actions or no `ifRoom` action found in Java code");
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MENU.equals(folderType);
    }

}