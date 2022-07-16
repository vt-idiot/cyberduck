package ch.cyberduck.core.s3;/*
 * Copyright (c) 2002-2022 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

import ch.cyberduck.core.Host;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathNormalizer;
import ch.cyberduck.core.Scheme;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.Location;
import ch.cyberduck.core.preferences.HostPreferences;
import ch.cyberduck.core.preferences.PreferencesReader;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.util.InetAddressUtils;
import org.apache.http.protocol.HTTP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jets3t.service.utils.RestUtils;
import org.jets3t.service.utils.ServiceUtils;

import java.util.Date;
import java.util.Map;

public class S3RequestFactory {
    private static final Logger log = LogManager.getLogger(S3RequestFactory.class);

    private final S3Session session;

    public S3RequestFactory(final S3Session session) {
        this.session = session;
    }

    public HttpUriRequest create(final String method, final String bucketName,
                                 final String objectKey, final Map<String, String> requestParameters) {
        final Host host = session.getHost();
        final PreferencesReader preferences = new HostPreferences(host);
        // Hostname taking into account transfer acceleration and bucket region
        String endpoint = host.getHostname();
        // Apply default configuration
        if(S3Session.isAwsHostname(host.getHostname(), false)) {
            if(StringUtils.isNotBlank(host.getRegion())) {
                if(log.isDebugEnabled()) {
                    log.debug(String.format("Apply default region %s to endpoint", host.getRegion()));
                }
                // Apply default region
                endpoint = createRegionSpecificEndpoint(host, host.getRegion());
            }
            else {
                // Only for AWS set endpoint to region specific
                if(preferences.getBoolean("s3.transferacceleration.enable")) {
                    // Already set to accelerated endpoint
                    if(log.isDebugEnabled()) {
                        log.debug(String.format("Use accelerated endpoint %s", S3TransferAccelerationService.S3_ACCELERATE_DUALSTACK_HOSTNAME));
                    }
                    endpoint = S3TransferAccelerationService.S3_ACCELERATE_DUALSTACK_HOSTNAME;
                }
                else {
                    // Only attempt to determine region specific endpoint if virtual host style requests are enabled
                    if(allowsVirtualHostStyleRequests(host)) {
                        // Check if not already request to query bucket location
                        if(!requestParameters.containsKey("location")) {
                            if(StringUtils.isNotBlank(bucketName)) {
                                if(log.isDebugEnabled()) {
                                    log.debug(String.format("Determine region for bucket %s", bucketName));
                                }
                                try {
                                    // Determine region for bucket using cache
                                    final Location.Name region = new S3LocationFeature(session, session.getClient().getRegionEndpointCache())
                                            .getLocation(bucketName);
                                    if(Location.unknown == region) {
                                        // Missing permission or not supported
                                        log.warn(String.format("Failure determining bucket location for %s", bucketName));
                                        endpoint = host.getHostname();
                                    }
                                    else {
                                        if(log.isDebugEnabled()) {
                                            log.debug(String.format("Determined region %s for bucket %s", region, bucketName));
                                        }
                                        endpoint = createRegionSpecificEndpoint(host, region.getIdentifier());
                                    }
                                }
                                catch(BackgroundException e) {
                                    // Ignore failure reading location for bucket
                                    log.error(String.format("Failure %s determining bucket location for %s", e, bucketName));
                                    endpoint = createRegionSpecificEndpoint(host, preferences.getProperty("s3.location"));
                                }
                            }
                        }
                    }
                    else {
                        log.warn(String.format("Virtual host style requests are disabled for %s", host));
                    }
                }
            }
        }
        if(log.isDebugEnabled()) {
            log.debug(String.format("Set endpoint to %s", endpoint));
        }
        // Virtual host style endpoint including bucket name
        String hostname = endpoint;
        String resource = String.valueOf(Path.DELIMITER);
        if(allowsVirtualHostStyleRequests(host)) {
            // Virtual host style requests enabled in connection profile
            if(StringUtils.isNotBlank(bucketName)) {
                if(ServiceUtils.isBucketNameValidDNSName(bucketName)) {
                    hostname = String.format("%s.%s", bucketName, endpoint);
                }
                else {
                    // Add bucket name to path
                    resource += bucketName + Path.DELIMITER;
                }
            }
        }
        else {
            if(StringUtils.isNotBlank(bucketName)) {
                // Add bucket name to path
                resource += bucketName + Path.DELIMITER;
            }
        }
        final HttpUriRequest request;
        // Prefix endpoint with bucket name for actual hostname
        if(log.isDebugEnabled()) {
            log.debug(String.format("Set hostname to %s", hostname));
        }
        final String virtualPath;
        // Allow for non-standard virtual directory paths on the server-side
        if(StringUtils.isNotBlank(host.getProtocol().getContext()) && !Scheme.isURL(host.getProtocol().getContext())) {
            virtualPath = PathNormalizer.normalize(host.getProtocol().getContext());
        }
        else {
            virtualPath = StringUtils.EMPTY;
        }
        if(objectKey != null) {
            resource += RestUtils.encodeUrlPath(objectKey, "/");
        }
        // Construct a URL representing a connection for the S3 resource.
        final StringBuilder url = new StringBuilder(String.format("%s://%s:%d%s%s", host.getProtocol().getScheme(), hostname, host.getPort(), virtualPath, resource));
        for(Map.Entry<String, String> entry : requestParameters.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            if(log.isDebugEnabled()) {
                log.debug(String.format("Added request parameter: %s=%s", key, value));
            }
            url.append(!url.toString().contains("?") ? "?" : "&").append(RestUtils.encodeUrlString(key));
            if(value != null) {
                url.append("=").append(RestUtils.encodeUrlString(value));
            }
        }
        if(log.isDebugEnabled()) {
            log.debug(String.format("Set URL to %s", url));
        }
        switch(method) {
            case "PUT":
                request = new HttpPut(url.toString());
                break;
            case "POST":
                request = new HttpPost(url.toString());
                break;
            case "HEAD":
                request = new HttpHead(url.toString());
                break;
            case "GET":
                request = new HttpGet(url.toString());
                break;
            case "DELETE":
                request = new HttpDelete(url.toString());
                break;
            default:
                throw new IllegalArgumentException(String.format("Unrecognised HTTP method name %s", method));
        }
        // Set mandatory Request headers.
        if(request.getFirstHeader("Date") == null) {
            request.setHeader("Date", ServiceUtils.formatRfc822Date(new Date(System.currentTimeMillis())));
        }
        if(preferences.getBoolean("s3.upload.expect-continue")) {
            if("PUT".equals(request.getMethod())) {
                // #7621
                request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
            }
        }
        if(preferences.getBoolean("s3.bucket.requesterpays")) {
            // Only for AWS
            if(S3Session.isAwsHostname(host.getHostname())) {
                // Downloading Objects in Requester Pays Buckets
                if("GET".equals(request.getMethod()) || "POST".equals(request.getMethod())) {
                    if(!preferences.getBoolean("s3.bucket.requesterpays")) {
                        // For GET and POST requests, include x-amz-request-payer : requester in the header
                        request.addHeader("x-amz-request-payer", "requester");
                    }
                }
            }
        }
        return request;
    }

    public static boolean allowsVirtualHostStyleRequests(final Host host) {
        if(InetAddressUtils.isIPv4Address(host.getHostname()) || InetAddressUtils.isIPv6Address(host.getHostname())) {
            return false;
        }
        return !new HostPreferences(host).getBoolean("s3.bucket.virtualhost.disable");
    }

    public static String createRegionSpecificEndpoint(final Host host, final String region) {
        final PreferencesReader preferences = new HostPreferences(host);
        if(log.isDebugEnabled()) {
            log.debug(String.format("Apply region %s to endpoint", region));
        }
        if(preferences.getBoolean("s3.endpoint.dualstack.enable")) {
            return String.format(preferences.getProperty("s3.endpoint.format.ipv6"), region);
        }
        return String.format(preferences.getProperty("s3.endpoint.format.ipv4"), region);
    }
}
