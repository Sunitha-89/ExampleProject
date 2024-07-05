package com.ibm.Git.service;

import java.util.List;

public interface GitService {
	
	 public List<String>  validateAndcloneFromGit(String gitUrl,  String user, String token, String localDirPath) throws Exception ;


}
