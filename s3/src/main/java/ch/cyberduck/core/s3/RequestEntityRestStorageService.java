package ch.cyberduck.core.s3;

/*
 * Copyright (c) 2002-2015 David Kocher. All rights reserved.
 * http://cyberduck.ch/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to feedback@cyberduck.ch
 */

import ch.cyberduck.core.Host;
import ch.cyberduck.core.PreferencesUseragentProvider;
import ch.cyberduck.core.preferences.HostPreferences;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.util.InetAddressUtils;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jets3t.service.Constants;
import org.jets3t.service.Jets3tProperties;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.ServiceException;
import org.jets3t.service.acl.AccessControlList;
import org.jets3t.service.impl.rest.XmlResponsesSaxParser;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.StorageBucket;
import org.jets3t.service.model.StorageBucketLoggingStatus;
import org.jets3t.service.model.StorageObject;
import org.jets3t.service.model.WebsiteConfig;
import org.jets3t.service.security.AWSSessionCredentials;
import org.jets3t.service.utils.ServiceUtils;

import java.util.Calendar;
import java.util.Collections;
import java.util.Map;

public class RequestEntityRestStorageService extends RestS3Service {
    private static final Logger log = LogManager.getLogger(RequestEntityRestStorageService.class);

    private final S3Session session;
    private final Jets3tProperties properties;

    protected static Jets3tProperties toProperties(final Host bookmark, final S3Protocol.AuthenticationHeaderSignatureVersion signatureVersion) {
        final Jets3tProperties properties = new Jets3tProperties();
        if(log.isDebugEnabled()) {
            log.debug(String.format("Configure for endpoint %s", bookmark));
        }
        properties.setProperty("s3service.enable-storage-classes", String.valueOf(true));
        // The maximum number of retries that will be attempted when an S3 connection fails
        // with an InternalServer error. To disable retries of InternalError failures, set this to 0.
        properties.setProperty("s3service.internal-error-retry-max", String.valueOf(0));
        // The maximum number of concurrent communication threads that will be started by
        // the multi-threaded service for upload and download operations.
        properties.setProperty("s3service.max-thread-count", String.valueOf(1));
        properties.setProperty("httpclient.proxy-autodetect", String.valueOf(false));
        properties.setProperty("httpclient.retry-max", String.valueOf(0));
        properties.setProperty("storage-service.internal-error-retry-max", String.valueOf(0));
        properties.setProperty("storage-service.request-signature-version", signatureVersion.toString());
        properties.setProperty("storage-service.disable-live-md5", String.valueOf(true));
        properties.setProperty("storage-service.default-region", bookmark.getRegion());
        properties.setProperty("xmlparser.sanitize-listings", String.valueOf(false));
        for(Map.Entry<String, String> property : bookmark.getProtocol().getProperties().entrySet()) {
            properties.setProperty(property.getKey(), property.getValue());
        }
        return properties;
    }

    public RequestEntityRestStorageService(final S3Session session, final HttpClientBuilder configuration) {
        super(null, new PreferencesUseragentProvider().get(), null, toProperties(session.getHost(), session.getSignatureVersion()));
        this.session = session;
        this.properties = this.getJetS3tProperties();
        // Client configuration
        final RequestEntityRestStorageService authorizer = this;
        configuration.setRetryHandler(new S3HttpRequestRetryHandler(authorizer, new HostPreferences(session.getHost()).getInteger("http.connections.retry")));
        configuration.setRedirectStrategy(new S3BucketRegionRedirectStrategy(this, session, authorizer));
        this.setHttpClient(configuration.build());
    }

    public Jets3tProperties getConfiguration() {
        return properties;
    }

    @Override
    public String getEndpoint() {
        return session.getHost().getHostname();
    }

    @Override
    protected void initializeDefaults() {
        //
    }

    @Override
    protected HttpClientBuilder initHttpClientBuilder() {
        return null;
    }


    @Override
    protected void initializeProxy(final HttpClientBuilder httpClientBuilder) {
        //
    }

    protected HttpUriRequest setupConnection(final String method, final String bucketName,
                                             final String objectKey, final Map<String, String> requestParameters) throws S3ServiceException {
        return this.setupConnection(HTTP_METHOD.valueOf(method), bucketName, objectKey, requestParameters);
    }

    @Override
    public HttpUriRequest setupConnection(final HTTP_METHOD method, final String bucketName,
                                          final String objectKey, final Map<String, String> requestParameters) throws S3ServiceException {
        final HttpUriRequest request = new S3RequestFactory(session).create(method.name(), bucketName, objectKey,
                null == requestParameters ? Collections.emptyMap() : requestParameters);
        if(this.getProviderCredentials() instanceof AWSSessionCredentials) {
            request.setHeader(Constants.AMZ_SECURITY_TOKEN, ((AWSSessionCredentials) getProviderCredentials()).getSessionToken());
        }
        return request;
    }

    @Override
    protected boolean getDisableDnsBuckets() {
        if(InetAddressUtils.isIPv4Address(session.getHost().getHostname()) || InetAddressUtils.isIPv6Address(session.getHost().getHostname())) {
            if(log.isWarnEnabled()) {
                log.warn(String.format("Disable virtual host style requests for hostname %s", session.getHost().getHostname()));
            }
            return true;
        }
        return new HostPreferences(session.getHost()).getBoolean("s3.bucket.virtualhost.disable");
    }

    @Override
    protected boolean isTargettingGoogleStorageService() {
        return session.getHost().getHostname().equals(Constants.GS_DEFAULT_HOSTNAME);
    }

    @Override
    protected StorageBucket createBucketImpl(String bucketName, String location,
                                             AccessControlList acl, Map<String, Object> headers) throws ServiceException {
        return super.createBucketImpl(bucketName, location, acl, headers);
    }

    @Override
    public void putObjectWithRequestEntityImpl(String bucketName, StorageObject object,
                                               HttpEntity requestEntity, Map<String, String> requestParams) throws ServiceException {
        super.putObjectWithRequestEntityImpl(bucketName, object, requestEntity, requestParams);
    }

    @Override
    public StorageObject getObjectImpl(boolean headOnly, String bucketName, String objectKey,
                                       Calendar ifModifiedSince, Calendar ifUnmodifiedSince,
                                       String[] ifMatchTags, String[] ifNoneMatchTags,
                                       Long byteRangeStart, Long byteRangeEnd, String versionId,
                                       Map<String, Object> requestHeaders,
                                       Map<String, String> requestParameters) throws ServiceException {
        return super.getObjectImpl(headOnly, bucketName, objectKey, ifModifiedSince, ifUnmodifiedSince, ifMatchTags, ifNoneMatchTags, byteRangeStart, byteRangeEnd,
                versionId, requestHeaders, requestParameters);
    }

    @Override
    public void verifyExpectedAndActualETagValues(String expectedETag, StorageObject uploadedObject) throws ServiceException {
        if(StringUtils.isBlank(uploadedObject.getETag())) {
            log.warn("No ETag to verify");
            return;
        }
        super.verifyExpectedAndActualETagValues(expectedETag, uploadedObject);
    }

    /**
     * @return the identifier for the signature algorithm.
     */
    @Override
    protected String getSignatureIdentifier() {
        return session.getSignatureIdentifier();
    }

    /**
     * @return header prefix for general Google Storage headers: x-goog-.
     */
    @Override
    public String getRestHeaderPrefix() {
        return session.getRestHeaderPrefix();
    }

    /**
     * @return header prefix for Google Storage metadata headers: x-goog-meta-.
     */
    @Override
    public String getRestMetadataPrefix() {
        return session.getRestMetadataPrefix();
    }

    @Override
    protected XmlResponsesSaxParser getXmlResponseSaxParser() throws ServiceException {
        return session.getXmlResponseSaxParser();
    }

    @Override
    public void setBucketLoggingStatusImpl(String bucketName, StorageBucketLoggingStatus status) throws ServiceException {
        super.setBucketLoggingStatusImpl(bucketName, status);
    }

    @Override
    public StorageBucketLoggingStatus getBucketLoggingStatusImpl(String bucketName) throws ServiceException {
        return super.getBucketLoggingStatusImpl(bucketName);
    }

    @Override
    public WebsiteConfig getWebsiteConfigImpl(String bucketName) throws ServiceException {
        return super.getWebsiteConfigImpl(bucketName);
    }

    @Override
    public void setWebsiteConfigImpl(String bucketName, WebsiteConfig config) throws ServiceException {
        super.setWebsiteConfigImpl(bucketName, config);
    }

    @Override
    public void deleteWebsiteConfigImpl(String bucketName) throws ServiceException {
        super.deleteWebsiteConfigImpl(bucketName);
    }

    @Override
    public void authorizeHttpRequest(final HttpUriRequest httpMethod, final HttpContext context,
                                     final String forceRequestSignatureVersion) throws ServiceException {
        if(forceRequestSignatureVersion != null) {
            final S3Protocol.AuthenticationHeaderSignatureVersion authenticationHeaderSignatureVersion
                    = S3Protocol.AuthenticationHeaderSignatureVersion.valueOf(StringUtils.remove(forceRequestSignatureVersion, "-"));
            log.warn(String.format("Switched authentication signature version to %s", forceRequestSignatureVersion));
            session.setSignatureVersion(authenticationHeaderSignatureVersion);
        }
        super.authorizeHttpRequest(httpMethod, context, forceRequestSignatureVersion);
    }

    @Override
    public HttpResponse performRestGet(final String bucketName, final String objectKey,
                                       final Map<String, String> requestParameters, final Map<String, Object> requestHeaders,
                                       final int[] expectedStatusCodes) throws ServiceException {
        return super.performRestGet(bucketName, objectKey, requestParameters, requestHeaders, expectedStatusCodes);
    }

    @Override
    protected boolean isXmlContentType(final String contentType) {
        if(null == contentType) {
            return false;
        }
        if(StringUtils.startsWithIgnoreCase(contentType, "application/xml")) {
            return true;
        }
        if(StringUtils.startsWithIgnoreCase(contentType, "text/xml")) {
            return true;
        }
        return false;
    }


    /**
     * @return Null if no container component in hostname prepended
     */
    public static String findBucketInHostname(final Host host) {
        if(StringUtils.isBlank(host.getProtocol().getDefaultHostname())) {
            if(log.isDebugEnabled()) {
                log.debug(String.format("No default hostname set in %s", host.getProtocol()));
            }
            return null;
        }
        final String hostname = host.getHostname();
        if(hostname.equals(host.getProtocol().getDefaultHostname())) {
            return null;
        }
        if(hostname.endsWith(host.getProtocol().getDefaultHostname())) {
            if(log.isDebugEnabled()) {
                log.debug(String.format("Find bucket name in %s", hostname));
            }
            return ServiceUtils.findBucketNameInHostname(hostname, host.getProtocol().getDefaultHostname());
        }
        return null;
    }
}
