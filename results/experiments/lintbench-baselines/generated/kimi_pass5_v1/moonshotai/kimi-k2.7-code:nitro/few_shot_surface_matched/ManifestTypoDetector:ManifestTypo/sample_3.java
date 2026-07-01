package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Manifest Typo",
                    "This check looks through the AndroidManifest.xml file and flags any element"
                            + " tags that appear to be misspellings of known manifest tags. A"
                            + " misspelled tag is ignored by the build system and can lead to"
                            + " missing components, permissions, or features.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    // Known AndroidManifest.xml element names (not exhaustive; covers the standard set).
    private static final String[] VALID_TAGS = {
        "manifest",
        "application",
        "activity",
        "activity-alias",
        "service",
        "receiver",
        "provider",
        "intent-filter",
        "action",
        "category",
        "data",
        "meta-data",
        "uses-permission",
        "uses-permission-sdk-23",
        "permission",
        "permission-group",
        "permission-tree",
        "uses-sdk",
        "uses-feature",
        "uses-library",
        "uses-configuration",
        "uses-split",
        "uses-static-library",
        "uses-native-library",
        "supports-screens",
        "supports-gl-texture",
        "compatible-screens",
        "screen",
        "screen-compatible",
        "instrumentation",
        "original-package",
        "adopt-permissions",
        "eat-comment",
        "path-permission",
        "grant-uri-permission",
        "queries",
        "package",
        "intent",
        "attribution",
        "feature-group",
        "overlay"
    };

    @Override
    public Collection<String> getApplicableElements() {
        // Visit every element in the manifest.
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        int colon = tag.indexOf(':');
        if (colon != -1) {
            tag = tag.substring(colon + 1);
        }

        if (isKnownTag(tag)) {
            return;
        }

        String closest = findClosestTag(tag);
        if (closest != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Possible manifest typo: `<" + tag + ">` did you mean `<" + closest + ">`?");
        }
    }

    private static boolean isKnownTag(String tag) {
        for (String valid : VALID_TAGS) {
            if (valid.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private static String findClosestTag(String tag) {
        String closest = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String valid : VALID_TAGS) {
            int distance = editDistance(tag, valid);
            if (distance < bestDistance && distance <= 2) {
                bestDistance = distance;
                closest = valid;
            }
        }
        return closest;
    }

    private static int editDistance(String s, String t) {
        int m = s.length();
        int n = t.length();
        if (m == 0) {
            return n;
        }
        if (n == 0) {
            return m;
        }

        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            char sc = s.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = (sc == t.charAt(j - 1)) ? 0 : 1;
                curr[j] =
                        Math.min(
                                Math.min(curr[j - 1] + 1, prev[j] + 1),
                                prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[n];
    }
}