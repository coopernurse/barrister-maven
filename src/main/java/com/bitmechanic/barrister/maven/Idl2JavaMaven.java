package com.bitmechanic.barrister.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Author: James Cooper <james@bitmechanic.com>
 * Date: 7/4/13
 *
 * @goal idl2java
 */
public class Idl2JavaMaven extends AbstractMojo {

    /**
     * Path to barrister Python script
     *
     * @parameter expression="${idl2java.barristerScript}" default-value="barrister"
     */
    private String barristerScript;

    /**
     * Comma separated list of IDL filenames. If the name is a directory, all
     * files ending in .idl contained in that directory will be processed.
     *
     * @parameter expression="${idl2java.idlFiles}" default-value="${basedir}/src/main/resources/barrister/"
     */
    private String idlFiles;

    /**
     * Comma separated list of filenames to exclude.
     *
     * @parameter expression="${idl2java.exclude}"
     */
    private String exclude;

    /**
     * If true, generated struct classes will be immutable
     *
     * @parameter expression="${idl2java.immutable}" default-value="false"
     */
    private String immutable;

    /**
     * Name of base Java package to write generated files into.
     * Each IDL file will be generated into a separate package under this base package
     * based on the IDL filename.
     *
     * @parameter expression="${idl2java.basePackage}" default-value="${project.groupId}.${project.artifactId}.generated"
     */
    private String basePackage;

    /**
     * Base source directory to write .java files to
     *
     * @parameter expression="${idl2java.outputDirectory}" default-value="${basedir}/src/main/java"
     */
    private String outputDirectory;

    /**
     * If true, the base directory: outputDirectory + basePackage will be cleaned (all files removed, recursively)
     *
     * @parameter expression="${idl2java.clean}" default-value="false"
     */
    private String clean;

    private File outputDirectoryPlusPackage;

    public void execute() throws MojoExecutionException, MojoFailureException {
        basePackage = sanitizeForJava(basePackage);

        outputDirectoryPlusPackage = new File((outputDirectory + File.separator + basePackageDir()).replace("/", File.separator));
        if (cleanBool()) {
            getLog().info("Cleaning output dir: " + outputDirectoryPlusPackage);
            if (outputDirectoryPlusPackage.exists()) {
                delete(outputDirectoryPlusPackage);
            }
        }
        else {
            getLog().info("Using output dir: " + outputDirectoryPlusPackage + " - consider setting <clean>true</clean> to ensure this directory is clean on build");
        }

        if (!outputDirectoryPlusPackage.isDirectory() && !outputDirectoryPlusPackage.mkdirs()) {
            throw new MojoExecutionException("Unable to create base output directory: " + outputDirectoryPlusPackage);
        }

        getLog().info("Using Barrister script: " + barristerScript);

        for (File idlFile : allIdlFiles()) {
            try {
                translateIdlToJava(idlFile);
            }
            catch (IOException e) {
                throw new MojoExecutionException("Error processing: " + idlFile, e);
            }
        }
    }

    private Set<String> excludeFiles() {
        HashSet<String> set = new HashSet<String>();
        for (String fname : this.exclude.split(",")) {
            set.add(fname);
        }
        return set;
    }

    private Set<File> allIdlFiles() throws MojoExecutionException {
        getLog().debug("Tokenizing idlFiles=" + idlFiles);

        Set<String> excludeFiles = excludeFiles();

        HashSet<File> set = new HashSet<File>();
        for (String frag : this.idlFiles.split(",")) {
            frag = frag.replace("/", File.separator);

            File fileOrDir = new File(frag);
            if (!fileOrDir.exists()) {
                throw new MojoExecutionException("File not found: " + frag);
            }

            idlFilesRecur(set, fileOrDir, excludeFiles);
        }

        if (set.isEmpty()) {
            getLog().info("No IDL files found in: " + idlFiles);
        }

        return set;
    }

    private void idlFilesRecur(HashSet<File> set, File fileOrDir, Set<String> excludeFiles) {
        if (fileOrDir.isDirectory()) {
            for (File child : fileOrDir.listFiles()) {
                if (child.isDirectory()) {
                    idlFilesRecur(set, child, excludeFiles);
                }
                else if (child.getName().endsWith(".idl")) {
                    if (excludeFiles.contains(child.getName())) {
                        getLog().debug("Excluding file: " + child);
                    }
                    else {
                        set.add(child);
                    }
                }
            }
        }
        else {
            set.add(fileOrDir);
        }
    }

    private String basePackageDir() {
        return basePackage.replace(".", "/").replace("/", File.separator);
    }

    private void translateIdlToJava(File idlFile) throws IOException, MojoExecutionException {
        File jsonFile = new File(outputDirectoryPlusPackage, idlFile.getName().replace(".idl", ".json"));
        jsonFile.getParentFile().mkdirs();

        getLog().info("Translating: " + idlFile + " to: " + jsonFile);

        translateIdlToJson(idlFile, jsonFile);

        try {
            new com.bitmechanic.barrister.Idl2Java(jsonFile.getAbsolutePath(),
                    idlFileToBasePackage(idlFile.getName()),
                    basePackage,
                    outputDirectory,
                    immutableBool());
        }
        catch (Exception e) {
            throw new MojoExecutionException("Error running idl2java with params: " + jsonFile.getAbsolutePath() +
                    idlFileToBasePackage(idlFile.getName()) +
                    basePackage +
                    "src/main/java".replace("/", File.separator) +
                    immutableBool(), e);
        }
    }

    private void translateIdlToJson(File idlFile, File jsonFile) throws IOException {
        if (barristerScript.startsWith("http://") || barristerScript.startsWith("https://")) {
            StringBuilder postStr = new StringBuilder();
            int i = 0;
            for (File f : idlFilesInDir(idlFile)) {
                byte[] contents = readFile(f);
                if (i > 0) postStr.append("&");
                postStr.append("idl." + i + ".filename=").append(URLEncoder.encode(f.getName(), "utf-8"))
                       .append("&idl." + i + ".content=").append(URLEncoder.encode(new String(contents, "utf-8"), "utf-8"));
                i++;
            }

            byte jsonData[] = httpPost(barristerScript, postStr.toString().getBytes("utf-8"));
            if (jsonData == null || jsonData.length == 0 || jsonData[0] != '[') {
                throw new IOException("Unexpected response from: " + barristerScript + " response: " + new String(jsonData, "utf-8"));
            }
            writeFile(jsonData, jsonFile);
        }
        else {
            exec(barristerScript, "-j", jsonFile.getAbsolutePath(), idlFile.getAbsolutePath());
        }
    }

    private String idlFileToBasePackage(String filename) {
        return basePackage + "." + sanitizeForJava(filename.replace(".idl", ""));
    }

    private String sanitizeForJava(String s) {
        return s.replaceAll("[^A-Za-z0-9_\\.]", "");
    }

    private boolean immutableBool() {
        return this.immutable != null && this.immutable.trim().equals("true");
    }

    private boolean cleanBool() {
        return this.clean != null && this.clean.trim().equals("true");
    }

    private void delete(File f) throws MojoExecutionException {
        if (f.isDirectory()) {
            for (File child : f.listFiles()) {
                delete(child);
            }
        }

        if (f.exists() && !f.delete()) {
            throw new MojoExecutionException("Unable to delete: " + f.getAbsolutePath());
        }
    }

    private byte[] httpPost(String endpointUrl, byte[] postData) throws IOException {
        URL url = new URL(endpointUrl);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setDoInput(true);
        conn.setDoOutput(true);

        conn.addRequestProperty("Content-Length", String.valueOf(postData.length));
        conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        OutputStream os = null;
        InputStream is  = null;
        InputStream err = null;
        try {
            os = conn.getOutputStream();
            os.write(postData);
            os.flush();

            is = conn.getInputStream();
            return streamToBytes(is);
        }
        catch (IOException e) {
            if (conn.getResponseCode() == 500) {
                err = conn.getErrorStream();
                throw new IOException("Error translating IDL: " + new String(streamToBytes(err), "utf-8"));
            }
            else {
                throw e;
            }
        }
        finally {
            closeQuietly(os);
            closeQuietly(is);
            closeQuietly(err);
        }
    }

    private byte[] readFile(File file) throws IOException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            return streamToBytes(fis);
        }
        finally {
            closeQuietly(fis);
        }
    }

    private void writeFile(byte[] data, File file) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(data);
            fos.flush();
        }
        finally {
            closeQuietly(fos);
        }
    }

    private byte[] streamToBytes(InputStream is) throws IOException {
        byte[] buffer = new byte[2048];
        int numRead = 0;
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        while((numRead = is.read(buffer)) > 0) {
            os.write(buffer, 0, numRead);
        }
        return os.toByteArray();
    }

    private void closeQuietly(Closeable c) {
        if (c != null) {
            try { c.close(); }
            catch (Exception e) { }
        }
    }

    private List<File> idlFilesInDir(File firstFile) {
        List<File> files = new ArrayList<File>();
        files.add(firstFile);
        for (File f : firstFile.getParentFile().listFiles()) {
            if (f.isFile() && !f.getAbsolutePath().equals(firstFile.getAbsolutePath())) {
                files.add(f);
            }
        }
        return files;
    }

    private void exec(String... args) throws IOException {
        String s;
        Process p = Runtime.getRuntime().exec(args);

        new DrainStream(p.getInputStream(), false).start();
        new DrainStream(p.getErrorStream(), true).start();

        try {
            if (p.waitFor() != 0) {
                throw new IOException("Command returned non-zero exit code: " + args);
            }
        }
        catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    class DrainStream implements Runnable {

        BufferedReader reader;
        boolean logErr;

        DrainStream(InputStream is, boolean logErr) {
            this.reader = new BufferedReader(new InputStreamReader(is));
            this.logErr = logErr;
        }

        public void start() {
            Thread t = new Thread(this);
            t.start();
        }

        public void run() {
            String s;
            try {
                while ((s = reader.readLine()) != null) {
                    if (logErr) {
                        getLog().error(s);
                    }
                    else {
                        getLog().info(s);
                    }
                }
            }
            catch (IOException e) {
                getLog().error(e);
            }
        }
    }

}
