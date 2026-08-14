package io.github.easy30.vue4j.util;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * esbuild 二进制管理 + 转译调用
 */
@Slf4j
public class EsbuildUtil {

    private static final String ESBUILD_VERSION = "0.28.0";
    private static volatile File esbuildBinary = null;

    public static void init(File defaultFile) throws Exception {
        File platformBin=null;
        File plainBin=null;
        String url =null;
        try {
           if (defaultFile.exists()) {
               esbuildBinary = defaultFile;
               return;
           }

           File dir = getDownloadDir();
           String platform = detectPlatform();
             platformBin = new File(new File(dir, platform), "esbuild");
           if (platformBin.exists()) {
               esbuildBinary = platformBin;
               return;
           }

             plainBin = new File(dir, "esbuild");
           if (plainBin.exists()) {
               esbuildBinary = plainBin;
               return;
           }

           platformBin.getParentFile().mkdirs();
           url = "https://registry.npmjs.org/@esbuild/" + platform + "/-/" + platform + "-" + ESBUILD_VERSION + ".tgz";
           download(url,platformBin);
           platformBin.setExecutable(true);
           esbuildBinary = platformBin;
       }finally {
           if (esbuildBinary == null) log.error("Cannot find esbuild binary in {},{},{}. \nYou can download from {} and copy to the correct path.",defaultFile, platformBin,plainBin,url);
           else log.info("Using esbuild binary: {}", esbuildBinary);
       }
    }
    /** 获取 esbuild 二进制，不存在则自动下载 */
    public static File getBinary() throws Exception {
              return esbuildBinary;

    }

    /** 用 esbuild 转译 JS/TS */
    public static String transpile(String source, String target, boolean esm) throws Exception {
        File bin = getBinary();
        List<String> args = new ArrayList<>();
        args.add(bin.getAbsolutePath());
        args.add("--loader=ts");
        args.add("--target=" + target);
        if (esm) args.add("--format=esm");
        args.add("--minify=false");
        args.add("--sourcemap=inline");

        ProcessBuilder pb = new ProcessBuilder(args);
        try { pb.directory(getJarDir()); } catch (Exception ignored) {}
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (OutputStream os = p.getOutputStream()) {
            os.write(source.getBytes(StandardCharsets.UTF_8));
        }

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = p.getInputStream().read(data)) != -1) buf.write(data, 0, n);
        String result = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        if (exit != 0) throw new IOException("esbuild exit " + exit + ":\n" + result);
        return result;
    }

    /** 转译 ES2020（给浏览器用） */
    public static String transpileEs2020(String source) throws Exception {
        return transpile(source, "es2020", true);
    }

    /** 转译 ES6（给解析器用，不加 --format 避免 __commonJS 外壳） */
    public static String transpileEs6(String source) throws Exception {
        return transpile(source, "es2015", false);
    }

    // ==================== 内部 ====================

    private static File getDownloadDir() {
        /*try {
            ProtectionDomain pd = EsbuildUtil.class.getProtectionDomain();
            if (pd != null && pd.getCodeSource() != null) {
                URI jarUri = pd.getCodeSource().getLocation().toURI();
                File jarFile = new File(jarUri);
                if (jarFile.isFile() && jarFile.getName().endsWith(".jar")) {
                    File dir = new File(jarFile.getParentFile(), "esbuild");
                    dir.mkdirs();
                    return dir;
                }
            }
        } catch (Exception e) { log.debug("Cannot determine JAR dir: {}", e.getMessage()); }*/
        File dir = new File(System.getProperty("user.home"), ".easy-vue4j/esbuild");
        dir.mkdirs();
        return dir;
    }

    private static File getJarDir() {
        try {
            ProtectionDomain pd = EsbuildUtil.class.getProtectionDomain();
            if (pd != null && pd.getCodeSource() != null) {
                URI jarUri = pd.getCodeSource().getLocation().toURI();
                File jarFile = new File(jarUri);
                if (jarFile.isFile() && jarFile.getName().endsWith(".jar")) {
                    File dir = jarFile.getParentFile();
                    if ("target".equals(dir.getName())) dir = dir.getParentFile();
                    return dir;
                }
            }
        } catch (Exception ignored) {}
        return new File(".");
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        String osName = os.contains("mac") || os.contains("darwin") ? "darwin" : "linux";
        String archName = arch.contains("aarch64") || arch.contains("arm64") ? "arm64"
                : arch.contains("amd64") || arch.contains("x86_64") ? "x64" : "x64";
        return osName + "-" + archName;
    }

    private static void download(String url, File target) throws Exception {

        log.info("Downloading esbuild from {} ...", url);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code != 200) {
            String err = readError(conn);
            throw new IOException("esbuild download failed, HTTP " + code + ": " + err);
        }
        try (InputStream tgz = conn.getInputStream()) {
            extractTarGz(tgz, "package/bin/esbuild", target);
        }
        log.info("esbuild downloaded {} ({} bytes)", target, target.length());
    }

    private static String readError(HttpURLConnection conn) {
        try (InputStream es = conn.getErrorStream()) {
            if (es == null) return "no body";
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] d = new byte[1024]; int n;
            while ((n = es.read(d)) != -1) b.write(d, 0, n);
            return b.toString("UTF-8");
        } catch (Exception e) { return e.getMessage(); }
    }

    private static void extractTarGz(InputStream tgz, String entryPath, File target) throws IOException {
        try (GZIPInputStream gz = new GZIPInputStream(tgz); BufferedInputStream buf = new BufferedInputStream(gz)) {
            byte[] header = new byte[512];
            while (true) {
                int read = readFully(buf, header);
                if (read < 512) break;
                if (isAllZero(header)) break;
                String name = readTarStr(header, 0, 100).trim();
                long size = Long.parseLong(readTarStr(header, 124, 12).trim(), 8);
                if (name.equals(entryPath)) {
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        byte[] d = new byte[8192];
                        long rem = size;
                        while (rem > 0) { int r = buf.read(d, 0, (int) Math.min(d.length, rem)); if (r < 0) break; fos.write(d, 0, r); rem -= r; }
                    }
                    long pad = (512 - (size % 512)) % 512;
                    while (pad > 0) pad -= buf.skip(pad);
                    return;
                }
                long skip = size;
                while (skip > 0) skip -= buf.skip(skip);
                long pad = (512 - (size % 512)) % 512;
                while (pad > 0) pad -= buf.skip(pad);
            }
        }
        throw new IOException("Entry not found: " + entryPath);
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) { int n = in.read(buf, total, buf.length - total); if (n < 0) break; total += n; }
        return total;
    }

    private static boolean isAllZero(byte[] b) { for (byte v : b) if (v != 0) return false; return true; }

    private static String readTarStr(byte[] buf, int off, int len) {
        int end = off;
        while (end < off + len && buf[end] != 0) end++;
        return new String(buf, off, end - off, StandardCharsets.US_ASCII);
    }
}
