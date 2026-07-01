package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import lombok.ast.AstVisitor;
import lombok.ast.Node;

public class AlwaysShowActionDetector extends Detector implements Detector.XmlScanner,
        Detector.JavaScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of `showAsAction=always`",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "
                    + "Java code is usually a deviation from the user interface style guide. Use "
                    + "`ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    Scope.ALL_RESOURCES_SCOPE));

    // ---- XML scanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("item");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mXmlReported) {
            return;
        }

        Document document = element.getOwnerDocument();
        NodeList items = document.getElementsByTagName("item");
        int alwaysCount = 0;
        int ifRoomCount = 0;
        Attr firstAlwaysAttr = null;

        for (int i = 0, n = items.getLength(); i < n; i++) {
            Node node = items.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) node;
            Attr attr = item.getAttributeNodeNS(ANDROID_URI, "showAsAction");
            if (attr == null) {
                continue;
            }
            String value = attr.getValue();
            if (value.contains("always")) {
                alwaysCount++;
                if (firstAlwaysAttr == null) {
                    firstAlwaysAttr = attr;
                }
            }
            if (value.contains("ifRoom")) {
                ifRoomCount++;
            }
        }

        if (alwaysCount > 2 || (alwaysCount > 0 && ifRoomCount == 0)) {
            context.report(ISSUE, firstAlwaysAttr, context.getLocation(firstAlwaysAttr),
                    "Prefer `showAsAction=\"ifRoom\"` instead of `always`");
            mXmlReported = true;
        }
    }

    // ---- Java scanner ----

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }

    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull Node node) {
        String name = node.toString();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(dot + 1);
        }

        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            mHasAlways = true;
            if (mFirstAlwaysLocation == null) {
                mFirstAlwaysLocation = context.getLocation(node);
            }
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            mHasIfRoom = true;
        }
    }

    // ---- Lifecycle callbacks ----

    private boolean mXmlReported;
    private boolean mHasAlways;
    private boolean mHasIfRoom;
    private Location mFirstAlwaysLocation;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mXmlReported = false;
        mHasAlways = false;
        mHasIfRoom = false;
        mFirstAlwaysLocation = null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mXmlReported = false;
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mHasAlways && !mHasIfRoom && mFirstAlwaysLocation != null) {
            context.report(ISSUE, mFirstAlwaysLocation,
                    "Prefer `MenuItem.SHOW_AS_ACTION_IF_ROOM` over "
                            + "`MenuItem.SHOW_AS_ACTION_ALWAYS`");
        }
    }
}