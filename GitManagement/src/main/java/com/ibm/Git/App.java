package com.ibm.Git;

import java.util.List;

import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import com.ibm.Git.service.impl.GitServiceimpl;

public class App {
	public static void main(String[] args) {
		GitServiceimpl gitService = new GitServiceimpl();
		String gitUrl = "https://github.com/Sunitha-89/SampleProject.git";
		String user = "Sunitha-89";
		String token = "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";
		String localDirPath = "C:\\Users\\SunithaGM\\Desktop\\New folder";

		UsernamePasswordCredentialsProvider upc = new UsernamePasswordCredentialsProvider(user, token);
		boolean hasWriteAccess = gitService.hasWriteAccessToGitRepo(gitUrl, upc);
		System.out.println("Write access: " + hasWriteAccess);

		try {
			// Clone the repository
			List<String> projectName = gitService.validateAndcloneFromGit(gitUrl, user, token, localDirPath);
			System.out.println("Repository cloned successfully. Project Name: " + projectName);

			// Commit changes to the branch
			gitService.commitToBranch(gitUrl, user, token, localDirPath);
			System.out.println("Changes committed successfully.");
		} catch (Exception e) {
			System.err.println("Error during Git operation: " + e.getMessage());
		}
	}
}
