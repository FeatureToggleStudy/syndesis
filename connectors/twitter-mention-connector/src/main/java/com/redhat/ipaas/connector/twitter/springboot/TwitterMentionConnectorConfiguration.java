package com.redhat.ipaas.connector.twitter.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * This connector integrates with Twitter to send tweets or search for tweets
 * and more.
 * 
 * Generated by camel-connector-maven-plugin - do not edit this file!
 */
@ConfigurationProperties(prefix = "twitter-mention")
public class TwitterMentionConnectorConfiguration {

    /**
     * The access token
     */
    private String accessToken;
    /**
     * The access token secret
     */
    private String accessTokenSecret;
    /**
     * The consumer key
     */
    private String consumerKey;
    /**
     * The consumer secret
     */
    private String consumerSecret;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessTokenSecret() {
        return accessTokenSecret;
    }

    public void setAccessTokenSecret(String accessTokenSecret) {
        this.accessTokenSecret = accessTokenSecret;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }
}