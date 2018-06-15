/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;

import htsjdk.samtools.BamFileIoUtils;
import htsjdk.samtools.SAMException;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.Random;


public class IOUtilTest extends HtsjdkTest {


    private static final Path TEST_DATA_DIR = Paths.get ("src/test/resources/htsjdk/samtools/io/");
    private static final Path TEST_VARIANT_DIR = Paths.get("src/test/resources/htsjdk/variant/");
    private static final Path SLURP_TEST_FILE = TEST_DATA_DIR.resolve("slurptest.txt");
    private static final Path EMPTY_FILE = TEST_DATA_DIR.resolve("empty.txt");
    private static final Path FIVE_SPACES_THEN_A_NEWLINE_THEN_FIVE_SPACES_FILE = TEST_DATA_DIR.resolve("5newline5.txt");
    private static final List<String> SLURP_TEST_LINES = Arrays.asList("bacon   and rice   ", "for breakfast  ", "wont you join me");
    private static final String SLURP_TEST_LINE_SEPARATOR = "\n";
    private static final String TEST_FILE_PREFIX = "htsjdk-IOUtilTest";
    private static final String TEST_FILE_EXTENSIONS[] = {".txt", ".txt.gz"};
    private static final String TEST_STRING = "bar!";

    private File existingTempFile;
    private String systemUser;
    private String systemTempDir;
    private FileSystem inMemoryFileSystem;

    @BeforeClass
    public void setUp() throws IOException {
        existingTempFile = File.createTempFile("FiletypeTest.", ".tmp");
        existingTempFile.deleteOnExit();
        systemTempDir = System.getProperty("java.io.tmpdir");
        final File tmpDir = new File(systemTempDir);
        inMemoryFileSystem = Jimfs.newFileSystem(Configuration.unix());;
        if (!tmpDir.isDirectory()) tmpDir.mkdir();
        if (!tmpDir.isDirectory())
            throw new RuntimeException("java.io.tmpdir (" + systemTempDir + ") is not a directory");
        systemUser = System.getProperty("user.name");
    }

    @AfterClass
    public void tearDown() throws IOException {
        // reset java properties to original
        System.setProperty("java.io.tmpdir", systemTempDir);
        System.setProperty("user.name", systemUser);
        inMemoryFileSystem.close();
    }

    @Test
    public void testFileReadingAndWriting() throws IOException {
        String randomizedTestString = TEST_STRING + System.currentTimeMillis();
        for (String ext : TEST_FILE_EXTENSIONS) {
            File f = File.createTempFile(TEST_FILE_PREFIX, ext);
            f.deleteOnExit();

            OutputStream os = IOUtil.openFileForWriting(f);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
            writer.write(randomizedTestString);
            writer.close();

            InputStream is = IOUtil.openFileForReading(f);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line = reader.readLine();
            Assert.assertEquals(randomizedTestString, line);
        }
    }

    @Test(groups = {"unix"})
    public void testGetCanonicalPath() throws IOException {
        String tmpPath = System.getProperty("java.io.tmpdir");
        String userName = System.getProperty("user.name");

        if (tmpPath.endsWith(userName)) {
            tmpPath = tmpPath.substring(0, tmpPath.length() - userName.length());
        }

        File tmpDir = new File(tmpPath, userName);
        tmpDir.mkdir();
        tmpDir.deleteOnExit();
        File actual = new File(tmpDir, "actual.txt");
        actual.deleteOnExit();
        ProcessExecutor.execute(new String[]{"touch", actual.getAbsolutePath()});
        File symlink = new File(tmpDir, "symlink.txt");
        symlink.deleteOnExit();
        ProcessExecutor.execute(new String[]{"ln", "-s", actual.getAbsolutePath(), symlink.getAbsolutePath()});
        File lnDir = new File(tmpDir, "symLinkDir");
        lnDir.deleteOnExit();
        ProcessExecutor.execute(new String[]{"ln", "-s", tmpDir.getAbsolutePath(), lnDir.getAbsolutePath()});
        File lnToActual = new File(lnDir, "actual.txt");
        lnToActual.deleteOnExit();
        File lnToSymlink = new File(lnDir, "symlink.txt");
        lnToSymlink.deleteOnExit();

        File files[] = {actual, symlink, lnToActual, lnToSymlink};
        for (File f : files) {
            Assert.assertEquals(IOUtil.getFullCanonicalPath(f), actual.getCanonicalPath());
        }
    }

    @Test
    public void testUtfWriting() throws IOException {
        final String utf8 = new StringWriter().append((char) 168).append((char) 197).toString();
        for (String ext : TEST_FILE_EXTENSIONS) {
            final File f = File.createTempFile(TEST_FILE_PREFIX, ext);
            f.deleteOnExit();

            final BufferedWriter writer = IOUtil.openFileForBufferedUtf8Writing(f);
            writer.write(utf8);
            CloserUtil.close(writer);

            final BufferedReader reader = IOUtil.openFileForBufferedUtf8Reading(f);
            final String line = reader.readLine();
            Assert.assertEquals(utf8, line, f.getAbsolutePath());

            CloserUtil.close(reader);

        }
    }

    @Test
    public void slurpLinesTest() throws FileNotFoundException {
        Assert.assertEquals(IOUtil.slurpLines(SLURP_TEST_FILE.toFile()), SLURP_TEST_LINES);
    }

    @Test
    public void slurpWhitespaceOnlyFileTest() throws FileNotFoundException {
        Assert.assertEquals(IOUtil.slurp(FIVE_SPACES_THEN_A_NEWLINE_THEN_FIVE_SPACES_FILE.toFile()), "     \n     ");
    }

    @Test
    public void slurpEmptyFileTest() throws FileNotFoundException {
        Assert.assertEquals(IOUtil.slurp(EMPTY_FILE.toFile()), "");
    }

    @Test
    public void slurpTest() throws FileNotFoundException {
        Assert.assertEquals(IOUtil.slurp(SLURP_TEST_FILE.toFile()), CollectionUtil.join(SLURP_TEST_LINES, SLURP_TEST_LINE_SEPARATOR));
    }

    @Test(dataProvider = "fileTypeTestCases")
    public void testFileType(final String path, boolean expectedIsRegularFile) {
        final File file = new File(path);
        Assert.assertEquals(IOUtil.isRegularPath(file), expectedIsRegularFile);
        Assert.assertEquals(IOUtil.isRegularPath(file.toPath()), expectedIsRegularFile);
    }

    @Test(dataProvider = "unixFileTypeTestCases", groups = {"unix"})
    public void testFileTypeUnix(final String path, boolean expectedIsRegularFile) {
        final File file = new File(path);
        Assert.assertEquals(IOUtil.isRegularPath(file), expectedIsRegularFile);
        Assert.assertEquals(IOUtil.isRegularPath(file.toPath()), expectedIsRegularFile);
    }

    @Test
    public void testAddExtension() throws IOException {
        Path p = IOUtil.getPath("/folder/file");
        Assert.assertEquals(IOUtil.addExtension(p, ".ext"), IOUtil.getPath("/folder/file.ext"));
        p = IOUtil.getPath("folder/file");
        Assert.assertEquals(IOUtil.addExtension(p, ".ext"), IOUtil.getPath("folder/file.ext"));
        try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            p = jimfs.getPath("folder/sub/file");
            Assert.assertEquals(IOUtil.addExtension(p, ".ext"), jimfs.getPath("folder/sub/file.ext"));
            p = jimfs.getPath("folder/file");
            Assert.assertEquals(IOUtil.addExtension(p, ".ext"), jimfs.getPath("folder/file.ext"));
            p = jimfs.getPath("file");
            Assert.assertEquals(IOUtil.addExtension(p, ".ext"), jimfs.getPath("file.ext"));
        }
    }

    @Test
    public void testAddExtensionOnList() throws IOException {
        Path p = IOUtil.getPath("/folder/file");
        List<FileSystemProvider> fileSystemProviders = FileSystemProvider.installedProviders();

        List<Path> paths = new ArrayList<>();
        List<String> strings = new ArrayList<>();

        paths.add(IOUtil.addExtension(p, ".ext"));
        strings.add("/folder/file.ext");

        p = IOUtil.getPath("folder/file");
        paths.add(IOUtil.addExtension(p, ".ext"));
        strings.add("folder/file.ext");

        List<Path> expectedPaths = IOUtil.getPaths(strings);

        Assert.assertEquals(paths, expectedPaths);
    }


    @DataProvider(name = "fileTypeTestCases")
    private Object[][] fileTypeTestCases() {
        return new Object[][]{
                {existingTempFile.getAbsolutePath(), Boolean.TRUE},
                {systemTempDir, Boolean.FALSE}

        };
    }

    @DataProvider(name = "unixFileTypeTestCases")
    private Object[][] unixFileTypeTestCases() {
        return new Object[][]{
                {"/dev/null", Boolean.FALSE},
                {"/dev/stdout", Boolean.FALSE},
                {"/non/existent/file", Boolean.TRUE},
        };
    }

    @DataProvider(name = "fileNamesForDelete")
    public Object[][] fileNamesForDelete() {
        return new Object[][] {
                {Collections.emptyList()},
                {Collections.singletonList("file1")},
                {Arrays.asList("file1", "file2")}
        };
    }

    @Test
    public void testGetDefaultTmpDirPath() throws Exception {
        try {
            Path testPath = IOUtil.getDefaultTmpDirPath();
            Assert.assertEquals(testPath.toFile().getAbsolutePath(), new File(systemTempDir).getAbsolutePath() + "/" + systemUser);

            // change the properties to test others
            final String newTempPath = Files.createTempDirectory("testGetDefaultTmpDirPath").toString();
            final String newUser = "my_user";
            System.setProperty("java.io.tmpdir", newTempPath);
            System.setProperty("user.name", newUser);
            testPath = IOUtil.getDefaultTmpDirPath();
            Assert.assertEquals(testPath.toFile().getAbsolutePath(), new File(newTempPath).getAbsolutePath() + "/" + newUser);

        } finally {
            // reset system properties
            System.setProperty("java.io.tmpdir", systemTempDir);
            System.setProperty("user.name", systemUser);
        }
    }

    @Test(dataProvider = "fileNamesForDelete")
    public void testDeletePathLocal(final List<String> fileNames) throws Exception {
        final File tmpDir = IOUtil.createTempDir("testDeletePath", "");
        final List<Path> paths = createLocalFiles(tmpDir, fileNames);
        testDeletePath(paths);
    }

    @Test(dataProvider = "fileNamesForDelete")
    public void testDeletePathJims(final List<String> fileNames) throws Exception {
        final List<Path> paths = createJimfsFiles("testDeletePath", fileNames);
        testDeletePath(paths);
    }

    @Test(dataProvider = "fileNamesForDelete")
    public void testDeleteArrayPathLocal(final List<String> fileNames) throws Exception {
        final File tmpDir = IOUtil.createTempDir("testDeletePath", "");
        final List<Path> paths = createLocalFiles(tmpDir, fileNames);
        testDeletePathArray(paths);
    }

    @Test(dataProvider = "fileNamesForDelete")
    public void testDeleteArrayPathJims(final List<String> fileNames) throws Exception {
        final List<Path> paths = createJimfsFiles("testDeletePath", fileNames);
        testDeletePathArray(paths);
    }

    private static void testDeletePath(final List<Path> paths) {
        paths.forEach(p -> Assert.assertTrue(Files.exists(p)));
        IOUtil.deletePaths(paths);
        paths.forEach(p -> Assert.assertFalse(Files.exists(p)));
    }

    private static void testDeletePathArray(final List<Path> paths) {
        paths.forEach(p -> Assert.assertTrue(Files.exists(p)));
        IOUtil.deletePaths(paths.toArray(new Path[paths.size()]));
        paths.forEach(p -> Assert.assertFalse(Files.exists(p)));
    }

    private static List<Path> createLocalFiles(final File tmpDir, final List<String> fileNames) throws Exception {
        final List<Path> paths = new ArrayList<>(fileNames.size());
        for (final String f: fileNames) {
            final File file = new File(tmpDir, f);
            Assert.assertTrue(file.createNewFile(), "failed to create test file" +file);
            paths.add(file.toPath());
        }
        return paths;
    }

    private List<Path> createJimfsFiles(final String folderName, final List<String> fileNames) throws Exception {
        final List<Path> paths = new ArrayList<>(fileNames.size());
        final Path folder = inMemoryFileSystem.getPath(folderName);
        if (Files.notExists(folder)) Files.createDirectory(folder);

        for (final String f: fileNames) {
            final Path p = inMemoryFileSystem.getPath(folderName, f);
            Files.createFile(p);
            paths.add(p);
        }

        return paths;
    }

    @DataProvider
    public Object[][] pathsForWritableDirectory() throws Exception {
        return new Object[][] {
                // non existent
                {inMemoryFileSystem.getPath("no_exists"), false},
                // non directory
                {Files.createFile(inMemoryFileSystem.getPath("testAssertDirectoryIsWritable_file")), false},
                // TODO - how to do in inMemoryFileSystem a non-writable directory?
                // writable directory
                {Files.createDirectory(inMemoryFileSystem.getPath("testAssertDirectoryIsWritable_directory")), true}
        };
    }

    @Test(dataProvider = "pathsForWritableDirectory")
    public void testAssertDirectoryIsWritablePath(final Path path, final boolean writable) {
        try {
            IOUtil.assertDirectoryIsWritable(path);
        } catch (SAMException e) {
            if (writable) {
                Assert.fail(e.getMessage());
            }
        }
    }

    @DataProvider
    public Object[][] filesForWritableDirectory() throws Exception {
        final File nonWritableFile = new File(systemTempDir, "testAssertDirectoryIsWritable_non_writable_dir");
        nonWritableFile.mkdir();
        nonWritableFile.setWritable(false);

        return new Object[][] {
                // non existent
                {new File("no_exists"), false},
                // non directory
                {existingTempFile, false},
                // non-writable directory
                {nonWritableFile, false},
                // writable directory
                {new File(systemTempDir), true},
        };
    }

    @Test(dataProvider = "filesForWritableDirectory")
    public void testAssertDirectoryIsWritableFile(final File file, final boolean writable) {
        try {
            IOUtil.assertDirectoryIsWritable(file);
        } catch (SAMException e) {
            if (writable) {
                Assert.fail(e.getMessage());
            }
        }
    }

    static final String level1 = "Level1.fofn";
    static final String level2 = "Level2.fofn";

    @DataProvider
    public Object[][] fofnData() throws IOException {

        Path fofnPath1 = inMemoryFileSystem.getPath(level1);
        Files.copy(TEST_DATA_DIR.resolve(level1), fofnPath1);

        Path fofnPath2 = inMemoryFileSystem.getPath(level2);
        Files.copy(TEST_DATA_DIR.resolve(level2), fofnPath2);

        return new Object[][]{
                {TEST_DATA_DIR + "/" + level1, new String[]{".vcf", ".vcf.gz"}, 2},
                {TEST_DATA_DIR + "/" + level2, new String[]{".vcf", ".vcf.gz"}, 4},
                {fofnPath1.toUri().toString(), new String[]{".vcf", ".vcf.gz"}, 2},
                {fofnPath2.toUri().toString(), new String[]{".vcf", ".vcf.gz"}, 4}
        };
    }

    @Test(dataProvider = "fofnData")
    public void testUnrollPaths(final String pathUri, final String[] extensions, final int expectedNumberOfUnrolledPaths) throws IOException {
        Path p = IOUtil.getPath(pathUri);
        List<Path> paths = IOUtil.unrollPaths(Collections.singleton(p), extensions);

        Assert.assertEquals(paths.size(), expectedNumberOfUnrolledPaths);
    }

    @DataProvider(name = "blockCompressedExtensionExtensionStrings")
    public static Object[][] createBlockCompressedExtensionStrings() {
        return new Object[][] {
                { "testzip.gz", true },
                { "test.gzip", true },
                { "test.bgz", true },
                { "test.bgzf", true },
                { "test.bzip2", false }
        };
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionStrings")
    public void testBlockCompressionExtensionString(final String testString, final boolean expected) {
        Assert.assertEquals(IOUtil.hasBlockCompressedExtension(testString), expected);
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionStrings")
    public void testBlockCompressionExtensionFile(final String testString, final boolean expected) {
        Assert.assertEquals(IOUtil.hasBlockCompressedExtension(new File(testString)), expected);
    }

    @DataProvider(name = "blockCompressedExtensionExtensionURIStrings")
    public static Object[][] createBlockCompressedExtensionURIs() {
        return new Object[][]{
                {"testzip.gz", true},
                {"test.gzip", true},
                {"test.bgz", true},
                {"test.bgzf", true},
                {"test", false},
                {"test.bzip2", false},

                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.gz", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.gzip", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bgz", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bgzf", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bzip2", false},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877", false},

                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.gz?alt=media", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.gzip?alt=media", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bgz?alt=media", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bgzf?alt=media", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bzip2?alt=media", false},

                {"ftp://ftp.broadinstitute.org/distribution/igv/TEST/cpgIslands.hg18.gz", true},
                {"ftp://ftp.broadinstitute.org/distribution/igv/TEST/cpgIslands.hg18.bed", false}
        };
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionURIStrings")
    public void testBlockCompressionExtension(final String testURIString, final boolean expected) {
        URI testURI = URI.create(testURIString);
        Assert.assertEquals(IOUtil.hasBlockCompressedExtension(testURI), expected);
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionURIStrings")
    public void testBlockCompressionExtensionStringVersion(final String testURIString, final boolean expected) {
        Assert.assertEquals(IOUtil.hasBlockCompressedExtension(testURIString), expected);
    }

    @DataProvider
    public static Object[][] blockCompressedFiles() {
        return new Object[][]{
                {TEST_DATA_DIR.resolve("ipsum.txt"), true, false},
                {TEST_DATA_DIR.resolve("ipsum.txt"), false, false},
                {TEST_DATA_DIR.resolve("ipsum.txt.gz"), true, false},
                {TEST_DATA_DIR.resolve("ipsum.txt.gz"), false, false},
                {TEST_DATA_DIR.resolve("ipsum.txt.bgz"), true, true},
                {TEST_DATA_DIR.resolve("ipsum.txt.bgz"), false, true},
                {TEST_DATA_DIR.resolve("ipsum.txt.bgz.wrongextension"), true, false},
                {TEST_DATA_DIR.resolve("ipsum.txt.bgz.wrongextension"), false, true},
                {TEST_DATA_DIR.resolve("ipsum.txt.bgzipped_with_gzextension.gz"), true, true},
                {TEST_DATA_DIR.resolve("ipsum.txt.bgzipped_with_gzextension.gz"), false, true},
                {TEST_DATA_DIR.resolve("example.bam"), true, false},
                {TEST_DATA_DIR.resolve("example.bam"), false, true}
        };
    }

    @Test(dataProvider = "blockCompressedFiles")
    public void testIsBlockCompressed(Path file, boolean checkExtension, boolean expected) throws IOException {
        Assert.assertEquals(IOUtil.isBlockCompressed(file, checkExtension), expected);
    }

    @Test(dataProvider = "blockCompressedFiles")
    public void testIsBlockCompressedOnJimfs(Path file, boolean checkExtension, boolean expected) throws IOException {
         try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
             final Path jimfsRoot = jimfs.getRootDirectories().iterator().next();
             final Path jimfsFile = Files.copy(file, jimfsRoot.resolve(file.getFileName().toString()));
             Assert.assertEquals(IOUtil.isBlockCompressed(jimfsFile, checkExtension), expected);
         }
    }
    @DataProvider
    public static Object [][] filesToCompress(){
        return new Object[][]{
                {TEST_VARIANT_DIR.resolve("test1.vcf"),".gz",7},
                {TEST_VARIANT_DIR.resolve("test1.vcf"),".bfq",7},
                {TEST_DATA_DIR.resolve("words_longs.txt"),".gz",8},
                {TEST_DATA_DIR.resolve("words_longs.txt"),".bfq",8}


        };

    }
    @Test(dataProvider = "filesToCompress")
    public void testCompressionLevel(Path file,String extension,int lastDifference) throws IOException{
        long origSize=file.toFile().length();
        long previousSize=origSize;
        String tmpPath = System.getProperty("java.io.tmpdir");
        String userName=System.getProperty("user.name");
        File tmpDir = new File(tmpPath, userName);
        tmpDir.mkdir();
        tmpDir.deleteOnExit();
        for(int cl=1;cl<=9;cl++)
        {
            Path outFile=Paths.get(tmpDir.getAbsolutePath(),"tmp"+extension);
            IOUtil.setCompressionLevel(cl);
            Assert.assertEquals(IOUtil.getCompressionLevel(),cl);
            InputStream inStream=IOUtil.openFileForReading(file);
            OutputStream outStream=IOUtil.openFileForWriting(outFile.toFile());
            IOUtil.transferByStream(inStream,outStream,origSize);
            outStream.close();
            outFile.toFile().deleteOnExit();
            long newSize=outFile.toFile().length();
            if(cl<=lastDifference)Assert.assertTrue(previousSize>newSize);
            else Assert.assertTrue(previousSize>=newSize);
            previousSize=newSize;

        }

    }

    @DataProvider
    public static Object [][] badCompressionLevels(){
        return new Object[][]{
                {-1},
                {10}
        };

    }

    @Test(dataProvider = "badCompressionLevels")
    public void testCompressionLevelExceptions(int cl){
        Assert.assertThrows(IllegalArgumentException.class,()->IOUtil.setCompressionLevel(cl));
    }

    @DataProvider
    public static Object [][] filesToCopy() {
        return new Object[][]{
                {TEST_VARIANT_DIR.resolve("test1.vcf")},
                {TEST_DATA_DIR.resolve("ipsum.txt")}

        };
    }

    @Test(dataProvider = "filesToCopy")
    public void testCopyFile(Path file) throws IOException{
        String tmpPath = System.getProperty("java.io.tmpdir");
        String userName=System.getProperty("user.name");
        File tmpDir = new File(tmpPath, userName);
        tmpDir.mkdir();
        tmpDir.deleteOnExit();
        Path outFile=Paths.get(tmpDir.getAbsolutePath(),"tmp"+file.getFileName());
        IOUtil.copyFile(file.toFile(),outFile.toFile());

        outFile.toFile().deleteOnExit();
        Stream<String> stream=Files.lines(file);
        Stream<String> outStream=Files.lines(outFile);

        Iterator<String> itStream=stream.iterator();
        Iterator<String> itOutStream=outStream.iterator();

        while(itStream.hasNext() && itOutStream.hasNext()){
            Assert.assertEquals(itStream.next(),itOutStream.next());
        }
        Assert.assertFalse(itStream.hasNext());
        Assert.assertFalse(itOutStream.hasNext());
    }

    @Test(dataProvider="filesToCopy")
    public void testCopyFileException(Path file){
        String tmpPath = System.getProperty("java.io.tmpdir");
        String userName=System.getProperty("user.name");
        File tmpDir = new File(tmpPath, userName);
        tmpDir.mkdir();
        tmpDir.deleteOnExit();
        Path outFile=Paths.get(tmpPath,userName,"tmp"+file.getFileName());
        ProcessExecutor.execute(new String[]{"touch", outFile.toAbsolutePath().toString()});
        file.toFile().setReadable(false);
        Assert.assertThrows(SAMException.class,()->IOUtil.copyFile(file.toFile(),outFile.toFile()));
        file.toFile().setReadable(true);
        outFile.toFile().setWritable(false);
        Assert.assertThrows(SAMException.class,()->IOUtil.copyFile(file.toFile(),outFile.toFile()));
        outFile.toFile().setWritable(true);

    }


    @DataProvider
    public static Object [][] baseNameTests() {
        return new Object[][]{
                {TEST_DATA_DIR.resolve("ipsum.txt"),"ipsum"},
                {TEST_DATA_DIR.resolve("ipsum.txt.bgz.wrongextension"),"ipsum.txt.bgz"},
                {TEST_DATA_DIR.resolve("ipsum.txt.bgzipped_with_gzextension.gz"),"ipsum.txt.bgzipped_with_gzextension"},
                {TEST_VARIANT_DIR.resolve("utils"),"utils"},
                {TEST_VARIANT_DIR.resolve("not_real_file.txt"),"not_real_file"}

        };
    }

    @Test(dataProvider="baseNameTests")
    public void testBasename(Path file, String expected){
        String result=IOUtil.basename(file.toFile());
        Assert.assertEquals(result,expected);

    }

    @DataProvider
    public static Object [][] regExpTests() {
        return new Object[][]{
                {"\\w+\\.txt",new String[]{"5newline5.txt","empty.txt","ipsum.txt","slurptest.txt","words_longs.txt"}},
                {"^((?!txt).)*$",new String[]{"Level1.fofn","Level2.fofn","example.bam"}},
                {"^\\d+.*",new String[]{"5newline5.txt"}}
        };

    }

    @Test(dataProvider = "regExpTests")
    public void testRegExp(String regexp, String[] expected){
        String [] allNames={"5newline5.txt","Level2.fofn","example.bam","ipsum.txt.bgz","ipsum.txt.bgzipped_with_gzextension.gz","slurptest.txt","Level1.fofn","empty.txt","ipsum.txt","ipsum.txt.bgz.wrongextension","ipsum.txt.gz","words_longs.txt"};
        String tmpPath = System.getProperty("java.io.tmpdir");
        String userName=System.getProperty("user.name");
        File tmpDir = new File(tmpPath, userName);
        tmpDir.mkdir();
        tmpDir.deleteOnExit();
        File regExpDir=new File(tmpDir.getAbsolutePath(),"regExpDir");
        regExpDir.mkdir();
        regExpDir.deleteOnExit();
        List<String> listExpected=Arrays.asList(expected);
        List<File> expectedFiles=new ArrayList<File>();
        for(String name:allNames) {
            Path file = Paths.get(regExpDir.getAbsolutePath(), name);
            ProcessExecutor.execute(new String[]{"touch", file.toAbsolutePath().toString()});
            if(listExpected.contains(name)){
               expectedFiles.add(file.toFile());
            }
            file.toFile().deleteOnExit();

        }
        File[] result=IOUtil.getFilesMatchingRegexp(regExpDir,regexp);

        Assert.assertEqualsNoOrder(result,expectedFiles.toArray());

    }


    @Test()
    public void testReadLines() throws IOException {
        String tmpPath = System.getProperty("java.io.tmpdir");
        String userName = System.getProperty("user.name");
        File tmpDir = new File(tmpPath, userName);
        tmpDir.mkdir();
        tmpDir.deleteOnExit();
        Path file = Paths.get(tmpDir.getAbsolutePath(), "tmp.txt");
        Random rand = new Random(System.currentTimeMillis());
        BufferedWriter writer = Files.newBufferedWriter(file);
        final int nLines = 5;
        String[] lines = new String[nLines];

        for (int i = 0; i < nLines; i++) {
            String line = TEST_STRING + Integer.toString(rand.nextInt(100000000));
            lines[i] = line;
            writer.write(line);
            writer.newLine();
        }
        writer.close();
        file.toFile().deleteOnExit();
        IterableOnceIterator<String> retLines = IOUtil.readLines(file.toFile());
        Stream<String> expectedLines = Stream.of(lines);
        Iterator<String> itExpected = expectedLines.iterator();
        while (retLines.hasNext() && itExpected.hasNext()) {
            Assert.assertEquals(retLines.next(), itExpected.next());
        }
        Assert.assertFalse(retLines.hasNext());
        Assert.assertFalse(itExpected.hasNext());



    }

    @DataProvider
    public static Object [][] fileSuffixTests(){
        return new Object[][]{
                {TEST_DATA_DIR.resolve("ipsum.txt"),".txt"},
                {TEST_DATA_DIR.resolve("ipsum.txt.bgz"),".bgz"},
                {TEST_DATA_DIR,null}
        };
    }

    @Test(dataProvider="fileSuffixTests")
    public void testSuffixTest(Path file,String expected){
        String ret=IOUtil.fileSuffix(file.toFile());
        Assert.assertEquals(ret,expected);

    }

    @Test
    public void testCopyDirectoryTree() throws IOException{
        String tmpPath = System.getProperty("java.io.tmpdir");
        String userName = System.getProperty("user.name");
        File tmpDir = new File(tmpPath, userName);
        tmpDir.mkdir();
        tmpDir.deleteOnExit();
        File copyToDir=new File(tmpDir.getAbsolutePath(),"copyToDir");
        copyToDir.deleteOnExit();
        IOUtil.copyDirectoryTree(TEST_VARIANT_DIR.toFile(),copyToDir);
        Stream<Path> fileStream=Files.walk(TEST_VARIANT_DIR);
        Stream<Path> copyFileStream=Files.walk(Paths.get(copyToDir.getAbsolutePath()));

        Iterator<Path> itFileStream=fileStream.iterator();
        Iterator<Path> itCopyFileStream=copyFileStream.iterator();

        List<String> listFiles=new ArrayList<String>();
        List<String> listCopyFiles=new ArrayList<String>();
        while(itFileStream.hasNext()){
            Path thisPath=itFileStream.next();
            if(!thisPath.equals(TEST_VARIANT_DIR)){
                listFiles.add(thisPath.getFileName().toString());
            }
        }
        while(itCopyFileStream.hasNext()){
            Path thisPath=itCopyFileStream.next();
            thisPath.toFile().deleteOnExit();
            if(!thisPath.equals(Paths.get(copyToDir.getAbsolutePath()))){
                listCopyFiles.add(thisPath.getFileName().toString());
            }
        }
        Assert.assertEqualsNoOrder(listFiles.toArray(new String[listFiles.size()]),listCopyFiles.toArray(new String[listCopyFiles.size()]));
    }

}

