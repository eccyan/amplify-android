/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amplifyframework.api.aws;

import com.amplifyframework.AmplifyException;
import com.amplifyframework.api.ApiException;
import com.amplifyframework.api.aws.sigv4.ApiKeyAuthProvider;
import com.amplifyframework.api.aws.sigv4.AppSyncSigV4SignerInterceptor;
import com.amplifyframework.api.aws.sigv4.CognitoUserPoolsAuthProvider;
import com.amplifyframework.api.aws.sigv4.DefaultCognitoUserPoolsAuthProvider;
import com.amplifyframework.api.aws.sigv4.OidcAuthProvider;
import com.amplifyframework.core.Amplify;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.mobile.client.AWSMobileClient;

/**
 * Implementation of {@link InterceptorFactory} that creates
 * {@link AppSyncSigV4SignerInterceptor} from provided configuration.
 * This factory should be constructed once in a plugin.
 */
final class AppSyncSigV4SignerInterceptorFactory implements InterceptorFactory {
    private static final String AUTH_DEPENDENCY_PLUGIN_KEY = "awsCognitoAuthPlugin";
    private final ApiAuthProviders apiAuthProviders;

    AppSyncSigV4SignerInterceptorFactory(ApiAuthProviders apiAuthProviders) {
        this.apiAuthProviders = apiAuthProviders;
    }

    /**
     * Implementation of {@link InterceptorFactory#create(ApiConfiguration)} that
     * uses preconfigured instances of authorization providers to construct a new
     * instance of {@link AppSyncSigV4SignerInterceptor}. It reads the
     * {@link ApiConfiguration} to determine the type of authorization mode, from
     * which it determines the type of authorization provider to use.
     *
     * If the authorization mode is {@link AuthorizationType#API_KEY} and the
     * {@link ApiAuthProviders} was not overridden with a custom
     * {@link ApiKeyAuthProvider}, then the API key is read from
     * {@link ApiConfiguration} each time this method is called.
     *
     * For all other authorization modes, the factory will reuse the auth providers
     * that were generated (or overridden) during construction of this factory
     * instance.
     *
     * @param config API configuration
     * @return configured interceptor that signs requests using
     *         authorization mode specified in API configuration
     */
    @Override
    public AppSyncSigV4SignerInterceptor create(ApiConfiguration config) throws ApiException {
        switch (config.getAuthorizationType()) {
            case API_KEY:
                // API key provider is configured per API, not per plugin.
                // If a custom instance of API key provider was provided, the
                // factory will remember and reuse it.
                // Otherwise, a new lambda is made per interceptor generation.
                ApiKeyAuthProvider keyProvider = apiAuthProviders.getApiKeyAuthProvider();
                if (keyProvider == null) {
                    final String apiKey = config.getApiKey();
                    if (apiKey == null) {
                        throw new IllegalArgumentException("API key in configuration must be non-null.");
                    }
                    keyProvider = () -> apiKey;
                }
                return new AppSyncSigV4SignerInterceptor(config.getEndpointType(), keyProvider);
            case AWS_IAM:
                // Initializes mobile client once and remembers the instance.
                // This instance is reused by this factory.
                AWSCredentialsProvider credentialsProvider = apiAuthProviders.getAWSCredentialsProvider();
                if (credentialsProvider == null) {
                    try {
                        credentialsProvider =
                                (AWSMobileClient) Amplify.Auth.getPlugin(AUTH_DEPENDENCY_PLUGIN_KEY).getEscapeHatch();
                    } catch (IllegalStateException exception) {
                        throw new ApiException(
                                "AWSApiPlugin depends on AWSCognitoAuthPlugin but it is currently missing",
                                exception,
                                "Before configuring Amplify, be sure to add AWSCognitoAuthPlugin same as you added " +
                                        "AWSApiPlugin."
                        );
                    }
                }
                return new AppSyncSigV4SignerInterceptor(credentialsProvider,
                        config.getRegion(),
                        config.getEndpointType());
            case AMAZON_COGNITO_USER_POOLS:

                // Initializes cognito user pool once and remembers the token
                // provider instance. This instance is reused by this factory.
                CognitoUserPoolsAuthProvider cognitoProvider = apiAuthProviders.getCognitoUserPoolsAuthProvider();
                if (cognitoProvider == null) {
                    cognitoProvider = new DefaultCognitoUserPoolsAuthProvider();
                }
                return new AppSyncSigV4SignerInterceptor(cognitoProvider, config.getEndpointType());
            case OPENID_CONNECT:
                // This factory does not have a default implementation for
                // OpenID Connect token provider. User-provided implementation
                // is remembered and reused by this factory.
                OidcAuthProvider oidcProvider = apiAuthProviders.getOidcAuthProvider();
                if (oidcProvider == null) {
                    oidcProvider = () -> {
                        throw new ApiException(
                            "OidcAuthProvider interface is not implemented.",
                                AmplifyException.TODO_RECOVERY_SUGGESTION
                        );
                    };
                }
                return new AppSyncSigV4SignerInterceptor(config.getEndpointType(), oidcProvider);
            default:
                throw new ApiException(
                        "Unsupported authorization mode.",
                        AmplifyException.TODO_RECOVERY_SUGGESTION
                );
        }
    }
}
