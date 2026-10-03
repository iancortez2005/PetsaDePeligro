import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Makes the "no comments" copy of the game: copies the NetBeans project to
 * another folder with every comment removed from the .java files. The code
 * itself is untouched - only comments and the blank lines they leave behind
 * go - so the copy compiles to exactly the same program.
 *
 * Not part of the game (it lives outside src/, so it never goes in the jar).
 * Run it from the project folder with Java 11 or newer, giving the folder
 * to write to (update-no-comments.bat does this, then commits and pushes):
 *
 *     java tools/StripComments.java "..\PetsaDePeligro2-NoComments"
 *
 * The output folder is replaced each time, except for a .git folder inside
 * it, so the copy can be a Git repository that is simply regenerated and
 * committed after each change to the main project.
 *
 * Copied: src/, test/, nbproject/ (minus private/ - this PC's settings),
 * build.xml, manifest.mf and .gitignore. Left out: this tools/ folder, the
 * README, the build outputs, the player's data (saves, leaderboard,
 * settings) and documents.
 */
public class StripComments {

    private static final List<String> COPIED = Arrays.asList("src", "test", "nbproject", "build.xml", "manifest.mf",
            ".gitignore");
    private static final Set<String> SKIPPED_NAMES = new HashSet<>(Arrays.asList("private", "desktop.ini"));
    private static final String KEPT_IN_OUTPUT = ".git";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java tools/StripComments.java <output folder>");
            return;
        }
        Path project = Paths.get("").toAbsolutePath();
        Path output = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.exists(project.resolve("src")) || output.startsWith(project)) {
            System.out.println("Run this from the project folder, with an output folder outside it.");
            return;
        }

        clearExceptGit(output);
        int javaFiles = 0;
        for (String name : COPIED) {
            Path from = project.resolve(name);
            if (!Files.exists(from)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(from)) {
                files = walk.filter(Files::isRegularFile).filter(p -> !isSkipped(project.relativize(p)))
                        .collect(Collectors.toList());
            }
            for (Path file : files) {
                Path to = output.resolve(project.relativize(file).toString());
                Files.createDirectories(to.getParent());
                if (file.toString().endsWith(".java")) {
                    String code = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    Files.write(to, strip(code).getBytes(StandardCharsets.UTF_8));
                    javaFiles++;
                } else {
                    Files.write(to, Files.readAllBytes(file));
                }
            }
        }
        System.out.println("Wrote " + output + " (" + javaFiles + " .java files without comments).");
    }

    private static boolean isSkipped(Path relative) {
        for (Path part : relative) {
            if (SKIPPED_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /** Empties the output folder (creating it if needed), keeping a .git folder if there is one. */
    private static void clearExceptGit(Path output) throws IOException {
        Files.createDirectories(output);
        List<Path> old;
        try (Stream<Path> walk = Files.walk(output)) {
            old = walk.filter(p -> !p.equals(output))
                    .filter(p -> !output.relativize(p).startsWith(KEPT_IN_OUTPUT))
                    .sorted(Comparator.reverseOrder()) // files before the folders that hold them
                    .collect(Collectors.toList());
        }
        for (Path p : old) {
            if (Files.isDirectory(p)) {
                // a folder open in NetBeans or Explorer can't be deleted on Windows;
                // it is emptied anyway and reused when the files are written back
                p.toFile().delete();
            } else {
                Files.delete(p);
            }
        }
    }

    /**
     * Removes // and block comments (Javadoc too) from Java source, leaving
     * string and char literals alone. Lines left empty by a removed comment
     * are dropped, trailing spaces trimmed, and runs of blank lines squeezed
     * to one.
     */
    static String strip(String code) {
        code = code.replace("\r\n", "\n");
        StringBuilder out = new StringBuilder();
        List<Boolean> lineHadComment = new ArrayList<>();
        boolean commentOnThisLine = false;
        int i = 0;
        while (i < code.length()) {
            char c = code.charAt(i);
            char next = i + 1 < code.length() ? code.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < code.length() && code.charAt(i) != '\n') {
                    i++;
                }
                commentOnThisLine = true;
            } else if (c == '/' && next == '*') {
                int end = code.indexOf("*/", i + 2);
                end = end < 0 ? code.length() : end + 2;
                for (int k = i; k < end; k++) {
                    if (code.charAt(k) == '\n') {
                        lineHadComment.add(true);
                        out.append('\n');
                    }
                }
                // keep tokens on either side apart, e.g. "int/*x*/y"
                boolean before = out.length() > 0 && !Character.isWhitespace(out.charAt(out.length() - 1));
                boolean after = end < code.length() && !Character.isWhitespace(code.charAt(end));
                if (before && after) {
                    out.append(' ');
                }
                commentOnThisLine = true;
                i = end;
            } else if (c == '"' || c == '\'') {
                boolean textBlock = c == '"' && code.startsWith("\"\"\"", i);
                String close = textBlock ? "\"\"\"" : String.valueOf(c);
                int k = i + close.length();
                while (k < code.length() && !code.startsWith(close, k)) {
                    k += code.charAt(k) == '\\' ? 2 : 1;
                }
                k = Math.min(code.length(), k + close.length());
                for (int j = i; j < k; j++) {
                    if (code.charAt(j) == '\n') {
                        lineHadComment.add(commentOnThisLine);
                        commentOnThisLine = false;
                    }
                }
                out.append(code, i, k);
                i = k;
            } else {
                if (c == '\n') {
                    lineHadComment.add(commentOnThisLine);
                    commentOnThisLine = false;
                }
                out.append(c);
                i++;
            }
        }
        lineHadComment.add(commentOnThisLine);

        String[] lines = out.toString().split("\n", -1);
        List<String> kept = new ArrayList<>();
        for (int n = 0; n < lines.length; n++) {
            String line = rtrim(lines[n]);
            boolean blank = line.isEmpty();
            if (blank && lineHadComment.get(n)) {
                continue;
            }
            boolean previousBlank = kept.isEmpty() || kept.get(kept.size() - 1).isEmpty();
            if (blank && previousBlank) {
                continue;
            }
            kept.add(line);
        }
        while (!kept.isEmpty() && kept.get(kept.size() - 1).isEmpty()) {
            kept.remove(kept.size() - 1);
        }
        return String.join("\n", kept) + "\n";
    }

    private static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }
}
