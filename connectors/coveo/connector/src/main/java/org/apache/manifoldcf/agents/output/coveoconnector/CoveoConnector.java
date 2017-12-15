/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.agents.output.coveo;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

import org.apache.manifoldcf.agents.output.BaseOutputConnector;

import org.apache.http.HttpResponse;

import java.util.*;
import java.io.*;

public class CoveoConnector extends BaseOutputConnector {
    public static final String _rcsid = "@(#)$Id: CoveoConnector.java 988245 2017-12-11 18:39:35Z jfcloutier $";

    /**
     * Activity definition
     */
    public final static String INGEST_ACTIVITY = "Document Added/Modified";
    public final static String REMOVE_ACTIVITY = "Document Deleted";
    public final static String JOB_COMPLETE_ACTIVITY = "Coveo Push completed";

    /**
     * Forward to the javascript to check the configuration parameters
     */
    private static final String EDIT_CONFIGURATION_JS = "editConfiguration.js";

    /**
     * Forward to the HTML template to edit the configuration parameters
     */
    private static final String EDIT_CONFIGURATION_HTML = "editConfiguration.html";

    /**
     * Forward to the HTML template to view the configuration parameters
     */
    private static final String VIEW_CONFIGURATION_HTML = "viewConfiguration.html";

    /**
     * Constructor.
     */
    public CoveoConnector() {
    }

    /**
     * Return the list of activities that this connector supports (i.e. writes into the log).
     *
     * @return the list.
     */
    @Override
    public String[] getActivitiesList() {
        return new String[]{INGEST_ACTIVITY, REMOVE_ACTIVITY, JOB_COMPLETE_ACTIVITY};
    }

    /**
     * Connect.
     *
     * @param configParameters is the set of configuration parameters, which
     *                         in this case describe the target appliance, basic auth configuration, etc.  (This formerly came
     *                         out of the ini file.)
     */
    @Override
    public void connect(ConfigParams configParameters) {
        super.connect(configParameters);
    }

    /** Set up a session */
    protected void getSession() throws ManifoldCFException, ServiceInterruption {
        // TODO: Have a buffer for when calls are batched
    }

    /**
     * Close the connection.  Call this before discarding the connection.
     */
    @Override
    public void disconnect()
            throws ManifoldCFException {
        super.disconnect();
    }

    /**
     * Test the connection.  Returns a string describing the connection integrity.
     *
     * @return the connection's status as a displayable string.
     */
    @Override
    public String check() throws ManifoldCFException {
        try
        {
            this.getSession();
            return super.check();
        }
        catch (ServiceInterruption e)
        {
            return "Transient error: "+e.getMessage();
        }
    }

    /**
     * Get an output version string, given an output specification.  The output version string is used to uniquely describe the pertinent details of
     * the output specification and the configuration, to allow the Connector Framework to determine whether a document will need to be output again.
     * Note that the contents of the document cannot be considered by this method, and that a different version string (defined in IRepositoryConnector)
     * is used to describe the version of the actual document.
     * <p>
     * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
     * necessary.
     *
     * @param spec is the current output specification for the job that is doing the crawling.
     * @return a string, of unlimited length, which uniquely describes output configuration and specification in such a way that if two such strings are equal,
     * the document will not need to be sent again to the output data store.
     */
    @Override
    public VersionContext getPipelineDescription(Specification spec) throws ManifoldCFException, ServiceInterruption {
        return new VersionContext("", params, spec);
    }

    /**
     * Add (or replace) a document from the Coveo Source.
     * This method presumes that the connector object has been configured, and it is thus able to communicate with the
     * Coveo Push API. No API check is done, but if failing, the request will be retried per manifoldcf, unless the
     * document is rejected or cancelled.
     *
     * @param documentURI         is the URI of the document.  The URI is presumed to be the unique identifier provided by the repository connector.
     * @param outputDescription   is the description string that was constructed for this document by the getOutputDescription() method.
     * @param document            is the document data to be processed (handed to the output data store).
     * @param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in with the repository document.  May be null.
     * @param activities          is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
     * @return the document status (accepted or permanently rejected).
     */
    @Override
    public int addOrReplaceDocumentWithException(String documentURI,
                                                 VersionContext outputDescription,
                                                 RepositoryDocument document,
                                                 String authorityNameString,
                                                 IOutputAddActivity activities
    ) throws ManifoldCFException, ServiceInterruption, IOException {
        this.getSession();
        // TODO: Batch calls, based on the configuration parameter, using the batcher that would be in getSession()

        try {
            // Build permissions Access Control Lists
            HashMap<String, String> ACLs = new HashMap<>();
            HashMap<String, String> denyACLs = new HashMap<>();
            final Iterator<String> securityKeys = document.securityTypesIterator();
            while (securityKeys.hasNext()) {
                final String key = securityKeys.next();
                String ACLValue = String.join(",", document.getSecurityACL(key));
                ACLs.put(key, ACLValue);
                String denyACLValue = String.join(",", document.getSecurityDenyACL(key));
                denyACLs.put(key, denyACLValue);

            }

            // Create the request object
            CoveoRequest request = new CoveoRequest()
                    .addDocumentId(documentURI)
                    .addRequestType(CoveoRequestType.ADD_OR_UPDATE)
                    .addDocumentBody(document.getBinaryStream())
                    .addMetadata("uri", documentURI)
                    .addACL(ACLs)
                    .addDenyACL(denyACLs);

            // Add metadata
            final Iterator<String> metadataKeys = document.getFields();
            while (metadataKeys.hasNext()) {
                final String key = metadataKeys.next();
                final String value = String.join(",", document.getFieldAsStrings(key));
                request.addMetadata(key, value);
            }

            HttpResponse response = request.execute(outputDescription.getParams());

            if (response.getStatusLine().getStatusCode() != 202) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
                StringBuilder responseBody = new StringBuilder();

                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    responseBody.append(line);
                }

                throw new Exception(responseBody.toString());
            }

            activities.recordActivity(null, INGEST_ACTIVITY, new Long(document.getBinaryLength()), documentURI, "OK", "202 - Success");

            return DOCUMENTSTATUS_ACCEPTED;
        } catch (Exception e) {
            // TODO: Improve error handling based on the error code and/or exception
            activities.recordActivity(null, INGEST_ACTIVITY, new Long(document.getBinaryLength()), documentURI, "ERROR", e.toString());

            return DOCUMENTSTATUS_REJECTED;
        }
    }

    /**
     * Remove a document from the Coveo Source.
     *
     * @param documentURI         is the URI of the document.  The URI is presumed to be the unique identifier provided by the repository connector.
     * @param outputDescription   is the description string that was constructed for this document by the getOutputDescription() method.
     * @param activities          is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
     */
    @Override
    public void removeDocument(String documentURI,
                               String outputDescription,
                               IOutputRemoveActivity activities
    ) throws ManifoldCFException, ServiceInterruption {
        this.getSession();
        // TODO: Batch calls, based on the configuration parameter, using the batcher that would be in getSession()

        try {
            CoveoRequest request = new CoveoRequest()
                    .addDocumentId(documentURI)
                    .addRequestType(CoveoRequestType.DELETE);

            HttpResponse response = request.execute(getConfiguration());

            if (response.getStatusLine().getStatusCode() != 202) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
                StringBuilder responseBody = new StringBuilder();

                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    responseBody.append(line);
                }

                throw new Exception(responseBody.toString());
            }

            activities.recordActivity(null, REMOVE_ACTIVITY, null, documentURI, "OK", null);
        } catch (Exception e) {
            // TODO: Improve error handling based on the error code and/or exception
            activities.recordActivity(null, REMOVE_ACTIVITY, null, documentURI, "ERROR", e.toString());
        }

        activities.recordActivity(null, REMOVE_ACTIVITY, null, documentURI, "OK", null);
    }

    /**
     * Notify the connector of a completed job.
     * This is meant to allow the connector to flush any internal data structures it has been keeping around, or to tell the output repository that this
     * is a good time to synchronize things.  It is called whenever a job is either completed or aborted.
     *
     * @param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
     */
    @Override
    public void noteJobComplete(IOutputNotifyActivity activities)
            throws ManifoldCFException, ServiceInterruption {
        // TODO: Once we have a batch mode, clear the outstanding unprocessed batch items from this method
        activities.recordActivity(null, JOB_COMPLETE_ACTIVITY, null, "", "OK", null);
    }

    /**
     * Output the configuration header section.
     * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
     * javascript methods that might be needed by the configuration editing HTML.
     *
     * @param threadContext is the local thread context.
     * @param out           is the output to which any HTML should be sent.
     * @param parameters    are the configuration parameters, as they currently exist, for this connection being configured.
     * @param tabsArray     is an array of tab names.  Add to this array any tab names that are specific to the connector.
     */
    @Override
    public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray) throws ManifoldCFException, IOException {
        super.outputConfigurationHeader(threadContext, out, locale, parameters, tabsArray);
        tabsArray.add(Messages.getString(locale, "CoveoConnector.Parameters"));
        outputResource(EDIT_CONFIGURATION_JS, out, locale, null, null, null, null);
    }

    /**
     * Output the configuration body section.
     * This method is called in the body section of the connector's configuration page.  Its purpose is to present the required form elements for editing.
     * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
     * form is "editconnection".
     *
     * @param threadContext is the local thread context.
     * @param out           is the output to which any HTML should be sent.
     * @param parameters    are the configuration parameters, as they currently exist, for this connection being configured.
     * @param tabName       is the current tab name.
     */
    @Override
    public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName) throws ManifoldCFException, IOException {
        super.outputConfigurationBody(threadContext, out, locale, parameters, tabName);
        CoveoConfigParams config = this.getConfigParameters(parameters);
        outputResource(EDIT_CONFIGURATION_HTML, out, locale, config, tabName, null, null);
    }

    /**
     * Read the content of a resource, replace the variable ${PARAMNAME} with the
     * value and copy it to the out.
     *
     * @param resName
     * @param out
     * @throws ManifoldCFException
     */
    private static void outputResource(String resName, IHTTPOutput out, Locale locale, CoveoConfigParams params, String tabName, Integer sequenceNumber, Integer actualSequenceNumber) throws ManifoldCFException {
        Map<String, String> paramMap = null;
        if (params != null) {
            paramMap = params.buildMap();
            if (tabName != null) {
                paramMap.put("TabName", tabName);
            }
            if (actualSequenceNumber != null)
                paramMap.put("SelectedNum", actualSequenceNumber.toString());
        } else {
            paramMap = new HashMap<String, String>();
        }
        if (sequenceNumber != null)
            paramMap.put("SeqNum", sequenceNumber.toString());
        Messages.outputResourceWithVelocity(out, locale, resName, paramMap, true);
    }

    /**
     * Process a configuration post.
     * This method is called at the start of the connector's configuration page, whenever there is a possibility that form data for a connection has been
     * posted.  Its purpose is to gather form information and modify the configuration parameters accordingly.
     * The name of the posted form is "editconnection".
     *
     * @param threadContext   is the local thread context.
     * @param variableContext is the set of variables available from the post, including binary file post information.
     * @param parameters      are the configuration parameters, as they currently exist, for this connection being configured.
     * @return null if all is well, or a string error message if there is an error that should prevent saving of the connection (and cause a redirection to an error page).
     */
    @Override
    public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext, Locale locale, ConfigParams parameters) throws ManifoldCFException {
        CoveoConfig.contextToConfig(variableContext, parameters);
        return null;
    }

    /**
     * View configuration.
     * This method is called in the body section of the connector's view configuration page.  Its purpose is to present the connection information to the user.
     * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
     *
     * @param threadContext is the local thread context.
     * @param out           is the output to which any HTML should be sent.
     * @param parameters    are the configuration parameters, as they currently exist, for this connection being configured.
     */
    @Override
    public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters) throws ManifoldCFException, IOException {
        outputResource(VIEW_CONFIGURATION_HTML, out, locale, getConfigParameters(parameters), null, null, null);
    }

    /**
     * @param configParams
     * @return
     */
    final private CoveoConfigParams getConfigParameters(ConfigParams configParams) {
        if (configParams == null)
            configParams = getConfiguration();
        return new CoveoConfig(configParams);
    }
}
