package com.github.dockerjava.cmd;

import com.github.dockerjava.api.*;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.command.*;
import com.github.dockerjava.core.util.CompressArchiveUtil;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

public class CopyArchiveToContainerCmdIT extends CmdIT {
    public static final Logger LOG = LoggerFactory.getLogger(CopyArchiveToContainerCmdIT.class);

    @Test
    public void copyFileToContainer() throws Exception {
        CreateContainerResponse container = prepareContainerForCopy("1");
        Path temp = Files.createTempFile("", ".tar.gz");
        CompressArchiveUtil.tar(Paths.get("src/test/resources/testReadFile"), temp, true, false);
        try (InputStream uploadStream = Files.newInputStream(temp)) {
            dockerRule.getClient()
                    .copyArchiveToContainerCmd(container.getId())
                    .withTarInputStream(uploadStream)
                    .exec();
            assertFileCopied(container);
        }
    }

    @Test
    public void copyStreamToContainer() throws Exception {
        CreateContainerResponse container = prepareContainerForCopy("2");
        dockerRule.getClient().copyArchiveToContainerCmd(container.getId())
                .withHostResource("src/test/resources/testReadFile")
                .exec();
        assertFileCopied(container);
    }

    @Test
    public void copyStreamToContainerTwice() throws Exception {
        CreateContainerResponse container = prepareContainerForCopy("rerun");
        CopyArchiveToContainerCmd copyArchiveToContainerCmd=dockerRule.getClient().copyArchiveToContainerCmd(container.getId())
                .withHostResource("src/test/resources/testReadFile");
        copyArchiveToContainerCmd.exec();
        assertFileCopied(container);
        //run again to make sure no DockerClientException
        copyArchiveToContainerCmd.exec();
    }

    private CreateContainerResponse prepareContainerForCopy(String method) {
        CreateContainerResponse container = dockerRule.getClient().createContainerCmd("busybox")
                .withName("docker-java-itest-copyToContainer" + method + dockerRule.getKind())
                .exec();
        LOG.info("Created container: {}", container);
        assertThat(container.getId(), not(isEmptyOrNullString()));
        dockerRule.getClient().startContainerCmd(container.getId()).exec();
        // Copy a folder to the container
        return container;
    }

    private void assertFileCopied(CreateContainerResponse container) throws IOException {
        try (InputStream response = dockerRule.getClient().copyArchiveFromContainerCmd(container.getId(), "testReadFile").exec()) {
            boolean bytesAvailable = response.available() > 0;
            assertTrue( "The file was not copied to the container.", bytesAvailable);
        }
    }

    @Test(expected = NotFoundException.class)
    public void copyToNonExistingContainer() throws Exception {

        dockerRule.getClient().copyArchiveToContainerCmd("non-existing").withHostResource("src/test/resources/testReadFile").exec();
    }

    @Test
    public void copyDirWithLastAddedTarEntryEmptyDir() throws Exception{
        // create a temp dir
        Path localDir = Files.createTempDirectory(null);
        localDir.toFile().deleteOnExit();
        // create empty sub-dir with name b
        Files.createDirectory(localDir.resolve("b"));
        // create sub-dir with name a
        Path dirWithFile = Files.createDirectory(localDir.resolve("a"));
        // create file in sub-dir b, name or conter are irrelevant
        Files.createFile(dirWithFile.resolve("file"));

        // create a test container
        CreateContainerResponse container = dockerRule.getClient().createContainerCmd("busybox")
                .withCmd("sleep", "9999")
                .exec();
        // start the container
        dockerRule.getClient().startContainerCmd(container.getId()).exec();
        // copy data from local dir to container
        dockerRule.getClient().copyArchiveToContainerCmd(container.getId())
                .withHostResource(localDir.toString())
                .exec();

        // cleanup dir
        FileUtils.deleteDirectory(localDir.toFile());
    }
    
    @Test
    public void copyFileWithoutArchiveMode() throws Exception {
      // test that a file copied without archive mode enabled (the default) 
      // that the uid/gid maps will not be preserved, the file should belong to someone
      // other than someuser:somegroup

      // create the container
      File baseDir = new File(Thread.currentThread().getContextClassLoader()
          .getResource("testCopyFileWithArchiveMode").getFile());
      String imageId = dockerRule.buildImage(baseDir);

      CreateContainerResponse container = dockerRule.getClient().createContainerCmd(imageId)
          .withUser("root")
          .withCmd("sleep", "9999")
          .exec();
      
      // start the container
      dockerRule.getClient().startContainerCmd(container.getId()).exec();

      // copy file to container
      dockerRule.getClient().copyArchiveToContainerCmd(container.getId())
          .withRemotePath("/tmp")
          .withHostResource("src/test/resources/testCopyFileWithArchiveMode/someFile.txt")
          .exec();
      
      // verify ownership of the file
      ExecCreateCmdResponse execCreateCmdResponse = dockerRule.getClient().execCreateCmd(container.getId())
          .withAttachStdout(true)
          .withUser("root")
          .withCmd("stat", "-c", "\"%U %G\" /tmp/someFile.txt")
          .exec();
      
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      dockerRule.getClient().execStartCmd(execCreateCmdResponse.getId())
          .exec(new ExecStartResultCallback(out, System.err))
          .awaitCompletion();

      System.out.println(out.toString());
      assertTrue(out.toString().contains("root root"));
    }
    
    @Test
    public void copyFileWithArchiveMode() throws Exception {
        // test that a file belonging to someone other than root:root
        // which is then copied with archive mode does not belong to root:root
      
      // create the container
      File baseDir = new File(Thread.currentThread().getContextClassLoader()
          .getResource("testCopyFileWithArchiveMode").getFile());
      String imageId = dockerRule.buildImage(baseDir);

      CreateContainerResponse container = dockerRule.getClient().createContainerCmd(imageId)
          .withUser("root")
          .withCmd("sleep", "9999")
          .exec();
      
      // start the container
      dockerRule.getClient().startContainerCmd(container.getId()).exec();
      
      // copy file to container
      dockerRule.getClient().copyArchiveToContainerCmd(container.getId())
          .withRemotePath("/tmp")
          .withArchiveMode(true)
          .withHostResource("src/test/resources/testCopyFileWithArchiveMode/someFile.txt")
          .exec();
      
      // verify ownership of the file
      ExecCreateCmdResponse execCreateCmdResponse = dockerRule.getClient().execCreateCmd(container.getId())
          .withAttachStdout(true)
          .withUser("root")
          .withCmd("stat", "-c", "\"%U %G\" /tmp/someFile.txt")
          .exec();
      
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      dockerRule.getClient().execStartCmd(execCreateCmdResponse.getId())
          .exec(new ExecStartResultCallback(out, System.err))
          .awaitCompletion();

        System.out.println(out.toString());
        assertTrue(out.toString().contains("someuser somegoup"));
    }
    
    @Test
    public void copyFileWithExecutePermission() throws Exception {
        // create script file, add permission to execute
        Path scriptPath = Files.createTempFile("run", ".sh");
        boolean executable = scriptPath.toFile().setExecutable(true, false);
        if (!executable){
            throw new Exception("Execute permission on file not set!");
        }
        String snippet = "Running script with execute permission.";
        String scriptTextStr = "#!/bin/sh\necho \"" + snippet + "\"";
        // write content for created script
        Files.write(scriptPath, scriptTextStr.getBytes());
        // create a test container which starts and waits 3 seconds for the
        // script to be copied to the container's home dir and then executes it
        String containerCmd = "sleep 3; /home/" + scriptPath.getFileName().toString();
        CreateContainerResponse container = dockerRule.getClient().createContainerCmd("busybox")
                .withName("copyFileWithExecutivePerm" + dockerRule.getKind())
                .withCmd("/bin/sh", "-c", containerCmd)
                .exec();
        // start the container
        dockerRule.getClient().startContainerCmd(container.getId()).exec();
        // copy script to container home dir
        dockerRule.getClient().copyArchiveToContainerCmd(container.getId())
                .withRemotePath("/home")
                .withHostResource(scriptPath.toString())
                .exec();
        // await exid code
        int exitCode = dockerRule.getClient().waitContainerCmd(container.getId())
                .exec(new WaitContainerResultCallback())
                .awaitStatusCode();
        // check result
        assertThat(exitCode, equalTo(0));
    }

}
