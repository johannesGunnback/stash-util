package com.evry.finance.gitutil;


import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class Cloner {

    private static final Logger log = LogManager.getLogger(Cloner.class);
    private static final String LIMIT_100 = "?limit=100";

    private static final String BIT_BUCKET_SERVER = "http://cuso.edb.se";
    private static final String REST_API_SUFFIX ="/stash";
    private static final String API_PROJECTS = "/projects";
    private static final String API_REPOSITORIES ="/repos";
    private final String outputDir;
    private final String user;
    private final String password;
    private final List<String> projectsToCheckOut;

    public Cloner(String user, String pass, String outputDir, List<String> projectFilter){
        this.user = user;
        this.password = pass;
        this.outputDir = outputDir;
        this.projectsToCheckOut = projectFilter;
    }

    private JSONArray obtainProjects() {
        log.info("Going to obtain projects from: " + BIT_BUCKET_SERVER +REST_API_SUFFIX + API_PROJECTS);

        try {
            return Unirest.get(BIT_BUCKET_SERVER +REST_API_SUFFIX + API_PROJECTS)
                    .basicAuth(user, password)
                    .header("accept", "application/json")
                    .asJson()
                    .getBody()
                    .getObject().getJSONArray("values");
        } catch (UnirestException e) {
            log.error("Cannot obtain list of projects from " + BIT_BUCKET_SERVER +REST_API_SUFFIX + API_PROJECTS);
            return null;
        }
    }

    private JSONArray obtainRepositories(String projectKey) {
        String projectReposURL = BIT_BUCKET_SERVER +REST_API_SUFFIX + API_PROJECTS + "/" + projectKey + API_REPOSITORIES + LIMIT_100;
        log.info("Obtaining repos from " + projectReposURL);
        try {
            return Unirest.get(projectReposURL)
                    .basicAuth(user, password)
                    .header("accept", "application/json")
                    .asJson()
                    .getBody()
                    .getObject().getJSONArray("values");
        } catch (UnirestException e) {
            log.error("Cannot obtain project repositories from " + projectReposURL);
            return null;
        }

    }
    private void cloneRepository(String gitURL,String repoDir){
        log.info("Going to clone repo " + gitURL);
        log.info("Repository will be stored at " + repoDir);
        try {
            Git.cloneRepository()
                    .setURI(gitURL)
                    .setDirectory(new File(repoDir))
                    .setCloneAllBranches(true)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, password))
                    .call();
        } catch (GitAPIException e) {
            log.error("Error on cloning repository from " + gitURL + " into local directory " + repoDir + ". Check the path.");
        }
    }
    public void cloneRepos(){
        JSONArray projects = obtainProjects();
        if (projects.length()<1){
            log.info("There are no projects available to process.");
        } else{
            for (Object p : projects) {
                JSONObject project = (JSONObject) p;
                String projectName = project.getString("name").toLowerCase().replace(" ","_");
                if(projectsToCheckOut.contains(projectName)) {
                    log.info("Project name: " + projectName);
                    String projectKey = project.get("key").toString();
                    log.info("Project key: " + projectKey);

                    JSONArray repositories =  obtainRepositories(projectKey);
                    for (Object repo :repositories) {
                        JSONObject repository = (JSONObject) repo;
                        String repoName = repository.getString("name");
                        log.info("Repository name: " + repoName);
                        String repoDir = outputDir + "/" + projectName + "/" + repoName;
                        log.info("Repository local directory where clone to: " + repoDir);
                        final JSONArray cloneURLs = (JSONArray) ((JSONObject) repository.get("links")).get("clone");
                        for (Object url : cloneURLs) {
                            if (((JSONObject) url).get("name").toString().equals("http")) {
                                log.info("HTTP repository link for clone found.");
                                String repoURL = ((JSONObject) url).getString("href");
                                cloneRepository(repoURL, repoDir);
                            } else {
                                log.info(((JSONObject) url).get("name").toString());
                            }

                        }

                    }
                }
            }
        }

    }

    public static void main(String[] args) throws UnirestException {
        String stashUser = "";
        String stashPass ="";
        String outputDir = "D:/landshypotek/integration-services";
        List<String> repoFilter = Arrays.asList("lp-landshypotek");
        Cloner c = new Cloner(stashUser, stashPass,outputDir,repoFilter);
        c.cloneRepos();
    }
}
