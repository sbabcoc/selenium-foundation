package com.nordstrom.automation.selenium.plugins;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.openqa.grid.common.GridRole;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.net.PortProber;
import org.openqa.selenium.os.CommandLine;

import com.nordstrom.automation.selenium.AbstractSeleniumConfig.SeleniumSettings;
import com.nordstrom.automation.selenium.DriverPlugin;
import com.nordstrom.automation.selenium.SeleniumConfig;
import com.nordstrom.automation.selenium.core.GridUtility;
import com.nordstrom.automation.selenium.core.LocalSeleniumGrid.LocalGridServer;
import com.nordstrom.automation.selenium.core.SeleniumGrid.GridServer;
import com.nordstrom.automation.selenium.exceptions.GridServerLaunchFailedException;
import com.nordstrom.automation.selenium.utility.BinaryFinder;

import net.bytebuddy.implementation.Implementation;

/**
 * This class provides the base plugin implementation for drivers provided by {@code appium}.
 */
public abstract class AbstractAppiumPlugin implements DriverPlugin {

    private static final String[] DEPENDENCY_CONTEXTS = {};
    private static final String[] APPIUM_PATH_TAIL = { "appium", "build", "lib", "main.js" };
    private static final String[] PROPERTY_NAMES = {};
    
    private static final Pattern OPTION_PATTERN = Pattern.compile("\\s*(-[a-zA-Z0-9]+|--[a-zA-Z0-9]+(?:-[a-zA-Z0-9]+)*)");
    
    private String browserName;
    
    protected AbstractAppiumPlugin(String browserName) {
        this.browserName = browserName;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getDependencyContexts() {
        return DEPENDENCY_CONTEXTS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBrowserName() {
        return browserName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getPropertyNames() {
        return PROPERTY_NAMES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LocalGridServer start(SeleniumConfig config, String launcherClassName, String[] dependencyContexts,
            GridServer hubServer, Path workingPath, Path outputPath) throws IOException {
        
        String capabilities = getCapabilities(config);
        Path nodeConfigPath = config.createNodeConfig(capabilities, hubServer.getUrl());
        
        List<String> argsList = new ArrayList<>();

        argsList.add(findMainScript().getAbsolutePath());
        
        String hostUrl = GridUtility.getLocalHost();
        Integer portNum = Integer.valueOf(PortProber.findFreePort());
        
        // specify server host
        argsList.add("--address");
        argsList.add(hostUrl);
        
        // specify server port
        argsList.add("--port");
        argsList.add(portNum.toString());
        
        // allow specification of multiple command line arguments
        String[] cliArgs = config.getStringArray(SeleniumSettings.APPIUM_CLI_ARGS.key());
        // if args specified
        if (cliArgs != null) {
            int head = 0;
            int tail = 0;
            int next = 0;
            int index = 0;
            boolean doLoop;
            
            // iterate over specifications
            for (String thisArg : cliArgs) {
                doLoop = true;
                Matcher matcher = OPTION_PATTERN.matcher(thisArg);
                
                // until done
                while (doLoop) {
                    // save list end index
                    index = argsList.size();
                    
                    // if option found
                    if (matcher.find()) {
                        // add option to args list
                        argsList.add(matcher.group(1));
                        // set last value tail 
                        tail = matcher.start();
                        // save next value head
                        next = matcher.end() + 1;
                    // otherwise
                    } else {
                        // set final value tail
                        tail = thisArg.length();
                        // set 'done'
                        doLoop = false;
                    }
                    
                    // if maybe value
                    if (head < tail) {
                        // extract potential value, trimming ends
                        String value = thisArg.substring(head, tail).trim();
                        
                        // if value is defined
                        if ( ! value.isEmpty()) {
                            // insert at saved index
                            argsList.add(index, value);
                        }
                    }
                    
                    // advance
                    head = next;
                }
            }
        }
        
        argsList.add("--nodeconfig");
        argsList.add(nodeConfigPath.toString());
        
        String executable = findNodeBinary().getAbsolutePath();
        CommandLine process = new CommandLine(executable, argsList.toArray(new String[0]));
        return new LocalGridServer(hostUrl, portNum, GridRole.NODE, process, workingPath, outputPath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Implementation getWebElementCtor(WebDriver driver, Class<? extends WebElement> refClass) {
        return null;
    }
    
    /**
     * Find the 'npm' (Node Package Manager) binary.
     * 
     * @return path to the 'npm' binary as a {@link File} object
     * @throws GridServerLaunchFailedException if 'npm' isn't found
     */
    private static File findNPM() throws GridServerLaunchFailedException {
        return findBinary("npm", SeleniumSettings.NPM_BINARY_PATH, "'npm' package manager");
    }
    
    /**
     * Find the 'node' binary.
     * 
     * @return path to the 'node' binary as a {@link File} object
     * @throws GridServerLaunchFailedException if 'npm' isn't found
     */
    private static File findNodeBinary() throws GridServerLaunchFailedException {
        return findBinary("node", SeleniumSettings.NODE_BINARY_PATH, "'node' JavaScript runtime");
    }
    
    /**
     * Find the 'appium' main script in the global 'node' modules repository.
     * 
     * @return path path to the 'appium' main script as a {@link File} object
     * @throws GridServerLaunchFailedException if the 'appium' main script isn't found
     */
    private static File findMainScript() throws GridServerLaunchFailedException {
        // check configuration for path to 'appium' main script
        try {
            return findBinary("main.js", SeleniumSettings.APPIUM_BINARY_PATH, "'appium' main script");
        } catch (GridServerLaunchFailedException eaten) {
            // path not specified - check modules repository below
        }
        
        // check for 'appium' main script in global 'node' modules repository
        
        String nodeModulesRoot;
        File npm = findNPM().getAbsoluteFile();
        
        List<String> argsList = new ArrayList<>();
        
        if (SystemUtils.IS_OS_WINDOWS) {
            argsList.add("cmd.exe");
            argsList.add("/c");
        }
        
        argsList.add(npm.getName());
        argsList.add("root");
        argsList.add("-g");
        
        ProcessBuilder builder = new ProcessBuilder(argsList);
        builder.directory(npm.getParentFile());
        
        try {
            nodeModulesRoot = IOUtils.toString(builder.start().getInputStream(), StandardCharsets.UTF_8).trim();
            File appiumMain = Paths.get(nodeModulesRoot, APPIUM_PATH_TAIL).toFile();
            if (appiumMain.exists()) return appiumMain;
            throw fileNotFound("'appium' main script", SeleniumSettings.APPIUM_BINARY_PATH);
        } catch (IOException cause) {
            throw new GridServerLaunchFailedException("node", cause);
        }
    }
    
    /**
     * Find the specified binary.
     * 
     * @param exeName file name of binary to find
     * @param setting associated configuration setting
     * @param what human-readable description of binary
     * @return path to specified binary as a {link File} object
     * @throws GridServerLaunchFailedException if specified binary isn't found
     */
    private static File findBinary(String exeName, SeleniumSettings setting, String what)
            throws GridServerLaunchFailedException {
        try {
            return BinaryFinder.findBinary(exeName, setting.key(), null, null);
        } catch (IllegalStateException eaten) {
            IOException cause = fileNotFound(what, setting);
            throw new GridServerLaunchFailedException("node", cause);
        }
    }
    
    /**
     * Assemble a 'file not found' exception for the indicated binary.
     * 
     * @param what human-readable description of binary
     * @param setting associated configuration setting
     * @return {@link FileNotFoundException} object
     */
    private static IOException fileNotFound(String what, SeleniumSettings setting) {
        String template = "%s not found; configure the %s setting (key: %s)";
        return new FileNotFoundException(String.format(template, what, setting.name(), setting.key()));
    }

}
