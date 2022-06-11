package com.mcmoddev.relauncher.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public interface BaseProcessManager {
    /**
     * Starts a process.
     */
    void startProcess();

    /**
     * Tries to start the process, after the launcher was started.
     */
    void tryFirstStart();

    /**
     * Clears the currently running process.
     */
    void clearProcess();

    /**
     * @return the currently running process, or else null
     */
    @Nullable
    ProcessInfo getProcess();

    /**
     * Kills the currently running process.
     * @param forcibly if the process should be forcibly destroyed
     */
    default void destroy(boolean forcibly) {
        if (getProcess() != null) {
            if (forcibly) getProcess().process().destroyForcibly();
            else getProcess().process().destroy();
        }
    }


    /**
     * Gets the agent jar as an {@link InputStream InputStream}.
     *
     * @return the agent
     * @apiNote it is recommended the agent is stored as a resource
     */
    @NotNull
    default InputStream getAgentResource() {
        var agent = getClass().getResourceAsStream("/relauncher-agent.jar");
        if (agent == null) {
            // If we can't find it as a .jar, try a .zip
            agent = getClass().getResourceAsStream("/relauncher-agent.zip");
        }
        return Objects.requireNonNull(agent);
    }

    /**
     * Gets the path of the agent to be installed on the process.
     *
     * @return the path of the agent
     */
    @NotNull
    Path getAgentPath();

    /**
     * @return an optional which may contain the version of the current process
     */
    Optional<String> getProcessVersion();
}
