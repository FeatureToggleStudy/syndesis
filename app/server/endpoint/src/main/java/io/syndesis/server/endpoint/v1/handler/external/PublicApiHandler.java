/*
 * Copyright (C) 2016 Red Hat, Inc.
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
package io.syndesis.server.endpoint.v1.handler.external;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.StreamingOutput;

import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import io.syndesis.common.model.Kind;
import io.syndesis.common.model.ListResult;
import io.syndesis.common.model.WithId;
import io.syndesis.common.model.WithName;
import io.syndesis.common.model.WithResourceId;
import io.syndesis.common.model.connection.Connection;
import io.syndesis.common.model.connection.ConnectionOverview;
import io.syndesis.common.model.connection.Connector;
import io.syndesis.common.model.integration.ContinuousDeliveryEnvironment;
import io.syndesis.common.model.integration.ContinuousDeliveryImportResults;
import io.syndesis.common.model.integration.Integration;
import io.syndesis.common.model.integration.IntegrationDeployment;
import io.syndesis.common.model.integration.IntegrationDeploymentState;
import io.syndesis.common.model.monitoring.IntegrationDeploymentStateDetails;
import io.syndesis.server.dao.manager.DataManager;
import io.syndesis.server.dao.manager.EncryptionComponent;
import io.syndesis.server.endpoint.monitoring.MonitoringProvider;
import io.syndesis.server.endpoint.v1.handler.connection.ConnectionHandler;
import io.syndesis.server.endpoint.v1.handler.integration.IntegrationDeploymentHandler;
import io.syndesis.server.endpoint.v1.handler.integration.support.IntegrationSupportHandler;

@Api("public-api")
@Path("/public")
@Component
@ConditionalOnProperty(value = "features.public-api.enabled", havingValue = "true")
public class PublicApiHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PublicApiHandler.class);
    private static final String PROPERTY_INTEGRATION_ID = "integrationId";

    private final DataManager dataMgr;
    private final IntegrationSupportHandler handler;
    private final EncryptionComponent encryptionComponent;
    private final IntegrationDeploymentHandler deploymentHandler;
    private final ConnectionHandler connectionHandler;
    private final MonitoringProvider monitoringProvider;

    protected PublicApiHandler(DataManager dataMgr, IntegrationSupportHandler handler, EncryptionComponent encryptionComponent, IntegrationDeploymentHandler deploymentHandler, ConnectionHandler connectionHandler, MonitoringProvider monitoringProvider) {
        this.dataMgr = dataMgr;
        this.handler = handler;
        this.encryptionComponent = encryptionComponent;
        this.deploymentHandler = deploymentHandler;
        this.connectionHandler = connectionHandler;
        this.monitoringProvider = monitoringProvider;
    }

    /**
     * List all available environments.
     */
    @GET
    @Path("environments")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getReleaseEnvironments() {
        return dataMgr.fetchAll(Integration.class).getItems().stream()
                .filter(i -> !i.isDeleted())
                .flatMap(i -> i.getContinuousDeliveryState().keySet().stream())
                .collect(Collectors.toList());
    }

    /**
     * Delete an environment across all integrations.
     */
    @DELETE
    @Path("environments/{env}")
    public void deleteEnvironment(@NotNull @PathParam("env") @ApiParam(required = true) String environment) {

        validateParam("environment", environment);

        // get and update list of integrations with this environment
        final List<Integration> integrations = dataMgr.fetchAll(Integration.class)
                .getItems()
                .stream()
                .filter(i -> i.getContinuousDeliveryState().containsKey(environment))
                .map(i -> {
                    final Map<String, ContinuousDeliveryEnvironment> state = new HashMap<>(i.getContinuousDeliveryState());
                    // untag
                    state.remove(environment);

                    return i.builder().continuousDeliveryState(state).build();
                })
                .collect(Collectors.toList());

        // no integrations using this environment name
        if (integrations.isEmpty()) {
            throw new ClientErrorException("Missing environment " + environment, Response.Status.NOT_FOUND);
        }

        // update environment names
        integrations.forEach(dataMgr::update);

    }

    /**
     * Rename an environment across all integrations.
     */
    @PUT
    @Path("environments/{env}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void renameEnvironment(@NotNull @PathParam("env") @ApiParam(required = true) String environment,
                                  @NotNull @ApiParam(required = true) String newEnvironment) {

        validateParam("environment", environment);
        validateParam("newEnvironment", newEnvironment);

        // ignore request if names are the same
        if (environment.equals(newEnvironment)) {
            return;
        }

        // get and update list of integrations with this environment
        final List<Integration> integrations = dataMgr.fetchAll(Integration.class)
                .getItems()
                .stream()
                .filter(i -> i.getContinuousDeliveryState().containsKey(environment))
                .map(i -> {
                    final Map<String, ContinuousDeliveryEnvironment> state = new HashMap<>(i.getContinuousDeliveryState());

                    state.put(newEnvironment, state.remove(environment)
                            .builder()
                            .name(newEnvironment)
                            .build());

                    return i.builder().continuousDeliveryState(state).build();
                })
                .collect(Collectors.toList());

        // environment not in use
        if (integrations.isEmpty()) {
            throw new ClientErrorException("Missing environment " + environment, Response.Status.NOT_FOUND);
        }

        // update environment names
        integrations.forEach(dataMgr::update);

    }

    /**
     * List all tags associated with this integration.
     */
    @GET
    @Path("integrations/{id}/tags")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, ContinuousDeliveryEnvironment> getReleaseTags(@NotNull @PathParam("id") @ApiParam(required = true) String integrationId) {
        final Map<String, ContinuousDeliveryEnvironment> result = new HashMap<>(
                getIntegration(integrationId).getContinuousDeliveryState());
        getReleaseEnvironments().forEach(e -> result.putIfAbsent(e, null));
        return result;
    }

    /**
     * Delete an environment tag associated with this integration.
     */
    @DELETE
    @Path("integrations/{id}/tags/{env}")
    public void deleteReleaseTag(@NotNull @PathParam("id") @ApiParam(required = true) String integrationId, @NotNull @PathParam("env") @ApiParam(required = true) String environment) {

        final Integration integration = getIntegration(integrationId);
        validateParam("environment", environment);
        final Map<String, ContinuousDeliveryEnvironment> deliveryState = new HashMap<>(integration.getContinuousDeliveryState());
        if (null == deliveryState.remove(environment)) {
            throw new ClientErrorException("Missing environment tag " + environment, Response.Status.NOT_FOUND);
        }

        // update json db
        dataMgr.update(integration.builder().continuousDeliveryState(deliveryState).build());
    }

    /**
     * Tag an integration for release to target environments.
     */
    @POST
    @Path("integrations/{id}/tags")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, ContinuousDeliveryEnvironment> tagForRelease(@NotNull @PathParam("id") @ApiParam(required = true) String integrationId,
                                                             @NotNull @ApiParam(required = true) List<String> environments) {

        if (environments == null || environments.isEmpty()) {
            throw new ClientErrorException("Missing parameter environments", Response.Status.BAD_REQUEST);
        }
        // validate individual environment names
        environments.forEach(e -> validateParam("environment", e));

        // fetch integration
        final Integration integration = getIntegration(integrationId);
        final HashMap<String, ContinuousDeliveryEnvironment> deliveryState = new HashMap<>(integration.getContinuousDeliveryState());

        Map<String, ContinuousDeliveryEnvironment> result = new HashMap<>();
        Date lastTaggedAt = new Date();
        for (String environment : environments) {
            // create or update tag
            result.put(environment, createOrUpdateTag(deliveryState, environment, lastTaggedAt));
        }

        // update json db
        dataMgr.update(integration.builder().continuousDeliveryState(deliveryState).build());

        LOG.debug("Tagged integration {} for environments {} at {}", integrationId, environments, lastTaggedAt);

        return result;
    }

    private ContinuousDeliveryEnvironment createOrUpdateTag(Map<String,
            ContinuousDeliveryEnvironment> deliveryState, String environment, Date lastTaggedAt) {

        ContinuousDeliveryEnvironment result = deliveryState.get(environment);
        if (result == null) {
            result = ContinuousDeliveryEnvironment.Builder.createFrom(environment, lastTaggedAt);
        } else {
            result = ContinuousDeliveryEnvironment.Builder.createFrom(result, lastTaggedAt);
        }
        deliveryState.put(environment, result);
        return result;
    }

    /**
     * Export integrations to a target environment.
     */
    @GET
    @Path("integrations/{env}/export.zip")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response exportResources(@NotNull @PathParam("env") @ApiParam(required = true) String environment,
                                    @QueryParam("all") @ApiParam boolean exportAll) throws IOException {

        // validate environment
        validateParam("environment", environment);

        // lookup integrations to export for this environment
        final ListResult<Integration> integrations;

        // export all integrations ignoring any missing/existing tags?
        if (exportAll) {

            integrations = dataMgr.fetchAll(Integration.class);

            // tag all integrations for export
            Date taggedAt = new Date();
            integrations.getItems().forEach(i -> {
                final HashMap<String, ContinuousDeliveryEnvironment> state = new HashMap<>(i.getContinuousDeliveryState());
                createOrUpdateTag(state, environment, taggedAt);
                dataMgr.update(i.builder().continuousDeliveryState(state).build());
            });

            LOG.debug("Exporting ALL ({}) integrations for environment {} at {} ...", integrations.getItems().size(), environment, taggedAt);
        } else {

            // export integrations freshly tagged or tagged after last export
            integrations = dataMgr.fetchAll(Integration.class, listResult -> listResult.getItems().stream().filter(i -> {

                boolean result = false;
                final Map<String, ContinuousDeliveryEnvironment> map = i.getContinuousDeliveryState();
                if (map.containsKey(environment)) {
                    final Date taggedAt = map.get(environment).getLastTaggedAt();
                    final Date exportedAt = map.get(environment).getLastExportedAt().orElse(null);
                    result = exportedAt == null || exportedAt.before(taggedAt);
                }

                return result;
            }).collect(ListResult.collector()).build());

            LOG.debug("Exporting ({}) integrations for environment {} ...", integrations.getItems().size(), environment);
        }

        final List<String> ids = integrations.getItems().stream()
                .map(integration -> integration.getId().get())
                .collect(Collectors.toList());

        if (ids.isEmpty()) {
            return Response.status(Response.Status.NO_CONTENT.getStatusCode(), "No integrations to export").build();
        }

        final Date exportedAt = new Date();
        final StreamingOutput output = handler.export(ids);

        // update lastExportedAt
        updateCDEnvironments(integrations.getItems(), environment, exportedAt, b -> b.lastExportedAt(exportedAt));

        LOG.debug("Exported ({}) integrations for environment {}", ids.size(), environment);
        return Response.ok(output).build();
    }

    private void validateParam(String name, String param) {
        if (param == null || param.isEmpty()) {
            throw new ClientErrorException("Missing parameter " + name, Response.Status.BAD_REQUEST);
        }
    }

    /**
     * Import integrations into a target environment.
     */
    @POST
    @Path("integrations")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ContinuousDeliveryImportResults importResources(@Context SecurityContext sec,
                                                    @NotNull @MultipartForm @ApiParam(required = true) ImportFormDataInput formInput) {

        if (formInput == null) {
            throw new ClientErrorException("Multipart request is empty", Response.Status.BAD_REQUEST);
        }

        final String environment = formInput.getEnvironment();
        validateParam("environment", environment);
        final boolean deploy = Boolean.TRUE.equals(formInput.getDeploy());

        try {
            // actual import data created using the exportResources endpoint above
            final InputStream importFile = formInput.getData();
            if (importFile == null) {
                throw new ClientErrorException("Missing file 'data' in multipart request", Response.Status.BAD_REQUEST);
            }

            // importedAt date to be updated in all imported integrations
            final Date lastImportedAt = new Date();

            final Map<String, List<WithResourceId>> resources = handler.importIntegration(sec, importFile);
            final List<WithResourceId> results = new ArrayList<>();
            resources.values().forEach(results::addAll);

            // get imported integrations
            final List<Integration> integrations = getResourcesOfType(results, Integration.class);

            // update importedAt field for imported integrations
            updateCDEnvironments(integrations, environment, lastImportedAt, b -> b.lastImportedAt(lastImportedAt));

            // optional connection properties configuration file
            final InputStream properties = formInput.getProperties();
            if (properties != null) {

                // update connection fields using properties
                Map<String, Map<String, String>> params = getParams(properties);

                final List<Connection> connections = getResourcesOfType(results, Connection.class);
                connections.forEach(c -> {
                    final Map<String, String> values = params.get(c.getName());
                    if (values != null) {
                        updateConnection(c, values, lastImportedAt);
                    }
                });
            }

            if (deploy) {
                // deploy integrations
                integrations.forEach(i -> publishIntegration(sec, i));
            }

            return new ContinuousDeliveryImportResults.Builder()
                    .lastImportedAt(lastImportedAt)
                    .results(results)
                    .build();

        } catch (IOException e) {
            throw new ClientErrorException(String.format("Error processing multipart request: %s", e.getMessage()),
                    Response.Status.BAD_REQUEST, e);
        }
    }

    /**
     * Configure a connection.
     */
    @POST
    @Path("connections/{id}/properties")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ConnectionOverview configureConnection(@Context SecurityContext sec, @NotNull @PathParam("id") @ApiParam(required = true) String connectionId,
                                           @NotNull @ApiParam(required = true) Map<String, String> properties) {

        validateParam("connectionId", connectionId);
        final Connection connection = getResource(Connection.class, connectionId);

        updateConnection(connection, properties);
        return connectionHandler.get(connectionId);
    }

    /**
     * Get Integration state.
     */
    @GET
    @Path("integrations/{id}/state")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public IntegrationDeploymentStateDetails getIntegrationState(@Context SecurityContext sec, @NotNull @PathParam("id") @ApiParam(required = true) String integrationId) {
        return monitoringProvider.getIntegrationStateDetails(getIntegration(integrationId).getId().get());
    }

    /**
     * Start Integration.
     */
    @POST
    @Path("integrations/{id}/deployments")
    @Produces(MediaType.APPLICATION_JSON)
    public IntegrationDeployment publishIntegration(@Context final SecurityContext sec, @NotNull @PathParam("id") @ApiParam(required = true) final String integrationId) {
        final Integration integration = getIntegration(integrationId);
        return publishIntegration(sec, integration);
    }

    /**
     * Stop Integration.
     */
    @PUT
    @Path("integrations/{id}/deployments/stop")
    @Produces(MediaType.APPLICATION_JSON)
    public void stopIntegration(@Context final SecurityContext sec, @NotNull @PathParam("id") @ApiParam(required = true) final String integrationId) {

        final Integration integration = getIntegration(integrationId);
        IntegrationDeploymentHandler.TargetStateRequest targetState = new IntegrationDeploymentHandler
                .TargetStateRequest();
        targetState.setTargetState(IntegrationDeploymentState.Unpublished);

        // find current deployed version
        final IntegrationDeployment[] deployment = {null};
        dataMgr.fetchAllByPropertyValue(IntegrationDeployment
                .class, PROPERTY_INTEGRATION_ID, integration.getId().get())
                .forEach(d -> {
                    if (d.getCurrentState() == IntegrationDeploymentState.Published) {
                        deployment[0] = d;
                    }
                });

        if (deployment[0] != null) {
            deploymentHandler.updateTargetState(integration.getId().get(), deployment[0].getVersion(), targetState);
        } else {
            throw new ClientErrorException("Integration " + integrationId + " is not published", Response.Status.FORBIDDEN);
        }
    }

    private IntegrationDeployment publishIntegration(SecurityContext sec, Integration integration) {
        return deploymentHandler.update(sec, integration.getId().get());
    }

    private void updateConnection(Connection c, Map<String, String> values) {
        updateConnection(c, values, new Date());
    }

    private void updateConnection(Connection c, Map<String, String> values, Date lastImportedAt) {
        // encrypt properties
        final Connector connector = dataMgr.fetch(Connector.class, c.getConnectorId());
        final Map<String, String> encryptedValues = encryptionComponent.encryptPropertyValues(values,
                connector.getProperties());

        // update connection properties
        final HashMap<String, String> map = new HashMap<>(c.getConfiguredProperties());
        map.putAll(encryptedValues);

        // TODO how can credential flow be handled without a user session??
        // is there any way to determine which connections require manual intervention??
        dataMgr.update(c.builder()
                .configuredProperties(map)
                .lastUpdated(lastImportedAt)
                .build());
    }

    private Integration getIntegration(String integrationId) {
        validateParam(PROPERTY_INTEGRATION_ID, integrationId);
        return getResource(Integration.class, integrationId);
    }

    private <T extends WithId<T> & WithName> T getResource(Class<T> resourceClass, String nameOrId) {
        // try fetching by name first, then by id
        final T resource = dataMgr.fetchByPropertyValue(resourceClass, "name", nameOrId)
                .orElse(dataMgr.fetch(resourceClass, nameOrId));
        if (resource == null) {
            throw new ClientErrorException(
                    String.format("Missing %s with name/id %s", Kind.from(resourceClass).getModelName(), nameOrId),
                    Response.Status.NOT_FOUND);
        }
        return resource;
    }

    private Map<String, Map<String, String>> getParams(InputStream paramFile) throws IOException {
        Properties properties = new Properties();
        properties.load(paramFile);

        Map<String, Map<String, String>> result = new HashMap<>();
        properties.forEach((key1, value) -> {

            final String key = key1.toString();
            final String val = value.toString();

            // key is of the form <connection-name>.<property-name>
            final int index = key.indexOf('.');
            if (index == -1) {
                LOG.warn(String.format("Ignoring invalid substitution key: %s", key));
            } else {
                final String conn = key.substring(0, index);
                final String prop = key.substring(index + 1);

                final Map<String, String> valueMap = result.computeIfAbsent(conn, k -> new HashMap<>());
                valueMap.put(prop, val);
            }
        });

        return result;
    }

    private <T> List<T> getResourcesOfType(List<WithResourceId> resources, Class<T> type) {
        return resources.stream()
                        .filter(type::isInstance)
                        .map(type::cast)
                        .collect(Collectors.toList());
    }

    private void updateCDEnvironments(List<Integration> integrations, String environment, Date taggedAt,
                                      Function<ContinuousDeliveryEnvironment.Builder, ContinuousDeliveryEnvironment.Builder> operator) {
        integrations.forEach(i -> updateCDEnvironment(i, environment, taggedAt, operator));
    }

    private void updateCDEnvironment(Integration integration, String environment, Date taggedAt,
                                     Function<ContinuousDeliveryEnvironment.Builder, ContinuousDeliveryEnvironment.Builder> operator) {
        final Map<String, ContinuousDeliveryEnvironment> map = new HashMap<>(integration.getContinuousDeliveryState());
        map.put(environment, operator.apply(map.getOrDefault(environment,
                ContinuousDeliveryEnvironment.Builder.createFrom(environment, taggedAt)).builder()).build());
        dataMgr.update(integration.builder().continuousDeliveryState(map).build());
    }

    /**
     * DTO for {@link org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput} for importResources.
     */
    public static class ImportFormDataInput {
        @FormParam("data")
        private InputStream data;

        @FormParam("properties")
        private InputStream properties;

        @FormParam("environment")
        private String environment;

        @FormParam("deploy")
        private Boolean deploy;

        public InputStream getData() {
            return data;
        }

        public void setData(InputStream data) {
            this.data = data;
        }

        public InputStream getProperties() {
            return properties;
        }

        public void setProperties(InputStream properties) {
            this.properties = properties;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public Boolean getDeploy() {
            return deploy;
        }

        public void setDeploy(Boolean deploy) {
            this.deploy = deploy;
        }
    }
}
