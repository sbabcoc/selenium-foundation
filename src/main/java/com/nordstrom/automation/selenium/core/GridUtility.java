package com.nordstrom.automation.selenium.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nordstrom.automation.selenium.AbstractSeleniumConfig;
import com.nordstrom.automation.selenium.AbstractSeleniumConfig.SeleniumSettings;
import com.nordstrom.automation.selenium.SeleniumConfig;
import com.nordstrom.automation.selenium.core.SeleniumGrid.GridServer;
import com.nordstrom.automation.selenium.exceptions.GridServerLaunchFailedException;
import com.nordstrom.common.base.UncheckedThrow;

/**
 * This class provides basic support for interacting with a Selenium Grid instance.
 */
public final class GridUtility {
    
    private static SeleniumGrid seleniumGrid;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GridUtility.class);
    
    /**
     * Private constructor to prevent instantiation.
     */
    private GridUtility() {
        throw new AssertionError("GridUtility is a static utility class that cannot be instantiated");
    }
    
    /**
     * Determine if the configured Selenium Grid hub is active.
     * 
     * @param hubHost HTTP host connection to be checked (may be {@code null})
     * @return 'true' if configured hub is active; otherwise 'false'
     */
    public static boolean isHubActive(HttpHost hubHost) {
        return isHostActive(hubHost, GridServer.HUB_CONFIG);
    }

    /**
     * Determine if the specified Selenium Grid host (hub or node) is active.
     * 
     * @param host HTTP host connection to be checked (may be {@code null})
     * @param request request path (may include parameters)
     * @return 'true' if specified host is active; otherwise 'false'
     */
    public static boolean isHostActive(final HttpHost host, final String request) {
        if (host != null) {
            try {
                HttpResponse response = getHttpResponse(host, request);
                return (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
            } catch (IOException e) { //NOSONAR
                // nothing to do here
            }
        }
        return false;
    }
    
    /**
     * Send the specified GET request to the indicated host.
     * 
     * @param host target HTTP host connection
     * @param request request path (may include parameters)
     * @return host response for the specified GET request
     * @throws IOException The request triggered an I/O exception
     */
    public static HttpResponse getHttpResponse(final HttpHost host, final String request) throws IOException {
        HttpClient client = HttpClientBuilder.create().build();
        URL sessionURL = new URL(host.toURI() + request);
        BasicHttpEntityEnclosingRequest basicHttpEntityEnclosingRequest = 
                new BasicHttpEntityEnclosingRequest("GET", sessionURL.toExternalForm());
        return client.execute(host, basicHttpEntityEnclosingRequest);
    }
    
    /**
     * Get a driver with "current" capabilities from the active Selenium Grid.
     * <p>
     * <b>NOTE</b>: This method acquires Grid URL and desired driver capabilities from the active configuration.
     * 
     * @return driver object (may be 'null')
     */
    public static WebDriver getDriver() {
        SeleniumConfig config = AbstractSeleniumConfig.getConfig();
        
        HttpHost hubHost = config.getHubHost();
        
        if (hubHost == null) {
            String localHost = LocalSeleniumGrid.getLocalHost();
            int hubPort = config.getInt(SeleniumSettings.HUB_PORT.key());
            hubHost = new HttpHost(localHost, hubPort);
        }
        
        boolean isActive = isHubActive(hubHost);
        
        if (isActive) {
            
        } else if (isLocalHost(hubHost)) {
            try {
                seleniumGrid = LocalSeleniumGrid.launch(config, config.getHubConfigPath());
                isActive = true;
            } catch (GridServerLaunchFailedException | IOException | TimeoutException e) {
                LOGGER.warn("Unable to launch Selenium Grid server", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (isActive) {
            hubHost = config.getHubHost();
            Capabilities capabilities = config.getCurrentCapabilities();
            return getDriver(hubHost, capabilities);
        }
        
        throw new IllegalStateException("Unable to launch local Selenium Grid instance");
    }
    
    /**
     * Get a driver with desired capabilities from specified Selenium Grid hub.
     * 
     * @param hubHost Grid hub from which to obtain the driver
     * @param desiredCapabilities desired capabilities for the driver
     * @return driver object (may be 'null')
     */
    public static WebDriver getDriver(HttpHost hubHost, Capabilities desiredCapabilities) {
        Objects.requireNonNull(hubHost, "[gridHub] must be non-null");
        try {
            if (isHubActive(hubHost)) {
                URL remoteAddress = URI.create(hubHost.toURI() + GridServer.HUB_BASE).toURL();
                return new RemoteWebDriver(remoteAddress, desiredCapabilities);
            } else {
                throw new IllegalStateException("No Selenium Grid instance was found at " + hubHost);
            }
        } catch (MalformedURLException e) {
            throw UncheckedThrow.throwUnchecked(e);
        }
    }
    
    /**
     * Read available input from the specified input stream.
     * 
     * @param inputStream input stream
     * @return available input
     * @throws IOException if an I/O error occurs
     */
    public static String readAvailable(InputStream inputStream) throws IOException {
        int length;
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * Get the list of node endpoints attached to the specified Selenium Grid hub.
     * @param hubHost Grid hub host
     * 
     * @return list of node endpoints
     * @throws IOException if an I/O error occurs
     */
    public static List<String> getGridProxies(HttpHost hubHost) throws IOException {
        String url = hubHost.toURI() + GridServer.GRID_CONSOLE;
        Document doc = Jsoup.connect(url).get();
        Elements proxyIds = doc.select("p.proxyid");
        List<String> nodeList = new ArrayList<>();
        for (Element proxyId : proxyIds) {
            String text = proxyId.text();
            int beginIndex = text.indexOf("http");
            int endIndex = text.indexOf(',');
            nodeList.add(text.substring(beginIndex, endIndex));
        }
        return nodeList;
    }
    
    /**
     * Get capabilities of the indicated node of the specified Selenium Grid hub.
     * 
     * @param config {@link SeleniumConfig} object
     * @param hubHost Grid hub host
     * @param nodeEndpoint node endpoint
     * @return {@link Capabilities} object for the specified node
     * @throws IOException if an I/O error occurs
     */
    public static Capabilities getNodeCapabilities(SeleniumConfig config, HttpHost hubHost, String nodeEndpoint) throws IOException {
        String json;
        String url = hubHost.toURI() + GridServer.NODE_CONFIG + "?id=" + nodeEndpoint;
        try (InputStream is = new URL(url).openStream()) {
            json = readAvailable(is);
        }
        return config.getCapabilitiesForJson(json);
    }

    /**
     * Determine if the specified server is the local host.
     * 
     * @param host HTTP host connection to be checked
     * @return 'true' if server is local host; otherwise 'false'
     */
    public static boolean isLocalHost(HttpHost host) {
        try {
            InetAddress addr = InetAddress.getByName(host.getHostName());
            return (GridUtility.isThisMyIpAddress(addr));
        } catch (UnknownHostException e) {
            LOGGER.warn("Unable to get IP address for '{}'", host.getHostName(), e);
            return false;
        }
    }
    
    /**
     * Determine if the specified address is local to the machine we're running on.
     * 
     * @param addr Internet protocol address object
     * @return 'true' if the specified address is local; otherwise 'false'
     */
    public static boolean isThisMyIpAddress(final InetAddress addr) {
        // Check if the address is a valid special local or loop back
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
            return true;
        }

        // Check if the address is defined on any interface
        try {
            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (SocketException e) { //NOSONAR
            LOGGER.warn("Attempt to associate IP address with adapter triggered I/O exception: {}", e.getMessage());
            return false;
        }
    }
}
