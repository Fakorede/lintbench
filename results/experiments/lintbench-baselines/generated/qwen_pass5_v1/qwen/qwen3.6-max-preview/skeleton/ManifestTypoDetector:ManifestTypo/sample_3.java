package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged. " +
                    "Misspelled tags are silently ignored by the Android build system, which can lead to subtle bugs and missing components.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private static final Set<String> KNOWN_TAGS = new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "activity-alias", "service", "receiver",
            "provider", "uses-permission", "uses-permission-sdk-23", "uses-sdk", "intent-filter",
            "action", "category", "data", "meta-data", "uses-feature", "supports-screens",
            "compatible-screens", "screen", "instrumentation", "permission", "permission-group",
            "permission-tree", "uses-library", "queries", "package", "intent", "profileable",
            "property", "static-library", "overlay", "protected-broadcast", "adopt-permissions",
            "original-package", "eat-comment", "grant-uri-permission", "path-permission",
            "uses-native-library", "uses-config", "library", "certificates", "certificate"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!context.file.getName().equals("AndroidManifest.xml")) {
            return;
        }

        String tag = element.getTagName();
        if (KNOWN_TAGS.contains(tag)) {
            return;
        }

        String closest = null;
        int minDist = 3;
        for (String known : KNOWN_TAGS) {
            int dist = getLevenshteinDistance(tag, known);
            if (dist < minDist) {
                minDist = dist;
                closest = known;
            }
        }

        if (closest != null && tag.length() > 2) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Suspicious tag name `" + tag + "`: did you mean `" + closest + "`?");
        }
    }

    private static int getLevenshteinDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        return prev[b.length()];
    }
}