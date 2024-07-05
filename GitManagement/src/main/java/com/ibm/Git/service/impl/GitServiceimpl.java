package com.ibm.Git.service.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.Git.service.GitService;

public class GitServiceimpl implements GitService {

	private static final Logger logger = LoggerFactory.getLogger(GitServiceimpl.class);
	private static final String HTTPS = "https";
	private static final String HTTP = "http";

	public List<String> validateAndcloneFromGit(String gitUrl, String user, String token, String localDirPath)
			throws Exception {
		UsernamePasswordCredentialsProvider upc = new UsernamePasswordCredentialsProvider(user, token);
		File localDir = new File(localDirPath);

		if (localDir.exists()) {
			clearDirectory(localDir.toPath());
		} else {
			localDir.mkdirs();
		}

		// Check if user has access to the repository
		try {
			Git.lsRemoteRepository().setRemote(gitUrl).setCredentialsProvider(upc).call();
		} catch (GitAPIException e) {
			logger.error("User does not have read access to the repository: {}", e.getMessage());
			throw new Exception("User does not have read access to the repository.");
		}

		String branch = getDefaultBranch(gitUrl, upc);

		try (Git git = Git.cloneRepository().setURI(gitUrl).setDirectory(localDir).setBranch(branch)
				.setCredentialsProvider(upc).call()) {

			StoredConfig config = git.getRepository().getConfig();
			config.setBoolean(HTTP, null, "sslVerify", false);
			config.setBoolean(HTTPS, null, "sslVerify", false);
			config.save();

			Collection<Ref> refs = git.lsRemoteRepository().setHeads(true).setRemote(gitUrl).setCredentialsProvider(upc)
					.call();

			Map<String, Ref> gitBranchMap = new HashMap<>();
			refs.forEach(ref -> {
				String branchName = ref.getName().substring(ref.getName().lastIndexOf("/") + 1);
				gitBranchMap.put(branchName, ref);
			});

			if (gitBranchMap.containsKey(branch)) {
				// Branch exists. Cloned to local repository successfully.
			} else {
				logger.error("Branch does not exist.");
				throw new Exception("Branch does not exist.");
			}
		} catch (GitAPIException | IOException e) {
			logger.error("Error during Git operations: {}", e.getMessage());
			throw new Exception("Git operation failed.", e);
		}

		// Get the project names from the subdirectories created after cloning
		List<String> projectNames = findProjectFolders(localDir.toPath());
		if (projectNames.isEmpty()) {
			throw new Exception("No valid Maven projects found in the repository.");
		}

		return projectNames;
	}

	private void clearDirectory(Path path) throws IOException {
		Files.walk(path).sorted((o1, o2) -> o2.compareTo(o1)).map(Path::toFile).forEach(File::delete);
	}

	public List<String> findProjectFolders(Path directoryPath) {
		try {
			if (!Files.isDirectory(directoryPath)) {
				logger.warn("Provided path is not a directory: {}", directoryPath);
				return Collections.emptyList();
			}

			List<String> projectFolders = Files.walk(directoryPath, 2).filter(Files::isDirectory)
					.filter(path -> !path.equals(directoryPath)) // Exclude the root directory itself
					.filter(this::isMavenProject).map(path -> path.getFileName().toString())
					.collect(Collectors.toList());

			if (!projectFolders.isEmpty()) {
				logger.info("Maven project folders found: {}", projectFolders);
				return projectFolders;
			} else {
				logger.warn("No Maven project folders found in directory: {}", directoryPath);
				return Collections.emptyList();
			}
		} catch (IOException e) {
			logger.error("Error while searching for project folders.", e);
			return Collections.emptyList();
		}
	}

	public boolean isMavenProject(Path directoryPath) {
		if (!Files.isDirectory(directoryPath)) {
			logger.warn("Provided path is not a directory: {}", directoryPath);
			return false;
		}

		Path pomFilePath = directoryPath.resolve("pom.xml");
		if (!Files.exists(pomFilePath)) {
			return false;
		}

		Path srcMainJava = directoryPath.resolve("src").resolve("main").resolve("java");
		Path srcMainResources = directoryPath.resolve("src").resolve("main").resolve("resources");
		Path srcTestJava = directoryPath.resolve("src").resolve("test").resolve("java");

		boolean hasMavenStructure = Files.isDirectory(srcMainJava) && Files.isDirectory(srcMainResources)
				&& Files.isDirectory(srcTestJava);

		if (hasMavenStructure) {
			logger.info("Valid Maven project found at: {}", directoryPath);
		} else {
			logger.warn("Directory does not follow Maven structure: {}", directoryPath);
		}

		return hasMavenStructure;
	}

	private String getDefaultBranch(String gitUrl, UsernamePasswordCredentialsProvider upc)
			throws GitAPIException, IOException {
		Collection<Ref> refs = Git.lsRemoteRepository().setHeads(true).setRemote(gitUrl).setCredentialsProvider(upc)
				.call();

		for (Ref ref : refs) {
			String branchName = ref.getName().substring(ref.getName().lastIndexOf("/") + 1);
			if (branchName.equals("main") || branchName.equals("master")) {
				return branchName;
			}
		}

		// If no main or master branch is found, just return the first branch found
		if (!refs.isEmpty()) {
			Ref firstRef = refs.iterator().next();
			return firstRef.getName().substring(firstRef.getName().lastIndexOf("/") + 1);
		}

		throw new IOException("No branches found in the repository.");
	}

	public void commitToBranch(String gitUrl, String user, String token, String localDirPath) throws Exception {
		UsernamePasswordCredentialsProvider upc = new UsernamePasswordCredentialsProvider(user, token);
		File localDir = new File(localDirPath);

		if (!hasWriteAccessToGitRepo(gitUrl, upc)) {
			logger.error("User does not have write access to the repository.");
			throw new Exception("User does not have write access to the repository.");
		}

		try (Git git = Git.open(localDir)) {
			// Stage all files
			git.add().addFilepattern(".").call();

			// Commit changes to the branch
			git.commit().setMessage("test commit").call();
			logger.info("Committed changes to the branch");

			Iterable<PushResult> pushResults = git.push().setCredentialsProvider(upc).setRemote("origin").call();

			for (PushResult pushResult : pushResults) {
				for (RemoteRefUpdate update : pushResult.getRemoteUpdates()) {
					if (update.getStatus() == RemoteRefUpdate.Status.OK) {
						logger.info("Pushed changes to the branch");
					} else {
						logger.error("Failed to push changes to the branch: {}", update.getStatus());
						throw new Exception("Failed to push changes to the branch: " + update.getStatus());
					}
				}
			}
		} catch (GitAPIException | IOException e) {
			logger.error("Error committing changes: {}", e.getMessage());
			throw new Exception("Commit operation failed.", e);
		}
	}

	public static boolean hasWriteAccessToGitRepo(String repoUrl, UsernamePasswordCredentialsProvider upc) {
		try {
			// Check write access by attempting a dry-run push
			File tempDir = Files.createTempDirectory("tempRepo").toFile();
			try (Git git = Git.cloneRepository().setURI(repoUrl).setCredentialsProvider(upc).setDirectory(tempDir)
					.call()) {

				git.push().setRemote("origin").setCredentialsProvider(upc).setDryRun(true).call();
			}

			FileUtils.deleteDirectory(tempDir);
			return true;
		} catch (GitAPIException | IOException e) {
			e.printStackTrace();
			return false;
		}
	}
}
