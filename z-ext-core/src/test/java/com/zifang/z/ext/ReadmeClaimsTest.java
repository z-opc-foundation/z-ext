package com.zifang.z.ext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * README 里每一条定量主张都必须能从源码重算出来 —— 广告出去的话就是承诺。
 *
 * <p>本仓的典型漂移源：结构树里的类数与 test 文件数、前端 dev 端口与代理目标、
 * 「本仓不启 HTTP 服务」这类<b>否定式</b>断言（要靠「真的一个都没有」来证），
 * 以及测试类清单（自指计数，加了守卫必然漂）。</p>
 *
 * <p>口径：</p>
 * <ol>
 *   <li><b>主张抠不到就红</b>（不许「没匹配上就跳过」，那正是空跑）；</li>
 *   <li><b>数值不等就红</b>，且把<b>重算值</b>打进消息；</li>
 *   <li><b>只数 src/main</b>：测试类不是 README 说的「类」。</li>
 * </ol>
 *
 * <p><b>实现上最要紧的一条</b>：README 侧的数字必须在核对<b>当下</b>从被核对的那份 README 里现解析，
 * 不能先算好再拿自己比 —— 否则尺子做的是恒等式比较，README 怎么改都是绿的。
 * 所以 {@link Facts} 里<b>只有源码侧</b>的事实。</p>
 */
class ReadmeClaimsTest {

    /** 仓根：向上找同时有 README.md 与 pom.xml 的目录（测试跑在模块目录，user.dir 不是仓根）。 */
    private static Path repoRoot() {
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 5 && p != null; i++, p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("README.md")) && Files.isRegularFile(p.resolve("pom.xml"))) {
                return p;
            }
        }
        throw new IllegalStateException("定位仓根失败：user.dir=" + System.getProperty("user.dir"));
    }

    private static String read(Path f) {
        try {
            return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> lines(String readme) {
        return Arrays.asList(readme.split("\n", -1));
    }

    /**
     * README 结构树里的某一行（树形前缀 + 节点名 + 终止条件）。
     *
     * <p>前缀允许 {@code │} —— 嵌套在别的节点下的行长这样：
     * {@code │       └── vite.config.js}，只认 {@code ├──} / {@code └──} 会漏掉它。</p>
     */
    private static String treeLine(String readme, String node) {
        Pattern p = Pattern.compile("^\\s*(?:│[ \\t]*)*(?:├──|└──)[ \\t]*" + Pattern.quote(node)
                + "(?![\\w-])[^\\n]*$");
        List<String> hits = new ArrayList<>();
        for (String l : lines(readme)) {
            if (p.matcher(l).find()) {
                hits.add(l);
            }
        }
        if (hits.isEmpty()) {
            throw new AssertionError("README 结构树里找不到节点行：" + node);
        }
        if (hits.size() > 1) {
            throw new AssertionError("README 结构树里节点定位不唯一（命中 " + hits.size() + " 行）：" + node);
        }
        return hits.get(0);
    }

    /** 唯一匹配的行；找不到或不唯一都红。 */
    private static String uniqueLine(String readme, Pattern anchor, String label) {
        List<String> hits = new ArrayList<>();
        for (String l : lines(readme)) {
            if (anchor.matcher(l).find()) {
                hits.add(l);
            }
        }
        if (hits.isEmpty()) {
            throw new AssertionError("README 里找不到主张行：" + label + "（定位 " + anchor.pattern() + "）");
        }
        if (hits.size() > 1) {
            throw new AssertionError("README 里主张行定位不唯一（命中 " + hits.size() + " 行）：" + label);
        }
        return hits.get(0);
    }

    /** 根 pom 剥掉 XML 注释后的正文。 */
    private static String rootPomText(Path root) {
        return read(root.resolve("pom.xml")).replaceAll("(?s)<!--.*?-->", "");
    }

    private static List<Path> filesUnder(Path root, String globDir) {
        Path d = root.resolve(globDir);
        if (!Files.isDirectory(d)) {
            return new ArrayList<>();
        }
        try (Stream<Path> s = Files.walk(d)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> !p.toString().contains("/node_modules/"))
                    .filter(p -> !p.toString().contains("/dist/"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 一次重算出的**源码侧**事实（README 侧的数字在核对时现抠）。 */
    private static final class Facts {
        final Path root;
        final String ownVersion;
        final boolean hasRevision;
        final long coreMainClasses;
        final long runtimeConfigFiles;
        final long controllerAnnotations;
        final List<String> webMainClasses;
        final String webMainBody;
        final int vitePort;
        final String viteProxyTarget;
        final boolean hasFrontendComponent;
        final boolean hasFrontendSuit;
        final List<String> namedTestClasses;

        Facts(Path root, String ownVersion, boolean hasRevision, long coreMainClasses,
              long runtimeConfigFiles, long controllerAnnotations, List<String> webMainClasses,
              String webMainBody, int vitePort, String viteProxyTarget, boolean hasFrontendComponent,
              boolean hasFrontendSuit, List<String> namedTestClasses) {
            this.root = root;
            this.ownVersion = ownVersion;
            this.hasRevision = hasRevision;
            this.coreMainClasses = coreMainClasses;
            this.runtimeConfigFiles = runtimeConfigFiles;
            this.controllerAnnotations = controllerAnnotations;
            this.webMainClasses = webMainClasses;
            this.webMainBody = webMainBody;
            this.vitePort = vitePort;
            this.viteProxyTarget = viteProxyTarget;
            this.hasFrontendComponent = hasFrontendComponent;
            this.hasFrontendSuit = hasFrontendSuit;
            this.namedTestClasses = namedTestClasses;
        }
    }

    private static Facts recomputed() {
        Path root = repoRoot();
        String pom = rootPomText(root);

        String ownVersion = "";
        int afterParent = pom.indexOf("</parent>");
        String tail = afterParent >= 0 ? pom.substring(afterParent) : pom;
        Matcher vm = Pattern.compile("<version>([^<]+)</version>").matcher(tail);
        if (vm.find()) {
            ownVersion = vm.group(1).trim();
        }
        boolean hasRevision = pom.contains("<revision>");

        long coreMain = filesUnder(root, "z-ext-core/src/main").stream()
                .filter(p -> p.toString().endsWith(".java"))
                .count();

        long runtimeConfig = filesUnder(root, "").stream()
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return n.startsWith("application") && n.endsWith(".yml")
                            || n.equals("logback.xml")
                            || n.startsWith("application") && n.endsWith(".yaml");
                })
                .count();

        long controllerAnnotations = 0;
        for (Path p : filesUnder(root, "z-ext-core/src/main")) {
            if (!p.toString().endsWith(".java")) {
                continue;
            }
            String src = read(p);
            Matcher cm = Pattern.compile("@(RestController|Controller|RequestMapping)\\b").matcher(src);
            while (cm.find()) {
                controllerAnnotations++;
            }
        }
        for (Path p : filesUnder(root, "z-ext-web/src/main")) {
            if (!p.toString().endsWith(".java")) {
                continue;
            }
            Matcher cm = Pattern.compile("@(RestController|Controller|RequestMapping)\\b").matcher(read(p));
            while (cm.find()) {
                controllerAnnotations++;
            }
        }

        List<String> webMain = filesUnder(root, "z-ext-web/src/main").stream()
                .filter(p -> p.toString().endsWith(".java"))
                .map(p -> p.getFileName().toString().replace(".java", ""))
                .sorted()
                .collect(Collectors.toList());
        String webMainBody = webMain.size() == 1
                ? read(root.resolve("z-ext-web/src/main/java/com/zifang/Main.java"))
                : "";

        String vite = read(root.resolve("_frontend/z-ext-suit/vite.config.js"));
        Matcher pm = Pattern.compile("port:\\s*(\\d+)").matcher(vite);
        int vitePort = pm.find() ? Integer.parseInt(pm.group(1)) : -1;
        Matcher tm = Pattern.compile("proxy:\\s*\\{[^}]*target:\\s*'([^']+)'").matcher(vite);
        String proxyTarget = tm.find() ? tm.group(1) : "";

        return new Facts(root, ownVersion, hasRevision, coreMain, runtimeConfig, controllerAnnotations,
                webMain, webMainBody, vitePort, proxyTarget,
                Files.isDirectory(root.resolve("_frontend/z-ext-component")),
                Files.isDirectory(root.resolve("_frontend/z-ext-suit")),
                new ArrayList<>());
    }

    /** README 里逐条抠出主张并与源码重算值对账。 */
    private static void verifyClaims(String readme, Facts f) {
        // 主张 1：当前版本 ⟺ 根 POM 自身的 <version>
        String verLine = uniqueLine(readme, Pattern.compile("\\*\\*当前版本\\*\\*"), "当前版本");
        Matcher vm = Pattern.compile("`([^`]+)`").matcher(verLine);
        if (!vm.find()) {
            throw new AssertionError("README 的「当前版本」那一行抠不出版本号：" + verLine);
        }
        assertEquals(f.ownVersion, vm.group(1),
                "当前版本对不上：README 写 " + vm.group(1) + "，根 POM 自身的 <version> 是 " + f.ownVersion);

        // 主张 2：本仓没有用 <revision> 占位
        assertTrue(verLine.contains("没有") && verLine.contains("<revision>"),
                "README 的「当前版本」那一行没声明本仓不用 <revision>：" + verLine);
        assertTrue(!f.hasRevision,
                "README 说本仓没用 <revision> 占位，但根 POM 里就有 <revision>");

        // 主张 3：z-ext-core 的 main 类数
        String coreLine = treeLine(readme, "z-ext-core/");
        Matcher cm = Pattern.compile("（(\\d+) 个 main 类").matcher(coreLine);
        if (!cm.find()) {
            throw new AssertionError("README 结构树的 z-ext-core 那一行抠不出「N 个 main 类」：" + coreLine);
        }
        assertEquals(f.coreMainClasses, Long.parseLong(cm.group(1)),
                "z-ext-core 的 main 类数对不上：README 写 " + cm.group(1) + "，实数是 " + f.coreMainClasses);

        // 主张 3b：正文里「全部实现都在 …（N 个 main 类）」是同一个数的**第二处表述**。
        //         少钉这一条，结构树那行改了、这句不改，就成了没人管的一句旧话。
        String implLine = uniqueLine(readme, Pattern.compile("全部实现都在"), "实现位置");
        Matcher im = Pattern.compile("（(\\d+) 个 main 类").matcher(implLine);
        if (!im.find()) {
            throw new AssertionError("README 的「全部实现都在」那一行抠不出「（N 个 main 类」：" + implLine);
        }
        assertEquals(f.coreMainClasses, Long.parseLong(im.group(1)),
                "z-ext-core 的 main 类数对不上：README 正文写 " + im.group(1) + "，实数是 " + f.coreMainClasses);

        // 主张 4：本仓不启 HTTP 服务 —— 否定式断言，要靠「真的一个都没有」来证
        String portLine = uniqueLine(readme, Pattern.compile("\\*\\*默认端口\\*\\*"), "默认端口");
        assertTrue(portLine.contains("无"),
                "README 的「默认端口」那一行没写「无」：" + portLine);
        assertEquals(0L, f.controllerAnnotations,
                "README 说本仓没有 @RestController/@RequestMapping，但源码里找到了 " + f.controllerAnnotations + " 处");
        assertEquals(0L, f.runtimeConfigFiles,
                "README 说本仓没有 application*.yml / logback.xml，但盘面上有 " + f.runtimeConfigFiles + " 个");

        // 主张 5：全仓 0 个 @Controller / @RequestMapping
        String apiLine = uniqueLine(readme, Pattern.compile("全仓 0 个 `@Controller`"), "HTTP API 段");
        assertTrue(apiLine.contains("@RequestMapping"),
                "README 的 HTTP API 段没同时点名 @RequestMapping：" + apiLine);
        assertEquals(0L, f.controllerAnnotations,
                "README 说全仓 0 个 @Controller/@RequestMapping，实数是 " + f.controllerAnnotations);

        // 主张 6：application*.yml / logback.xml 实测 0 个
        String cfgLine = uniqueLine(readme, Pattern.compile("`application\\*\\.yml` / `logback\\.xml` 实测"), "运行时配置");
        Matcher zm = Pattern.compile("实测 (\\d+) 个").matcher(cfgLine);
        if (!zm.find()) {
            throw new AssertionError("README 的运行时配置那一行抠不出个数：" + cfgLine);
        }
        assertEquals(f.runtimeConfigFiles, Long.parseLong(zm.group(1)),
                "运行时配置文件数对不上：README 写 " + zm.group(1) + "，实数是 " + f.runtimeConfigFiles);

        // 主张 7：z-ext-web 只有一个打印 Hello and welcome! 的 Main
        String webLine = treeLine(readme, "z-ext-web/");
        assertTrue(webLine.contains("Main.java") && webLine.contains("Hello and welcome!"),
                "README 结构树的 z-ext-web 那一行没点名 Main.java / Hello and welcome!：" + webLine);
        assertEquals(List.of("Main"), f.webMainClasses,
                "z-ext-web/src/main 下的类与 README 说的「只有一个 Main」对不上");
        assertTrue(f.webMainBody.contains("Hello and welcome!"),
                "z-ext-web 的 Main 里没有打印 Hello and welcome!");

        // 主张 8：前端 dev 端口与代理目标
        String viteLine = treeLine(readme, "vite.config.js");
        Matcher vp = Pattern.compile("dev 端口 (\\d+)").matcher(viteLine);
        if (!vp.find()) {
            throw new AssertionError("README 结构树的 vite.config.js 那一行抠不出 dev 端口：" + viteLine);
        }
        assertEquals(f.vitePort, Integer.parseInt(vp.group(1)),
                "前端 dev 端口对不上：README 写 " + vp.group(1) + "，vite.config.js 是 " + f.vitePort);
        assertTrue(viteLine.contains(f.viteProxyTarget),
                "README 结构树的 vite.config.js 那一行没写实际的 proxy 目标 " + f.viteProxyTarget + "：" + viteLine);
        assertTrue(f.hasFrontendComponent && f.hasFrontendSuit,
                "README 结构树列了 _frontend 下的两个工程，但盘面上目录不全");

        // 主张 9：点名的测试类都要真实存在（点名不计数 —— 计数是自指的，必然漂）
        for (String cls : new String[]{"ExtRegistryTest", "ExtRouterTest", "ExtProxyFactoryTest",
                "ExtInvocationHandlerTest", "ExtIntegrationTest", "ScriptBackedImplNamingTest"}) {
            assertTrue(readme.contains(cls), "README 的测试清单没点名测试类 " + cls);
            assertTrue(filesUnder(f.root, "z-ext-core/src/test").stream()
                            .anyMatch(p -> p.getFileName().toString().equals(cls + ".java")),
                    "README 点名了测试类 " + cls + "，但仓里找不到这个测试文件");
        }
    }

    @Test
    @DisplayName("README 的定量主张与源码重算值逐条对账")
    void readmeClaimsMatchTheCode() {
        String readme = read(repoRoot().resolve("README.md"));
        verifyClaims(readme, recomputed());
    }

    @Test
    @DisplayName("尺子的牙口：把 README 的数改错，判定必须判红")
    void rulerActuallyCondemnsWrongNumbers() {
        String readme = read(repoRoot().resolve("README.md"));
        Facts f = recomputed();

        // 变异 1：结构树里的 main 类数改错（瞄准结构树那一行，正文那一处是另一条主张）
        String tamperedClasses = readme.replaceFirst(
                "(├── z-ext-core/[^\\n]*（)(\\d+)( 个 main 类)",
                "$1" + (f.coreMainClasses + 1) + "$3");
        assertNotEquals(readme, tamperedClasses, "篡改失败：README 结构树里找不到 z-ext-core 那一行的「（N 个 main 类」");
        AssertionError err1 = assertThrows(AssertionError.class, () -> verifyClaims(tamperedClasses, f));
        assertTrue(err1.getMessage().contains("main 类数对不上"),
                "main 类数改错必须判红，实际是：" + err1.getMessage());

        // 变异 2：dev 端口改错
        String tamperedPort = readme.replaceFirst("dev 端口 \\d+", "dev 端口 " + (f.vitePort + 1));
        assertNotEquals(readme, tamperedPort, "篡改失败：README 里找不到「dev 端口 N」");
        AssertionError err2 = assertThrows(AssertionError.class, () -> verifyClaims(tamperedPort, f));
        assertTrue(err2.getMessage().contains("前端 dev 端口对不上"),
                "dev 端口改错必须判红，实际是：" + err2.getMessage());

        // 变异 3：当前版本改错
        String tamperedVersion = readme.replaceFirst("\\*\\*当前版本\\*\\* \\| `[^`]+`",
                "**当前版本** | `9.9.9`");
        assertNotEquals(readme, tamperedVersion, "篡改失败：README 里找不到「**当前版本** | `X.Y.Z`」");
        AssertionError err3 = assertThrows(AssertionError.class, () -> verifyClaims(tamperedVersion, f));
        assertTrue(err3.getMessage().contains("当前版本对不上"),
                "当前版本改错必须判红，实际是：" + err3.getMessage());
    }

    @Test
    @DisplayName("不许冤枉健康页：真实 README 不得被判红")
    void healthyReadmeIsNotCondemned() {
        String readme = read(repoRoot().resolve("README.md"));
        verifyClaims(readme, recomputed());
    }
}
