/*
 * Copyright (C) 2015 Maxim Varfolomeyev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.maxvar.jira.cleaner;

import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClient;
import com.atlassian.jira.rest.client.internal.async.DisposableHttpClient;
import com.atlassian.jira.rest.client.internal.async.MyAsynchronousHttpClientFactory;
import com.atlassian.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final URI jiraServerUri = URI.create("https://jira.mycompany.com/");
    private static final String LOGIN = "user@mycompany.com";
    private static final String PASSWORD = "********";
    private static final String PROJECT_CODE = "PKEY";
    private static final String EDGE_DATE = "\"2014-01-01\"";
    private static final int WANTED_BATCH_SIZE = 100;

    public static void main(String[] args) throws IOException {
        LOG.debug("Started Main with {}", args);

        final DisposableHttpClient httpClient = createDisposableHttpClient();

        try (JiraRestClient restClient = new AsynchronousJiraRestClient(jiraServerUri, httpClient)) {
            final int buildNumber = restClient.getMetadataClient().getServerInfo().claim().getBuildNumber();
            LOG.info("Connected to server, server version {}", buildNumber);

            final SearchRestClient searchClient = restClient.getSearchClient();
            final String jql = "project = " + PROJECT_CODE + " AND createdDate < " + EDGE_DATE;

            int leftToDelete = 1;
            while (leftToDelete > 0) {
                final SearchResult searchResult = searchClient.searchJql(jql, WANTED_BATCH_SIZE, null, null).claim();
                final int totalResults = searchResult.getTotal();
                final int batchSize = searchResult.getMaxResults();
                final List<Promise<Void>> promiseList = new ArrayList<>(batchSize);
                LOG.debug("Total  {}, max.results: {}", totalResults, batchSize);

                final long startedTimeMillis = System.currentTimeMillis();
                int deleted = 0;
                LOG.debug("Deleting asynchronously...");
                for (BasicIssue issue : searchResult.getIssues()) {
                    LOG.trace(issue.getKey());
                    promiseList.add(restClient.getIssueClient().deleteIssue(issue.getKey(), true));
                    deleted++;
                }
                final long deletedTimeMillis = System.currentTimeMillis();
                LOG.debug("Collecting results...");
                promiseList.forEach(voidPromise ->
                        {
                            try {
                                voidPromise.claim();
                            } catch (RestClientException ignored) {
                                LOG.warn("Got exception while collecting results", ignored);
                            }
                        }
                );

                final long collectedTimeMillis = System.currentTimeMillis();
                leftToDelete = totalResults - deleted;
                LOG.info("Deleting {} issues took {} ms, collecting results {} ms. Left to delete {}",
                        deleted,
                        (deletedTimeMillis - startedTimeMillis),
                        (collectedTimeMillis - deletedTimeMillis),
                        leftToDelete);
            }
        }
        LOG.debug("Ending Main.");
    }

    private static DisposableHttpClient createDisposableHttpClient() {
        final AuthenticationHandler authenticationHandler = new BasicHttpAuthenticationHandler(LOGIN, PASSWORD);
        final MyAsynchronousHttpClientFactory asynchronousHttpClientFactory = new MyAsynchronousHttpClientFactory();
        return asynchronousHttpClientFactory.createClient(jiraServerUri, authenticationHandler);
    }

}
