/**
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.credential.salesforce;

import io.syndesis.credential.Applicator;
import io.syndesis.credential.CredentialProvider;
import io.syndesis.credential.CredentialProviderLocator;
import io.syndesis.credential.DefaultCredentialProvider;
import io.syndesis.credential.OAuth2Applicator;
import io.syndesis.model.connection.Connection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.social.SocialProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.social.oauth2.AccessGrant;
import org.springframework.social.oauth2.OAuth2Template;
import org.springframework.social.salesforce.api.Salesforce;
import org.springframework.social.salesforce.connect.SalesforceConnectionFactory;

@Configuration
@ConditionalOnClass(SalesforceConnectionFactory.class)
@ConditionalOnProperty(prefix = "spring.social.salesforce", name = "app-id")
@EnableConfigurationProperties(SalesforceProperties.class)
public class SalesforceConfiguration {

    protected static final class SalesforceApplicator extends OAuth2Applicator {

        private final SalesforceConnectionFactory salesforce;

        public SalesforceApplicator(final SalesforceConnectionFactory salesforce,
            final SocialProperties socialProperties) {
            super(socialProperties);
            this.salesforce = salesforce;

            setClientIdProperty("clientId");
            setClientSecretProperty("clientSecret");
            setRefreshTokenProperty("refreshToken");
        }

        @Override
        protected void additionalApplication(final Connection.Builder mutableConnection,
            final AccessGrant accessGrant) {

            final org.springframework.social.connect.Connection<Salesforce> salesforceConnection = salesforce
                .createConnection(accessGrant);
            final Salesforce salesforceApi = salesforceConnection.getApi();

            final String instanceUrl = salesforceApi.getInstanceUrl();
            Applicator.applyProperty(mutableConnection, "instanceUrl", instanceUrl);
        }
    }

    @Autowired
    public SalesforceConfiguration(final SalesforceProperties salesforceProperties,
        final CredentialProviderLocator locator) {
        locator.addCredentialProvider(create(salesforceProperties));
    }

    public static CredentialProvider<Salesforce, AccessGrant> create(final SalesforceProperties salesforceProperties) {
        final SalesforceConnectionFactory connectionFactory = createConnectionFactory(salesforceProperties);

        return new DefaultCredentialProvider<>("salesforce", connectionFactory,
            new SalesforceApplicator(connectionFactory, salesforceProperties));
    }

    protected static SalesforceConnectionFactory
        createConnectionFactory(final SalesforceProperties salesforceProperties) {
        final SalesforceConnectionFactory salesforce = new SalesforceConnectionFactory(salesforceProperties.getAppId(),
            salesforceProperties.getAppSecret());

        final OAuth2Template oAuthOperations = (OAuth2Template) salesforce.getOAuthOperations();

        // Salesforce requires OAuth client id and secret on the OAuth request
        oAuthOperations.setUseParametersForClientAuthentication(true);

        return salesforce;
    }

}
