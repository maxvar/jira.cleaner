# jira.cleaner
Cleaner tool for JIRA

Designed to massively delete old issues from a given project together with subtasks and attachments.
Uses Jira REST Java Client (JRJC) to access functionality with customized http client implementation in MyAsynchronousHttpClientFactory to increase the default timeout (5s).

Tested on a real Jira instance, deleting about 150K issues from 5 projects.

To run this you need to set up constants in Main:
* jiraServerUri to point to your Jira instance
* LOGIN and PASSWORD of your credentials with sufficient privileges to delete issues
* PROJECT_CODE of a project you want to clean
* all issues in the project before EDGE_DATE (YYYY-MM-DD) will be deleted
* WANTED_BATCH_SIZE sets the number of isses in a batch returned by search and deleted

Proceed with caution and good luck! =)
